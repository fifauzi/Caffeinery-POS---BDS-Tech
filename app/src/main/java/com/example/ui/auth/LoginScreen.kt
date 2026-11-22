package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.EmployeeItem
import com.example.ui.PosViewModel
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CardBg
import com.example.ui.theme.ScaffoldBg
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.TextDarkMuted
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary

/**
 * Modern POS Staff Login & Multi-Role Authentication Screen:
 * - Brand identity with official Caffeinery POS logo asset
 * - Clean, uniform Staff selection cards with role badges (no nested boxes or gray background collision)
 * - Obfuscated & secure masked PIN input field (••••)
 * - Native numeric device keyboard activation (custom on-screen numpad completely removed)
 * - Role-based authorization feedback and instant login validation
 */
@Composable
fun LoginScreen(
    viewModel: PosViewModel,
    modifier: Modifier = Modifier
) {
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val outletInfo by viewModel.outletInfo.collectAsStateWithLifecycle()

    var selectedEmployee by remember(employees) {
        mutableStateOf<EmployeeItem?>(employees.firstOrNull())
    }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Automatically focus PIN field and summon native keyboard when staff is chosen
    LaunchedEffect(selectedEmployee?.id) {
        if (selectedEmployee != null) {
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScaffoldBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Branding Header with Official Caffeinery POS Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .shadow(8.dp, shape = RoundedCornerShape(22.dp), spotColor = Color(0x250052CC))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.iconpos),
                    contentDescription = "Caffeinery POS Logo",
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Caffeinery POS",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDarkPrimary,
                letterSpacing = (-0.5).sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Store,
                    contentDescription = null,
                    tint = BrandBlue,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${outletInfo.name} • ${outletInfo.branch}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDarkSecondary
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Section 1: Staff Account Selection
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "PILIH AKUN PEGAWAI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDarkMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                // List of Staff Cards with Uniform Clean Backgrounds
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    employees.forEach { emp ->
                        val isSelected = selectedEmployee?.id == emp.id
                        val roleBg = when (emp.role.lowercase()) {
                            "owner" -> Color(0xFFEDE9FE) // Soft Violet
                            "manager" -> Color(0xFFE0F2FE) // Soft Sky
                            "kasir" -> Color(0xFFDCFCE7) // Soft Green
                            else -> Color(0xFFFEF3C7) // Soft Amber for Barista
                        }
                        val roleTint = when (emp.role.lowercase()) {
                            "owner" -> Color(0xFF6D28D9)
                            "manager" -> Color(0xFF0284C7)
                            "kasir" -> Color(0xFF16A34A)
                            else -> Color(0xFFD97706)
                        }

                        // Clean single uniform background, no inner rectangle or elevation gray box collision
                        val cardBgColor = if (isSelected) Color(0xFFF0F5FF) else CardBg
                        val cardBorderColor = if (isSelected) BrandBlue else BorderSubtle

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBgColor)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = cardBorderColor,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    selectedEmployee = emp
                                    enteredPin = ""
                                    pinError = null
                                    try {
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    } catch (_: Exception) {}
                                }
                                .padding(horizontal = 16.dp, vertical = 13.dp)
                                .testTag("staff_card_${emp.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) BrandBlue else SurfaceSubtle),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = emp.name.take(1).uppercase(),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else TextDarkPrimary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Text(
                                            text = emp.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextDarkPrimary
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(roleBg)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = emp.role,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = roleTint
                                            )
                                        }
                                    }
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(BrandBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Masked PIN Input (Native Keyboard)
            if (selectedEmployee != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardBg)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Masukkan PIN Otorisasi",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkPrimary
                    )
                    Text(
                        text = "Login sebagai ${selectedEmployee?.name} (${selectedEmployee?.role})",
                        fontSize = 12.sp,
                        color = TextDarkSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Masked PIN display field triggering Native Device Numeric Keyboard
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 6) {
                                enteredPin = input
                                pinError = null
                                // Auto-validate if 4 digits are typed
                                if (input.length == 4) {
                                    val emp = selectedEmployee
                                    if (emp != null && emp.pin.isNotBlank() && emp.pin == input) {
                                        keyboardController?.hide()
                                        viewModel.loginStaff(emp, input)
                                    }
                                }
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                val emp = selectedEmployee
                                if (emp == null) {
                                    pinError = "Silakan pilih akun pegawai terlebih dahulu"
                                    return@KeyboardActions
                                }
                                val success = viewModel.loginStaff(emp, enteredPin)
                                if (!success) {
                                    pinError = "PIN otorisasi tidak sesuai. Silakan coba lagi."
                                }
                            }
                        ),
                        placeholder = {
                            Text(
                                text = "••••",
                                fontSize = 18.sp,
                                color = TextDarkMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = BrandBlue)
                        },
                        singleLine = true,
                        isError = pinError != null,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandBlue,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            errorContainerColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("login_pin_input")
                    )

                    // Error text
                    AnimatedVisibility(
                        visible = pinError != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = pinError ?: "",
                            color = Color(0xFFDC2626),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Login Action Button
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            val emp = selectedEmployee
                            if (emp == null) {
                                pinError = "Silakan pilih akun pegawai terlebih dahulu"
                                return@Button
                            }
                            val success = viewModel.loginStaff(emp, enteredPin)
                            if (!success) {
                                pinError = "PIN otorisasi tidak sesuai. Silakan coba lagi."
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_submit_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Masuk ke Sistem Kasir",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Caffeinery POS by BDS Tech • Role-Based Security",
                fontSize = 11.sp,
                color = TextDarkMuted
            )
        }
    }
}
