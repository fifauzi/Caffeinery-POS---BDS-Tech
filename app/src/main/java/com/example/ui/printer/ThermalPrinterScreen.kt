package com.example.ui.printer

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.PrintDisabled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.model.OrderEntity
import com.example.data.local.model.OrderItemEntity
import com.example.service.EscPosPrinterService
import com.example.ui.PosViewModel
import com.example.ui.checkout.ReceiptDashedLine
import com.example.ui.checkout.ReceiptRowMonospace
import com.example.ui.components.CurrencyUtils
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandBlueLight
import com.example.ui.theme.CardBg
import com.example.ui.theme.ScaffoldBg
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.TextDarkMuted
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ThermalPrinterScreen(
    viewModel: PosViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pairedPrinters by viewModel.pairedPrinters.collectAsStateWithLifecycle()
    val selectedPrinterAddress by viewModel.selectedPrinterAddress.collectAsStateWithLifecycle()
    val printerStatus by viewModel.printerStatusMessage.collectAsStateWithLifecycle()
    val isPrinting by viewModel.isPrintingAnimation.collectAsStateWithLifecycle()
    val showPrinterDisconnectedAlert by viewModel.showPrinterDisconnectedAlert.collectAsStateWithLifecycle()
    val showBluetoothDisabledAlert by viewModel.showBluetoothDisabledAlert.collectAsStateWithLifecycle()
    val printerErrorMessage by viewModel.printerErrorMessage.collectAsStateWithLifecycle()
    val isScanning by viewModel.isBluetoothScanning.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.refreshBluetoothPrinters(context, triggerScan = true)
        } else {
            Toast.makeText(context, "Izin Bluetooth diperlukan untuk mencari printer thermal", Toast.LENGTH_LONG).show()
        }
    }

    val requestScan = {
        if (EscPosPrinterService.hasBluetoothPermissions(context)) {
            if (!EscPosPrinterService.isBluetoothEnabled(context)) {
                viewModel.triggerBluetoothDisabledAlert()
            } else {
                viewModel.refreshBluetoothPrinters(context, triggerScan = true)
            }
        } else {
            permissionLauncher.launch(EscPosPrinterService.getRequiredBluetoothPermissions())
        }
    }

    var showHexInspector by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (EscPosPrinterService.hasBluetoothPermissions(context)) {
            viewModel.refreshBluetoothPrinters(context, triggerScan = false)
        } else {
            permissionLauncher.launch(EscPosPrinterService.getRequiredBluetoothPermissions())
        }
    }

    val testOrder = remember {
        OrderEntity(
            invoiceNumber = "INV-TEST-0001",
            timestamp = System.currentTimeMillis(),
            customerName = "Pelanggan Test Thermal",
            subtotal = 69000.0,
            itemDiscountTotal = 0.0,
            orderDiscountAmount = 0.0,
            taxPercentage = 10.0,
            taxAmount = 6900.0,
            grandTotal = 75900.0,
            paymentMethod = "QRIS",
            tenderedAmount = 75900.0,
            changeAmount = 0.0
        )
    }

    val testItems = remember {
        listOf(
            OrderItemEntity(
                orderInvoiceNumber = "INV-TEST-0001",
                productId = 1,
                productName = "Spanish Latte",
                sku = "CF-001",
                basePrice = 35000.0,
                variantSummary = "Large, Iced, Less Sugar",
                unitPrice = 40000.0,
                quantity = 1,
                lineTotal = 40000.0
            ),
            OrderItemEntity(
                orderInvoiceNumber = "INV-TEST-0001",
                productId = 10,
                productName = "Butter Croissant",
                sku = "PS-001",
                basePrice = 28000.0,
                variantSummary = "Warmed Up",
                unitPrice = 28000.0,
                quantity = 1,
                lineTotal = 28000.0
            )
        )
    }

    val sampleBytes = remember {
        EscPosPrinterService.buildReceiptBytes(testOrder, testItems, context = context, showLogo = true)
    }

    val hexDump = remember(sampleBytes) {
        EscPosPrinterService.bytesToHexDump(sampleBytes)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScaffoldBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Thermal Printer",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextDarkPrimary
                    )
                    Text(
                        text = "Koneksi Bluetooth ESC/POS 58mm",
                        fontSize = 12.sp,
                        color = TextDarkSecondary
                    )
                }

                IconButton(
                    onClick = requestScan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBg)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = BrandBlue,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Pindai Bluetooth",
                            tint = BrandBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Connection Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(BrandBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothConnected,
                                contentDescription = null,
                                tint = BrandBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Status Bluetooth Printer",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkPrimary
                            )
                            Text(
                                text = if (isScanning) "Sedang mencari perangkat Bluetooth terdekat..." else printerStatus,
                                fontSize = 12.sp,
                                color = if (isScanning) BrandBlue else if (printerStatus.contains("error", ignoreCase = true) || printerStatus.contains("mati", ignoreCase = true) || printerStatus.contains("terputus", ignoreCase = true)) Color(0xFFE11D48) else Color(0xFF16A34A)
                            )
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = BorderSubtle, modifier = Modifier.padding(vertical = 14.dp))

                    Text(
                        text = "PERANGKAT BLUETOOTH TERSEDIA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BrandBlue,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (pairedPrinters.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceSubtle)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Tidak ada printer Bluetooth ditemukan",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDarkPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Pastikan printer Bluetooth menyala, berada dalam jangkauan, dan Bluetooth perangkat aktif.",
                                    fontSize = 11.sp,
                                    color = TextDarkSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = requestScan,
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlueLight),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pindai Perangkat", fontWeight = FontWeight.Bold, color = BrandBlue, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        pairedPrinters.forEach { device ->
                            val isSelected = device.address.equals(selectedPrinterAddress, ignoreCase = true)
                            val (badgeBg, badgeText) = when (device.statusBadge) {
                                "Connected" -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D)) // Green
                                "Ready" -> Pair(Color(0xFFDBEAFE), Color(0xFF1D4ED8)) // Blue
                                else -> Pair(Color(0xFFFEE2E2), Color(0xFFDC2626)) // Red / Disconnected
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, if (isSelected) BrandBlue else BorderSubtle, RoundedCornerShape(14.dp))
                                    .background(if (isSelected) BrandBlueLight else SurfaceSubtle)
                                    .clickable { viewModel.selectPrinter(device.address, device.name) }
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) BrandBlue else TextDarkPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = device.address,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextDarkMuted
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(badgeBg)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = device.statusBadge,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeText
                                        )
                                    }

                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(BrandBlue),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = "Terpilih", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Test Print CTA (BrandBlue Button)
            Button(
                onClick = {
                    viewModel.attemptPrintReceipt(context, testOrder, testItems)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("test_print_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isPrinting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Streaming ESC/POS...", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.Print, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Cetak Thermal (${sampleBytes.size} bytes)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showHexInspector = !showHexInspector },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Icon(imageVector = Icons.Default.Code, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (showHexInspector) "Tutup Inspector Byte" else "Lihat Format Byte ESC/POS",
                    color = TextDarkPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            AnimatedVisibility(visible = showHexInspector) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ESC/POS BINARY BUFFER (${sampleBytes.size} BYTES)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandBlue,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = hexDump,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextDarkPrimary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "PRATINJAU STRUK 58MM",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDarkSecondary,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.06f))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFFAFAFA))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.iconpos),
                        contentDescription = "Logo Outlet",
                        modifier = Modifier
                            .size(52.dp)
                            .padding(bottom = 6.dp)
                    )
                    Text(
                        text = "CAFFEINERY SPECIALTY",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Jl. Senopati No. 42, Jakarta",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF64748B)
                    )
                    ReceiptDashedLine()
                    ReceiptRowMonospace("Invoice:", testOrder.invoiceNumber)
                    ReceiptRowMonospace("Waktu:", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()))
                    ReceiptDashedLine()
                    testItems.forEach { itm ->
                        ReceiptRowMonospace("${itm.productName} x${itm.quantity}", CurrencyUtils.formatIdr(itm.lineTotal))
                    }
                    ReceiptDashedLine()
                    ReceiptRowMonospace("Subtotal", CurrencyUtils.formatIdr(testOrder.subtotal))
                    ReceiptRowMonospace("PB1 Pajak (10%)", CurrencyUtils.formatIdr(testOrder.taxAmount))
                    ReceiptDashedLine()
                    ReceiptRowMonospace("TOTAL BAYAR", CurrencyUtils.formatIdr(testOrder.grandTotal))
                    ReceiptRowMonospace("Metode", testOrder.paymentMethod)
                    ReceiptDashedLine()
                    Text(
                        text = "Terima kasih atas kunjungan Anda!",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // Bluetooth Disconnected Alert Dialog Popup
        if (showPrinterDisconnectedAlert) {
            val errorDetail = printerErrorMessage ?: "Gagal mencetak: Perangkat printer Bluetooth terputus atau mati"

            AlertDialog(
                onDismissRequest = { viewModel.dismissPrinterDisconnectedAlert() },
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.testTag("printer_error_text")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Pastikan printer Bluetooth dalam kondisi menyala, berada dalam jangkauan, dan Bluetooth perangkat sudah aktif sebelum mencoba kembali.",
                            fontSize = 12.sp,
                            color = TextDarkSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.dismissPrinterDisconnectedAlert()
                            viewModel.attemptPrintReceipt(context, testOrder, testItems)
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cetak Ulang (Coba Lagi)", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.dismissPrinterDisconnectedAlert()
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Pengaturan Bluetooth", fontWeight = FontWeight.SemiBold, color = BrandBlue, fontSize = 12.sp)
                        }

                        TextButton(
                            onClick = { viewModel.dismissPrinterDisconnectedAlert() },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Tutup", fontWeight = FontWeight.SemiBold, color = TextDarkSecondary)
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = CardBg
            )
        }

        // Bluetooth Disabled Alert Dialog Popup
        if (showBluetoothDisabledAlert) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBluetoothDisabledAlert() },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothDisabled,
                            contentDescription = "Bluetooth Mati",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Bluetooth Tidak Aktif",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextDarkPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Bluetooth pada perangkat Anda belum diaktifkan.",
                            fontSize = 14.sp,
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Silakan aktifkan Bluetooth pada ponsel untuk mencari, menghubungkan, dan mencetak struk kasir ke printer thermal Bluetooth ESC/POS.",
                            fontSize = 12.sp,
                            color = TextDarkSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.dismissBluetoothDisabledAlert()
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Buka Pengaturan Bluetooth", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissBluetoothDisabledAlert() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text("Tutup", fontWeight = FontWeight.SemiBold, color = TextDarkSecondary)
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = CardBg
            )
        }
    }
}
