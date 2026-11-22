package com.example.data.repository

import com.example.data.local.dao.PosDao
import com.example.data.local.model.CashExpenseEntity
import com.example.data.local.model.CategoryEntity
import com.example.data.local.model.OrderEntity
import com.example.data.local.model.OrderItemEntity
import com.example.data.local.model.ProductEntity
import com.example.data.local.model.OpenBillEntity
import com.example.data.local.model.VariantGroupEntity
import kotlinx.coroutines.flow.Flow

class PosRepository(private val posDao: PosDao) {

    val categories: Flow<List<CategoryEntity>> = posDao.getAllCategories()
    val products: Flow<List<ProductEntity>> = posDao.getAllProducts()
    val allProductsIncludingInactive: Flow<List<ProductEntity>> = posDao.getAllProductsIncludingInactive()
    val orders: Flow<List<OrderEntity>> = posDao.getAllOrders()
    val allOrderItems: Flow<List<OrderItemEntity>> = posDao.getAllOrderItems()
    val variantGroups: Flow<List<VariantGroupEntity>> = posDao.getAllVariantGroups()
    val activeOpenBills: Flow<List<OpenBillEntity>> = posDao.getActiveOpenBills()

    fun getOrderItems(invoiceNumber: String): Flow<List<OrderItemEntity>> {
        return posDao.getOrderItemsForInvoice(invoiceNumber)
    }

    suspend fun getOrderItemsSync(invoiceNumber: String): List<OrderItemEntity> {
        return posDao.getOrderItemsForInvoiceSync(invoiceNumber)
    }

    suspend fun addCategory(category: CategoryEntity) {
        posDao.insertCategory(category)
    }

    suspend fun updateCategory(category: CategoryEntity) {
        posDao.updateCategory(category)
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        posDao.deleteCategory(category)
    }

    suspend fun addProduct(product: ProductEntity): Long {
        return posDao.insertProduct(product)
    }

    suspend fun updateProduct(product: ProductEntity) {
        posDao.updateProduct(product)
    }

    suspend fun deleteProduct(product: ProductEntity) {
        posDao.deleteProduct(product)
    }

    suspend fun updateStock(productId: Long, newStock: Int) {
        posDao.setStock(productId, newStock)
    }

    suspend fun addVariantGroup(group: VariantGroupEntity) {
        posDao.insertVariantGroup(group)
    }

    suspend fun updateVariantGroup(group: VariantGroupEntity) {
        posDao.updateVariantGroup(group)
    }

    suspend fun deleteVariantGroup(group: VariantGroupEntity) {
        posDao.deleteVariantGroup(group)
    }

    suspend fun completeSale(order: OrderEntity, items: List<OrderItemEntity>) {
        posDao.recordSale(order, items)
    }

    suspend fun saveOpenBill(bill: OpenBillEntity) {
        posDao.insertOpenBill(bill)
    }

    suspend fun deleteOpenBill(id: String) {
        posDao.deleteOpenBillById(id)
    }

    suspend fun getCategoryCount(): Int = posDao.getCategoryCount()
    suspend fun getProductCount(): Int = posDao.getProductCount()
    suspend fun getOrderCount(): Int = posDao.getOrderCount()
    suspend fun getVariantGroupCount(): Int = posDao.getVariantGroupCount()
    suspend fun getAllProductsSync(): List<ProductEntity> = posDao.getAllProductsSync()
    suspend fun getAllProductsIncludingInactiveSync(): List<ProductEntity> = posDao.getAllProductsIncludingInactiveSync()
    suspend fun getAllCategoriesSync(): List<CategoryEntity> = posDao.getAllCategoriesSync()
    suspend fun getAllOrdersSync(): List<OrderEntity> = posDao.getAllOrdersSync()
    suspend fun getAllOrderItemsSync(): List<OrderItemEntity> = posDao.getAllOrderItemsSync()

    suspend fun updateProductActiveStatus(productId: Long, isActive: Boolean) {
        posDao.updateProductActiveStatus(productId, isActive)
    }

    suspend fun insertCategories(categories: List<CategoryEntity>) = posDao.insertCategories(categories)
    suspend fun insertProducts(products: List<ProductEntity>) = posDao.insertProducts(products)
    suspend fun insertVariantGroups(groups: List<VariantGroupEntity>) = posDao.insertVariantGroups(groups)
    suspend fun insertOrders(orders: List<OrderEntity>) = posDao.insertOrders(orders)
    suspend fun insertOrderItems(items: List<OrderItemEntity>) = posDao.insertOrderItems(items)

    suspend fun getOrderItemsForInvoices(invoices: List<String>): List<OrderItemEntity> {
        if (invoices.isEmpty()) return emptyList()
        return posDao.getOrderItemsForInvoicesSync(invoices)
    }

    // Shifts & Petty Cash
    val allShifts: Flow<List<com.example.data.local.model.ShiftEntity>> = posDao.getAllShifts()
    val activeShiftFlow: Flow<com.example.data.local.model.ShiftEntity?> = posDao.getActiveShiftFlow()

    suspend fun getActiveShift(): com.example.data.local.model.ShiftEntity? = posDao.getActiveShift()

    suspend fun openShift(shift: com.example.data.local.model.ShiftEntity): Long {
        return posDao.insertShift(shift)
    }

    suspend fun updateShift(shift: com.example.data.local.model.ShiftEntity) {
        posDao.updateShift(shift)
    }

    suspend fun getShiftById(id: Long): com.example.data.local.model.ShiftEntity? = posDao.getShiftById(id)

    suspend fun getOrdersBetween(fromTime: Long, toTime: Long): List<OrderEntity> {
        return posDao.getOrdersBetweenSync(fromTime, toTime)
    }

    // Cash Expenses (Pengeluaran Tunai Dalam Shift)
    suspend fun insertCashExpense(expense: CashExpenseEntity): Long = posDao.insertCashExpense(expense)

    fun getCashExpensesForShiftFlow(shiftCode: String): Flow<List<CashExpenseEntity>> =
        posDao.getCashExpensesForShiftFlow(shiftCode)

    suspend fun getCashExpensesForShiftSync(shiftCode: String): List<CashExpenseEntity> =
        posDao.getCashExpensesForShiftSync(shiftCode)

    suspend fun deleteCashExpenseById(id: Long) = posDao.deleteCashExpenseById(id)

    suspend fun seedCatalogIfEmpty() {
        com.example.data.local.DatabaseSeeder.seedIfEmpty(posDao)
    }

    suspend fun wipeAllData() = posDao.wipeAllDatabaseData()
}
