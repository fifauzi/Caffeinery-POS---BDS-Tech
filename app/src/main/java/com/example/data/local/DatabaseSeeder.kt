package com.example.data.local

import com.example.data.local.dao.PosDao
import com.example.data.local.model.CategoryEntity
import com.example.data.local.model.OrderEntity
import com.example.data.local.model.OrderItemEntity
import com.example.data.local.model.ProductEntity
import com.example.data.local.model.VariantGroupEntity

/**
 * Idempotent Database Seeder for BDS Coffee Company POS.
 * Ensures initial catalog, categories, variant groups, and sample orders
 * exist on fresh install or empty database without overwriting user data.
 */
object DatabaseSeeder {

    suspend fun seedIfEmpty(dao: PosDao) {
        // 1. Seed Categories if empty
        if (dao.getCategoryCount() == 0) {
            val categories = listOf(
                CategoryEntity("espresso_based", "Espresso Based", "coffee", "#7C4A27", 1),
                CategoryEntity("cold_brew", "Cold Brew", "local_cafe", "#1E40AF", 2),
                CategoryEntity("pour_over", "Pour Over", "coffee", "#0D9488", 3),
                CategoryEntity("special_choco", "Special Choco", "local_cafe", "#78350F", 4),
                CategoryEntity("tea_tisane", "Tea Tisane", "local_cafe", "#059669", 5),
                CategoryEntity("coffee_series", "Coffee Series (Sweet & Comfort)", "coffee", "#D97706", 6),
                CategoryEntity("bites_pastry", "Bites Pastry", "bakery_dining", "#B45309", 7),
                CategoryEntity("coffee_milk_bottle", "Coffee Milk Bottle 500ml", "inventory_2", "#4338CA", 8)
            )
            dao.insertCategories(categories)
        }

        // 2. Seed Variant Groups if empty
        if (dao.getVariantGroupCount() == 0) {
            val initialVariantGroups = listOf(
                VariantGroupEntity(
                    id = "addons",
                    name = "Add-ons",
                    isMultiSelect = true,
                    optionsJson = """[{"name":"Cashew Milk","price":5000.0},{"name":"Iced","price":3000.0},{"name":"Extra Shot","price":5000.0}]""",
                    applicableCategoryIds = "espresso_based,cold_brew,pour_over,special_choco,tea_tisane,coffee_series",
                    sortOrder = 1
                )
            )
            dao.insertVariantGroups(initialVariantGroups)
        }

        // 3. Seed Products if empty (Automatic 35% HPP baseline)
        if (dao.getProductCount() == 0) {
            fun hpp35(price: Double): Double = kotlin.math.round(price * 0.35)
            fun menuProduct(
                sku: String,
                name: String,
                categoryId: String,
                basePrice: Double,
                unit: String,
                description: String,
                iconName: String,
                stockQuantity: Int = 100
            ) = ProductEntity(
                sku = sku,
                name = name,
                categoryId = categoryId,
                basePrice = basePrice,
                costPrice = hpp35(basePrice),
                stockQuantity = stockQuantity,
                unit = unit,
                description = description,
                iconName = iconName,
                isActive = true
            )

            val initialProducts = listOf(
                // ESPRESSO BASED
                menuProduct("EB-001", "Espresso", "espresso_based", 20000.0, "cup", "A Shot of Mood Breaker", "coffee"),
                menuProduct("EB-002", "Black", "espresso_based", 23000.0, "cup", "Double Ristretto & Water | H/C*", "coffee"),
                menuProduct("EB-003", "Split Shot Piccolo", "espresso_based", 32000.0, "cup", "Split Shot Espresso & Milk Beverage, 100ml Cup", "coffee"),
                menuProduct("EB-004", "Magic", "espresso_based", 30000.0, "cup", "Double Ristretto & Milk, 120ml Cup | H/C*", "coffee"),
                menuProduct("EB-005", "Cappuccino", "espresso_based", 30000.0, "cup", "Ristretto & Milk, 120ml Cup | H/C*", "coffee"),
                menuProduct("EB-006", "Café Latte", "espresso_based", 32000.0, "cup", "Ristretto & Milk, 200ml Cup | H/C*", "coffee"),
                menuProduct("EB-007", "Café Mocha", "espresso_based", 35000.0, "cup", "Chocolate, Ristretto & Milk, 200ml Cup | H/C*", "coffee"),
                menuProduct("EB-008", "Dirty Latte", "espresso_based", 30000.0, "glass", "Half & Half With Ristretto on Top, 200ml Chilled Glass", "coffee"),
                menuProduct("EB-009", "Lil Tiger", "espresso_based", 33000.0, "cup", "Fresh Montblanc meets rich, creamy Dirty Latte", "coffee"),
                menuProduct("EB-010", "O.G Coffee Milk", "espresso_based", 28000.0, "cup", "Half & Half with Brown Sugar & Double Ristretto, 150ml Served*", "coffee"),
                menuProduct("EB-011", "Cashew Coffee Milk", "espresso_based", 30000.0, "cup", "Cashew Milk with Brown Sugar & Double Ristretto, 150ml Served*", "coffee"),
                menuProduct("EB-012", "Rhum Coffee Milk", "espresso_based", 30000.0, "cup", "Milk with Rhum, Brown Sugar & Double Ristretto, 150ml Served", "coffee"),

                // COLD BREW
                menuProduct("CB-001", "Neat Pour Cold Brew", "cold_brew", 20000.0, "glass", "Original Cold Brewed Coffee", "local_cafe"),
                menuProduct("CB-002", "Black Citrucy", "cold_brew", 33000.0, "glass", "Bright and Refreshing, 150ml Served*", "local_cafe"),
                menuProduct("CB-003", "Black Opium", "cold_brew", 33000.0, "glass", "Floral, Fragrant and Crisp with a Juicy Apple finish, 150ml Served*", "local_cafe"),
                menuProduct("CB-004", "Black Fruitas", "cold_brew", 33000.0, "glass", "Juicy, Tropical and Sweet-Tart, 150ml Served*", "local_cafe"),
                menuProduct("CB-005", "Black Minister", "cold_brew", 33000.0, "glass", "Warm Spice Meets Zesty Orange, 150ml Served*", "local_cafe"),

                // POUR OVER
                menuProduct("PO-001", "Filter Coffee", "pour_over", 25000.0, "glass", "Serve at Sensoric Glass Between 200 - 220ml", "coffee"),
                menuProduct("PO-002", "Cold Filter Coffee (Japanese)", "pour_over", 25000.0, "glass", "Serve Cold at Tuscany Glass Between 200 - 220ml", "coffee"),

                // SPECIAL CHOCO
                menuProduct("SC-001", "Single Origin Chocolate", "special_choco", 32000.0, "cup", "Single Origin Choco & Milk, 200ml Cup | H/C*", "local_cafe"),
                menuProduct("SC-002", "Choco Berry", "special_choco", 38000.0, "cup", "Specialty Choco with Berry & Milk", "local_cafe"),
                menuProduct("SC-003", "Choco Cheese", "special_choco", 38000.0, "cup", "Specialty Choco with Cream Cheese & Milk", "local_cafe"),

                // TEA TISANE
                menuProduct("TT-001", "Matcha Latte", "tea_tisane", 32000.0, "cup", "Matcha & Milk, 200ml Cup | H/C*", "local_cafe"),
                menuProduct("TT-002", "Matcha Berry", "tea_tisane", 38000.0, "cup", "Matcha & Milk and Berry Pure", "local_cafe"),
                menuProduct("TT-003", "Matcha Cheese", "tea_tisane", 38000.0, "cup", "Matcha & Cream Cheese Milk", "local_cafe"),
                menuProduct("TT-004", "Artisan Tea", "tea_tisane", 25000.0, "pot", "250ml Tea Pot | H/C*", "local_cafe"),

                // COFFEE SERIES (Sweet & Comfort)
                menuProduct("CS-001", "Apple Pie", "coffee_series", 35000.0, "cup", "Coldbrew, Apple Syrup, Cinnamon (Warmth & Sweet)", "coffee"),
                menuProduct("CS-002", "Montblanc", "coffee_series", 35000.0, "cup", "Coldbrew, Orange, Nutmeg (Sweet & Bold)", "coffee"),
                menuProduct("CS-003", "Cheeseblanc", "coffee_series", 35000.0, "cup", "Coldbrew, Cream Cheese, Strawberry Pure (Sweet & Savory)", "coffee"),

                // BITES PASTRY
                menuProduct("BP-001", "Cranberry Cheese", "bites_pastry", 28000.0, "pcs", "Sourdough by Roti Ciung Jakarta", "bakery_dining", 40),
                menuProduct("BP-002", "Cranberry Cheese Choco", "bites_pastry", 28000.0, "pcs", "Sourdough by Roti Ciung Jakarta", "bakery_dining", 40),
                menuProduct("BP-003", "Burn Cheese Cake", "bites_pastry", 25000.0, "pcs", "by Butter it Better", "bakery_dining", 40),

                // COFFEE MILK BOTTLE 500ML
                menuProduct("CB5-001", "O.G Coffee Milk (500ml)", "coffee_milk_bottle", 80000.0, "bottle", "Signature Coffeemilk, Bottle 500ml", "inventory_2", 30),
                menuProduct("CB5-002", "Cashew Coffee Milk (500ml)", "coffee_milk_bottle", 90000.0, "bottle", "Signature Coffeemilk with Cashew Milk, Bottle 500ml", "inventory_2", 30),
                menuProduct("CB5-003", "Rhum Coffee Milk (500ml)", "coffee_milk_bottle", 80000.0, "bottle", "Signature Coffeemilk with Rhum, Bottle 500ml", "inventory_2", 30)
            )
            dao.insertProducts(initialProducts)
        }
    }
}
