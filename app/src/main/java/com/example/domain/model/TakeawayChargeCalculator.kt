package com.example.domain.model

/**
 * Smart Takeaway Packaging & Cup Charge Engine.
 * - Applies strictly to Beverage & Pastry/Food items.
 * - Exempts Roastery / Coffee Beans, Manual Brew Equipment, and Merchandise categories.
 * - Supports either quantity-based (per applicable item) or flat-fee-per-bill calculation.
 */
object TakeawayChargeCalculator {

    private val excludedCategoryKeywords = listOf(
        "bean",
        "beans",
        "roast",
        "roastery",
        "biji",
        "equip",
        "equipment",
        "manual_brew",
        "manual brew",
        "alat",
        "tool",
        "gear",
        "kettle",
        "grinder",
        "dripper",
        "scale",
        "merch",
        "merchandise",
        "apparel",
        "tumbler",
        "mug",
        "totebag",
        "tote",
        "aksesoris",
        "accessory",
        "accessories",
        "hardware"
    )

    fun isPackagingFeeApplicable(categoryId: String, categoryName: String = ""): Boolean {
        val combined = "${categoryId.trim()} ${categoryName.trim()}".lowercase()
        if (combined.isBlank()) return true
        return excludedCategoryKeywords.none { keyword -> combined.contains(keyword) }
    }

    fun countEligibleTakeawayQuantity(
        items: List<CartItem>,
        categoryNameById: Map<String, String> = emptyMap()
    ): Int {
        return items.sumOf { item ->
            val catName = categoryNameById[item.product.categoryId] ?: ""
            if (isPackagingFeeApplicable(item.product.categoryId, catName)) item.quantity else 0
        }
    }

    fun calculatePackagingFee(
        orderType: String,
        feesConfig: AdditionalFeesConfig,
        items: List<CartItem>,
        categoryNameById: Map<String, String> = emptyMap()
    ): Double {
        if (orderType != "TAKEAWAY" || !feesConfig.isTakeawayPackagingEnabled) return 0.0
        val eligibleQty = countEligibleTakeawayQuantity(items, categoryNameById)
        if (eligibleQty <= 0) return 0.0
        val unitFee = feesConfig.takeawayPackagingFee.coerceAtLeast(0.0)
        return if (feesConfig.isTakeawayFeePerItem) unitFee * eligibleQty else unitFee
    }
}
