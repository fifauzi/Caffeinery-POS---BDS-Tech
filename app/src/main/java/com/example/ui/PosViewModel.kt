package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsStorageService
import com.example.data.local.model.CashExpenseEntity
import com.example.data.local.model.CategoryEntity
import com.example.data.local.model.OrderEntity
import com.example.data.local.model.OrderItemEntity
import com.example.data.local.model.ProductEntity
import com.example.data.local.model.OpenBillEntity
import com.example.data.local.model.ShiftEntity
import com.example.data.repository.PosRepository
import org.json.JSONArray
import org.json.JSONObject
import com.example.domain.model.CartItem
import com.example.domain.model.DiscountType
import com.example.domain.model.AdditionalFeesConfig
import com.example.domain.model.EmployeeItem
import com.example.domain.model.OrderMethodsConfig
import com.example.domain.model.OutletInfo
import com.example.domain.model.PaymentGatewaysConfig
import com.example.domain.model.ReceiptCustomizerConfig
import com.example.domain.model.VariantOption
import com.example.service.BluetoothPrinterDevice
import com.example.service.CsvExportService
import com.example.service.EscPosPrinterService
import com.example.service.QrisPayloadGenerator
import com.example.service.ShiftReportService
import com.example.service.VariantDataMapper
import com.example.ui.components.CurrencyUtils
import com.example.ui.components.DateTimeUtils
import com.example.ui.components.PosTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

import com.example.domain.model.TakeawayChargeCalculator
import kotlinx.coroutines.withContext

data class CartSummary(
    val items: List<CartItem> = emptyList(),
    val subtotal: Double = 0.0,
    val itemDiscountTotal: Double = 0.0,
    val orderDiscountType: DiscountType = DiscountType.NONE,
    val orderDiscountValue: Double = 0.0,
    val orderDiscountAmount: Double = 0.0,
    val serviceFeePercentage: Double = 0.0,
    val serviceFeeAmount: Double = 0.0,
    val packagingFeeAmount: Double = 0.0,
    val eligibleTakeawayItemCount: Int = 0,
    val taxPercentage: Double = 10.0, // 10% PB1
    val taxAmount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val totalItemCount: Int = 0
)

class PosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PosRepository
    private val settingsStorage: SettingsStorageService = SettingsStorageService(application)

    // Draft Restoration Banner
    private val _wasDraftRestored = MutableStateFlow(false)
    val wasDraftRestored: StateFlow<Boolean> = _wasDraftRestored.asStateFlow()

    fun dismissDraftRestoredBanner() {
        _wasDraftRestored.value = false
    }

    // Multi-Role Staff Authentication State
    private val _currentUser = MutableStateFlow<EmployeeItem?>(null)
    val currentUser: StateFlow<EmployeeItem?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // Active Shift Session & Modals
    private val _activeShiftSession = MutableStateFlow<ShiftEntity?>(null)
    val activeShiftSession: StateFlow<ShiftEntity?> = _activeShiftSession.asStateFlow()

    private val _showOpenShiftModal = MutableStateFlow(false)
    val showOpenShiftModal: StateFlow<Boolean> = _showOpenShiftModal.asStateFlow()

    private val _showCloseShiftModal = MutableStateFlow(false)
    val showCloseShiftModal: StateFlow<Boolean> = _showCloseShiftModal.asStateFlow()

    private val _showCashExpenseModal = MutableStateFlow(false)
    val showCashExpenseModal: StateFlow<Boolean> = _showCashExpenseModal.asStateFlow()

    private val _activeShiftOrdersForClose = MutableStateFlow<List<OrderEntity>>(emptyList())
    val activeShiftOrdersForClose: StateFlow<List<OrderEntity>> = _activeShiftOrdersForClose.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeShiftExpenses: StateFlow<List<CashExpenseEntity>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = PosRepository(database.posDao())
        activeShiftExpenses = _activeShiftSession
            .flatMapLatest { shift ->
                if (shift == null) flowOf(emptyList())
                else repository.getCashExpensesForShiftFlow(shift.shiftCode)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        settingsStorage.initDefaultsIfEmpty()

        viewModelScope.launch(Dispatchers.IO) {
            // Seed static master catalog once on fresh install without overwriting custom edits
            if (!settingsStorage.isMasterCatalogSeeded()) {
                repository.seedCatalogIfEmpty()
                settingsStorage.markMasterCatalogSeeded()
            }

            // Restore active shift session from Room if any
            val existingShift = repository.getActiveShift()

            launch(Dispatchers.Main) {
                _activeShiftSession.value = existingShift
                if (existingShift != null) {
                    _activeShift.value = existingShift.shiftName
                }
                restoreActiveCartDraft()
            }
        }
    }

    // Role-Based Tab Navigation Guard
    private val _currentTab = MutableStateFlow<PosTab>(PosTab.KASIR)
    val currentTab: StateFlow<PosTab> = _currentTab.asStateFlow()

    fun setCurrentTab(tab: PosTab) {
        val user = _currentUser.value
        if (user != null && !user.allowedTabs().contains(tab)) {
            // Non-authorized tab access redirect to KASIR
            _currentTab.value = PosTab.KASIR
            return
        }
        _currentTab.value = tab
    }

    // Staff Authentication Methods
    fun loginStaff(employee: EmployeeItem, pin: String): Boolean {
        if (employee.pin.isNotBlank() && employee.pin != pin.trim()) {
            return false
        }
        _currentUser.value = employee
        _isLoggedIn.value = true
        setActiveEmployee(employee)

        if (employee.isSuperAdmin()) {
            _currentTab.value = PosTab.DASHBOARD
        } else {
            _currentTab.value = PosTab.KASIR
            // For Kasir / Barista, prompt open shift if no shift is currently open
            if (_activeShiftSession.value == null) {
                _showOpenShiftModal.value = true
            }
        }
        return true
    }

    fun logoutStaff() {
        _currentUser.value = null
        _isLoggedIn.value = false
        _showOpenShiftModal.value = false
        _showCloseShiftModal.value = false
        _showCashExpenseModal.value = false
        _currentTab.value = PosTab.KASIR
    }

    // Shift Session & Petty Cash Management
    fun openShiftSession(startingCash: Double, shiftName: String) {
        val user = _currentUser.value ?: _activeEmployee.value
        val shiftCode = "SHF-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = com.example.ui.components.DateTimeUtils.WIB_ZONE
        }.format(Date())

        val newShift = ShiftEntity(
            shiftCode = shiftCode,
            staffId = user.id,
            staffName = user.name,
            staffRole = user.role,
            shiftName = shiftName,
            startingCash = startingCash,
            status = "OPEN"
        )

        // Optimistically update active shift in StateFlow immediately
        _activeShiftSession.value = newShift
        _activeShift.value = shiftName
        _showOpenShiftModal.value = false

        viewModelScope.launch(Dispatchers.IO) {
            val id = repository.openShift(newShift)
            val inserted = repository.getActiveShift()
            if (inserted != null) {
                launch(Dispatchers.Main) {
                    _activeShiftSession.value = inserted
                }
            }
        }
    }

    fun closeShiftSession(actualCash: Double, notes: String) {
        val current = _activeShiftSession.value ?: return
        // Optimistically clear active shift session and close modal immediately
        _activeShiftSession.value = null
        _showCloseShiftModal.value = false
        val isKasirOrBarista = _currentUser.value != null && !_currentUser.value!!.isSuperAdmin()
        if (isKasirOrBarista) {
            logoutStaff()
        }

        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val shiftOrders = repository.getOrdersBetween(current.openedAt, now)
            val cashSales = shiftOrders.filter { it.paymentMethod.equals("CASH", ignoreCase = true) }.sumOf { it.grandTotal }
            val bankTransferSales = shiftOrders.filter {
                it.paymentMethod.contains("TRANSFER", ignoreCase = true) ||
                it.paymentMethod.contains("TF", ignoreCase = true) ||
                it.paymentMethod.equals("BANK_TRANSFER", ignoreCase = true)
            }.sumOf { it.grandTotal }
            val qrisSales = shiftOrders.filter { it.paymentMethod.contains("QRIS", ignoreCase = true) }.sumOf { it.grandTotal }
            val nonCashSales = shiftOrders.filter { !it.paymentMethod.equals("CASH", ignoreCase = true) }.sumOf { it.grandTotal }
            val grandTotalRevenue = shiftOrders.sumOf { it.grandTotal }

            val shiftExpenses = repository.getCashExpensesForShiftSync(current.shiftCode)
            val totalCashExpenses = shiftExpenses.sumOf { it.amount }
            val expected = (current.startingCash + cashSales) - totalCashExpenses
            val diff = actualCash - expected

            // Fetch sold products for comprehensive product sales breakdown
            val invoices = shiftOrders.map { it.invoiceNumber }
            val orderItems = if (invoices.isNotEmpty()) repository.getOrderItemsForInvoices(invoices) else emptyList()
            val productSalesMap = orderItems.groupBy { it.productName }
                .mapValues { (_, items) -> items.sumOf { it.quantity } }

            val updated = current.copy(
                closedAt = now,
                totalCashSales = cashSales,
                totalNonCashSales = nonCashSales,
                totalCashExpenses = totalCashExpenses,
                totalTransactions = shiftOrders.size,
                expectedCash = expected,
                actualCash = actualCash,
                cashDifference = diff,
                notes = notes,
                status = "CLOSED"
            )
            repository.updateShift(updated)

            // Auto-print Shift Closing Report to paired Bluetooth thermal printer
            val address = _selectedPrinterAddress.value
            val rc = _receiptCustomizer.value
            val paperWidth = if (rc.paperSize == "80mm") 48 else 32
            val payload = EscPosPrinterService.buildShiftClosingReceiptBytes(
                shift = updated,
                cashSales = cashSales,
                nonCashSales = nonCashSales,
                bankTransferSales = bankTransferSales,
                qrisSales = qrisSales,
                grandTotalRevenue = grandTotalRevenue,
                actualCash = actualCash,
                transactionCount = shiftOrders.size,
                productSales = productSalesMap,
                cashExpenses = shiftExpenses,
                totalCashExpenses = totalCashExpenses,
                storeName = _outletInfo.value.name,
                storeBranch = _outletInfo.value.branch,
                paperWidth = paperWidth,
                context = getApplication(),
                showLogo = rc.showLogo
            )

            if (address.isNotBlank()) {
                val printResult = EscPosPrinterService.printBytes(
                    deviceAddress = address,
                    payload = payload,
                    context = getApplication()
                )
                printResult.onSuccess { msg ->
                    launch(Dispatchers.Main) {
                        _printerStatusMessage.value = msg
                        _printerErrorMessage.value = null
                        _showPrinterDisconnectedAlert.value = false
                    }
                }.onFailure { err ->
                    val errorMsg = err.localizedMessage ?: "Gagal mencetak: Perangkat printer Bluetooth terputus atau mati"
                    launch(Dispatchers.Main) {
                        _printerStatusMessage.value = errorMsg
                        _printerErrorMessage.value = errorMsg
                        _showPrinterDisconnectedAlert.value = true
                    }
                }
            } else {
                launch(Dispatchers.Main) {
                    val errorMsg = "Printer terputus atau belum dipilih. Pastikan printer Bluetooth menyala"
                    _printerStatusMessage.value = errorMsg
                    _printerErrorMessage.value = errorMsg
                    _showPrinterDisconnectedAlert.value = true
                }
            }
        }
    }

    fun requestOpenShift() {
        _showOpenShiftModal.value = true
    }

    fun dismissOpenShift() {
        _showOpenShiftModal.value = false
    }

    fun requestOpenCashExpenseModal() {
        if (_activeShiftSession.value == null) {
            _showOpenShiftModal.value = true
            return
        }
        _showCashExpenseModal.value = true
    }

    fun dismissCashExpenseModal() {
        _showCashExpenseModal.value = false
    }

    fun recordCashExpense(description: String, amount: Double) {
        val current = _activeShiftSession.value ?: return
        val cleanDesc = description.trim()
        if (cleanDesc.isBlank() || amount <= 0.0) return
        val user = _currentUser.value ?: _activeEmployee.value
        val expense = CashExpenseEntity(
            shiftId = current.id,
            shiftCode = current.shiftCode,
            staffName = user.name,
            description = cleanDesc,
            amount = amount,
            timestamp = System.currentTimeMillis()
        )
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertCashExpense(expense)
        }
    }

    fun deleteCashExpense(expenseId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCashExpenseById(expenseId)
        }
    }

    fun requestCloseShift() {
        val current = _activeShiftSession.value
        _activeShiftOrdersForClose.value = getOrdersForActiveShift()
        _showCloseShiftModal.value = true
        if (current != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val freshOrders = repository.getOrdersBetween(current.openedAt, now)
                launch(Dispatchers.Main) {
                    _activeShiftOrdersForClose.value = freshOrders
                }
            }
        }
    }

    fun dismissCloseShift() {
        _showCloseShiftModal.value = false
    }

    fun getOrdersForActiveShift(): List<OrderEntity> {
        val current = _activeShiftSession.value ?: return emptyList()
        val now = System.currentTimeMillis()
        val all = orders.value
        return all.filter { DateTimeUtils.getOrderEffectiveTimestamp(it) in current.openedAt..now }
    }

    suspend fun buildShiftClosingWhatsAppReportForShift(
        shift: ShiftEntity,
        actualCash: Double,
        notes: String
    ): String {
        val now = System.currentTimeMillis()
        val shiftOrders = repository.getOrdersBetween(shift.openedAt, now)
        val cashSales = shiftOrders.filter { it.paymentMethod.equals("CASH", ignoreCase = true) }.sumOf { it.grandTotal }
        val bankTransferSales = shiftOrders.filter {
            it.paymentMethod.contains("TRANSFER", ignoreCase = true) ||
            it.paymentMethod.contains("TF", ignoreCase = true) ||
            it.paymentMethod.equals("BANK_TRANSFER", ignoreCase = true)
        }.sumOf { it.grandTotal }
        val qrisSales = shiftOrders.filter { it.paymentMethod.contains("QRIS", ignoreCase = true) }.sumOf { it.grandTotal }
        val grandTotalRevenue = shiftOrders.sumOf { it.grandTotal }

        val invoices = shiftOrders.map { it.invoiceNumber }
        val orderItems = if (invoices.isNotEmpty()) repository.getOrderItemsForInvoices(invoices) else emptyList()
        val productSalesMap = orderItems.groupBy { it.productName }
            .mapValues { (_, items) -> items.sumOf { it.quantity } }

        val shiftExpenses = repository.getCashExpensesForShiftSync(shift.shiftCode)
        val totalCashExpenses = shiftExpenses.sumOf { it.amount }
        val info = _outletInfo.value

        return ShiftReportService.buildShiftClosingWhatsAppMessage(
            shift = shift.copy(closedAt = now),
            storeName = info.name,
            storeBranch = info.branch,
            cashSales = cashSales,
            qrisSales = qrisSales,
            bankTransferSales = bankTransferSales,
            grandTotalRevenue = grandTotalRevenue,
            cashExpenses = shiftExpenses,
            totalCashExpenses = totalCashExpenses,
            actualCash = actualCash,
            transactionCount = shiftOrders.size,
            productSales = productSalesMap,
            notes = notes
        )
    }

    fun sendActiveShiftReportToWhatsApp(
        context: Context,
        actualCash: Double,
        notes: String,
        targetPhone: String = _outletInfo.value.ownerWhatsAppNumber
    ) {
        val current = _activeShiftSession.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val msg = buildShiftClosingWhatsAppReportForShift(current, actualCash, notes)
            launch(Dispatchers.Main) {
                ShiftReportService.shareShiftReportToWhatsApp(context, msg, targetPhone)
            }
        }
    }

    fun printActiveShiftClosingReceipt(context: Context, actualCash: Double) {
        val current = _activeShiftSession.value ?: run {
            val noShiftMsg = "Tidak ada sesi shift aktif"
            _printerErrorMessage.value = noShiftMsg
            _printerStatusMessage.value = noShiftMsg
            _showPrinterDisconnectedAlert.value = true
            _isPrintingAnimation.value = false
            return
        }

        val address = _selectedPrinterAddress.value
        val checkResult = EscPosPrinterService.checkPrinterConnection(context, address)
        if (checkResult.isFailure) {
            val errorMsg = checkResult.exceptionOrNull()?.message ?: "Printer terputus. Pastikan printer Bluetooth menyala"
            _printerErrorMessage.value = errorMsg
            _printerStatusMessage.value = errorMsg
            _showPrinterDisconnectedAlert.value = true
            _isPrintingAnimation.value = false
            return
        }

        _isPrintingAnimation.value = true
        _printerStatusMessage.value = "Menghubungkan ke printer..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val shiftOrders = repository.getOrdersBetween(current.openedAt, now)
                val cashSales = shiftOrders.filter { it.paymentMethod.equals("CASH", ignoreCase = true) }.sumOf { it.grandTotal }
                val bankTransferSales = shiftOrders.filter {
                    it.paymentMethod.contains("TRANSFER", ignoreCase = true) ||
                    it.paymentMethod.contains("TF", ignoreCase = true) ||
                    it.paymentMethod.equals("BANK_TRANSFER", ignoreCase = true)
                }.sumOf { it.grandTotal }
                val qrisSales = shiftOrders.filter { it.paymentMethod.contains("QRIS", ignoreCase = true) }.sumOf { it.grandTotal }
                val nonCashSales = shiftOrders.filter { !it.paymentMethod.equals("CASH", ignoreCase = true) }.sumOf { it.grandTotal }
                val grandTotalRevenue = shiftOrders.sumOf { it.grandTotal }

                val shiftExpenses = repository.getCashExpensesForShiftSync(current.shiftCode)
                val totalCashExpenses = shiftExpenses.sumOf { it.amount }

                val invoices = shiftOrders.map { it.invoiceNumber }
                val orderItems = if (invoices.isNotEmpty()) repository.getOrderItemsForInvoices(invoices) else emptyList()
                val productSalesMap = orderItems.groupBy { it.productName }
                    .mapValues { (_, items) -> items.sumOf { it.quantity } }

                val rc = _receiptCustomizer.value
                val paperWidth = if (rc.paperSize == "80mm") 48 else 32
                val payload = EscPosPrinterService.buildShiftClosingReceiptBytes(
                    shift = current.copy(closedAt = now),
                    cashSales = cashSales,
                    nonCashSales = nonCashSales,
                    bankTransferSales = bankTransferSales,
                    qrisSales = qrisSales,
                    grandTotalRevenue = grandTotalRevenue,
                    actualCash = actualCash,
                    transactionCount = shiftOrders.size,
                    productSales = productSalesMap,
                    cashExpenses = shiftExpenses,
                    totalCashExpenses = totalCashExpenses,
                    storeName = _outletInfo.value.name,
                    storeBranch = _outletInfo.value.branch,
                    paperWidth = paperWidth,
                    context = context,
                    showLogo = rc.showLogo
                )

                launch(Dispatchers.Main) {
                    _printerStatusMessage.value = "Mengirim data struk penutupan kasir..."
                }

                val printResult = EscPosPrinterService.printBytes(address, payload, context)
                printResult.onSuccess { msg ->
                    launch(Dispatchers.Main) {
                        _printerStatusMessage.value = "Struk penutupan kasir berhasil dicetak!"
                        _printerErrorMessage.value = null
                        _showPrinterDisconnectedAlert.value = false
                        _isPrintingAnimation.value = false
                    }
                }.onFailure { err ->
                    val errorMsg = err.localizedMessage ?: "Gagal mencetak: Perangkat printer Bluetooth terputus atau mati"
                    launch(Dispatchers.Main) {
                        _printerStatusMessage.value = errorMsg
                        _printerErrorMessage.value = errorMsg
                        _showPrinterDisconnectedAlert.value = true
                        _isPrintingAnimation.value = false
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Gagal mencetak: Terjadi kesalahan koneksi printer"
                launch(Dispatchers.Main) {
                    _printerStatusMessage.value = errorMsg
                    _printerErrorMessage.value = errorMsg
                    _showPrinterDisconnectedAlert.value = true
                    _isPrintingAnimation.value = false
                }
            }
        }
    }

    // Search and Category Filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow("all")
    val selectedCategoryId: StateFlow<String> = _selectedCategoryId.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val products: StateFlow<List<ProductEntity>> = repository.products
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allProducts: StateFlow<List<ProductEntity>> = repository.allProductsIncludingInactive
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val orders: StateFlow<List<OrderEntity>> = repository.orders
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allOrderItems: StateFlow<List<OrderItemEntity>> = repository.allOrderItems
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val variantGroups: StateFlow<List<com.example.data.local.model.VariantGroupEntity>> = repository.variantGroups
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeOpenBills: StateFlow<List<OpenBillEntity>> = repository.activeOpenBills
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allShifts: StateFlow<List<ShiftEntity>> = repository.allShifts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addVariantGroup(group: com.example.data.local.model.VariantGroupEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addVariantGroup(group)
        }
    }

    fun updateVariantGroup(group: com.example.data.local.model.VariantGroupEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateVariantGroup(group)
        }
    }

    fun deleteVariantGroup(group: com.example.data.local.model.VariantGroupEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteVariantGroup(group)
        }
    }

    // Filtered Products for Catalog
    val filteredProducts: StateFlow<List<ProductEntity>> = combine(
        products,
        _selectedCategoryId,
        _searchQuery
    ) { all, catId, query ->
        all.filter { prod ->
            val matchesCategory = catId == "all" || prod.categoryId == catId
            val matchesSearch = query.isBlank() || prod.name.contains(query, ignoreCase = true) || prod.sku.contains(query, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectCategory(categoryId: String) {
        _selectedCategoryId.value = categoryId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Cart State
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _orderDiscountType = MutableStateFlow(DiscountType.NONE)
    val orderDiscountType: StateFlow<DiscountType> = _orderDiscountType.asStateFlow()

    private val _orderDiscountValue = MutableStateFlow(0.0)
    val orderDiscountValue: StateFlow<Double> = _orderDiscountValue.asStateFlow()

    private val _customerName = MutableStateFlow("")
    val customerName: StateFlow<String> = _customerName.asStateFlow()

    fun setCustomerName(name: String) {
        _customerName.value = name
        persistCurrentCartState()
    }

    private val _orderType = MutableStateFlow("DINE_IN") // "DINE_IN" or "TAKEAWAY"
    val orderType: StateFlow<String> = _orderType.asStateFlow()

    fun setOrderType(type: String) {
        _orderType.value = type
        persistCurrentCartState()
    }

    // Business Settings Management (Persisted via SettingsStorageService)
    private val _outletInfo = MutableStateFlow(settingsStorage.loadOutletInfo())
    val outletInfo: StateFlow<OutletInfo> = _outletInfo.asStateFlow()

    fun updateOutletInfo(info: OutletInfo) {
        _outletInfo.value = info
        settingsStorage.saveOutletInfo(info)
    }

    private val _employees = MutableStateFlow(settingsStorage.loadEmployees())
    val employees: StateFlow<List<EmployeeItem>> = _employees.asStateFlow()

    // Active Logged-in Cashier & Shift Tracking
    private val _activeEmployee = MutableStateFlow(settingsStorage.loadActiveEmployee())
    val activeEmployee: StateFlow<EmployeeItem> = _activeEmployee.asStateFlow()

    fun setActiveEmployee(emp: EmployeeItem) {
        _activeEmployee.value = emp
        settingsStorage.saveActiveEmployee(emp)
    }

    private val _activeShift = MutableStateFlow(settingsStorage.loadActiveShift())
    val activeShift: StateFlow<String> = _activeShift.asStateFlow()

    fun setActiveShift(shift: String) {
        _activeShift.value = shift
        settingsStorage.saveActiveShift(shift)
    }

    fun addEmployee(name: String, role: String, pin: String) {
        val newItem = EmployeeItem(name = name, role = role, pin = pin)
        val updated = _employees.value + newItem
        _employees.value = updated
        settingsStorage.saveEmployees(updated)
        if (_activeEmployee.value.id.isBlank() || _activeEmployee.value.name == "Belum Ada Kasir") {
            setActiveEmployee(newItem)
        }
    }

    fun updateEmployee(updatedEmployee: EmployeeItem) {
        val currentList = _employees.value
        val updatedList = currentList.map { if (it.id == updatedEmployee.id) updatedEmployee else it }
        _employees.value = updatedList
        settingsStorage.saveEmployees(updatedList)
        if (_activeEmployee.value.id == updatedEmployee.id) {
            setActiveEmployee(updatedEmployee)
        }
        if (_currentUser.value?.id == updatedEmployee.id) {
            _currentUser.value = updatedEmployee
        }
    }

    fun removeEmployee(id: String): Boolean {
        val currentList = _employees.value
        val target = currentList.find { it.id == id } ?: return false
        val ownerCount = currentList.count { it.role.equals("Owner", ignoreCase = true) }
        if (target.role.equals("Owner", ignoreCase = true) && ownerCount <= 1) {
            return false
        }
        val updated = currentList.filter { it.id != id }
        _employees.value = updated
        settingsStorage.saveEmployees(updated)
        if (_activeEmployee.value.id == id) {
            val fallback = updated.firstOrNull() ?: SettingsStorageService.defaultOwnerEmployee()
            setActiveEmployee(fallback)
        }
        if (_currentUser.value?.id == id) {
            _currentUser.value = updated.firstOrNull()
        }
        return true
    }

    private val _orderMethods = MutableStateFlow(settingsStorage.loadOrderMethods())
    val orderMethods: StateFlow<OrderMethodsConfig> = _orderMethods.asStateFlow()

    fun updateOrderMethods(config: OrderMethodsConfig) {
        _orderMethods.value = config
        settingsStorage.saveOrderMethods(config)
    }

    private val _paymentGateways = MutableStateFlow(settingsStorage.loadPaymentGateways())
    val paymentGateways: StateFlow<PaymentGatewaysConfig> = _paymentGateways.asStateFlow()

    fun updatePaymentGateways(config: PaymentGatewaysConfig) {
        _paymentGateways.value = config
        settingsStorage.savePaymentGateways(config)
    }

    private val _additionalFees = MutableStateFlow(settingsStorage.loadAdditionalFees())
    val additionalFees: StateFlow<AdditionalFeesConfig> = _additionalFees.asStateFlow()

    fun updateAdditionalFees(config: AdditionalFeesConfig) {
        _additionalFees.value = config
        settingsStorage.saveAdditionalFees(config)
    }

    fun setTakeawayPackagingEnabled(enabled: Boolean) {
        val updated = _additionalFees.value.copy(isTakeawayPackagingEnabled = enabled)
        updateAdditionalFees(updated)
    }

    private val _receiptCustomizer = MutableStateFlow(settingsStorage.loadReceiptCustomizer())
    val receiptCustomizer: StateFlow<ReceiptCustomizerConfig> = _receiptCustomizer.asStateFlow()

    fun updateReceiptCustomizer(config: ReceiptCustomizerConfig) {
        _receiptCustomizer.value = config
        settingsStorage.saveReceiptCustomizer(config)
    }

    val cartSummary: StateFlow<CartSummary> = combine(
        combine(_cartItems, _orderDiscountType, _orderDiscountValue) { items, discType, discVal ->
            Triple(items, discType, discVal)
        },
        combine(_additionalFees, _orderType, categories) { feesConfig, ordType, cats ->
            Triple(feesConfig, ordType, cats)
        }
    ) { (items, discType, discVal), (feesConfig, ordType, cats) ->
        val subtotal = items.sumOf { it.lineTotal }
        val itemDiscTotal = items.sumOf { it.totalDiscountAmount }
        val totalCount = items.sumOf { it.quantity }

        val orderDiscAmount = when (discType) {
            DiscountType.PERCENTAGE -> (subtotal * (discVal / 100.0)).coerceAtLeast(0.0)
            DiscountType.FIXED_IDR -> discVal.coerceAtMost(subtotal).coerceAtLeast(0.0)
            DiscountType.NONE -> 0.0
        }

        val afterDiscount = (subtotal - orderDiscAmount).coerceAtLeast(0.0)

        // Dine-In Charge (2.5% default when orderType == DINE_IN)
        val serviceFeePct = if (ordType == "DINE_IN" && feesConfig.isServiceFeeEnabled) feesConfig.serviceFeePercentage else 0.0
        val serviceFeeAmount = (afterDiscount * (serviceFeePct / 100.0)).coerceAtLeast(0.0)

        // Smart Takeaway Packaging & Cup fee (Beverage & Pastry only; Beans/Equipment/Merchandise exempted)
        val catNameById = cats.associate { it.id to it.name }
        val eligibleTakeawayQty = TakeawayChargeCalculator.countEligibleTakeawayQuantity(items, catNameById)
        val packagingFeeAmount = TakeawayChargeCalculator.calculatePackagingFee(
            orderType = ordType,
            feesConfig = feesConfig,
            items = items,
            categoryNameById = catNameById
        )

        val taxableBase = afterDiscount + serviceFeeAmount
        val taxPct = if (feesConfig.isTaxEnabled) feesConfig.taxPercentage else 0.0
        val taxAmount = (taxableBase * (taxPct / 100.0)).coerceAtLeast(0.0)

        var total = afterDiscount + serviceFeeAmount + packagingFeeAmount + taxAmount
        if (feesConfig.isRoundingEnabled) {
            total = kotlin.math.round(total / 100.0) * 100.0
        }

        CartSummary(
            items = items,
            subtotal = subtotal,
            itemDiscountTotal = itemDiscTotal,
            orderDiscountType = discType,
            orderDiscountValue = discVal,
            orderDiscountAmount = orderDiscAmount,
            serviceFeePercentage = serviceFeePct,
            serviceFeeAmount = serviceFeeAmount,
            packagingFeeAmount = packagingFeeAmount,
            eligibleTakeawayItemCount = eligibleTakeawayQty,
            taxPercentage = taxPct,
            taxAmount = taxAmount,
            grandTotal = total,
            totalItemCount = totalCount
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CartSummary())

    // Modals & Sheets
    private val _selectedProductForVariant = MutableStateFlow<ProductEntity?>(null)
    val selectedProductForVariant: StateFlow<ProductEntity?> = _selectedProductForVariant.asStateFlow()

    private val _isCartSheetOpen = MutableStateFlow(false)
    val isCartSheetOpen: StateFlow<Boolean> = _isCartSheetOpen.asStateFlow()

    private val _isCheckoutModalOpen = MutableStateFlow(false)
    val isCheckoutModalOpen: StateFlow<Boolean> = _isCheckoutModalOpen.asStateFlow()

    private val _isReceiptModalOpen = MutableStateFlow(false)
    val isReceiptModalOpen: StateFlow<Boolean> = _isReceiptModalOpen.asStateFlow()

    private val _activeCompletedOrder = MutableStateFlow<Pair<OrderEntity, List<OrderItemEntity>>?>(null)
    val activeCompletedOrder: StateFlow<Pair<OrderEntity, List<OrderItemEntity>>?> = _activeCompletedOrder.asStateFlow()

    // Real Bluetooth Hardware Printer Settings
    private val _pairedPrinters = MutableStateFlow<List<BluetoothPrinterDevice>>(emptyList())
    val pairedPrinters: StateFlow<List<BluetoothPrinterDevice>> = _pairedPrinters.asStateFlow()

    private val _selectedPrinterAddress = MutableStateFlow(settingsStorage.loadSelectedPrinter())
    val selectedPrinterAddress: StateFlow<String> = _selectedPrinterAddress.asStateFlow()

    private val _selectedPrinterName = MutableStateFlow(settingsStorage.loadSelectedPrinterName())
    val selectedPrinterName: StateFlow<String> = _selectedPrinterName.asStateFlow()

    private val _isBluetoothScanning = MutableStateFlow(false)
    val isBluetoothScanning: StateFlow<Boolean> = _isBluetoothScanning.asStateFlow()

    private val _printerStatusMessage = MutableStateFlow(
        if (settingsStorage.loadSelectedPrinter().isBlank()) "Belum ada printer Bluetooth yang dipilih"
        else "Printer terpasang: ${settingsStorage.loadSelectedPrinterName().ifBlank { settingsStorage.loadSelectedPrinter() }}"
    )
    val printerStatusMessage: StateFlow<String> = _printerStatusMessage.asStateFlow()

    private val _isPrintingAnimation = MutableStateFlow(false)
    val isPrintingAnimation: StateFlow<Boolean> = _isPrintingAnimation.asStateFlow()

    private val _showPrinterDisconnectedAlert = MutableStateFlow(false)
    val showPrinterDisconnectedAlert: StateFlow<Boolean> = _showPrinterDisconnectedAlert.asStateFlow()

    private val _printerErrorMessage = MutableStateFlow<String?>("Gagal mencetak: Perangkat printer Bluetooth terputus atau mati")
    val printerErrorMessage: StateFlow<String?> = _printerErrorMessage.asStateFlow()

    fun dismissPrinterDisconnectedAlert() {
        _showPrinterDisconnectedAlert.value = false
    }

    fun triggerPrinterDisconnectedAlert(reason: String = "Gagal mencetak: Perangkat printer Bluetooth terputus atau mati") {
        _printerErrorMessage.value = reason
        _printerStatusMessage.value = reason
        _showPrinterDisconnectedAlert.value = true
    }

    private val _showBluetoothDisabledAlert = MutableStateFlow(false)
    val showBluetoothDisabledAlert: StateFlow<Boolean> = _showBluetoothDisabledAlert.asStateFlow()

    fun dismissBluetoothDisabledAlert() {
        _showBluetoothDisabledAlert.value = false
    }

    fun triggerBluetoothDisabledAlert() {
        _showBluetoothDisabledAlert.value = true
    }

    fun openVariantPicker(product: ProductEntity) {
        _selectedProductForVariant.value = product
    }

    fun closeVariantPicker() {
        _selectedProductForVariant.value = null
    }

    fun openCartSheet() {
        _isCartSheetOpen.value = true
    }

    fun closeCartSheet() {
        _isCartSheetOpen.value = false
    }

    fun openCheckoutModal() {
        if (_activeShiftSession.value == null) {
            _showOpenShiftModal.value = true
            return
        }
        _isCartSheetOpen.value = false
        _isCheckoutModalOpen.value = true
    }

    fun closeCheckoutModal() {
        _isCheckoutModalOpen.value = false
    }

    fun closeReceiptModal() {
        _isReceiptModalOpen.value = false
        _activeCompletedOrder.value = null
    }

    fun addToCart(
        product: ProductEntity,
        selectedVariants: Map<String, List<VariantOption>>,
        quantity: Int = 1,
        discountType: DiscountType = DiscountType.NONE,
        discountValue: Double = 0.0,
        notes: String = ""
    ) {
        val current = _cartItems.value.toMutableList()
        // Check if an identical item (same product and exact same variants and discount) already exists
        val existingIndex = current.indexOfFirst {
            it.product.id == product.id &&
                    it.selectedVariants == selectedVariants &&
                    it.discountType == discountType &&
                    it.discountValue == discountValue
        }

        if (existingIndex >= 0) {
            val oldItem = current[existingIndex]
            current[existingIndex] = oldItem.copy(quantity = oldItem.quantity + quantity)
        } else {
            current.add(
                CartItem(
                    product = product,
                    selectedVariants = selectedVariants,
                    quantity = quantity,
                    discountType = discountType,
                    discountValue = discountValue,
                    notes = notes
                )
            )
        }
        _cartItems.value = current
        closeVariantPicker()
        persistCurrentCartState()
    }

    fun updateCartItemQuantity(cartItemId: String, delta: Int) {
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.cartItemId == cartItemId }
        if (index >= 0) {
            val item = current[index]
            val newQty = item.quantity + delta
            if (newQty <= 0) {
                current.removeAt(index)
            } else {
                current[index] = item.copy(quantity = newQty)
            }
            _cartItems.value = current
            persistCurrentCartState()
        }
    }

    fun removeCartItem(cartItemId: String) {
        _cartItems.value = _cartItems.value.filter { it.cartItemId != cartItemId }
        persistCurrentCartState()
    }

    fun setOrderDiscount(type: DiscountType, value: Double) {
        _orderDiscountType.value = type
        _orderDiscountValue.value = value
        persistCurrentCartState()
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _orderDiscountType.value = DiscountType.NONE
        _orderDiscountValue.value = 0.0
        _customerName.value = ""
        _orderType.value = "DINE_IN"
        settingsStorage.clearActiveCartDraft()
    }

    // Process Sale Checkout
    fun completeCheckout(
        paymentMethod: String,
        tenderedAmount: Double,
        notes: String = ""
    ) {
        val summary = cartSummary.value
        val items = summary.items
        if (items.isEmpty()) return

        val now = System.currentTimeMillis()
        val invoice = "INV-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = com.example.ui.components.DateTimeUtils.WIB_ZONE
        }.format(Date(now))
        val change = (tenderedAmount - summary.grandTotal).coerceAtLeast(0.0)

        val activeUserName = _currentUser.value?.name?.takeIf { it.isNotBlank() } ?: _activeEmployee.value.name
        val order = OrderEntity(
            invoiceNumber = invoice,
            timestamp = now,
            customerName = _customerName.value.trim().ifBlank { "Pelanggan Walk-in" },
            orderType = _orderType.value,
            subtotal = summary.subtotal,
            itemDiscountTotal = summary.itemDiscountTotal,
            orderDiscountPercentage = if (summary.orderDiscountType == DiscountType.PERCENTAGE) summary.orderDiscountValue else 0.0,
            orderDiscountAmount = summary.orderDiscountAmount,
            serviceFeePercentage = summary.serviceFeePercentage,
            serviceFeeAmount = summary.serviceFeeAmount,
            packagingFeeAmount = summary.packagingFeeAmount,
            taxPercentage = summary.taxPercentage,
            taxAmount = summary.taxAmount,
            grandTotal = summary.grandTotal,
            paymentMethod = paymentMethod,
            tenderedAmount = tenderedAmount,
            changeAmount = change,
            paymentStatus = "PAID",
            cashierName = activeUserName,
            shiftName = _activeShift.value,
            notes = notes
        )

        viewModelScope.launch(Dispatchers.IO) {
            val categoryMap = repository.getAllCategoriesSync().associateBy({ it.id }, { it.name })
            val cachedCatMap = categories.value.associateBy({ it.id }, { it.name })

            val orderItems = items.map { cartItem ->
                val catName = categoryMap[cartItem.product.categoryId]
                    ?: cachedCatMap[cartItem.product.categoryId]
                    ?: cartItem.product.categoryId.ifBlank { "Coffee" }
                val mappedVariants = VariantDataMapper.extractFromCartItem(cartItem)

                val b2bTag = if (cartItem.isB2bPriceApplied) " [B2B]" else ""
                val displayName = cartItem.product.name + b2bTag
                val itemVariantSummary = if (cartItem.isB2bPriceApplied) {
                    val tierStr = cartItem.b2bTierLabel ?: "Harga B2B"
                    if (cartItem.variantSummary == "Standard" || cartItem.variantSummary.isBlank()) tierStr else "${cartItem.variantSummary}, $tierStr"
                } else {
                    cartItem.variantSummary
                }

                OrderItemEntity(
                    orderInvoiceNumber = invoice,
                    productId = cartItem.product.id,
                    productName = displayName,
                    sku = cartItem.product.sku,
                    basePrice = cartItem.activeBasePrice,
                    costPrice = cartItem.product.costPrice,
                    variantSummary = itemVariantSummary,
                    unitPrice = cartItem.finalUnitPrice,
                    quantity = cartItem.quantity,
                    itemDiscount = cartItem.totalDiscountAmount,
                    lineTotal = cartItem.lineTotal,
                    categoryName = catName,
                    variantSize = mappedVariants.size,
                    variantType = mappedVariants.type,
                    variantBeans = mappedVariants.beansType,
                    variantGrind = mappedVariants.grindSize,
                    variantSugar = mappedVariants.sugarLevel,
                    variantAddons = mappedVariants.addOns,
                    variantExtraTotal = mappedVariants.extraPriceTotal
                )
            }

            repository.completeSale(order, orderItems)
            _activeCompletedOrder.value = Pair(order, orderItems)
            _isCheckoutModalOpen.value = false
            _isReceiptModalOpen.value = true
            clearCart()
            // Auto-Print Trigger upon completing payment
            attemptPrintReceipt(getApplication(), order, orderItems)
        }
    }

    // Real Bluetooth Printer Discovery & Management
    private var activeDiscoveryReceiver: android.content.BroadcastReceiver? = null

    fun refreshBluetoothPrinters(context: Context, triggerScan: Boolean = true) {
        if (!EscPosPrinterService.isBluetoothEnabled(context)) {
            _printerStatusMessage.value = "Bluetooth pada perangkat tidak aktif"
            _pairedPrinters.value = emptyList()
            if (triggerScan) {
                _showBluetoothDisabledAlert.value = true
            }
            return
        }

        val selected = _selectedPrinterAddress.value
        val paired = EscPosPrinterService.getPairedBluetoothPrinters(context, selected)
        _pairedPrinters.value = paired

        if (selected.isBlank()) {
            _printerStatusMessage.value = "Belum ada printer Bluetooth yang dipilih"
        } else {
            val isConn = EscPosPrinterService.isPrinterConnected(context, selected)
            val name = _selectedPrinterName.value.ifBlank { selected }
            _printerStatusMessage.value = if (isConn) "Terhubung: $name" else "Printer belum terhubung atau Bluetooth mati"
        }

        if (triggerScan) {
            startBluetoothScan(context)
        }
    }

    fun startBluetoothScan(context: Context) {
        if (!EscPosPrinterService.isBluetoothEnabled(context)) {
            _showBluetoothDisabledAlert.value = true
            _isBluetoothScanning.value = false
            return
        }
        if (_isBluetoothScanning.value) return
        _isBluetoothScanning.value = true

        activeDiscoveryReceiver?.let {
            EscPosPrinterService.stopDiscovery(context, it)
            activeDiscoveryReceiver = null
        }

        activeDiscoveryReceiver = EscPosPrinterService.startDiscovery(
            context = context,
            onDeviceFound = { discovered ->
                val current = _pairedPrinters.value.toMutableList()
                val existingIndex = current.indexOfFirst { it.address.equals(discovered.address, ignoreCase = true) }
                val isSelected = discovered.address.equals(_selectedPrinterAddress.value, ignoreCase = true)
                val badge = if (isSelected) "Connected" else discovered.statusBadge

                if (existingIndex >= 0) {
                    current[existingIndex] = discovered.copy(
                        isConnected = isSelected,
                        statusBadge = badge
                    )
                } else {
                    current.add(
                        discovered.copy(
                            isConnected = isSelected,
                            statusBadge = badge
                        )
                    )
                }
                _pairedPrinters.value = current
            },
            onDiscoveryFinished = {
                _isBluetoothScanning.value = false
            }
        )
    }

    fun stopBluetoothScan(context: Context) {
        activeDiscoveryReceiver?.let {
            EscPosPrinterService.stopDiscovery(context, it)
            activeDiscoveryReceiver = null
        }
        _isBluetoothScanning.value = false
    }

    fun selectPrinter(address: String, name: String = "") {
        _selectedPrinterAddress.value = address
        _selectedPrinterName.value = name
        settingsStorage.saveSelectedPrinter(address, name)

        // Dynamically update status badges in the list
        val updated = _pairedPrinters.value.map { dev ->
            val isSelected = dev.address.equals(address, ignoreCase = true)
            dev.copy(
                isConnected = isSelected,
                statusBadge = if (isSelected) "Connected" else "Ready"
            )
        }
        _pairedPrinters.value = updated
        _printerStatusMessage.value = if (address.isNotBlank()) "Printer aktif: ${name.ifBlank { address }}" else "Belum ada printer Bluetooth yang dipilih"
    }

    /**
     * Guards print execution: monitors Bluetooth status before sending print job.
     * If disconnected, turned off, or unpaired, captures reason and displays user-friendly alert popup.
     */
    fun attemptPrintReceipt(context: Context, order: OrderEntity, items: List<OrderItemEntity>) {
        val address = _selectedPrinterAddress.value
        val checkResult = EscPosPrinterService.checkPrinterConnection(context, address)
        if (checkResult.isFailure) {
            val errorMsg = checkResult.exceptionOrNull()?.message ?: "Gagal mencetak: Perangkat printer Bluetooth terputus atau mati"
            _printerErrorMessage.value = errorMsg
            _printerStatusMessage.value = errorMsg
            _showPrinterDisconnectedAlert.value = true
            return
        }
        printCurrentReceipt(context, order, items)
    }

    fun printCurrentReceipt(context: Context, order: OrderEntity, items: List<OrderItemEntity>) {
        viewModelScope.launch {
            _isPrintingAnimation.value = true
            _printerStatusMessage.value = "Mengirim data ke printer thermal..."
            val info = _outletInfo.value
            val rc = _receiptCustomizer.value
            val queueLabel = withContext(Dispatchers.IO) {
                try {
                    val effTs = DateTimeUtils.getOrderEffectiveTimestamp(order)
                    val startOfDay = DateTimeUtils.getStartOfDayWib(effTs)
                    val todayOrders = repository.getOrdersBetween(startOfDay, effTs)
                        .sortedBy { DateTimeUtils.getOrderEffectiveTimestamp(it) }
                    val idx = todayOrders.indexOfFirst { it.invoiceNumber == order.invoiceNumber }
                    val seq = if (idx >= 0) idx + 1 else (todayOrders.size + 1)
                    String.format(Locale.US, "%02d", seq)
                } catch (_: Exception) {
                    null
                }
            }
            val bytes = EscPosPrinterService.buildReceiptBytes(
                order = order,
                items = items,
                storeName = info.name.ifBlank { "BDS COFFEE CO" },
                storeAddress = "${info.address} (${info.branch})",
                storePhone = "Telp: ${info.phone}",
                footerMessage = rc.footerNote,
                showTax = rc.showTax,
                paperWidth = if (rc.paperSize == "80mm") 48 else 32,
                context = context,
                showLogo = rc.showLogo,
                queueNumber = queueLabel
            )
            val result = EscPosPrinterService.printBytes(
                deviceAddress = _selectedPrinterAddress.value,
                payload = bytes,
                context = context
            )
            result.onSuccess { msg ->
                _printerStatusMessage.value = msg
                _printerErrorMessage.value = null
                _showPrinterDisconnectedAlert.value = false
            }.onFailure { err ->
                val errorMsg = err.localizedMessage ?: "Gagal mencetak: Perangkat printer Bluetooth terputus atau mati"
                _printerStatusMessage.value = errorMsg
                _printerErrorMessage.value = errorMsg
                _showPrinterDisconnectedAlert.value = true
            }
            _isPrintingAnimation.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeDiscoveryReceiver?.let {
            EscPosPrinterService.stopDiscovery(getApplication(), it)
            activeDiscoveryReceiver = null
        }
    }

    /**
     * Retries printing the current active completed order from success modal or alert popup.
     */
    fun retryPrintActiveOrder(context: Context) {
        val active = _activeCompletedOrder.value ?: return
        attemptPrintReceipt(context, active.first, active.second)
    }

    // Inventory Management
    fun addProduct(product: ProductEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addProduct(product)
        }
    }

    fun updateProduct(product: ProductEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateProduct(product)
        }
    }

    fun updateStock(productId: Long, newStock: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateStock(productId, newStock)
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteProduct(product)
        }
    }

    fun toggleProductActive(productId: Long, isActive: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateProductActiveStatus(productId, isActive)
        }
    }

    // Category Management
    fun addCategory(category: CategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addCategory(category)
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCategory(category)
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCategory(category)
        }
    }

    // CSV Sales Export (Queries directly from Room across ALL accounts, products, variants & fees)
    suspend fun generateFullSalesCsvFromRoom(): String {
        val allOrders = repository.getAllOrdersSync()
        val rawItems = repository.getAllOrderItemsSync()
        val categoryNameMap = repository.getAllCategoriesSync().associateBy({ it.id }, { it.name })
        val productCatMap = repository.getAllProductsIncludingInactiveSync().associate { it.id to it.categoryId }
        val enrichedItems = rawItems.map { item ->
            if (item.categoryName.isNotBlank()) item
            else {
                val catId = productCatMap[item.productId]
                val resolvedCat = catId?.let { categoryNameMap[it] } ?: catId ?: "Coffee"
                item.copy(categoryName = resolvedCat)
            }
        }
        val allItems = enrichedItems.groupBy { it.orderInvoiceNumber }
        return CsvExportService.generateSalesCsv(allOrders, allItems)
    }

    fun exportSalesCsv(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val csv = generateFullSalesCsvFromRoom()
            launch(Dispatchers.Main) {
                CsvExportService.shareCsvReport(context, csv)
            }
        }
    }

    // 📧 Email Report Dispatcher
    fun sendSalesReportEmail(
        context: Context,
        targetEmail: String = "bdscoffeecompany@gmail.com"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val allOrders = repository.getAllOrdersSync()
            val rawItems = repository.getAllOrderItemsSync()
            val categoryNameMap = repository.getAllCategoriesSync().associateBy({ it.id }, { it.name })
            val productCatMap = repository.getAllProductsIncludingInactiveSync().associate { it.id to it.categoryId }
            val enrichedItems = rawItems.map { item ->
                if (item.categoryName.isNotBlank()) item
                else {
                    val catId = productCatMap[item.productId]
                    val resolvedCat = catId?.let { categoryNameMap[it] } ?: catId ?: "Coffee"
                    item.copy(categoryName = resolvedCat)
                }
            }
            val allItems = enrichedItems.groupBy { it.orderInvoiceNumber }
            val csv = CsvExportService.generateSalesCsv(allOrders, allItems)

            val totalRevenue = allOrders.sumOf { it.grandTotal }
            val totalHpp = rawItems.sumOf { it.costPrice * it.quantity }
            val totalProfit = (totalRevenue - totalHpp).coerceAtLeast(0.0)

            val summaryStats = """
                • Total Transaksi: ${allOrders.size} pesanan
                • Total Omzet (Revenue): ${com.example.ui.components.CurrencyUtils.formatIdr(totalRevenue)}
                • Estimasi Laba Bersih: ${com.example.ui.components.CurrencyUtils.formatIdr(totalProfit)}
                • Tanggal Laporan: ${SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date())}
            """.trimIndent()

            launch(Dispatchers.Main) {
                CsvExportService.sendCsvReportViaEmail(
                    context = context,
                    csvContent = csv,
                    targetEmail = targetEmail,
                    outletName = _outletInfo.value.name,
                    summaryStats = summaryStats
                )
            }
        }
    }

    // 📝 Open Bill / Hold Order Actions
    fun saveCurrentCartAsOpenBill(
        customerName: String,
        tableNumber: String,
        orderType: String,
        notes: String = ""
    ) {
        val summary = cartSummary.value
        val items = summary.items
        if (items.isEmpty()) return

        val now = System.currentTimeMillis()
        val billNum = "OB-" + SimpleDateFormat("HHmmss", Locale.US).format(Date(now))

        val jsonArray = serializeCartItemsToJson(items)

        val openBill = OpenBillEntity(
            billNumber = billNum,
            customerName = customerName.ifBlank { "Tamu" },
            tableNumber = tableNumber,
            orderType = orderType,
            itemsJson = jsonArray.toString(),
            totalItemCount = summary.totalItemCount,
            subtotal = summary.subtotal,
            taxAmount = summary.taxAmount,
            serviceFeeAmount = summary.serviceFeeAmount,
            packagingFeeAmount = summary.packagingFeeAmount,
            grandTotal = summary.grandTotal,
            notes = notes,
            cashierName = _activeEmployee.value.name,
            createdAt = now,
            status = "ACTIVE"
        )

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveOpenBill(openBill)
            launch(Dispatchers.Main) {
                clearCart()
                closeCartSheet()
            }
        }
    }

    fun recallOpenBill(bill: OpenBillEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val restoredItems = mutableListOf<CartItem>()
            try {
                val array = JSONArray(bill.itemsJson)
                val allProds = repository.getAllProductsIncludingInactiveSync().associateBy { it.id }

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val cId = obj.optString("cartItemId", UUID.randomUUID().toString())
                    val pId = obj.optLong("productId", 0L)
                    val pName = obj.optString("productName", "Produk")
                    val pPrice = obj.optDouble("productPrice", 0.0)
                    val pCost = obj.optDouble("productCost", 0.0)
                    val pCat = obj.optString("productCategory", "coffee")
                    val pStock = obj.optInt("productStock", 999)
                    val pUnit = obj.optString("productUnit", "cup")
                    val hasB2b = obj.optBoolean("hasB2bPricing", false)
                    val b2bT1Qty = obj.optInt("b2bTier1MinQty", 5)
                    val b2bT1Price = obj.optDouble("b2bTier1Price", 0.0)
                    val b2bT2Qty = obj.optInt("b2bTier2MinQty", 10)
                    val b2bT2Price = obj.optDouble("b2bTier2Price", 0.0)
                    val qty = obj.optInt("quantity", 1)
                    val dTypeStr = obj.optString("discountType", "NONE")
                    val dVal = obj.optDouble("discountValue", 0.0)
                    val notes = obj.optString("notes", "")

                    val product = allProds[pId] ?: ProductEntity(
                        id = pId,
                        sku = obj.optString("sku", "ITEM-$pId"),
                        name = pName,
                        categoryId = pCat,
                        basePrice = pPrice,
                        costPrice = pCost,
                        stockQuantity = pStock,
                        unit = pUnit,
                        hasB2bPricing = hasB2b,
                        b2bTier1MinQty = b2bT1Qty,
                        b2bTier1Price = b2bT1Price,
                        b2bTier2MinQty = b2bT2Qty,
                        b2bTier2Price = b2bT2Price
                    )

                    val variantsMap = mutableMapOf<String, List<VariantOption>>()
                    val vObj = obj.optJSONObject("selectedVariants")
                    if (vObj != null) {
                        val keys = vObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val oArr = vObj.optJSONArray(k)
                            val oList = mutableListOf<VariantOption>()
                            if (oArr != null) {
                                for (j in 0 until oArr.length()) {
                                    val itemObj = oArr.getJSONObject(j)
                                    oList.add(
                                        VariantOption(
                                            name = itemObj.optString("name", ""),
                                            priceAdjustment = itemObj.optDouble("price", 0.0)
                                        )
                                    )
                                }
                            }
                            variantsMap[k] = oList
                        }
                    }

                    restoredItems.add(
                        CartItem(
                            cartItemId = cId,
                            product = product,
                            selectedVariants = variantsMap,
                            quantity = qty,
                            discountType = try { DiscountType.valueOf(dTypeStr) } catch (_: Exception) { DiscountType.NONE },
                            discountValue = dVal,
                            notes = notes
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            repository.deleteOpenBill(bill.id)

            launch(Dispatchers.Main) {
                _cartItems.value = restoredItems
                _orderType.value = bill.orderType
                _customerName.value = if (bill.tableNumber.isNotBlank()) "${bill.customerName} (${bill.tableNumber})" else bill.customerName
                persistCurrentCartState()
                openCartSheet()
            }
        }
    }

    fun deleteOpenBill(billId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteOpenBill(billId)
        }
    }

    // ==========================================
    // 💾 CART STATE PERSISTENCE & RESTORATION
    // ==========================================
    private fun serializeCartItemsToJson(items: List<CartItem>): JSONArray {
        val jsonArray = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("cartItemId", item.cartItemId)
                put("productId", item.product.id)
                put("sku", item.product.sku)
                put("productName", item.product.name)
                put("productPrice", item.product.basePrice)
                put("productCost", item.product.costPrice)
                put("productCategory", item.product.categoryId)
                put("productStock", item.product.stockQuantity)
                put("productUnit", item.product.unit)
                put("hasB2bPricing", item.product.hasB2bPricing)
                put("b2bTier1MinQty", item.product.b2bTier1MinQty)
                put("b2bTier1Price", item.product.b2bTier1Price)
                put("b2bTier2MinQty", item.product.b2bTier2MinQty)
                put("b2bTier2Price", item.product.b2bTier2Price)
                put("quantity", item.quantity)
                put("discountType", item.discountType.name)
                put("discountValue", item.discountValue)
                put("notes", item.notes)

                val variantsObj = JSONObject()
                for ((groupName, options) in item.selectedVariants) {
                    val optsArray = JSONArray()
                    for (opt in options) {
                        val o = JSONObject().apply {
                            put("name", opt.name)
                            put("price", opt.priceAdjustment)
                        }
                        optsArray.put(o)
                    }
                    variantsObj.put(groupName, optsArray)
                }
                put("selectedVariants", variantsObj)
            }
            jsonArray.put(obj)
        }
        return jsonArray
    }

    private fun persistCurrentCartState() {
        val items = _cartItems.value
        if (items.isEmpty()) {
            settingsStorage.clearActiveCartDraft()
        } else {
            val jsonArray = serializeCartItemsToJson(items)
            settingsStorage.saveActiveCartDraft(
                itemsJson = jsonArray.toString(),
                customerName = _customerName.value,
                orderType = _orderType.value,
                discountType = _orderDiscountType.value.name,
                discountValue = _orderDiscountValue.value
            )
        }
    }

    private suspend fun restoreActiveCartDraft() {
        if (_cartItems.value.isNotEmpty()) return
        val draft = settingsStorage.loadActiveCartDraft() ?: return
        if (draft.itemsJson.isBlank() || draft.itemsJson == "[]") return

        try {
            val array = JSONArray(draft.itemsJson)
            if (array.length() == 0) return

            val liveProducts = repository.getAllProductsIncludingInactiveSync().associateBy { it.id }
            val restoredItems = mutableListOf<CartItem>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val cId = obj.optString("cartItemId", UUID.randomUUID().toString())
                val pId = obj.optLong("productId", 0L)
                val sku = obj.optString("sku", "ITEM-$pId")
                val pName = obj.optString("productName", "Produk")
                val pPrice = obj.optDouble("productPrice", 0.0)
                val pCost = obj.optDouble("productCost", 0.0)
                val pCat = obj.optString("productCategory", "coffee")
                val pStock = obj.optInt("productStock", 999)
                val pUnit = obj.optString("productUnit", "cup")
                val hasB2b = obj.optBoolean("hasB2bPricing", false)
                val b2bT1Qty = obj.optInt("b2bTier1MinQty", 5)
                val b2bT1Price = obj.optDouble("b2bTier1Price", 0.0)
                val b2bT2Qty = obj.optInt("b2bTier2MinQty", 10)
                val b2bT2Price = obj.optDouble("b2bTier2Price", 0.0)
                val qty = obj.optInt("quantity", 1)
                val dTypeStr = obj.optString("discountType", "NONE")
                val dVal = obj.optDouble("discountValue", 0.0)
                val notes = obj.optString("notes", "")

                val product = liveProducts[pId] ?: ProductEntity(
                    id = pId,
                    sku = sku,
                    name = pName,
                    categoryId = pCat,
                    basePrice = pPrice,
                    costPrice = pCost,
                    stockQuantity = pStock,
                    unit = pUnit,
                    hasB2bPricing = hasB2b,
                    b2bTier1MinQty = b2bT1Qty,
                    b2bTier1Price = b2bT1Price,
                    b2bTier2MinQty = b2bT2Qty,
                    b2bTier2Price = b2bT2Price
                )

                val variantsMap = mutableMapOf<String, List<VariantOption>>()
                val vObj = obj.optJSONObject("selectedVariants")
                if (vObj != null) {
                    val keys = vObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val oArr = vObj.optJSONArray(k)
                        val oList = mutableListOf<VariantOption>()
                        if (oArr != null) {
                            for (j in 0 until oArr.length()) {
                                val itemObj = oArr.getJSONObject(j)
                                oList.add(
                                    VariantOption(
                                        name = itemObj.optString("name", ""),
                                        priceAdjustment = itemObj.optDouble("price", 0.0)
                                    )
                                )
                            }
                        }
                        variantsMap[k] = oList
                    }
                }

                restoredItems.add(
                    CartItem(
                        cartItemId = cId,
                        product = product,
                        selectedVariants = variantsMap,
                        quantity = qty,
                        discountType = try { DiscountType.valueOf(dTypeStr) } catch (_: Exception) { DiscountType.NONE },
                        discountValue = dVal,
                        notes = notes
                    )
                )
            }

            if (restoredItems.isNotEmpty()) {
                _cartItems.value = restoredItems
                _customerName.value = draft.customerName
                _orderType.value = draft.orderType
                _orderDiscountType.value = try { DiscountType.valueOf(draft.discountType) } catch (_: Exception) { DiscountType.NONE }
                _orderDiscountValue.value = draft.discountValue
                _wasDraftRestored.value = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
