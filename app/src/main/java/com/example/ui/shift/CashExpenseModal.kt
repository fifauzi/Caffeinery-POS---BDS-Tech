package com.example.ui.shift

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.example.data.local.model.CashExpenseEntity
import com.example.data.local.model.ShiftEntity
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
 * Cash Expense (Pengeluaran Tunai Shift) Modal:
 * Allows cashiers during an active shift to record cash-out expenses from the drawer
 * (e.g., plastic bags, mineral water, delivery courier fees).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CashExpenseModal(
    shift: ShiftEntity,
    expenses: List<CashExpenseEntity>,
    onDismiss: () -> Unit,
    onAddExpense: (description: String, amount: Double) -> Unit,
    onDeleteExpense: (expenseId: Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var descriptionInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    val quickSuggestions = listOf("Plastik Kemasan", "Air Mineral", "Es Batu", "Ongkos Kurir")
    val totalExpenses = remember(expenses) { expenses.sumOf { it.amount } }

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
                            .background(Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoneyOff,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Pengeluaran Kas Shift",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextDarkPrimary
                        )
                        Text(
                            text = "${shift.shiftName} • ${shift.staffName}",
                            fontSize = 12.sp,
                            color = TextDarkSecondary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextDarkSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Total Cash Expenses Summary Pill
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
                    Column {
                        Text(
                            text = "Total Pengeluaran Kas Shift Ini",
                            fontSize = 12.sp,
                            color = TextDarkSecondary
                        )
                        Text(
                            text = "${expenses.size} catatan pengeluaran",
                            fontSize = 11.sp,
                            color = TextDarkMuted
                        )
                    }
                    Text(
                        text = CurrencyUtils.formatRupiah(totalExpenses),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFDC2626)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Suggestion Chips
            Text(
                text = "KATEGORI CEPAT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickSuggestions.forEach { label ->
                    val isSelected = descriptionInput.equals(label, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) BrandBlueLight else SurfaceSubtle)
                            .border(1.dp, if (isSelected) BrandBlue else BorderSubtle, RoundedCornerShape(10.dp))
                            .clickable {
                                descriptionInput = label
                                formError = null
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) BrandBlue else TextDarkPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "KETERANGAN PENGELUARAN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = descriptionInput,
                onValueChange = {
                    descriptionInput = it
                    formError = null
                },
                placeholder = { Text("Contoh: Beli kantong plastik & air mineral", fontSize = 13.sp, color = TextDarkMuted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandBlue,
                    unfocusedBorderColor = BorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cash_expense_desc_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "NOMINAL PENGELUARAN (DARI LACI KASIR)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = amountInput,
                onValueChange = {
                    amountInput = it.filter { ch -> ch.isDigit() }
                    formError = null
                },
                prefix = {
                    Text("Rp ", fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                supportingText = {
                    val amt = amountInput.toDoubleOrNull() ?: 0.0
                    Text("Terbaca: ${CurrencyUtils.formatRupiah(amt)}", fontSize = 11.sp, color = TextDarkSecondary)
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandBlue,
                    unfocusedBorderColor = BorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cash_expense_amount_input")
            )

            if (formError != null) {
                Text(
                    text = formError!!,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFDC2626),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    val amountVal = amountInput.toDoubleOrNull() ?: 0.0
                    if (descriptionInput.isBlank()) {
                        formError = "Masukkan keterangan pengeluaran"
                        return@Button
                    }
                    if (amountVal <= 0.0) {
                        formError = "Masukkan nominal pengeluaran lebih dari Rp 0"
                        return@Button
                    }
                    onAddExpense(descriptionInput.trim(), amountVal)
                    descriptionInput = ""
                    amountInput = ""
                    formError = null
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_cash_expense_btn")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simpan Pengeluaran Kas", fontWeight = FontWeight.Bold, color = Color.White)
            }

            if (expenses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(thickness = 1.dp, color = BorderSubtle)
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "DAFTAR PENGELUARAN SHIFT INI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDarkMuted,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                expenses.forEach { exp ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceSubtle)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("cash_expense_list_item_${exp.id}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exp.description,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkPrimary
                            )
                            Text(
                                text = "${DateTimeUtils.formatTimeWib(exp.timestamp)} WIB • ${exp.staffName}",
                                fontSize = 11.sp,
                                color = TextDarkSecondary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = CurrencyUtils.formatRupiah(exp.amount),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFDC2626)
                            )
                            IconButton(
                                onClick = { onDeleteExpense(exp.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Hapus Pengeluaran",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
