package com.example.domain.model

import java.util.UUID

data class OutletInfo(
    val name: String = "BDS Coffee Company",
    val branch: String = "Senopati Roastery",
    val address: String = "Jl. Senopati No. 42, Kebayoran Baru, Jakarta Selatan",
    val phone: String = "+62 812-9988-7766",
    val taxId: String = "01.234.567.8-012.000",
    val instagram: String = "@bdscoffeecompany",
    val ownerWhatsAppNumber: String = ""
)

data class EmployeeItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String, // Owner, Manager, Kasir, Barista
    val shift: String = "Shift 1",
    val pin: String = "1234",
    val isActive: Boolean = true
) {
    fun isSuperAdmin(): Boolean {
        val r = role.trim().lowercase()
        return r.contains("owner") || r.contains("manager")
    }

    fun canAccessDashboard(): Boolean = isSuperAdmin()

    fun canManageCatalog(): Boolean = isSuperAdmin()

    fun canAccessSettings(): Boolean = isSuperAdmin()

    fun canExportReports(): Boolean = isSuperAdmin()

    fun allowedTabs(): List<com.example.ui.components.PosTab> {
        return if (isSuperAdmin()) {
            listOf(
                com.example.ui.components.PosTab.DASHBOARD,
                com.example.ui.components.PosTab.KASIR,
                com.example.ui.components.PosTab.KATALOG,
                com.example.ui.components.PosTab.TRANSAKSI,
                com.example.ui.components.PosTab.SETTINGS
            )
        } else {
            // Kasir / Barista: Restrict navigation strictly to Kasir (POS) and Transaksi
            listOf(
                com.example.ui.components.PosTab.KASIR,
                com.example.ui.components.PosTab.TRANSAKSI
            )
        }
    }
}

data class OrderMethodsConfig(
    val isDineInEnabled: Boolean = true,
    val isTakeAwayEnabled: Boolean = true,
    val isOnlineDeliveryEnabled: Boolean = true,
    val isTableNumberRequired: Boolean = true
)

data class PaymentGatewaysConfig(
    val isCashEnabled: Boolean = true,
    val isQrisEnabled: Boolean = true,
    val qrisMerchantName: String = "BDS COFFEE CO",
    val qrisNmid: String = "ID1024365195063",
    val qrisTerminal: String = "A01",
    val isBankTransferEnabled: Boolean = true,
    val bcaAccount: String = "BCA 827-019-2819 a/n PT Caffeinery Retail",
    val mandiriAccount: String = "Mandiri 137-00-1928374-1 a/n Caffeinery Coffee"
)

data class AdditionalFeesConfig(
    val isTaxEnabled: Boolean = true,
    val taxPercentage: Double = 10.0, // 10% PB1
    val isServiceFeeEnabled: Boolean = true,
    val serviceFeePercentage: Double = 2.5, // 2.5% Dine-in service charge
    val isTakeawayPackagingEnabled: Boolean = false,
    val takeawayPackagingFee: Double = 2000.0,
    val isTakeawayFeePerItem: Boolean = true, // true = per applicable item qty, false = flat per bill
    val isRoundingEnabled: Boolean = true
)

data class ReceiptCustomizerConfig(
    val paperSize: String = "58mm", // 58mm or 80mm
    val showLogo: Boolean = true,
    val showTax: Boolean = true,
    val headerNote: String = "BDS COFFEE COMPANY",
    val footerNote: String = "Terima kasih atas pembelian Anda.",
    val printCopies: Int = 1
)
