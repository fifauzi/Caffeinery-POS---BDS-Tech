package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.PosViewModel
import com.example.ui.analytics.AnalyticsScreen
import com.example.ui.cart.CartBottomSheet
import com.example.ui.catalog.CatalogScreen
import com.example.ui.checkout.CheckoutModal
import com.example.ui.checkout.ReceiptSuccessModal
import com.example.ui.components.IosBottomBar
import com.example.ui.components.PosTab
import com.example.ui.inventory.InventoryScreen
import com.example.ui.orders.OrdersHistoryScreen
import com.example.ui.printer.ThermalPrinterScreen
import com.example.ui.theme.MyApplicationTheme

import com.example.ui.dashboard.DashboardScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.ScaffoldBg

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.ui.splash.SplashScreen

class MainActivity : ComponentActivity() {

    private val posViewModel: PosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                val isLoggedIn by posViewModel.isLoggedIn.collectAsStateWithLifecycle()

                if (showSplash) {
                    SplashScreen(onSplashFinished = { showSplash = false })
                } else if (!isLoggedIn) {
                    com.example.ui.auth.LoginScreen(viewModel = posViewModel)
                } else {
                    PosAppRoot(viewModel = posViewModel)
                }
            }
        }
    }
}

@Composable
fun PosAppRoot(viewModel: PosViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isCartSheetOpen by viewModel.isCartSheetOpen.collectAsStateWithLifecycle()
    val isCheckoutModalOpen by viewModel.isCheckoutModalOpen.collectAsStateWithLifecycle()
    val isReceiptModalOpen by viewModel.isReceiptModalOpen.collectAsStateWithLifecycle()
    val activeCompletedOrder by viewModel.activeCompletedOrder.collectAsStateWithLifecycle()
    val cartSummary by viewModel.cartSummary.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val activeShiftSession by viewModel.activeShiftSession.collectAsStateWithLifecycle()
    val showOpenShiftModal by viewModel.showOpenShiftModal.collectAsStateWithLifecycle()
    val showCloseShiftModal by viewModel.showCloseShiftModal.collectAsStateWithLifecycle()
    val showCashExpenseModal by viewModel.showCashExpenseModal.collectAsStateWithLifecycle()
    val activeShiftExpenses by viewModel.activeShiftExpenses.collectAsStateWithLifecycle()
    val activeShiftOrdersForClose by viewModel.activeShiftOrdersForClose.collectAsStateWithLifecycle()
    val outletInfo by viewModel.outletInfo.collectAsStateWithLifecycle()

    val allowedTabs = remember(currentUser) {
        currentUser?.allowedTabs() ?: listOf(PosTab.KASIR, PosTab.TRANSAKSI)
    }
    val homeTab = if (currentUser?.isSuperAdmin() == true) PosTab.DASHBOARD else PosTab.KASIR

    // Back handling
    BackHandler(enabled = isReceiptModalOpen || isCheckoutModalOpen || isCartSheetOpen || showCashExpenseModal || showCloseShiftModal || showOpenShiftModal || currentTab != homeTab) {
        when {
            showCashExpenseModal -> viewModel.dismissCashExpenseModal()
            showCloseShiftModal -> viewModel.dismissCloseShift()
            showOpenShiftModal && currentUser?.isSuperAdmin() == true -> viewModel.dismissOpenShift()
            isReceiptModalOpen -> viewModel.closeReceiptModal()
            isCheckoutModalOpen -> viewModel.closeCheckoutModal()
            isCartSheetOpen -> viewModel.closeCartSheet()
            currentTab != homeTab -> viewModel.setCurrentTab(homeTab)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ScaffoldBg,
        bottomBar = {
            IosBottomBar(
                currentTab = currentTab,
                onTabSelected = { viewModel.setCurrentTab(it) },
                cartItemCount = cartSummary.totalItemCount,
                allowedTabs = allowedTabs
            )
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            label = "TabCrossfade"
        ) { tab ->
            when (tab) {
                PosTab.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                PosTab.KASIR -> CatalogScreen(viewModel = viewModel)
                PosTab.KATALOG -> InventoryScreen(viewModel = viewModel)
                PosTab.TRANSAKSI -> OrdersHistoryScreen(viewModel = viewModel)
                PosTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }

    // Modal Overlays
    if (isCartSheetOpen) {
        CartBottomSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.closeCartSheet() }
        )
    }

    if (isCheckoutModalOpen) {
        CheckoutModal(
            viewModel = viewModel,
            onDismiss = { viewModel.closeCheckoutModal() }
        )
    }

    if (isReceiptModalOpen && activeCompletedOrder != null) {
        ReceiptSuccessModal(
            order = activeCompletedOrder!!.first,
            items = activeCompletedOrder!!.second,
            viewModel = viewModel,
            onDismiss = { viewModel.closeReceiptModal() }
        )
    }

    // Open Shift (Buka Kasir & Petty Cash) Modal
    if (showOpenShiftModal && currentUser != null) {
        com.example.ui.shift.OpenShiftModal(
            currentUser = currentUser!!,
            onDismiss = { viewModel.dismissOpenShift() },
            onConfirmOpenShift = { startingCash, shiftName ->
                viewModel.openShiftSession(startingCash, shiftName)
            },
            canDismiss = currentUser!!.isSuperAdmin()
        )
    }

    // Cash Expense (Pengeluaran Kas Shift) Modal
    if (showCashExpenseModal && activeShiftSession != null) {
        com.example.ui.shift.CashExpenseModal(
            shift = activeShiftSession!!,
            expenses = activeShiftExpenses,
            onDismiss = { viewModel.dismissCashExpenseModal() },
            onAddExpense = { description, amount ->
                viewModel.recordCashExpense(description, amount)
            },
            onDeleteExpense = { expenseId ->
                viewModel.deleteCashExpense(expenseId)
            }
        )
    }

    // Close Shift (Tutup Kasir) Modal
    if (showCloseShiftModal && activeShiftSession != null) {
        val shiftOrders = if (activeShiftOrdersForClose.isNotEmpty()) {
            activeShiftOrdersForClose
        } else {
            viewModel.getOrdersForActiveShift()
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        com.example.ui.shift.CloseShiftModal(
            shift = activeShiftSession!!,
            shiftOrders = shiftOrders,
            shiftExpenses = activeShiftExpenses,
            defaultWhatsAppPhone = outletInfo.ownerWhatsAppNumber,
            onDismiss = { viewModel.dismissCloseShift() },
            onConfirmCloseShift = { actualCash, notes ->
                viewModel.closeShiftSession(actualCash, notes)
            },
            onPrintShiftReceipt = { actualCash ->
                viewModel.printActiveShiftClosingReceipt(context, actualCash)
            },
            onOpenCashExpense = {
                viewModel.requestOpenCashExpenseModal()
            },
            onSendWhatsAppReport = { actualCash, notes, targetPhone ->
                viewModel.sendActiveShiftReportToWhatsApp(context, actualCash, notes, targetPhone)
            },
            viewModel = viewModel
        )
    }
}
