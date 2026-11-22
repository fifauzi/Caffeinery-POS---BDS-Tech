package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.PosDao
import com.example.data.local.model.CashExpenseEntity
import com.example.data.local.model.CategoryEntity
import com.example.data.local.model.OpenBillEntity
import com.example.data.local.model.OrderEntity
import com.example.data.local.model.OrderItemEntity
import com.example.data.local.model.ProductEntity
import com.example.data.local.model.ShiftEntity
import com.example.data.local.model.VariantGroupEntity
import kotlinx.coroutines.CoroutineScope

@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        VariantGroupEntity::class,
        OpenBillEntity::class,
        ShiftEntity::class,
        CashExpenseEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun posDao(): PosDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun existingColumns(db: SupportSQLiteDatabase, table: String): Set<String> {
            val cols = mutableSetOf<String>()
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0) {
                        cols.add(cursor.getString(nameIdx).lowercase())
                    }
                }
            }
            return cols
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
            definition: String
        ) {
            val cols = existingColumns(db, table)
            if (!cols.contains(column.lowercase())) {
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
            }
        }

        /**
         * Non-destructive schema migration helper.
         * Safely creates any missing tables and appends any newly added columns while
         * strictly preserving 100% of existing orders, order items, shifts, open bills,
         * and custom products across app updates.
         */
        internal fun ensureSchemaIntegrity(db: SupportSQLiteDatabase) {
            // 1. categories
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `categories` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `iconName` TEXT NOT NULL,
                    `colorHex` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            addColumnIfMissing(db, "categories", "sortOrder", "INTEGER NOT NULL DEFAULT 0")

            // 2. products
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `products` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `sku` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `categoryId` TEXT NOT NULL,
                    `basePrice` REAL NOT NULL,
                    `costPrice` REAL NOT NULL DEFAULT 0.0,
                    `stockQuantity` INTEGER NOT NULL,
                    `unit` TEXT NOT NULL DEFAULT 'cup',
                    `description` TEXT NOT NULL DEFAULT '',
                    `iconName` TEXT NOT NULL DEFAULT 'coffee',
                    `variantsJson` TEXT NOT NULL DEFAULT '',
                    `isActive` INTEGER NOT NULL DEFAULT 1,
                    `hasB2bPricing` INTEGER NOT NULL DEFAULT 0,
                    `b2bTier1MinQty` INTEGER NOT NULL DEFAULT 5,
                    `b2bTier1Price` REAL NOT NULL DEFAULT 0.0,
                    `b2bTier2MinQty` INTEGER NOT NULL DEFAULT 10,
                    `b2bTier2Price` REAL NOT NULL DEFAULT 0.0
                )
                """.trimIndent()
            )
            addColumnIfMissing(db, "products", "costPrice", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "products", "unit", "TEXT NOT NULL DEFAULT 'cup'")
            addColumnIfMissing(db, "products", "description", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "products", "iconName", "TEXT NOT NULL DEFAULT 'coffee'")
            addColumnIfMissing(db, "products", "variantsJson", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "products", "isActive", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(db, "products", "hasB2bPricing", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "products", "b2bTier1MinQty", "INTEGER NOT NULL DEFAULT 5")
            addColumnIfMissing(db, "products", "b2bTier1Price", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "products", "b2bTier2MinQty", "INTEGER NOT NULL DEFAULT 10")
            addColumnIfMissing(db, "products", "b2bTier2Price", "REAL NOT NULL DEFAULT 0.0")

            // 3. orders
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `orders` (
                    `invoiceNumber` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `customerName` TEXT NOT NULL DEFAULT 'Walk-in Guest',
                    `orderType` TEXT NOT NULL DEFAULT 'DINE_IN',
                    `subtotal` REAL NOT NULL,
                    `itemDiscountTotal` REAL NOT NULL DEFAULT 0.0,
                    `orderDiscountPercentage` REAL NOT NULL DEFAULT 0.0,
                    `orderDiscountAmount` REAL NOT NULL DEFAULT 0.0,
                    `serviceFeePercentage` REAL NOT NULL DEFAULT 0.0,
                    `serviceFeeAmount` REAL NOT NULL DEFAULT 0.0,
                    `packagingFeeAmount` REAL NOT NULL DEFAULT 0.0,
                    `taxPercentage` REAL NOT NULL DEFAULT 10.0,
                    `taxAmount` REAL NOT NULL DEFAULT 0.0,
                    `grandTotal` REAL NOT NULL,
                    `paymentMethod` TEXT NOT NULL,
                    `tenderedAmount` REAL NOT NULL DEFAULT 0.0,
                    `changeAmount` REAL NOT NULL DEFAULT 0.0,
                    `paymentStatus` TEXT NOT NULL DEFAULT 'PAID',
                    `cashierName` TEXT NOT NULL DEFAULT 'Rangga Maheza',
                    `shiftName` TEXT NOT NULL DEFAULT 'Shift 1',
                    `notes` TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY(`invoiceNumber`)
                )
                """.trimIndent()
            )
            addColumnIfMissing(db, "orders", "customerName", "TEXT NOT NULL DEFAULT 'Walk-in Guest'")
            addColumnIfMissing(db, "orders", "orderType", "TEXT NOT NULL DEFAULT 'DINE_IN'")
            addColumnIfMissing(db, "orders", "itemDiscountTotal", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "orders", "orderDiscountPercentage", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "orders", "orderDiscountAmount", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "orders", "serviceFeePercentage", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "orders", "serviceFeeAmount", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "orders", "packagingFeeAmount", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "orders", "taxPercentage", "REAL NOT NULL DEFAULT 10.0")
            addColumnIfMissing(db, "orders", "taxAmount", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "orders", "tenderedAmount", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "orders", "changeAmount", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "orders", "paymentStatus", "TEXT NOT NULL DEFAULT 'PAID'")
            addColumnIfMissing(db, "orders", "cashierName", "TEXT NOT NULL DEFAULT 'Rangga Maheza'")
            addColumnIfMissing(db, "orders", "shiftName", "TEXT NOT NULL DEFAULT 'Shift 1'")
            addColumnIfMissing(db, "orders", "notes", "TEXT NOT NULL DEFAULT ''")

            // 4. order_items
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `order_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `orderInvoiceNumber` TEXT NOT NULL,
                    `productId` INTEGER NOT NULL,
                    `productName` TEXT NOT NULL,
                    `sku` TEXT NOT NULL,
                    `basePrice` REAL NOT NULL,
                    `costPrice` REAL NOT NULL DEFAULT 0.0,
                    `variantSummary` TEXT NOT NULL DEFAULT '',
                    `unitPrice` REAL NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `itemDiscount` REAL NOT NULL DEFAULT 0.0,
                    `lineTotal` REAL NOT NULL,
                    `categoryName` TEXT NOT NULL DEFAULT '',
                    `variantSize` TEXT NOT NULL DEFAULT '',
                    `variantType` TEXT NOT NULL DEFAULT '',
                    `variantBeans` TEXT NOT NULL DEFAULT '',
                    `variantGrind` TEXT NOT NULL DEFAULT '',
                    `variantSugar` TEXT NOT NULL DEFAULT '',
                    `variantAddons` TEXT NOT NULL DEFAULT '',
                    `variantExtraTotal` REAL NOT NULL DEFAULT 0.0,
                    FOREIGN KEY(`orderInvoiceNumber`) REFERENCES `orders`(`invoiceNumber`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_orderInvoiceNumber` ON `order_items` (`orderInvoiceNumber`)")
            addColumnIfMissing(db, "order_items", "costPrice", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "order_items", "variantSummary", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "order_items", "itemDiscount", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "order_items", "categoryName", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "order_items", "variantSize", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "order_items", "variantType", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "order_items", "variantBeans", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "order_items", "variantGrind", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "order_items", "variantSugar", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "order_items", "variantAddons", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "order_items", "variantExtraTotal", "REAL NOT NULL DEFAULT 0.0")

            // 5. variant_groups
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `variant_groups` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `isMultiSelect` INTEGER NOT NULL DEFAULT 0,
                    `optionsJson` TEXT NOT NULL,
                    `applicableCategoryIds` TEXT NOT NULL DEFAULT 'all',
                    `sortOrder` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            addColumnIfMissing(db, "variant_groups", "isMultiSelect", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "variant_groups", "applicableCategoryIds", "TEXT NOT NULL DEFAULT 'all'")
            addColumnIfMissing(db, "variant_groups", "sortOrder", "INTEGER NOT NULL DEFAULT 0")

            // 6. open_bills
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `open_bills` (
                    `id` TEXT NOT NULL,
                    `billNumber` TEXT NOT NULL,
                    `customerName` TEXT NOT NULL DEFAULT '',
                    `tableNumber` TEXT NOT NULL DEFAULT '',
                    `orderType` TEXT NOT NULL DEFAULT 'DINE_IN',
                    `itemsJson` TEXT NOT NULL,
                    `totalItemCount` INTEGER NOT NULL DEFAULT 0,
                    `subtotal` REAL NOT NULL DEFAULT 0.0,
                    `taxAmount` REAL NOT NULL DEFAULT 0.0,
                    `serviceFeeAmount` REAL NOT NULL DEFAULT 0.0,
                    `packagingFeeAmount` REAL NOT NULL DEFAULT 0.0,
                    `grandTotal` REAL NOT NULL DEFAULT 0.0,
                    `notes` TEXT NOT NULL DEFAULT '',
                    `cashierName` TEXT NOT NULL DEFAULT 'Rangga Maheza',
                    `createdAt` INTEGER NOT NULL DEFAULT 0,
                    `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            addColumnIfMissing(db, "open_bills", "customerName", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "open_bills", "tableNumber", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "open_bills", "orderType", "TEXT NOT NULL DEFAULT 'DINE_IN'")
            addColumnIfMissing(db, "open_bills", "totalItemCount", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "open_bills", "subtotal", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "open_bills", "taxAmount", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "open_bills", "serviceFeeAmount", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "open_bills", "packagingFeeAmount", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "open_bills", "grandTotal", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "open_bills", "notes", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "open_bills", "cashierName", "TEXT NOT NULL DEFAULT 'Rangga Maheza'")
            addColumnIfMissing(db, "open_bills", "createdAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "open_bills", "status", "TEXT NOT NULL DEFAULT 'ACTIVE'")

            // 7. shifts
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `shifts` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `shiftCode` TEXT NOT NULL,
                    `staffId` TEXT NOT NULL,
                    `staffName` TEXT NOT NULL,
                    `staffRole` TEXT NOT NULL,
                    `shiftName` TEXT NOT NULL,
                    `openedAt` INTEGER NOT NULL,
                    `closedAt` INTEGER,
                    `startingCash` REAL NOT NULL DEFAULT 0.0,
                    `totalCashSales` REAL NOT NULL DEFAULT 0.0,
                    `totalNonCashSales` REAL NOT NULL DEFAULT 0.0,
                    `totalCashExpenses` REAL NOT NULL DEFAULT 0.0,
                    `totalTransactions` INTEGER NOT NULL DEFAULT 0,
                    `expectedCash` REAL NOT NULL DEFAULT 0.0,
                    `actualCash` REAL,
                    `cashDifference` REAL,
                    `notes` TEXT NOT NULL DEFAULT '',
                    `status` TEXT NOT NULL DEFAULT 'OPEN'
                )
                """.trimIndent()
            )
            addColumnIfMissing(db, "shifts", "closedAt", "INTEGER")
            addColumnIfMissing(db, "shifts", "startingCash", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "shifts", "totalCashSales", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "shifts", "totalNonCashSales", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "shifts", "totalCashExpenses", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "shifts", "totalTransactions", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "shifts", "expectedCash", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "shifts", "actualCash", "REAL")
            addColumnIfMissing(db, "shifts", "cashDifference", "REAL")
            addColumnIfMissing(db, "shifts", "notes", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "shifts", "status", "TEXT NOT NULL DEFAULT 'OPEN'")

            // 8. cash_expenses
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cash_expenses` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `shiftId` INTEGER NOT NULL DEFAULT 0,
                    `shiftCode` TEXT NOT NULL,
                    `staffName` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `timestamp` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        private fun stepMigration(from: Int, to: Int) = object : Migration(from, to) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureSchemaIntegrity(db)
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            stepMigration(1, 2),
            stepMigration(2, 3),
            stepMigration(3, 4),
            stepMigration(4, 5),
            stepMigration(5, 6),
            stepMigration(6, 7),
            stepMigration(7, 8),
            stepMigration(8, 9),
            stepMigration(9, 10)
        )

        fun getDatabase(context: Context, scope: CoroutineScope? = null): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "caffeinery_pos.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
