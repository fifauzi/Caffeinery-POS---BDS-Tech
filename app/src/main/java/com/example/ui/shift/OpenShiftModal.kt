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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.EmployeeItem
import com.example.ui.components.CurrencyUtils
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandBlueLight
import com.example.ui.theme.CardBg
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.TextDarkMuted
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary

/**
 * Open Shift (Buka Kasir) Modal:
 * Prompt cashier to open shift before taking orders by inputting starting cash drawer
 * amount (Petty Cash / Kas Kecil modal kembalian).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenShiftModal(
    currentUser: EmployeeItem,
    onDismiss: () -> Unit,
    onConfirmOpenShift: (startingCash: Double, shiftName: String) -> Unit,
    canDismiss: Boolean = true
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedShift by remember { mutableStateOf("Shift 1") }
    var pettyCashInput by remember { mutableStateOf("500000") }
    var inputError by remember { mutableStateOf<String?>(null) }

    val shiftOptions = listOf("Shift 1", "Shift 2", "Event")
    val quickPresets = listOf(200000.0, 300000.0, 500000.0, 1000000.0)

    ModalBottomSheet(
        onDismissRequest = {
            if (canDismiss) onDismiss()
        },
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
                            .background(Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Buka Kasir (Open Shift)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextDarkPrimary
                        )
                        Text(
                            text = "Masukkan modal awal kas kecil untuk uang kembalian",
                            fontSize = 12.sp,
                            color = TextDarkSecondary
                        )
                    }
                }

                if (canDismiss) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextDarkSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Staff Info Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceSubtle)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = BrandBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = currentUser.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkPrimary
                            )
                            Text(
                                text = "Peran: ${currentUser.role}",
                                fontSize = 12.sp,
                                color = TextDarkSecondary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandBlueLight)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Kasir Aktif",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Shift Selection
            Text(
                text = "PILIH SESI SHIFT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                shiftOptions.forEach { sName ->
                    val isSelected = selectedShift == sName
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) BrandBlueLight else SurfaceSubtle)
                            .border(1.5.dp, if (isSelected) BrandBlue else BorderSubtle, RoundedCornerShape(12.dp))
                            .clickable { selectedShift = sName }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (isSelected) BrandBlue else TextDarkMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = sName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) BrandBlue else TextDarkPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Starting Cash / Petty Cash input
            Text(
                text = "MODAL AWAL KAS KECIL (PETTY CASH)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = pettyCashInput,
                onValueChange = {
                    val clean = it.filter { ch -> ch.isDigit() }
                    pettyCashInput = clean
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
                        val numeric = pettyCashInput.toDoubleOrNull() ?: 0.0
                        Text("Terbaca: ${CurrencyUtils.formatRupiah(numeric)}", color = TextDarkSecondary, fontSize = 11.sp)
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandBlue,
                    unfocusedBorderColor = BorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("starting_cash_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Preset Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPresets.forEach { preset ->
                    val isMatch = (pettyCashInput.toDoubleOrNull() ?: -1.0) == preset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isMatch) BrandBlue else SurfaceSubtle)
                            .clickable {
                                pettyCashInput = preset.toLong().toString()
                                inputError = null
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = CurrencyUtils.formatRupiah(preset).replace(",00", ""),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMatch) Color.White else TextDarkPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    val amount = pettyCashInput.toDoubleOrNull()
                    if (amount == null || amount < 0) {
                        inputError = "Masukkan nominal kas kecil yang valid (contoh: 500000)"
                        return@Button
                    }
                    onConfirmOpenShift(amount, selectedShift)
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_open_shift_btn")
            ) {
                Icon(imageVector = Icons.Default.Payments, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Buka Kasir Sekarang",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
