package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.model.CashExpenseEntity
import com.example.data.local.model.CategoryEntity
import com.example.data.local.model.OrderEntity
import com.example.data.local.model.OrderItemEntity
import com.example.data.local.model.ProductEntity
import com.example.data.local.model.VariantGroupEntity
import com.example.data.local.model.OpenBillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PosDao {

    // Categories
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllCategoriesSync(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int

    // Products
    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE categoryId = :categoryId AND isActive = 1 ORDER BY name ASC")
    fun getProductsByCategory(categoryId: String): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("UPDATE products SET stockQuantity = stockQuantity - :qty WHERE id = :productId")
    suspend fun decreaseStock(productId: Long, qty: Int)

    @Query("UPDATE products SET stockQuantity = :newStock WHERE id = :productId")
    suspend fun setStock(productId: Long, newStock: Int)

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    suspend fun getAllProductsSync(): List<ProductEntity>

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProductsIncludingInactive(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsIncludingInactiveSync(): List<ProductEntity>

    @Query("UPDATE products SET isActive = :isActive WHERE id = :productId")
    suspend fun updateProductActiveStatus(productId: Long, isActive: Boolean)

    // Orders
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT COUNT(*) FROM orders")
    suspend fun getOrderCount(): Int

    @Query("SELECT * FROM orders WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getOrdersBetween(startTime: Long, endTime: Long): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getOrderByInvoice(invoiceNumber: String): OrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    suspend fun getAllOrdersSync(): List<OrderEntity>

    @Update
    suspend fun updateOrders(orders: List<OrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Query("SELECT * FROM order_items WHERE orderInvoiceNumber = :invoiceNumber")
    fun getOrderItemsForInvoice(invoiceNumber: String): Flow<List<OrderItemEntity>>

    @Query("SELECT * FROM order_items WHERE orderInvoiceNumber = :invoiceNumber")
    suspend fun getOrderItemsForInvoiceSync(invoiceNumber: String): List<OrderItemEntity>

    @Query("SELECT * FROM order_items WHERE orderInvoiceNumber IN (:invoices)")
    suspend fun getOrderItemsForInvoicesSync(invoices: List<String>): List<OrderItemEntity>

    @Query("SELECT * FROM order_items")
    fun getAllOrderItems(): Flow<List<OrderItemEntity>>

    @Query("SELECT * FROM order_items")
    suspend fun getAllOrderItemsSync(): List<OrderItemEntity>

    @Transaction
    suspend fun recordSale(order: OrderEntity, items: List<OrderItemEntity>) {
        insertOrder(order)
        insertOrderItems(items)
        for (item in items) {
            decreaseStock(item.productId, item.quantity)
        }
    }

    // Variant Groups
    @Query("SELECT * FROM variant_groups ORDER BY sortOrder ASC, name ASC")
    fun getAllVariantGroups(): Flow<List<VariantGroupEntity>>

    @Query("SELECT COUNT(*) FROM variant_groups")
    suspend fun getVariantGroupCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariantGroup(group: VariantGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariantGroups(groups: List<VariantGroupEntity>)

    @Update
    suspend fun updateVariantGroup(group: VariantGroupEntity)

    @Delete
    suspend fun deleteVariantGroup(group: VariantGroupEntity)

    // Open Bills (Hold Orders)
    @Query("SELECT * FROM open_bills WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun getActiveOpenBills(): Flow<List<OpenBillEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpenBill(openBill: OpenBillEntity)

    @Update
    suspend fun updateOpenBill(openBill: OpenBillEntity)

    @Delete
    suspend fun deleteOpenBill(openBill: OpenBillEntity)

    @Query("DELETE FROM open_bills WHERE id = :id")
    suspend fun deleteOpenBillById(id: String)

    // Shifts & Petty Cash Management
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: com.example.data.local.model.ShiftEntity): Long

    @Update
    suspend fun updateShift(shift: com.example.data.local.model.ShiftEntity)

    @Query("SELECT * FROM shifts WHERE status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    suspend fun getActiveShift(): com.example.data.local.model.ShiftEntity?

    @Query("SELECT * FROM shifts WHERE status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    fun getActiveShiftFlow(): Flow<com.example.data.local.model.ShiftEntity?>

    @Query("SELECT * FROM shifts ORDER BY openedAt DESC")
    fun getAllShifts(): Flow<List<com.example.data.local.model.ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getShiftById(id: Long): com.example.data.local.model.ShiftEntity?

    @Query("SELECT * FROM orders WHERE timestamp >= :fromTime AND timestamp <= :toTime ORDER BY timestamp DESC")
    suspend fun getOrdersBetweenSync(fromTime: Long, toTime: Long): List<OrderEntity>

    @Query("DELETE FROM shifts")
    suspend fun clearAllShifts()

    // Cash Expenses (Pengeluaran Tunai Dalam Shift)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashExpense(expense: CashExpenseEntity): Long

    @Query("SELECT * FROM cash_expenses WHERE shiftCode = :shiftCode ORDER BY timestamp DESC")
    fun getCashExpensesForShiftFlow(shiftCode: String): Flow<List<CashExpenseEntity>>

    @Query("SELECT * FROM cash_expenses WHERE shiftCode = :shiftCode ORDER BY timestamp DESC")
    suspend fun getCashExpensesForShiftSync(shiftCode: String): List<CashExpenseEntity>

    @Query("SELECT * FROM cash_expenses ORDER BY timestamp DESC")
    fun getAllCashExpenses(): Flow<List<CashExpenseEntity>>

    @Query("DELETE FROM cash_expenses WHERE id = :id")
    suspend fun deleteCashExpenseById(id: Long)

    @Query("DELETE FROM cash_expenses")
    suspend fun clearAllCashExpenses()

    // Complete Data Wipeout queries
    @Query("DELETE FROM order_items")
    suspend fun clearAllOrderItems()

    @Query("DELETE FROM orders")
    suspend fun clearAllOrders()

    @Query("DELETE FROM open_bills")
    suspend fun clearAllOpenBills()

    @Query("DELETE FROM products")
    suspend fun clearAllProducts()

    @Query("DELETE FROM categories")
    suspend fun clearAllCategories()

    @Query("DELETE FROM variant_groups")
    suspend fun clearAllVariantGroups()

    @Transaction
    suspend fun wipeAllDatabaseData() {
        clearAllOrderItems()
        clearAllOrders()
        clearAllOpenBills()
        clearAllProducts()
        clearAllCategories()
        clearAllVariantGroups()
        clearAllCashExpenses()
        clearAllShifts()
    }
}
