package com.example.ui.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PrintDisabled
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.model.CashExpenseEntity
import com.example.data.local.model.OrderEntity
import com.example.data.local.model.ShiftEntity
import com.example.ui.PosViewModel
import com.example.ui.components.CurrencyUtils
import com.example.ui.components.DateTimeUtils
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandBlueLight
import com.example.ui.theme.CardBg
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.TextDarkMuted
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary

/**
 * Close Shift (Tutup Kasir / End Shift) Modal:
 * Compares system-recorded cash sales + starting petty cash against actual physical cash in drawer.
 * Computes overage/shortage (selisih) and finalizes the shift record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseShiftModal(
    shift: ShiftEntity,
    shiftOrders: List<OrderEntity>,
    shiftExpenses: List<CashExpenseEntity> = emptyList(),
    defaultWhatsAppPhone: String = "",
    onDismiss: () -> Unit,
    onConfirmCloseShift: (actualCash: Double, notes: String) -> Unit,
    onPrintShiftReceipt: (actualCash: Double) -> Unit,
    onOpenCashExpense: () -> Unit = {},
    onSendWhatsAppReport: (actualCash: Double, notes: String, targetPhone: String) -> Unit = { _, _, _ -> },
    viewModel: PosViewModel? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isPrinting = viewModel?.isPrintingAnimation?.collectAsStateWithLifecycle()?.value ?: false
    val showPrinterDisconnectedAlert = viewModel?.showPrinterDisconnectedAlert?.collectAsStateWithLifecycle()?.value ?: false
    val printerErrorMessage = viewModel?.printerErrorMessage?.collectAsStateWithLifecycle()?.value
    val printerStatusMessage = viewModel?.printerStatusMessage?.collectAsStateWithLifecycle()?.value ?: ""

    val cashSales = remember(shiftOrders) {
        shiftOrders.filter { it.paymentMethod.equals("CASH", ignoreCase = true) }
            .sumOf { it.grandTotal }
    }
    val bankTransferSales = remember(shiftOrders) {
        shiftOrders.filter {
            it.paymentMethod.contains("TRANSFER", ignoreCase = true) ||
            it.paymentMethod.contains("TF", ignoreCase = true) ||
            it.paymentMethod.equals("BANK_TRANSFER", ignoreCase = true)
        }.sumOf { it.grandTotal }
    }
    val qrisSales = remember(shiftOrders) {
        shiftOrders.filter { it.paymentMethod.contains("QRIS", ignoreCase = true) }
            .sumOf { it.grandTotal }
    }
    val grandTotalRevenue = remember(shiftOrders) {
        shiftOrders.sumOf { it.grandTotal }
    }
    val totalCashExpenses = remember(shiftExpenses) {
        shiftExpenses.sumOf { it.amount }
    }
    val expectedCashInDrawer = (shift.startingCash + cashSales) - totalCashExpenses

    var hasEditedCashInput by remember { mutableStateOf(false) }
    var physicalCashInput by remember { mutableStateOf(expectedCashInDrawer.toLong().coerceAtLeast(0L).toString()) }
    LaunchedEffect(expectedCashInDrawer) {
        if (!hasEditedCashInput) {
            physicalCashInput = expectedCashInDrawer.toLong().coerceAtLeast(0L).toString()
        }
    }
    var notesInput by remember { mutableStateOf("") }
    var targetWhatsAppPhone by remember(defaultWhatsAppPhone) { mutableStateOf(defaultWhatsAppPhone) }
    var inputError by remember { mutableStateOf<String?>(null) }

    val actualCash = physicalCashInput.toDoubleOrNull() ?: 0.0
    val cashDifference = actualCash - expectedCashInDrawer

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = CardBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 38.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFCBD5E1))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFFEE2E2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockClock,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Tutup Kasir (End Shift)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextDarkPrimary
                        )
                        Text(
                            text = "Rekapitulasi kas kecil dan omset penjualan shift",
                            fontSize = 12.sp,
                            color = TextDarkSecondary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextDarkSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Shift Profile Header Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceSubtle)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${shift.staffName} (${shift.staffRole})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkPrimary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(BrandBlueLight)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = shift.shiftName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandBlue
                            )
                        }
                    }

                    Text(
                        text = "Dibuka: ${DateTimeUtils.formatTimeWib(shift.openedAt)} WIB (${DateTimeUtils.formatDateWib(shift.openedAt)})",
                        fontSize = 12.sp,
                        color = TextDarkSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Breakdown Grid Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardBg)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "RINGKASAN PENJUALAN SHIFT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkMuted,
                        letterSpacing = 0.8.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Modal Kas Kecil Awal (Petty Cash)", fontSize = 13.sp, color = TextDarkSecondary)
                        Text(
                            text = CurrencyUtils.formatRupiah(shift.startingCash),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDarkPrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Penjualan Tunai (Cash)", fontSize = 13.sp, color = TextDarkSecondary)
                        Text(
                            text = CurrencyUtils.formatRupiah(cashSales),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF16A34A)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Transfer Bank (TF)", fontSize = 13.sp, color = TextDarkSecondary)
                        Text(
                            text = CurrencyUtils.formatRupiah(bankTransferSales),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandBlue
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "QRIS", fontSize = 13.sp, color = TextDarkSecondary)
                        Text(
                            text = CurrencyUtils.formatRupiah(qrisSales),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandBlue
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Total Pendapatan (Omset)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                        Text(
                            text = CurrencyUtils.formatRupiah(grandTotalRevenue),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandBlue
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Pengeluaran Kas (${shiftExpenses.size})", fontSize = 13.sp, color = TextDarkSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFEF3C7))
                                    .clickable { onOpenCashExpense() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("close_shift_add_expense_btn"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Tambah Pengeluaran",
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Catat",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }
                        Text(
                            text = "-${CurrencyUtils.formatRupiah(totalCashExpenses)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFDC2626)
                        )
                    }

                    if (shiftExpenses.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceSubtle)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            shiftExpenses.forEach { exp ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "• ${exp.description}",
                                        fontSize = 11.sp,
                                        color = TextDarkSecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = CurrencyUtils.formatRupiah(exp.amount),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextDarkPrimary
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Total Transaksi Selesai", fontSize = 13.sp, color = TextDarkSecondary)
                        Text(
                            text = "${shiftOrders.size} transaksi",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDarkPrimary
                        )
                    }

                    HorizontalDivider(thickness = 1.dp, color = BorderSubtle)

                    // Expected Cash in Drawer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Ekspektasi Kas di Laci",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkPrimary
                            )
                            Text(
                                text = "(Modal Awal + Penjualan Tunai) - Pengeluaran Kas",
                                fontSize = 11.sp,
                                color = TextDarkSecondary
                            )
                        }

                        Text(
                            text = CurrencyUtils.formatRupiah(expectedCashInDrawer),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Physical Cash Input
            Text(
                text = "UANG FISIK KAS DI LACI (AKTUAL)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = physicalCashInput,
                onValueChange = {
                    val clean = it.filter { ch -> ch.isDigit() }
                    hasEditedCashInput = true
                    physicalCashInput = clean
                    inputError = null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                prefix = {
                    Text("Rp ", fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                },
                singleLine = true,
                isError = inputError != null,
                supportingText = {
                    if (inputError != null) {
                        Text(inputError!!, color = Color(0xFFDC2626), fontSize = 11.sp)
                    } else {
                        Text("Terbaca: ${CurrencyUtils.formatRupiah(actualCash)}", color = TextDarkSecondary, fontSize = 11.sp)
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandBlue,
                    unfocusedBorderColor = BorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("physical_cash_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Difference Banner
            val (diffBg, diffBorder, diffTint, diffLabel) = when {
                cashDifference == 0.0 -> Quad(
                    Color(0xFFDCFCE7),
                    Color(0xFF86EFAC),
                    Color(0xFF16A34A),
                    "Uang Kas Pas / Sesuai (Rp 0)"
                )
                cashDifference > 0.0 -> Quad(
                    Color(0xFFE0F2FE),
                    Color(0xFF7DD3FC),
                    Color(0xFF0284C7),
                    "Kelebihan Kas: +${CurrencyUtils.formatRupiah(cashDifference)}"
                )
                else -> Quad(
                    Color(0xFFFEE2E2),
                    Color(0xFFFCA5A5),
                    Color(0xFFDC2626),
                    "Selisih Kurang: ${CurrencyUtils.formatRupiah(cashDifference)}"
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(diffBg)
                    .border(1.dp, diffBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = diffLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = diffTint
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notes
            Text(
                text = "CATATAN TUTUP KASIR (OPSIONAL)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                placeholder = { Text("Contoh: Diserahkan ke kasir shift 2 dalam kondisi rapi", fontSize = 13.sp, color = TextDarkMuted) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandBlue,
                    unfocusedBorderColor = BorderSubtle
                ),
                modifier = Modifier.fillMaxWidth().testTag("close_shift_notes")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Printer Status Feedback Banner (e.g. Success or Disconnected)
            if (printerStatusMessage.isNotBlank()) {
                val isSuccess = printerStatusMessage.contains("berhasil", ignoreCase = true)
                val isError = printerStatusMessage.contains("gagal", ignoreCase = true) ||
                        printerStatusMessage.contains("terputus", ignoreCase = true) ||
                        printerStatusMessage.contains("error", ignoreCase = true)
                if (isSuccess || isError) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSuccess) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                            .border(1.dp, if (isSuccess) Color(0xFF86EFAC) else Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("printer_status_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.PrintDisabled,
                                contentDescription = null,
                                tint = if (isSuccess) Color(0xFF16A34A) else Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = printerStatusMessage,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSuccess) Color(0xFF16A34A) else Color(0xFFDC2626),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Optional WhatsApp target number for owner/manager
            Text(
                text = "NOMOR WHATSAPP OWNER / MANAGER (LAPORAN SHIFT)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = targetWhatsAppPhone,
                onValueChange = { targetWhatsAppPhone = it },
                placeholder = { Text("Contoh: 081299887766 (opsional)", fontSize = 13.sp, color = TextDarkMuted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF25D366),
                    unfocusedBorderColor = BorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("whatsapp_target_phone_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Secondary Row: Print Receipt & Send to WhatsApp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (!isPrinting) {
                            onPrintShiftReceipt(actualCash)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isPrinting,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("print_shift_summary_btn")
                ) {
                    if (isPrinting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = BrandBlue
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Mencetak...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandBlue
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cetak Struk", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
                    }
                }

                Button(
                    onClick = {
                        onSendWhatsAppReport(actualCash, notesInput, targetWhatsAppPhone.trim())
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    modifier = Modifier
                        .weight(1.15f)
                        .height(50.dp)
                        .testTag("whatsapp_shift_report_btn")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kirim ke WhatsApp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Submit: Close Shift (Auto-prints receipt)
            Button(
                onClick = {
                    val numeric = physicalCashInput.toDoubleOrNull()
                    if (numeric == null || numeric < 0) {
                        inputError = "Masukkan nominal kas fisik yang valid"
                        return@Button
                    }
                    onConfirmCloseShift(numeric, notesInput)
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_close_shift_btn")
            ) {
                Icon(imageVector = Icons.Default.LockClock, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tutup Kasir & Auto-Print Laporan", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Bluetooth Disconnected / Print Error Alert Popup
    if (showPrinterDisconnectedAlert) {
        val errorDetail = printerErrorMessage ?: "Printer terputus. Pastikan printer Bluetooth menyala"

        AlertDialog(
            onDismissRequest = { viewModel?.dismissPrinterDisconnectedAlert() },
            icon = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PrintDisabled,
                        contentDescription = "Printer Gagal",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Gagal Mencetak Struk",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextDarkPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = errorDetail,
                        fontSize = 14.sp,
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.testTag("printer_error_text")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Pastikan printer Bluetooth dalam kondisi menyala, berada dalam jangkauan, dan Bluetooth perangkat sudah aktif sebelum mencetak ulang.",
                        fontSize = 12.sp,
                        color = TextDarkSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel?.dismissPrinterDisconnectedAlert()
                        onPrintShiftReceipt(actualCash)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("retry_print_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Coba Lagi",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel?.dismissPrinterDisconnectedAlert() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dismiss_printer_error_btn")
                ) {
                    Text(
                        text = "Tutup",
                        color = TextDarkSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
