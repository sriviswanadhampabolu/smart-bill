package com.grocer.billing.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.grocer.billing.core.data.repository.AuthRepository
import com.grocer.billing.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PinUnlockScreen(
    authRepository: AuthRepository,
    onUnlocked: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    val activeShop by authRepository.observeActiveShop().collectAsState(initial = null)

    fun onNumberClick(digit: String) {
        if (enteredPin.length < 4) {
            val newPin = enteredPin + digit
            enteredPin = newPin
            errorMessage = null

            if (newPin.length == 4) {
                coroutineScope.launch {
                    val isValid = authRepository.verifyPin(newPin)
                    if (isValid) {
                        onUnlocked()
                    } else {
                        errorMessage = "Incorrect PIN. Try again."
                        enteredPin = ""
                    }
                }
            }
        }
    }

    fun onBackspaceClick() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    LiquidGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section: Shop info & Frosted Glass Lock Lens
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 36.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f))
                        .border(
                            BorderStroke(1.5.dp, GlassBorderHighlight),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = GreenPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = activeShop?.name ?: "Kirana Billing",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "Enter 4-digit PIN to open counter",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 4-Dot Liquid Glass PIN Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val isFilled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) {
                                        androidx.compose.ui.graphics.Brush.radialGradient(
                                            listOf(Color(0xFF2E7D32), GreenPrimary)
                                        )
                                    } else {
                                        androidx.compose.ui.graphics.Brush.radialGradient(
                                            listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.2f))
                                        )
                                    }
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (isFilled) GreenPrimary else GlassBorderHighlight,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = AlertRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom section: Liquid Glass Counter Numpad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("CLEAR", "0", "BACK")
                )

                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (key in row) {
                            val isAction = key in listOf("CLEAR", "BACK")
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isAction) {
                                            Color.White.copy(alpha = 0.55f)
                                        } else {
                                            Color.White.copy(alpha = 0.82f)
                                        }
                                    )
                                    .border(
                                        BorderStroke(
                                            1.2.dp,
                                            if (isAction) GlassBorderSubtle else GlassBorderHighlight
                                        ),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable {
                                        when (key) {
                                            "CLEAR" -> enteredPin = ""
                                            "BACK" -> onBackspaceClick()
                                            else -> onNumberClick(key)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                when (key) {
                                    "CLEAR" -> Text("CLR", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextSecondary)
                                    "BACK" -> Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Back", tint = TextSecondary)
                                    else -> Text(key, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                                }
                            }
                        }
                    }
                }

                // Change PIN option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { showChangePinDialog = true }) {
                        Text(
                            text = "Change PIN / Reset",
                            color = GreenPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            activeShop = activeShop,
            authRepository = authRepository,
            onDismiss = { showChangePinDialog = false },
            onPinChanged = {
                showChangePinDialog = false
                enteredPin = ""
                errorMessage = "PIN updated successfully! Enter new PIN."
            }
        )
    }
}

@Composable
fun ChangePinDialog(
    activeShop: com.grocer.billing.core.data.local.entities.ShopEntity?,
    authRepository: AuthRepository,
    onDismiss: () -> Unit,
    onPinChanged: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var currentPinOrPhone by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Counter PIN", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Verify your current PIN or registered phone (${activeShop?.phone ?: "registered phone"}) to set a new 4-digit PIN.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = currentPinOrPhone,
                    onValueChange = { if (it.length <= 10) currentPinOrPhone = it },
                    label = { Text("Current 4-digit PIN or Phone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("New 4-Digit PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("Confirm New PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                if (dialogError != null) {
                    Text(
                        text = dialogError!!,
                        color = AlertRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPin.length != 4) {
                        dialogError = "New PIN must be exactly 4 digits"
                        return@Button
                    }
                    if (newPin != confirmPin) {
                        dialogError = "New PIN and Confirm PIN do not match"
                        return@Button
                    }

                    coroutineScope.launch {
                        isSubmitting = true
                        dialogError = null
                        val isPinValid = authRepository.verifyPin(currentPinOrPhone)
                        val isPhoneValid = (activeShop?.phone != null && currentPinOrPhone.trim() == activeShop.phone.trim())
                        
                        if (!isPinValid && !isPhoneValid) {
                            dialogError = "Current PIN or mobile number incorrect"
                            isSubmitting = false
                            return@launch
                        }

                        val success = authRepository.updateActiveShopPin(newPin)
                        isSubmitting = false
                        if (success) {
                            onPinChanged()
                        } else {
                            dialogError = "Failed to update PIN. Try again."
                        }
                    }
                },
                enabled = !isSubmitting && newPin.length == 4 && confirmPin.length == 4,
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Text(if (isSubmitting) "Updating..." else "Save New PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

