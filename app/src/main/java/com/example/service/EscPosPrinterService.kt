package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.model.OrderEntity
import com.example.data.local.model.OrderItemEntity
import com.example.ui.components.CurrencyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BluetoothPrinterDevice(
    val name: String,
    val address: String,
    val isConnected: Boolean = false,
    val isBonded: Boolean = true,
    val statusBadge: String = "Ready" // "Connected", "Ready", "Disconnected"
)

object EscPosPrinterService {

    // Standard SPP (Serial Port Profile) UUID for Bluetooth Thermal Printers
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ESC/POS Command Constants
    private val ESC_INIT = byteArrayOf(0x1B, 0x40) // ESC @
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2
    private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
    private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0
    private val GS_TEXT_DOUBLE = byteArrayOf(0x1D, 0x21, 0x11) // GS ! 0x11 (Double width + height)
    private val GS_TEXT_DOUBLE_HEIGHT = byteArrayOf(0x1D, 0x21, 0x01) // GS ! 0x01 (Normal width, double height)
    private val GS_TEXT_NORMAL = byteArrayOf(0x1D, 0x21, 0x00) // GS ! 0x00 (Normal)
    private val GS_CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x42, 0x00) // GS V 66 0
    private val LF = byteArrayOf(0x0A)

    private const val PAPER_WIDTH_CHARS = 32 // Standard 58mm thermal paper column width

    fun wrapAtWordBoundary(text: String, maxWidth: Int = PAPER_WIDTH_CHARS): String {
        if (text.isBlank()) return ""
        val resultLines = mutableListOf<String>()
        val paragraphs = text.split("\n")
        for (para in paragraphs) {
            val trimmed = para.trim()
            if (trimmed.isEmpty()) {
                resultLines.add("")
                continue
            }
            val words = trimmed.split(Regex("\\s+"))
            var currentLine = ""
            for (word in words) {
                if (currentLine.isEmpty()) {
                    if (word.length <= maxWidth) {
                        currentLine = word
                    } else {
                        var remaining = word
                        while (remaining.length > maxWidth) {
                            resultLines.add(remaining.take(maxWidth))
                            remaining = remaining.drop(maxWidth)
                        }
                        currentLine = remaining
                    }
                } else if (currentLine.length + 1 + word.length <= maxWidth) {
                    currentLine = "$currentLine $word"
                } else {
                    resultLines.add(currentLine)
                    if (word.length <= maxWidth) {
                        currentLine = word
                    } else {
                        var remaining = word
                        while (remaining.length > maxWidth) {
                            resultLines.add(remaining.take(maxWidth))
                            remaining = remaining.drop(maxWidth)
                        }
                        currentLine = remaining
                    }
                }
            }
            if (currentLine.isNotEmpty()) {
                resultLines.add(currentLine)
            }
        }
        return resultLines.joinToString("\n")
    }

    fun wrapIndentedAtWordBoundary(text: String, indent: String = "  ", maxWidth: Int = PAPER_WIDTH_CHARS): String {
        val innerWidth = (maxWidth - indent.length).coerceAtLeast(8)
        val wrapped = wrapAtWordBoundary(text, innerWidth)
        if (wrapped.isBlank()) return ""
        return wrapped.split("\n").joinToString("\n") { "$indent$it" }
    }

    private fun sanitizeEscPos(text: String): String {
        return text
            .replace("é", "e")
            .replace("è", "e")
            .replace("ê", "e")
            .replace("É", "E")
            .replace("•", "-")
            .replace("≥", ">=")
            .replace("≤", "<=")
            .replace("↳", ">")
            .replace("—", "-")
            .replace("–", "-")
            .map { ch ->
                if (ch == '\n' || ch == '\r' || ch == '\t' || ch.code in 0x20..0x7E) ch else ' '
            }
            .joinToString("")
    }

    private fun String.toEscPosBytes(): ByteArray = sanitizeEscPos(this).toByteArray(Charsets.US_ASCII)

    /**
     * Converts official outlet logo into compact ESC/POS monochrome 1-bit raster bit image (GS v 0).
     * Uses minimal byte width centered via ESC_ALIGN_CENTER to avoid overflowing small printer buffers.
     */
    fun getMonochromeLogoRaster(context: Context?, paperWidth: Int = PAPER_WIDTH_CHARS): ByteArray? {
        if (context == null) return null
        var bitmap: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
            bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.logo)
                ?: BitmapFactory.decodeResource(context.resources, R.drawable.iconpos)
                ?: return null

            val maxLogoWidth = if (paperWidth >= 48) 256 else 160
            val scale = maxLogoWidth.toFloat() / bitmap.width.coerceAtLeast(1)
            val targetLogoHeight = (bitmap.height * scale).toInt().coerceIn(24, maxLogoWidth)
            val targetLogoWidth = (((bitmap.width * (targetLogoHeight.toFloat() / bitmap.height.coerceAtLeast(1))).toInt()
                .coerceIn(32, maxLogoWidth)) / 8) * 8

            scaled = Bitmap.createScaledBitmap(bitmap, targetLogoWidth.coerceAtLeast(8), targetLogoHeight, true)
            val bytesPerRow = (scaled.width + 7) / 8

            val out = ByteArrayOutputStream()
            // ESC/POS Raster command: GS v 0 m xL xH yL yH
            out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
            out.write(bytesPerRow and 0xFF)
            out.write((bytesPerRow shr 8) and 0xFF)
            out.write(targetLogoHeight and 0xFF)
            out.write((targetLogoHeight shr 8) and 0xFF)

            for (y in 0 until targetLogoHeight) {
                val row = ByteArray(bytesPerRow)
                for (x in 0 until scaled.width) {
                    val pixel = scaled.getPixel(x, y)
                    val alpha = Color.alpha(pixel)
                    if (alpha > 40) {
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)
                        val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                        if (lum < 175) {
                            val byteIndex = x / 8
                            val bitIndex = 7 - (x % 8)
                            row[byteIndex] = (row[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                        }
                    }
                }
                out.write(row)
            }
            out.write(LF)
            out.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            try { scaled?.recycle() } catch (_: Exception) {}
            try { bitmap?.recycle() } catch (_: Exception) {}
        }
    }

    /**
     * Builds Livin' Merchant style ESC/POS byte array payload for 58mm / 80mm thermal printers.
     */
    fun buildReceiptBytes(
        order: OrderEntity,
        items: List<OrderItemEntity>,
        storeName: String = "BDS COFFEE CO",
        storeAddress: String = "Jl. Senopati No. 42, Jakarta",
        storePhone: String = "Telp: +62 21 555-8900",
        footerMessage: String = "Terima kasih atas pembelian Anda.",
        showTax: Boolean = true,
        paperWidth: Int = PAPER_WIDTH_CHARS,
        context: Context? = null,
        showLogo: Boolean = true,
        queueNumber: String? = null
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm 'WIB'", Locale("id", "ID")).apply {
            timeZone = com.example.ui.components.DateTimeUtils.WIB_ZONE
        }
        val effTime = com.example.ui.components.DateTimeUtils.getOrderEffectiveTimestamp(order)
        val dateStr = dateFormat.format(Date(effTime))
        val orderTypeLabel = if (order.orderType == "TAKEAWAY") "Takeaway" else "Dine In"
        val resolvedQueue = queueNumber?.trim()?.takeIf { it.isNotBlank() } ?: run {
            val suffix = order.invoiceNumber.substringAfterLast("-", "").trim()
            if (suffix.isNotBlank()) suffix.takeLast(6) else order.invoiceNumber.takeLast(6)
        }

        // 1. Initialize
        out.write(ESC_INIT)

        // 2. Header (Center, Bold, Double Height with normal width so full 32 chars fit cleanly on 58mm)
        out.write(ESC_ALIGN_CENTER)
        out.write(ESC_BOLD_ON)
        out.write(GS_TEXT_DOUBLE_HEIGHT)
        out.write((wrapAtWordBoundary(storeName.uppercase(), paperWidth) + "\n").toEscPosBytes())
        out.write(GS_TEXT_NORMAL)
        out.write(ESC_BOLD_OFF)

        if (storeAddress.isNotBlank()) {
            out.write((wrapAtWordBoundary(storeAddress, paperWidth) + "\n").toEscPosBytes())
        }
        if (storePhone.isNotBlank()) {
            out.write((wrapAtWordBoundary(storePhone, paperWidth) + "\n").toEscPosBytes())
        }
        out.write(dashedLine(paperWidth).toEscPosBytes())

        // 3. Order Info (Split Columns: Antrean [No] | Dine In / Takeaway, followed by Payment, Time, Cashier)
        out.write(ESC_ALIGN_LEFT)
        out.write(ESC_BOLD_ON)
        out.write(formatTwoColumns("Antrean $resolvedQueue", orderTypeLabel, paperWidth).toEscPosBytes())
        out.write(ESC_BOLD_OFF)
        out.write(formatTwoColumns("Pembayaran:", order.paymentMethod, paperWidth).toEscPosBytes())
        out.write(formatTwoColumns("Waktu:", dateStr, paperWidth).toEscPosBytes())
        out.write(formatTwoColumns("Kasir:", order.cashierName.ifBlank { "Rangga Maheza" }, paperWidth).toEscPosBytes())
        out.write(formatTwoColumns("Invoice:", order.invoiceNumber, paperWidth).toEscPosBytes())
        if (order.customerName.isNotBlank()) {
            out.write(formatTwoColumns("Pelanggan:", order.customerName, paperWidth).toEscPosBytes())
        }
        out.write(dashedLine(paperWidth).toEscPosBytes())

        // 4. Items & Modifiers ([Qty] [Product Name] left | Line Total right; indented modifiers below)
        for (item in items) {
            val itemLeft = "${item.quantity} ${item.productName}"
            val totalStr = formatIdr(item.lineTotal)
            out.write(formatTwoColumns(itemLeft, totalStr, paperWidth).toEscPosBytes())

            if (item.variantSummary.isNotBlank() && !item.variantSummary.equals("Standard", ignoreCase = true)) {
                val modifiers = item.variantSummary
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.equals("Standard", ignoreCase = true) }
                for (mod in modifiers) {
                    val indented = wrapIndentedAtWordBoundary(mod, "  ", paperWidth)
                    if (indented.isNotBlank()) {
                        out.write("$indented\n".toEscPosBytes())
                    }
                }
            }
            if (item.itemDiscount > 0) {
                out.write(formatTwoColumns("  Diskon Item", "-${formatIdr(item.itemDiscount)}", paperWidth).toEscPosBytes())
            }
        }

        out.write(dashedLine(paperWidth).toEscPosBytes())

        // 5. Summary (Subtotal, Service Charge, Packaging Fee, Tax, Bold Total Tagihan, Cash & Change)
        out.write(formatTwoColumns("Subtotal:", formatIdr(order.subtotal), paperWidth).toEscPosBytes())
        val totalDiscount = order.itemDiscountTotal + order.orderDiscountAmount
        if (totalDiscount > 0) {
            out.write(formatTwoColumns("Diskon:", "-${formatIdr(totalDiscount)}", paperWidth).toEscPosBytes())
        }
        if (order.serviceFeeAmount > 0) {
            out.write(formatTwoColumns("Service Charge (${CurrencyUtils.formatPercent(order.serviceFeePercentage)}):", formatIdr(order.serviceFeeAmount), paperWidth).toEscPosBytes())
        }
        if (order.packagingFeeAmount > 0) {
            out.write(formatTwoColumns("Biaya Kemasan:", formatIdr(order.packagingFeeAmount), paperWidth).toEscPosBytes())
        }
        if (showTax && order.taxAmount > 0) {
            out.write(formatTwoColumns("Pajak PB1 (${CurrencyUtils.formatPercent(order.taxPercentage)}):", formatIdr(order.taxAmount), paperWidth).toEscPosBytes())
        }

        out.write(solidLine(paperWidth).toEscPosBytes())

        // Total Tagihan (Bold)
        out.write(ESC_BOLD_ON)
        out.write(formatTwoColumns("Total Tagihan:", formatIdr(order.grandTotal), paperWidth).toEscPosBytes())
        out.write(ESC_BOLD_OFF)

        if (order.paymentMethod == "CASH") {
            out.write(formatTwoColumns("Tunai:", formatIdr(order.tenderedAmount), paperWidth).toEscPosBytes())
            out.write(formatTwoColumns("Kembalian:", formatIdr(order.changeAmount), paperWidth).toEscPosBytes())
        }

        out.write(dashedLine(paperWidth).toEscPosBytes())

        // 6. Footer (Center, cleanly word-wrapped)
        out.write(ESC_ALIGN_CENTER)
        val effectiveFooter = footerMessage.ifBlank { "Terima kasih atas pembelian Anda." }
        val wrappedFooter = wrapAtWordBoundary(effectiveFooter, paperWidth)
        if (wrappedFooter.isNotBlank()) {
            out.write("$wrappedFooter\n".toEscPosBytes())
        }

        // 7. Bottom Outlet Logo (Right after footer text)
        if (showLogo && context != null) {
            val logoBytes = getMonochromeLogoRaster(context, paperWidth)
            if (logoBytes != null && logoBytes.isNotEmpty()) {
                out.write(LF)
                out.write(ESC_ALIGN_CENTER)
                out.write(logoBytes)
                out.write(GS_TEXT_NORMAL)
                out.write(ESC_BOLD_OFF)
            }
        }

        out.write(LF)
        out.write(LF)
        out.write(LF)

        // 8. Cut paper
        out.write(GS_CUT_PAPER)

        return out.toByteArray()
    }

    fun dashedLine(width: Int = PAPER_WIDTH_CHARS): String = "-".repeat(width) + "\n"
    fun solidLine(width: Int = PAPER_WIDTH_CHARS): String = "=".repeat(width) + "\n"

    /**
     * Builds ESC/POS byte payload for Shift Closing & Petty Cash reconciliation receipt.
     * Contains:
     * - Header info: Store Name, Shift ID, Cashier, Date & Time Range
     * - Cash drawer summary: Starting Cash (Petty cash), Cash sales, Expected cash, Actual cash, Difference
     * - Revenue breakdown by payment method: Cash, Bank Transfer (TF), QRIS, Grand Total Revenue
     * - Product sales breakdown: Items sold with total quantity
     * - Sign-off verification space
     */
    fun buildShiftClosingReceiptBytes(
        shift: com.example.data.local.model.ShiftEntity,
        cashSales: Double,
        nonCashSales: Double = 0.0,
        bankTransferSales: Double = 0.0,
        qrisSales: Double = 0.0,
        grandTotalRevenue: Double = cashSales + (if (bankTransferSales + qrisSales > 0) bankTransferSales + qrisSales else nonCashSales),
        actualCash: Double,
        transactionCount: Int,
        productSales: Map<String, Int> = emptyMap(),
        cashExpenses: List<com.example.data.local.model.CashExpenseEntity> = emptyList(),
        totalCashExpenses: Double = cashExpenses.sumOf { it.amount },
        storeName: String = "BDS COFFEE COMPANY",
        storeBranch: String = "Senopati Roastery",
        paperWidth: Int = PAPER_WIDTH_CHARS,
        context: Context? = null,
        showLogo: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss 'WIB'", Locale.getDefault()).apply {
            timeZone = com.example.ui.components.DateTimeUtils.WIB_ZONE
        }
        val openedStr = dateFormat.format(Date(shift.openedAt))
        val closedTime = if (shift.closedAt != null && shift.closedAt > 0) shift.closedAt else System.currentTimeMillis()
        val closedStr = dateFormat.format(Date(closedTime))
        val expectedCash = (shift.startingCash + cashSales) - totalCashExpenses
        val diff = actualCash - expectedCash

        val effectiveTfSales = if (bankTransferSales > 0) bankTransferSales else (if (nonCashSales > 0 && qrisSales == 0.0) 0.0 else bankTransferSales)
        val effectiveQrisSales = if (qrisSales > 0) qrisSales else (if (nonCashSales > 0 && bankTransferSales == 0.0) nonCashSales else qrisSales)

        out.write(ESC_INIT)

        out.write(ESC_ALIGN_CENTER)
        out.write(ESC_BOLD_ON)
        out.write(GS_TEXT_DOUBLE_HEIGHT)
        out.write((wrapAtWordBoundary(storeName, paperWidth) + "\n").toEscPosBytes())
        out.write(GS_TEXT_NORMAL)
        if (storeBranch.isNotBlank()) {
            out.write((wrapAtWordBoundary(storeBranch, paperWidth) + "\n").toEscPosBytes())
        }
        out.write("REKAP PENUTUPAN KASIR\n".toEscPosBytes())
        out.write(ESC_BOLD_OFF)
        out.write(solidLine(paperWidth).toEscPosBytes())

        // 1. Header Info
        out.write(ESC_ALIGN_LEFT)
        val shiftIdDisplay = if (shift.id > 0) " (ID: #${shift.id})" else ""
        out.write(formatTwoColumns("Shift:", "${shift.shiftName}$shiftIdDisplay", paperWidth).toEscPosBytes())
        out.write(formatTwoColumns("Kasir:", shift.staffName, paperWidth).toEscPosBytes())
        out.write(formatTwoColumns("Peran:", shift.staffRole, paperWidth).toEscPosBytes())
        out.write(formatTwoColumns("Buka:", openedStr, paperWidth).toEscPosBytes())
        out.write(formatTwoColumns("Tutup:", closedStr, paperWidth).toEscPosBytes())
        out.write(dashedLine(paperWidth).toEscPosBytes())

        // 2. Cash Reconciliation Details (Rekonsiliasi Kas Laci)
        out.write(ESC_BOLD_ON)
        out.write("REKONSILIASI KAS LACI\n".toEscPosBytes())
        out.write(ESC_BOLD_OFF)
        out.write(formatTwoColumns("Kas Awal (Petty Cash):", formatIdr(shift.startingCash), paperWidth).toEscPosBytes())
        out.write(formatTwoColumns("Pendapatan Tunai Sistem:", formatIdr(cashSales), paperWidth).toEscPosBytes())
        out.write(formatTwoColumns("Pengeluaran Kas:", "-${formatIdr(totalCashExpenses)}", paperWidth).toEscPosBytes())
        if (cashExpenses.isNotEmpty()) {
            cashExpenses.forEach { exp ->
                out.write(formatTwoColumns("  - ${exp.description.take(18)}", formatIdr(exp.amount), paperWidth).toEscPosBytes())
            }
        }
        out.write(ESC_BOLD_ON)
        out.write(formatTwoColumns("Ekspektasi Kas di Laci:", formatIdr(expectedCash), paperWidth).toEscPosBytes())
        out.write(formatTwoColumns("Kas Fisik di Tangan:", formatIdr(actualCash), paperWidth).toEscPosBytes())
        val diffLabel = when {
            diff == 0.0 -> "Rp 0 (Pas)"
            diff > 0 -> "+${formatIdr(diff)}"
            else -> formatIdr(diff)
        }
        out.write(formatTwoColumns("Selisih (Discrepancy):", diffLabel, paperWidth).toEscPosBytes())
        out.write(ESC_BOLD_OFF)
        out.write(dashedLine(paperWidth).toEscPosBytes())

        // 3. Revenue Breakdown by Payment Method
        out.write(ESC_BOLD_ON)
        out.write("PENDAPATAN METODE BAYAR\n".toEscPosBytes())
        out.write(ESC_BOLD_OFF)
        out.write(formatTwoColumns("Total Cash:", formatIdr(cashSales), paperWidth).toEscPosBytes())
        out.write(formatTwoColumns("Total QRIS:", formatIdr(effectiveQrisSales), paperWidth).toEscPosBytes())
        out.write(formatTwoColumns("Total Bank Transfer (TF):", formatIdr(effectiveTfSales), paperWidth).toEscPosBytes())
        out.write(formatTwoColumns("Total Transaksi:", "$transactionCount pesanan", paperWidth).toEscPosBytes())
        out.write(ESC_BOLD_ON)
        out.write(formatTwoColumns("Grand Total Omset Shift:", formatIdr(grandTotalRevenue), paperWidth).toEscPosBytes())
        out.write(ESC_BOLD_OFF)
        out.write(dashedLine(paperWidth).toEscPosBytes())

        // 4. Product Breakdown (Itemized Sales)
        out.write(ESC_BOLD_ON)
        out.write("PRODUK TERJUAL (ITEMIZED SALES)\n".toEscPosBytes())
        out.write(ESC_BOLD_OFF)
        if (productSales.isNotEmpty()) {
            var totalPcs = 0
            productSales.entries.sortedByDescending { it.value }.forEach { (prodName, qty) ->
                totalPcs += qty
                out.write(formatTwoColumns(prodName, "x$qty", paperWidth).toEscPosBytes())
            }
            out.write(ESC_BOLD_ON)
            out.write(formatTwoColumns("Total Item Terjual:", "$totalPcs pcs", paperWidth).toEscPosBytes())
            out.write(ESC_BOLD_OFF)
        } else {
            out.write("Tidak ada produk terjual\n".toEscPosBytes())
        }
        out.write(dashedLine(paperWidth).toEscPosBytes())

        if (shift.notes.isNotBlank()) {
            out.write((wrapAtWordBoundary("Catatan: ${shift.notes}", paperWidth) + "\n").toEscPosBytes())
            out.write(dashedLine(paperWidth).toEscPosBytes())
        }

        // 5. Sign-off / Verification Space
        out.write(ESC_ALIGN_CENTER)
        out.write("TANDA TANGAN & VERIFIKASI\n\n\n\n".toEscPosBytes())
        val colWidth = paperWidth / 2
        val leftSign = "( ${shift.staffName.take(colWidth - 4).trim()} )"
        val rightSign = "(  Supervisor  )"
        out.write(formatTwoColumns(leftSign, rightSign, paperWidth).toEscPosBytes())
        out.write(formatTwoColumns("Kasir", "Supervisor", paperWidth).toEscPosBytes())
        out.write(LF)
        out.write((wrapAtWordBoundary("Caffeinery POS by BDS Tech", paperWidth) + "\n").toEscPosBytes())

        // Bottom Outlet Logo right after footer text
        if (showLogo && context != null) {
            val logoBytes = getMonochromeLogoRaster(context, paperWidth)
            if (logoBytes != null && logoBytes.isNotEmpty()) {
                out.write(LF)
                out.write(ESC_ALIGN_CENTER)
                out.write(logoBytes)
                out.write(GS_TEXT_NORMAL)
                out.write(ESC_BOLD_OFF)
            }
        }

        out.write("\n\n\n".toEscPosBytes())
        out.write(GS_CUT_PAPER)

        return out.toByteArray()
    }

    fun formatTwoColumns(left: String, right: String, maxWidth: Int = PAPER_WIDTH_CHARS): String {
        val totalLen = left.length + right.length
        return if (totalLen < maxWidth) {
            val spaces = maxWidth - totalLen
            left + " ".repeat(spaces) + right + "\n"
        } else if (totalLen == maxWidth) {
            left + right + "\n"
        } else {
            val wrappedLeft = wrapAtWordBoundary(left, maxWidth).ifBlank { left }
            if (right.length < maxWidth) {
                wrappedLeft + "\n" + right.padStart(maxWidth) + "\n"
            } else {
                wrappedLeft + "\n" + right + "\n"
            }
        }
    }

    private fun formatIdr(amount: Double): String {
        return "Rp " + String.format(Locale.GERMAN, "%,.0f", amount)
    }

    /**
     * Required Bluetooth runtime permissions based on Android API level.
     * Android 12+ (API 31+) requires BLUETOOTH_SCAN and BLUETOOTH_CONNECT.
     * Legacy Android requires FINE/COARSE LOCATION and BLUETOOTH permissions.
     */
    fun getRequiredBluetoothPermissions(): Array<String> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    /**
     * Checks if all required runtime Bluetooth permissions are currently granted.
     */
    fun hasBluetoothPermissions(context: Context): Boolean {
        val perms = getRequiredBluetoothPermissions()
        return perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Validates whether the hardware Bluetooth adapter is present and active (turned on).
     */
    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val adapter = getBluetoothAdapter(context)
            adapter != null && adapter.isEnabled
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Obtains BluetoothAdapter using modern BluetoothManager or fallback to default adapter.
     */
    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        } catch (_: Exception) {
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    /**
     * Retrieves real paired/bonded Bluetooth devices from the Android device.
     * Computes dynamic status badge ("Connected", "Ready", "Disconnected").
     * Safe against missing runtime permissions on Android 12+.
     */
    @SuppressLint("MissingPermission")
    fun getPairedBluetoothPrinters(context: Context, selectedAddress: String = ""): List<BluetoothPrinterDevice> {
        val list = mutableListOf<BluetoothPrinterDevice>()
        try {
            if (!hasBluetoothPermissions(context)) {
                return list
            }
            val adapter = getBluetoothAdapter(context)
            if (adapter != null && adapter.isEnabled) {
                val pairedDevices: Set<BluetoothDevice>? = adapter.bondedDevices
                pairedDevices?.forEach { device ->
                    val addr = device.address ?: ""
                    if (addr.isNotBlank()) {
                        val isSelected = addr.equals(selectedAddress, ignoreCase = true)
                        val isConn = isSelected && isPrinterConnected(context, addr)
                        val badge = when {
                            isConn -> "Connected"
                            isSelected -> "Disconnected"
                            else -> "Ready"
                        }
                        val devName = try { device.name } catch (_: Exception) { null }
                        val cleanName = if (!devName.isNullOrBlank()) devName else "Bluetooth Printer ($addr)"
                        list.add(
                            BluetoothPrinterDevice(
                                name = cleanName,
                                address = addr,
                                isConnected = isConn,
                                isBonded = true,
                                statusBadge = badge
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    /**
     * Triggers classic Bluetooth discovery for nearby active Bluetooth thermal printers.
     * Registers a BroadcastReceiver to stream newly discovered devices dynamically.
     * Uses Context.RECEIVER_EXPORTED on Android 13+ to avoid SecurityException.
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery(
        context: Context,
        onDeviceFound: (BluetoothPrinterDevice) -> Unit,
        onDiscoveryFinished: () -> Unit
    ): BroadcastReceiver? {
        if (!hasBluetoothPermissions(context)) {
            onDiscoveryFinished()
            return null
        }
        val adapter = getBluetoothAdapter(context) ?: run {
            onDiscoveryFinished()
            return null
        }
        if (!adapter.isEnabled) {
            onDiscoveryFinished()
            return null
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        if (device != null) {
                            val addr = device.address ?: ""
                            if (addr.isNotBlank()) {
                                val isBonded = device.bondState == BluetoothDevice.BOND_BONDED
                                val devName = try { device.name } catch (_: Exception) { null }
                                val cleanName = if (!devName.isNullOrBlank()) devName else "Bluetooth Device ($addr)"
                                onDeviceFound(
                                    BluetoothPrinterDevice(
                                        name = cleanName,
                                        address = addr,
                                        isConnected = false,
                                        isBonded = isBonded,
                                        statusBadge = if (isBonded) "Ready" else "Disconnected"
                                    )
                                )
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        onDiscoveryFinished()
                    }
                }
            }
        }

        return try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(receiver, filter)
            }

            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            val started = adapter.startDiscovery()
            if (!started) {
                onDiscoveryFinished()
            }
            receiver
        } catch (e: Exception) {
            onDiscoveryFinished()
            null
        }
    }

    /**
     * Safely stops discovery and unregisters the receiver.
     */
    @SuppressLint("MissingPermission")
    fun stopDiscovery(context: Context, receiver: BroadcastReceiver?) {
        try {
            val adapter = getBluetoothAdapter(context)
            if (adapter?.isDiscovering == true) {
                adapter.cancelDiscovery()
            }
        } catch (_: Exception) {}
        try {
            if (receiver != null) {
                context.applicationContext.unregisterReceiver(receiver)
            }
        } catch (_: Exception) {}
    }

    /**
     * Inspects Bluetooth adapter and connection readiness before attempting transmission.
     */
    @SuppressLint("MissingPermission")
    fun checkPrinterConnection(context: Context, deviceAddress: String): Result<Unit> {
        if (deviceAddress.isBlank()) {
            return Result.failure(Exception("Gagal mencetak: Belum ada printer Bluetooth yang dipilih. Silakan pilih printer di pengaturan."))
        }
        val adapter = getBluetoothAdapter(context)
            ?: return Result.failure(Exception("Gagal mencetak: Perangkat tidak mendukung koneksi Bluetooth."))
        if (!adapter.isEnabled) {
            return Result.failure(Exception("Gagal mencetak: Bluetooth pada perangkat tidak aktif. Silakan aktifkan Bluetooth."))
        }
        return try {
            val device = adapter.getRemoteDevice(deviceAddress)
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                Result.failure(Exception("Gagal mencetak: Perangkat printer Bluetooth belum dipasangkan (unpaired). Silakan pasangkan di pengaturan Bluetooth."))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal mencetak: Perangkat printer Bluetooth terputus atau mati", e))
        }
    }

    /**
     * Checks if the Bluetooth thermal printer is actively connected or ready.
     * Validates adapter status and paired/bonded state.
     */
    @SuppressLint("MissingPermission")
    fun isPrinterConnected(context: Context, deviceAddress: String): Boolean {
        return checkPrinterConnection(context, deviceAddress).isSuccess
    }

    /**
     * Connects to a physical Bluetooth thermal printer and transmits ESC/POS bytes.
     * Catches network/RFCOMM socket errors and timeouts gracefully.
     */
    @SuppressLint("MissingPermission")
    suspend fun printBytes(
        deviceAddress: String,
        payload: ByteArray,
        context: Context? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (deviceAddress.isBlank()) {
            return@withContext Result.failure(Exception("Gagal mencetak: Belum ada printer Bluetooth yang dipilih. Silakan pilih printer di pengaturan."))
        }

        val rawAdapter = if (context != null) getBluetoothAdapter(context) else BluetoothAdapter.getDefaultAdapter()
        val adapter = rawAdapter
            ?: return@withContext Result.failure(Exception("Gagal mencetak: Perangkat tidak mendukung koneksi Bluetooth."))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(Exception("Gagal mencetak: Bluetooth pada perangkat tidak aktif. Silakan aktifkan Bluetooth."))
        }

        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null

        try {
            // Cancel discovery before connecting to ensure maximum RFCOMM throughput
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }

            val device = adapter.getRemoteDevice(deviceAddress)

            // Connect using standard SPP UUID with fallback to reflection socket if needed
            socket = try {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            } catch (_: Exception) {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                m.invoke(device, 1) as BluetoothSocket
            }

            // Real physical connection attempt with 7-second timeout
            withTimeout(7000L) {
                socket.connect()
            }

            outputStream = socket.outputStream
            // Chunk writes into 256-byte packets with short pauses so thermal printer RX buffers do not overflow after raster logos
            val chunkSize = 256
            var offset = 0
            while (offset < payload.size) {
                val len = kotlin.math.min(chunkSize, payload.size - offset)
                outputStream.write(payload, offset, len)
                outputStream.flush()
                offset += len
                if (offset < payload.size) {
                    delay(25)
                }
            }

            // Allow printer buffer to drain and finish printing before closing socket
            delay(1200)

            val devName = try { device.name } catch (_: Exception) { null }
            val displayName = if (!devName.isNullOrBlank()) devName else deviceAddress
            Result.success("Struk berhasil dicetak ke $displayName")
        } catch (e: Exception) {
            val errorMsg = "Gagal mencetak: Perangkat printer Bluetooth terputus atau mati"
            Result.failure(Exception(errorMsg, e))
        } finally {
            try {
                outputStream?.close()
            } catch (_: Exception) {}
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Converts raw bytes to readable Hex dump string for debugging and protocol inspection.
     */
    fun bytesToHexDump(bytes: ByteArray): String {
        val hexChars = "0123456789ABCDEF"
        val sb = StringBuilder()
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            sb.append(hexChars[v ushr 4])
            sb.append(hexChars[v and 0x0F])
            sb.append(" ")
            if ((i + 1) % 16 == 0) sb.append("\n")
            else if ((i + 1) % 8 == 0) sb.append("  ")
        }
        return sb.toString()
    }
}
