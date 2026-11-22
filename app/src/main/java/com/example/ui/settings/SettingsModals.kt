package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.model.OrderEntity
import com.example.data.local.model.OrderItemEntity
import com.example.domain.model.AdditionalFeesConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.domain.model.EmployeeItem
import com.example.domain.model.OrderMethodsConfig
import com.example.domain.model.OutletInfo
import com.example.domain.model.PaymentGatewaysConfig
import com.example.domain.model.ReceiptCustomizerConfig
import com.example.service.EscPosPrinterService
import com.example.ui.PosViewModel
import com.example.ui.components.CurrencyUtils
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandBlueLight
import com.example.ui.theme.CardBg
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.TextDarkMuted
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutletSettingsSheet(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val outletInfo by viewModel.outletInfo.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf(outletInfo.name) }
    var branch by remember { mutableStateOf(outletInfo.branch) }
    var address by remember { mutableStateOf(outletInfo.address) }
    var phone by remember { mutableStateOf(outletInfo.phone) }
    var taxId by remember { mutableStateOf(outletInfo.taxId) }
    var instagram by remember { mutableStateOf(outletInfo.instagram) }
    var ownerWa by remember { mutableStateOf(outletInfo.ownerWhatsAppNumber) }

    BaseSettingsSheet(title = "Informasi Outlet", subtitle = "Pengaturan profil usaha dan struk fisik", onDismiss = onDismiss) {
        SettingInputField(label = "Nama Usaha / Toko", value = name, onValueChange = { name = it })
        SettingInputField(label = "Nama Cabang", value = branch, onValueChange = { branch = it })
        SettingInputField(label = "Alamat Lengkap", value = address, onValueChange = { address = it })
        SettingInputField(label = "Nomor Telepon", value = phone, onValueChange = { phone = it }, keyboardType = KeyboardType.Phone)
        SettingInputField(label = "Nomor WhatsApp Owner / Manager (Laporan Shift)", value = ownerWa, onValueChange = { ownerWa = it }, keyboardType = KeyboardType.Phone)
        SettingInputField(label = "NPWP / Pajak ID", value = taxId, onValueChange = { taxId = it })
        SettingInputField(label = "Instagram / Media Sosial", value = instagram, onValueChange = { instagram = it })

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                viewModel.updateOutletInfo(
                    OutletInfo(
                        name = name.trim(),
                        branch = branch.trim(),
                        address = address.trim(),
                        phone = phone.trim(),
                        taxId = taxId.trim(),
                        instagram = instagram.trim(),
                        ownerWhatsAppNumber = ownerWa.trim()
                    )
                )
                Toast.makeText(context, "Informasi Outlet Berhasil Disimpan", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
        ) {
            Text("Simpan Perubahan Outlet", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmployeeSettingsSheet(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val employees by viewModel.employees.collectAsStateWithLifecycle()

    var isFormOpen by remember { mutableStateOf(false) }
    var editingEmployee by remember { mutableStateOf<EmployeeItem?>(null) }
    var empName by remember { mutableStateOf("") }
    var empRole by remember { mutableStateOf("Kasir") }
    var empPin by remember { mutableStateOf("") }

    BaseSettingsSheet(title = "Kelola Pegawai", subtitle = "Daftar akun Owner & staf (Kasir / Barista)", onDismiss = onDismiss) {
        if (!isFormOpen) {
            Button(
                onClick = {
                    editingEmployee = null
                    empName = ""
                    empRole = "Kasir"
                    empPin = ""
                    isFormOpen = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_employee_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlueLight)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("+ Tambah Pegawai Baru", fontWeight = FontWeight.Bold, color = BrandBlue)
            }

            Spacer(modifier = Modifier.height(14.dp))

            employees.forEach { emp ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceSubtle),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = emp.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                            Text(text = "Peran: ${emp.role} • PIN: ****", fontSize = 12.sp, color = TextDarkSecondary)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    editingEmployee = emp
                                    empName = emp.name
                                    empRole = emp.role
                                    empPin = emp.pin
                                    isFormOpen = true
                                },
                                modifier = Modifier.testTag("edit_employee_${emp.id}")
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Pegawai", tint = BrandBlue)
                            }

                            IconButton(
                                onClick = {
                                    val removed = viewModel.removeEmployee(emp.id)
                                    if (!removed) {
                                        Toast.makeText(context, "Minimal harus ada 1 akun Owner aktif", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.testTag("delete_employee_${emp.id}")
                            ) {
                                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                text = if (editingEmployee != null) "Edit Data Pegawai" else "Tambah Pegawai Baru",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            SettingInputField(label = "Nama Lengkap Pegawai", value = empName, onValueChange = { empName = it })

            Text("Peran Jabatan", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Owner", "Kasir", "Barista", "Supervisor", "Manager").forEach { role ->
                    val isSel = empRole.equals(role, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) BrandBlue else SurfaceSubtle)
                            .clickable { empRole = role }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(text = role, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else TextDarkSecondary)
                    }
                }
            }

            SettingInputField(label = "PIN Akses (4 Digit)", value = empPin, onValueChange = { if (it.length <= 4) empPin = it.filter { c -> c.isDigit() } }, keyboardType = KeyboardType.NumberPassword)

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        editingEmployee = null
                        isFormOpen = false
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceSubtle),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Batal", color = TextDarkSecondary, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (empName.isNotBlank()) {
                            val target = editingEmployee
                            val pinClean = empPin.ifBlank { target?.pin ?: "1234" }
                            if (target != null) {
                                viewModel.updateEmployee(
                                    target.copy(
                                        name = empName.trim(),
                                        role = empRole,
                                        pin = pinClean
                                    )
                                )
                                Toast.makeText(context, "Data ${empName.trim()} Berhasil Diperbarui", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.addEmployee(empName.trim(), empRole, pinClean)
                                Toast.makeText(context, "Pegawai ${empName.trim()} Berhasil Ditambahkan", Toast.LENGTH_SHORT).show()
                            }
                            editingEmployee = null
                            empName = ""
                            empPin = ""
                            isFormOpen = false
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("save_employee_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Simpan", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderMethodsSettingsSheet(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentConfig by viewModel.orderMethods.collectAsStateWithLifecycle()

    var isDineIn by remember { mutableStateOf(currentConfig.isDineInEnabled) }
    var isTakeAway by remember { mutableStateOf(currentConfig.isTakeAwayEnabled) }
    var isOnline by remember { mutableStateOf(currentConfig.isOnlineDeliveryEnabled) }
    var isTableReq by remember { mutableStateOf(currentConfig.isTableNumberRequired) }

    BaseSettingsSheet(title = "Metode Pemesanan", subtitle = "Aktifkan saluran pemesanan di POS", onDismiss = onDismiss) {
        SettingSwitchRow(title = "Makan di Tempat (Dine-in)", subtitle = "Pesanan konsumsi langsung di meja outlet", checked = isDineIn, onCheckedChange = { isDineIn = it })
        SettingSwitchRow(title = "Wajib Input Nomor Meja", subtitle = "Meminta nomor meja saat order Dine-in", checked = isTableReq, onCheckedChange = { isTableReq = it })
        SettingSwitchRow(title = "Bawa Pulang (Take Away)", subtitle = "Pesanan dikemas untuk dibawa pulang", checked = isTakeAway, onCheckedChange = { isTakeAway = it })
        SettingSwitchRow(title = "Online Delivery (GoFood / GrabFood)", subtitle = "Saluran integrasi pesanan kurir online", checked = isOnline, onCheckedChange = { isOnline = it })

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                viewModel.updateOrderMethods(
                    OrderMethodsConfig(
                        isDineInEnabled = isDineIn,
                        isTakeAwayEnabled = isTakeAway,
                        isOnlineDeliveryEnabled = isOnline,
                        isTableNumberRequired = isTableReq
                    )
                )
                Toast.makeText(context, "Metode Pemesanan Diperbarui", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
        ) {
            Text("Simpan Pengaturan Pemesanan", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentGatewaysSettingsSheet(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val config by viewModel.paymentGateways.collectAsStateWithLifecycle()

    var isCash by remember { mutableStateOf(config.isCashEnabled) }
    var isQris by remember { mutableStateOf(config.isQrisEnabled) }
    var qrisMerchant by remember { mutableStateOf(config.qrisMerchantName) }
    var qrisNmid by remember { mutableStateOf(config.qrisNmid) }
    var qrisTerminal by remember { mutableStateOf(config.qrisTerminal) }
    var isTransfer by remember { mutableStateOf(config.isBankTransferEnabled) }
    var bcaAcc by remember { mutableStateOf(config.bcaAccount) }
    var mandiriAcc by remember { mutableStateOf(config.mandiriAccount) }

    BaseSettingsSheet(title = "Metode Pembayaran", subtitle = "Atur channel pembayaran kasir & QRIS", onDismiss = onDismiss) {
        SettingSwitchRow(title = "Pembayaran Tunai (Cash)", subtitle = "Perhitungan uang diterima dan kembalian instan", checked = isCash, onCheckedChange = { isCash = it })
        
        SettingSwitchRow(title = "Dynamic QRIS (ASPI / BI)", subtitle = "Generate QR otomatis sesuai nominal nota", checked = isQris, onCheckedChange = { isQris = it })
        if (isQris) {
            SettingInputField(label = "Nama Merchant QRIS", value = qrisMerchant, onValueChange = { qrisMerchant = it })
            SettingInputField(label = "NMID Merchant", value = qrisNmid, onValueChange = { qrisNmid = it })
            SettingInputField(label = "Terminal ID / Kasir", value = qrisTerminal, onValueChange = { qrisTerminal = it })
        }

        SettingSwitchRow(title = "Transfer Bank", subtitle = "Konfirmasi transfer manual rekening bank", checked = isTransfer, onCheckedChange = { isTransfer = it })
        if (isTransfer) {
            SettingInputField(label = "Rekening Bank BCA", value = bcaAcc, onValueChange = { bcaAcc = it })
            SettingInputField(label = "Rekening Bank Mandiri", value = mandiriAcc, onValueChange = { mandiriAcc = it })
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                viewModel.updatePaymentGateways(
                    PaymentGatewaysConfig(
                        isCashEnabled = isCash,
                        isQrisEnabled = isQris,
                        qrisMerchantName = qrisMerchant.trim(),
                        qrisNmid = qrisNmid.trim(),
                        qrisTerminal = qrisTerminal.trim(),
                        isBankTransferEnabled = isTransfer,
                        bcaAccount = bcaAcc.trim(),
                        mandiriAccount = mandiriAcc.trim()
                    )
                )
                Toast.makeText(context, "Metode Pembayaran Disimpan", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
        ) {
            Text("Simpan Metode Pembayaran", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdditionalFeesSettingsSheet(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val config by viewModel.additionalFees.collectAsStateWithLifecycle()

    var isTax by remember { mutableStateOf(config.isTaxEnabled) }
    var taxPctText by remember { mutableStateOf(CurrencyUtils.formatDecimal(config.taxPercentage)) }
    var isService by remember { mutableStateOf(config.isServiceFeeEnabled) }
    var servicePctText by remember { mutableStateOf(CurrencyUtils.formatDecimal(config.serviceFeePercentage)) }
    var isTakeawayPackaging by remember { mutableStateOf(config.isTakeawayPackagingEnabled) }
    var takeawayPackagingFeeText by remember { mutableStateOf(config.takeawayPackagingFee.toLong().toString()) }
    var isTakeawayPerItem by remember { mutableStateOf(config.isTakeawayFeePerItem) }
    var isRounding by remember { mutableStateOf(config.isRoundingEnabled) }

    BaseSettingsSheet(title = "Biaya Tambahan & Pajak", subtitle = "Atur PB1, biaya layanan Dine-in & kemasan Takeaway", onDismiss = onDismiss) {
        SettingSwitchRow(title = "Pajak Restoran (PB1 / PPN)", subtitle = "Dihitung otomatis saat kalkulasi kasir", checked = isTax, onCheckedChange = { isTax = it })
        if (isTax) {
            SettingInputField(
                label = "Tarif Pajak (%)",
                value = taxPctText,
                onValueChange = { taxPctText = CurrencyUtils.cleanDecimalInput(it) },
                keyboardType = KeyboardType.Decimal
            )
        }

        SettingSwitchRow(title = "Biaya Layanan Dine-In (Service Charge)", subtitle = "Otomatis 2.5% khusus untuk pesanan Dine In", checked = isService, onCheckedChange = { isService = it })
        if (isService) {
            SettingInputField(
                label = "Tarif Layanan Dine-In (%)",
                value = servicePctText,
                onValueChange = { servicePctText = CurrencyUtils.cleanDecimalInput(it) },
                keyboardType = KeyboardType.Decimal
            )
        }

        SettingSwitchRow(title = "Biaya Kemasan & Cup (Takeaway)", subtitle = "Hanya berlaku untuk Minuman & Pastry (Beans, Alat Seduh & Merchandise gratis)", checked = isTakeawayPackaging, onCheckedChange = { isTakeawayPackaging = it })
        if (isTakeawayPackaging) {
            SettingInputField(
                label = "Nominal Biaya Kemasan (Rp)",
                value = takeawayPackagingFeeText,
                onValueChange = { takeawayPackagingFeeText = it.filter { ch -> ch.isDigit() } },
                keyboardType = KeyboardType.Number
            )
            SettingSwitchRow(
                title = "Hitung Per Item / Cup (Minuman & Pastry)",
                subtitle = if (isTakeawayPerItem) {
                    "Aktif: Dikalikan jumlah item minuman & pastry di keranjang"
                } else {
                    "Nonaktif: Tarif flat per struk jika terdapat minuman/pastry"
                },
                checked = isTakeawayPerItem,
                onCheckedChange = { isTakeawayPerItem = it }
            )
        }

        SettingSwitchRow(title = "Pembulatan Otomatis", subtitle = "Bulatkan nominal pembayaran ke pecahan terdekat", checked = isRounding, onCheckedChange = { isRounding = it })

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                val taxPct = taxPctText.replace(',', '.').toDoubleOrNull() ?: 10.0
                val srvPct = servicePctText.replace(',', '.').toDoubleOrNull() ?: 2.5
                val pkgFee = (takeawayPackagingFeeText.toDoubleOrNull() ?: 2000.0).coerceAtLeast(0.0)
                viewModel.updateAdditionalFees(
                    AdditionalFeesConfig(
                        isTaxEnabled = isTax,
                        taxPercentage = taxPct,
                        isServiceFeeEnabled = isService,
                        serviceFeePercentage = srvPct,
                        isTakeawayPackagingEnabled = isTakeawayPackaging,
                        takeawayPackagingFee = pkgFee,
                        isTakeawayFeePerItem = isTakeawayPerItem,
                        isRoundingEnabled = isRounding
                    )
                )
                Toast.makeText(context, "Pajak & Biaya Berhasil Diterapkan", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
        ) {
            Text("Terapkan Biaya ke Kasir", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptAndPrinterSettingsSheet(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val receiptConfig by viewModel.receiptCustomizer.collectAsStateWithLifecycle()
    val pairedPrinters by viewModel.pairedPrinters.collectAsStateWithLifecycle()
    val selectedAddress by viewModel.selectedPrinterAddress.collectAsStateWithLifecycle()
    val printerStatus by viewModel.printerStatusMessage.collectAsStateWithLifecycle()
    val isPrinting by viewModel.isPrintingAnimation.collectAsStateWithLifecycle()
    val isScanning by viewModel.isBluetoothScanning.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.refreshBluetoothPrinters(context, triggerScan = true)
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

    var paperSize by remember { mutableStateOf(receiptConfig.paperSize) }
    var showLogo by remember { mutableStateOf(receiptConfig.showLogo) }
    var showTax by remember { mutableStateOf(receiptConfig.showTax) }
    var headerNote by remember { mutableStateOf(receiptConfig.headerNote) }
    var footerNote by remember { mutableStateOf(receiptConfig.footerNote) }

    LaunchedEffect(Unit) {
        if (EscPosPrinterService.hasBluetoothPermissions(context)) {
            viewModel.refreshBluetoothPrinters(context, triggerScan = false)
        }
    }

    BaseSettingsSheet(title = "Tampilan Struk & Printer", subtitle = "Kustomisasi cetak nota & Bluetooth thermal", onDismiss = onDismiss) {
        Text("Lebar Kertas Thermal", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("58mm", "80mm").forEach { size ->
                val isSel = paperSize == size
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) BrandBlue else SurfaceSubtle)
                        .clickable { paperSize = size }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = "Thermal $size", fontWeight = FontWeight.Bold, color = if (isSel) Color.White else TextDarkSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        SettingSwitchRow(title = "Tampilkan Logo di Struk", subtitle = "Cetak logo outlet di bagian bawah struk", checked = showLogo, onCheckedChange = { showLogo = it })
        SettingSwitchRow(title = "Tampilkan Rincian Pajak di Struk", subtitle = "Cetak baris rincian PB1 pada struk", checked = showTax, onCheckedChange = { showTax = it })
        SettingInputField(label = "Teks Header Struk", value = headerNote, onValueChange = { headerNote = it })
        SettingInputField(label = "Pesan Kaki (Footer Struk)", value = footerNote, onValueChange = { footerNote = it })

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = BorderSubtle)
        Spacer(modifier = Modifier.height(14.dp))

        // Hardware Bluetooth Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Bluetooth Thermal Printer", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                Text(
                    text = if (isScanning) "Memindai perangkat Bluetooth terdekat..." else printerStatus,
                    fontSize = 11.sp,
                    color = if (isScanning) BrandBlue else if (printerStatus.contains("error", ignoreCase = true) || printerStatus.contains("mati", ignoreCase = true) || printerStatus.contains("terputus", ignoreCase = true)) Color(0xFFEF4444) else Color(0xFF15803D)
                )
            }

            IconButton(
                onClick = requestScan,
                modifier = Modifier.size(40.dp)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(color = BrandBlue, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Pindai Perangkat", tint = BrandBlue)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (pairedPrinters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceSubtle)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Tidak ada printer Bluetooth ditemukan.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDarkPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pastikan Bluetooth aktif, printer menyala, dan dalam mode pairing / jangkauan.",
                        fontSize = 11.sp,
                        color = TextDarkSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = requestScan,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlueLight),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pindai Ulang Bluetooth", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
                    }
                }
            }
        } else {
            pairedPrinters.forEach { dev ->
                val isSel = dev.address.equals(selectedAddress, ignoreCase = true)
                val (badgeBg, badgeText) = when (dev.statusBadge) {
                    "Connected" -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D)) // Green
                    "Ready" -> Pair(Color(0xFFDBEAFE), Color(0xFF1D4ED8)) // Blue
                    else -> Pair(Color(0xFFFEE2E2), Color(0xFFDC2626)) // Red / Disconnected
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSel) BrandBlueLight else SurfaceSubtle)
                        .border(1.dp, if (isSel) BrandBlue else BorderSubtle, RoundedCornerShape(14.dp))
                        .clickable { viewModel.selectPrinter(dev.address, dev.name) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dev.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) BrandBlue else TextDarkPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dev.address,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextDarkMuted
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Dynamic Status Badge (Connected / Ready / Disconnected)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = dev.statusBadge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeText
                            )
                        }

                        if (isSel) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Terpilih",
                                tint = BrandBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Test Print CTA
        Button(
            onClick = {
                val dummyOrder = OrderEntity(
                    invoiceNumber = "TEST-PRINT",
                    timestamp = System.currentTimeMillis(),
                    customerName = "Pelanggan Uji Coba",
                    subtotal = 35000.0,
                    itemDiscountTotal = 0.0,
                    orderDiscountAmount = 0.0,
                    taxPercentage = 10.0,
                    taxAmount = 3500.0,
                    grandTotal = 38500.0,
                    paymentMethod = "TUNAI",
                    tenderedAmount = 50000.0,
                    changeAmount = 11500.0
                )
                val dummyItems = listOf(
                    OrderItemEntity(
                        orderInvoiceNumber = "TEST-PRINT",
                        productId = 1,
                        productName = "Spanish Latte (Large)",
                        sku = "CF-001",
                        basePrice = 35000.0,
                        variantSummary = "Less Sugar",
                        unitPrice = 35000.0,
                        quantity = 1,
                        lineTotal = 35000.0
                    )
                )
                viewModel.attemptPrintReceipt(context, dummyOrder, dummyItems)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlueLight),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isPrinting) {
                CircularProgressIndicator(color = BrandBlue, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mengirim data ESC/POS...", color = BrandBlue, fontWeight = FontWeight.Bold)
            } else {
                Icon(imageVector = Icons.Default.Print, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test Cetak Struk Fisik", color = BrandBlue, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = {
                viewModel.updateReceiptCustomizer(
                    ReceiptCustomizerConfig(
                        paperSize = paperSize,
                        showLogo = showLogo,
                        showTax = showTax,
                        headerNote = headerNote.trim(),
                        footerNote = footerNote.trim()
                    )
                )
                Toast.makeText(context, "Kustomisasi Struk Berhasil Disimpan", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
        ) {
            Text("Simpan Tampilan Struk", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseSettingsSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = CardBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(BorderSubtle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextDarkPrimary)
                    Text(text = subtitle, fontSize = 12.sp, color = TextDarkSecondary)
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextDarkSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            content()

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun SettingInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = if (keyboardType == KeyboardType.NumberPassword || keyboardType == KeyboardType.Password) PasswordVisualTransformation() else VisualTransformation.None
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandBlue,
                unfocusedBorderColor = BorderSubtle
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
            Text(text = subtitle, fontSize = 12.sp, color = TextDarkSecondary)
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandBlue
            )
        )
    }
}

@Composable
fun CategoryManagementSheet(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()

    var newCategoryName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("coffee") }
    var editingCategory by remember { mutableStateOf<com.example.data.local.model.CategoryEntity?>(null) }
    var categoryToDelete by remember { mutableStateOf<com.example.data.local.model.CategoryEntity?>(null) }

    val iconOptions = listOf(
        Pair("coffee", "Coffee"),
        Pair("beans", "Beans"),
        Pair("pastry", "Pastry"),
        Pair("raw", "Bahan"),
        Pair("other", "Lainnya")
    )

    BaseSettingsSheet(
        title = "Kategori Produk",
        subtitle = "Kelola daftar kategori katalog produk usahamu",
        onDismiss = onDismiss
    ) {
        // Form to add or edit category
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (editingCategory != null) "Edit Kategori" else "Tambah Kategori Baru",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDarkPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder = { Text("Nama Kategori (ex: Non-Coffee, Merchandise)", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandBlue,
                        unfocusedBorderColor = BorderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Pilih Ikon:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDarkSecondary)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    iconOptions.forEach { (iconKey, label) ->
                        val isSelected = selectedIcon == iconKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) BrandBlue else CardBg)
                                .border(1.dp, if (isSelected) BrandBlue else BorderSubtle, RoundedCornerShape(10.dp))
                                .clickable { selectedIcon = iconKey }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else TextDarkPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editingCategory != null) {
                        Button(
                            onClick = {
                                editingCategory = null
                                newCategoryName = ""
                                selectedIcon = "coffee"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64748B)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Batal", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            if (newCategoryName.isNotBlank()) {
                                if (editingCategory != null) {
                                    val updated = editingCategory!!.copy(
                                        name = newCategoryName.trim(),
                                        iconName = selectedIcon
                                    )
                                    viewModel.updateCategory(updated)
                                    editingCategory = null
                                    Toast.makeText(context, "Kategori diperbarui", Toast.LENGTH_SHORT).show()
                                } else {
                                    val slug = newCategoryName.trim().lowercase().replace("\\s+".toRegex(), "_")
                                    val newCat = com.example.data.local.model.CategoryEntity(
                                        id = "cat_${slug}_${System.currentTimeMillis() % 10000}",
                                        name = newCategoryName.trim(),
                                        iconName = selectedIcon,
                                        colorHex = "#0052CC",
                                        sortOrder = categories.size + 1
                                    )
                                    viewModel.addCategory(newCat)
                                    Toast.makeText(context, "Kategori berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                                }
                                newCategoryName = ""
                                selectedIcon = "coffee"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (editingCategory != null) "Simpan" else "Tambah Kategori",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Daftar Kategori Tersimpan (${categories.size})",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextDarkPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { cat ->
                val count = products.count { it.categoryId == cat.id }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BrandBlueLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = com.example.ui.catalog.getCategoryIcon(cat.iconName),
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = cat.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDarkPrimary
                                )
                                Text(
                                    text = "$count produk terdaftar",
                                    fontSize = 11.sp,
                                    color = TextDarkSecondary
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    editingCategory = cat
                                    newCategoryName = cat.name
                                    selectedIcon = cat.iconName
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = TextDarkSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = { categoryToDelete = cat },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Hapus",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    categoryToDelete?.let { cat ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Hapus Kategori?") },
            text = { Text("Kategori \"${cat.name}\" akan dihapus.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.deleteCategory(cat)
                        categoryToDelete = null
                        Toast.makeText(context, "Kategori dihapus", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Hapus", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { categoryToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}
