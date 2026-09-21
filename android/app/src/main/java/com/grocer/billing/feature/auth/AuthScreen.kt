package com.grocer.billing.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import com.grocer.billing.core.data.backup.BackupMetadata
import com.grocer.billing.core.data.backup.LocalBackupManager
import com.grocer.billing.core.data.repository.AuthRepository
import com.grocer.billing.ui.theme.*
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import com.grocer.billing.core.data.remote.RetrofitClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    localBackupManager: LocalBackupManager? = null,
    retrofitClient: RetrofitClient? = null,
    onAuthSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val lastPhone = remember { authRepository.getLastPhoneNumber() }
    val lastShop = remember { authRepository.getLastShopName() }
    val lastOwner = remember { authRepository.getLastOwnerName() }
    val hasPastAccount = remember { authRepository.hasRegisteredShop() || lastPhone.isNotBlank() }

    var isSignUp by remember { mutableStateOf(!hasPastAccount) }

    var shopName by remember { mutableStateOf(lastShop) }
    var ownerName by remember { mutableStateOf(lastOwner) }
    var phone by remember { mutableStateOf(lastPhone) }
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var availableBackup by remember { mutableStateOf<BackupMetadata?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    var showServerDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(retrofitClient?.getServerUrl() ?: "http://10.0.2.2:8000/") }
    var serverTestMessage by remember { mutableStateOf<String?>(null) }
    var isTestingServer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val existingShop = authRepository.getActiveShop()
        if (existingShop != null) {
            if (phone.isBlank()) phone = existingShop.phone
            if (shopName.isBlank()) shopName = existingShop.name
            if (ownerName.isBlank()) ownerName = existingShop.ownerName
            isSignUp = false
        }
        if (localBackupManager != null) {
            availableBackup = localBackupManager.getAvailableBackup()
        }
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Smart Bill",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "High-Speed Counter Sync",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            serverUrlInput = retrofitClient?.getServerUrl() ?: "http://10.0.2.2:8000/"
                            serverTestMessage = null
                            showServerDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = "Backend Server URL",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GreenPrimary.copy(alpha = 0.90f)
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Offline Backup Card (if found in phone's public storage)
                if (availableBackup != null) {
                    val backup = availableBackup!!
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        tint = Color(0xFFE8F5E9).copy(alpha = 0.90f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Backup, contentDescription = null, tint = GreenPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Offline Backup Found",
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Found saved data for \"${backup.shopName}\" (${backup.itemCount} items, ${backup.billCount} bills) from ${backup.backupDateFormatted}.",
                            fontSize = 13.sp,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LiquidGlassButton(
                            onClick = {
                                isRestoring = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val success = localBackupManager?.restoreBackup() == true
                                    isRestoring = false
                                    if (success) {
                                        onAuthSuccess()
                                    } else {
                                        errorMessage = "Failed to restore backup file"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isRestoring
                        ) {
                            if (isRestoring) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restoring Data...", color = Color.White)
                            } else {
                                Icon(Icons.Default.Restore, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("1-Tap Restore My Shop & Data", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = Color.White.copy(alpha = 0.85f),
                    elevation = 8.dp
                ) {
                    Text(
                        text = if (isSignUp) "Setup Your Shop" else "Welcome Back",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isSignUp) "Fast offline billing for your grocery store" else "Enter your phone & 4-digit PIN",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 18.dp)
                    )

                    // Mode Selector Liquid Glass Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(25.dp))
                            .background(Color(0xFFE8EDE9))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { isSignUp = true; errorMessage = null },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSignUp) GreenPrimary else Color.Transparent,
                                contentColor = if (isSignUp) Color.White else TextSecondary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isSignUp) 3.dp else 0.dp)
                        ) {
                            Text("New Shop", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { isSignUp = false; errorMessage = null },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isSignUp) GreenPrimary else Color.Transparent,
                                contentColor = if (!isSignUp) Color.White else TextSecondary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = if (!isSignUp) 3.dp else 0.dp)
                        ) {
                            Text("Login", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

            if (isSignUp) {
                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = { Text("Shop Name (e.g. Balaji Provisions)") },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = { Text("Owner Name") },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = phone,
                onValueChange = { if (it.length <= 10) phone = it },
                label = { Text("Mobile Number (10 digits)") },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 4) pin = it },
                label = { Text("4-Digit PIN") },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            LiquidGlassButton(
                    onClick = {
                        if (isSignUp) {
                            if (shopName.isBlank()) {
                                errorMessage = "Please enter your shop name."
                                return@LiquidGlassButton
                            }
                            if (ownerName.isBlank()) {
                                errorMessage = "Please enter the owner's name."
                                return@LiquidGlassButton
                            }
                            if (phone.length < 10) {
                                errorMessage = "Please enter a valid 10-digit phone number."
                                return@LiquidGlassButton
                            }
                            if (pin.length != 4) {
                                errorMessage = "Please choose a 4-digit PIN for quick counter unlock."
                                return@LiquidGlassButton
                            }

                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    authRepository.createLocalShop(
                                        shopName = shopName,
                                        ownerName = ownerName,
                                        phone = phone,
                                        pin = pin
                                    )
                                    onAuthSuccess()
                                } catch (e: Exception) {
                                    errorMessage = "Could not setup shop: ${e.localizedMessage}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        } else {
                            if (phone.length < 10) {
                                errorMessage = "Please enter your 10-digit mobile number."
                                return@LiquidGlassButton
                            }
                            if (pin.length != 4) {
                                errorMessage = "Please enter your 4-digit PIN."
                                return@LiquidGlassButton
                            }
                            isLoading = true
                            coroutineScope.launch {
                                val result = authRepository.login(phone, pin)
                                if (result.isSuccess) {
                                    onAuthSuccess()
                                } else {
                                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Invalid mobile number or PIN. Please try again."
                                }
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(18.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = if (isSignUp) "Create Shop & Continue" else "Login & Open Store",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } // closes LiquidGlassCard
        } // closes Column
    } // closes Scaffold
    } // closes LiquidGlassBackground

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Cloud Backend Server URL", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Set your FastAPI backend address. For Android Emulator use 10.0.2.2:8000, or use your computer's Wi-Fi IP / cloud URL.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://10.0.2.2:8000/") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (serverTestMessage != null) {
                        Text(
                            text = serverTestMessage ?: "",
                            fontSize = 13.sp,
                            color = if (serverTestMessage?.startsWith("✓") == true) GreenPrimary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        retrofitClient?.setServerUrl(serverUrlInput)
                        showServerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
