package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.local.model.CashExpenseEntity
import com.example.data.local.model.ShiftEntity
import com.example.ui.components.CurrencyUtils
import com.example.ui.components.DateTimeUtils
import java.net.URLEncoder

/**
 * Formats and shares Shift Closing & Cash Reconciliation Reports directly to WhatsApp.
 */
object ShiftReportService {

    fun buildShiftClosingWhatsAppMessage(
        shift: ShiftEntity,
        storeName: String = "BDS Coffee Company",
        storeBranch: String = "",
        cashSales: Double,
        qrisSales: Double,
        bankTransferSales: Double,
        grandTotalRevenue: Double,
        cashExpenses: List<CashExpenseEntity> = emptyList(),
        totalCashExpenses: Double = cashExpenses.sumOf { it.amount },
        actualCash: Double,
        transactionCount: Int,
        productSales: Map<String, Int> = emptyMap(),
        notes: String = ""
    ): String {
        val expectedCash = (shift.startingCash + cashSales) - totalCashExpenses
        val diff = actualCash - expectedCash
        val closedTime = if (shift.closedAt != null && shift.closedAt > 0) shift.closedAt else System.currentTimeMillis()
        val shiftIdStr = if (shift.id > 0) "#${shift.id} (${shift.shiftCode})" else shift.shiftCode

        val diffText = when {
            diff == 0.0 -> "Rp 0 (Pas / Sesuai)"
            diff > 0.0 -> "+${CurrencyUtils.formatIdr(diff)} (Surplus)"
            else -> "${CurrencyUtils.formatIdr(diff)} (Minus / Kurang)"
        }

        val sb = StringBuilder()
        val storeHeader = if (storeBranch.isNotBlank()) "$storeName - $storeBranch" else storeName
        sb.appendLine("📊 *LAPORAN TUTUP KASIR (SHIFT REPORT)*")
        sb.appendLine("🏪 *$storeHeader*")
        sb.appendLine("━━━━━━━━━━━━━━━━━━")
        sb.appendLine("🆔 *Shift ID:* $shiftIdStr")
        sb.appendLine("🕒 *Shift:* ${shift.shiftName}")
        sb.appendLine("👤 *Kasir:* ${shift.staffName} (${shift.staffRole})")
        sb.appendLine("🟢 *Buka:* ${DateTimeUtils.formatDateWib(shift.openedAt)}, ${DateTimeUtils.formatTimeWib(shift.openedAt)} WIB")
        sb.appendLine("🔴 *Tutup:* ${DateTimeUtils.formatDateWib(closedTime)}, ${DateTimeUtils.formatTimeWib(closedTime)} WIB")
        sb.appendLine("━━━━━━━━━━━━━━━━━━")
        sb.appendLine("💰 *RINCIAN PENJUALAN ($transactionCount Transaksi)*")
        sb.appendLine("• Tunai (Cash): ${CurrencyUtils.formatIdr(cashSales)}")
        sb.appendLine("• QRIS: ${CurrencyUtils.formatIdr(qrisSales)}")
        sb.appendLine("• Bank Transfer (TF): ${CurrencyUtils.formatIdr(bankTransferSales)}")
        sb.appendLine("• *Total Omset Shift:* *${CurrencyUtils.formatIdr(grandTotalRevenue)}*")
        sb.appendLine("━━━━━━━━━━━━━━━━━━")
        sb.appendLine("🧮 *REKONSILIASI KAS LACI*")
        sb.appendLine("• Kas Awal (Petty Cash): ${CurrencyUtils.formatIdr(shift.startingCash)}")
        sb.appendLine("• Penjualan Tunai: +${CurrencyUtils.formatIdr(cashSales)}")
        sb.appendLine("• Total Pengeluaran Kas: -${CurrencyUtils.formatIdr(totalCashExpenses)}")
        if (cashExpenses.isNotEmpty()) {
            cashExpenses.forEach { exp ->
                sb.appendLine("   ↳ _${exp.description}: ${CurrencyUtils.formatIdr(exp.amount)}_")
            }
        }
        sb.appendLine("• *Ekspektasi Kas di Laci:* *${CurrencyUtils.formatIdr(expectedCash)}*")
        sb.appendLine("• *Kas Fisik Aktual:* *${CurrencyUtils.formatIdr(actualCash)}*")
        sb.appendLine("• *Selisih (Discrepancy):* *$diffText*")
        sb.appendLine("━━━━━━━━━━━━━━━━━━")
        sb.appendLine("☕ *RINCIAN PRODUK TERJUAL*")
        if (productSales.isNotEmpty()) {
            var totalQty = 0
            productSales.entries.sortedByDescending { it.value }.forEach { (prodName, qty) ->
                totalQty += qty
                sb.appendLine("• $prodName: ${qty}x")
            }
            sb.appendLine("*Total Item Terjual:* $totalQty pcs")
        } else {
            sb.appendLine("• Tidak ada produk terjual pada shift ini")
        }

        if (notes.isNotBlank()) {
            sb.appendLine("━━━━━━━━━━━━━━━━━━")
            sb.appendLine("📝 *Catatan:* $notes")
        }

        sb.appendLine("━━━━━━━━━━━━━━━━━━")
        sb.appendLine("_Laporan otomatis dari Caffeinery POS by BDS Tech_")
        return sb.toString()
    }

    fun normalizeWhatsAppPhone(phone: String): String {
        val digits = phone.replace(Regex("[^0-9]"), "")
        return when {
            digits.isBlank() -> ""
            digits.startsWith("0") -> "62" + digits.substring(1)
            else -> digits
        }
    }

    fun shareShiftReportToWhatsApp(
        context: Context,
        message: String,
        ownerPhone: String = ""
    ) {
        val cleanPhone = normalizeWhatsAppPhone(ownerPhone)
        try {
            val encodedMsg = URLEncoder.encode(message, "UTF-8")
            val url = if (cleanPhone.isNotEmpty()) {
                "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMsg"
            } else {
                "https://api.whatsapp.com/send?text=$encodedMsg"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val chooser = Intent.createChooser(shareIntent, "Kirim Laporan Shift ke WhatsApp").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal membuka WhatsApp: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
