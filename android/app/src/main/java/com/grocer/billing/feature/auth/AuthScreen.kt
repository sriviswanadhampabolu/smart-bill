package com.grocer.billing.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grocer.billing.R
import com.grocer.billing.core.data.backup.BackupMetadata
import com.grocer.billing.core.data.backup.LocalBackupManager
import com.grocer.billing.core.data.remote.RetrofitClient
import com.grocer.billing.core.data.repository.AuthRepository
import com.grocer.billing.ui.theme.*
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    localBackupManager: LocalBackupManager? = null,
    retrofitClient: RetrofitClient? = null,
    onAuthSuccess: (isNewUser: Boolean) -> Unit,
    onNavigateToDashboard: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lastPhone = remember { authRepository.getLastPhoneNumber() }
    val lastShop = remember { authRepository.getLastShopName() }
    val lastOwner = remember { authRepository.getLastOwnerName() }
    val userEmail = remember { authRepository.getUserEmail() }
    val hasPastAccount = remember { authRepository.hasRegisteredShop() || lastPhone.isNotBlank() }

    // Tab: 0 = Login, 1 = Register
    var selectedTab by remember { mutableStateOf(if (hasPastAccount) 0 else 1) }

    var phoneInput by remember { mutableStateOf(lastPhone) }
    var pinInput by remember { mutableStateOf("") }
    var regShopName by remember { mutableStateOf(lastShop) }
    var regOwnerName by remember { mutableStateOf(lastOwner) }
    var regAddress by remember { mutableStateOf("") }
    var regUpiId by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var availableBackup by remember { mutableStateOf<BackupMetadata?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    // Google Credential Manager State & Logic
    val googleAuthManager = remember { GoogleAuthManager() }
    var isGoogleLoading by remember { mutableStateOf(false) }

    fun handleGoogleSignIn() {
        if (isGoogleLoading || isLoading) return
        isGoogleLoading = true
        errorMessage = null

        coroutineScope.launch {
            try {
                val signInResult = googleAuthManager.signIn(context)
                if (signInResult.isSuccess) {
                    val googleUser = signInResult.getOrThrow()
                    val userEmail = googleUser.email
                    val userDisplayName = googleUser.displayName ?: "Merchant"

                    if (selectedTab == 0) {
                        // User Login Tab: verify account exists in Neon database
                        val authResult = authRepository.loginWithGoogle(
                            email = userEmail,
                            displayName = userDisplayName,
                            idToken = googleUser.idToken,
                            photoUrl = googleUser.photoUrl,
                            phone = googleUser.phoneNumber ?: phoneInput.takeIf { it.trim().length == 10 }
                        )
                        if (authResult.isSuccess) {
                            if (onNavigateToDashboard != null) {
                                onNavigateToDashboard()
                            } else {
                                onAuthSuccess(false)
                            }
                        } else {
                            val err = authResult.exceptionOrNull()?.localizedMessage
                                ?: "No store account found for $userEmail. Please register your account."
                            errorMessage = err
                            if (err.contains("register", ignoreCase = true) || err.contains("No store account", ignoreCase = true) || err.contains("No account", ignoreCase = true)) {
                                selectedTab = 1
                                if (regOwnerName.isBlank()) regOwnerName = userDisplayName
                                if (regShopName.isBlank()) regShopName = "${userDisplayName}'s Store"
                                if (phoneInput.isBlank() && !googleUser.phoneNumber.isNullOrBlank()) {
                                    phoneInput = googleUser.phoneNumber!!.filter { it.isDigit() }.takeLast(10)
                                }
                            }
                        }
                    } else {
                        // Register Tab: register new store linked with Google account
                        val effectiveShopName = regShopName.trim().ifBlank { "${userDisplayName}'s Store" }
                        val effectiveOwnerName = regOwnerName.trim().ifBlank { userDisplayName }
                        val effectivePhone = phoneInput.trim().takeIf { it.length == 10 }
                            ?: (googleUser.phoneNumber?.filter { it.isDigit() }?.takeLast(10) ?: "")

                        val regResult = authRepository.registerWithGoogle(
                            email = userEmail,
                            displayName = userDisplayName,
                            shopName = effectiveShopName,
                            ownerName = effectiveOwnerName,
                            phone = effectivePhone,
                            pin = pinInput.trim().ifBlank { "1234" },
                            address = regAddress.trim(),
                            upiId = regUpiId.trim().ifBlank { null },
                            idToken = googleUser.idToken
                        )
                        if (regResult.isSuccess) {
                            if (onNavigateToDashboard != null) {
                                onNavigateToDashboard()
                            } else {
                                onAuthSuccess(true)
                            }
                        } else {
                            val err = regResult.exceptionOrNull()?.localizedMessage
                                ?: "Failed to register account with Google in Neon database."
                            errorMessage = err
                            if (err.contains("already registered", ignoreCase = true)) {
                                selectedTab = 0
                            }
                        }
                    }
                } else {
                    val ex = signInResult.exceptionOrNull()
                    if (ex is GoogleAuthCancellationException) {
                        errorMessage = "Google sign-in was cancelled."
                    } else {
                        errorMessage = ex?.localizedMessage ?: "Google sign-in failed. Please try again."
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Google sign-in error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                isGoogleLoading = false
            }
        }
    }


    // Backend Cloud Connection Dialog State
    var showServerDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(retrofitClient?.getServerUrl() ?: "http://10.0.2.2:8000/") }
    var serverTestMessage by remember { mutableStateOf<String?>(null) }
    var isTestingServer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val existingShop = authRepository.getActiveShop()
        if (existingShop != null) {
            if (phoneInput.isBlank()) phoneInput = existingShop.phone
            if (regShopName.isBlank()) regShopName = existingShop.name
            if (regOwnerName.isBlank()) regOwnerName = existingShop.ownerName
            if (regAddress.isBlank()) regAddress = existingShop.address
            if (regUpiId.isBlank() && !existingShop.upiId.isNullOrBlank()) regUpiId = existingShop.upiId
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "Smart Bill Logo",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Smart Bill",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = "Neon Cloud & High-Speed Counter Sync",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
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
                                contentDescription = "Neon Backend Server URL",
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
                // Offline Backup Banner (if found in storage)
                if (availableBackup != null) {
                    val backup = availableBackup!!
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        tint = Color(0xFFE8F5E9).copy(alpha = 0.92f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Backup, contentDescription = null, tint = GreenPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Existing Backup Found",
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Found \"${backup.shopName}\" (${backup.itemCount} items, ${backup.billCount} bills).",
                            fontSize = 12.sp,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LiquidGlassButton(
                            onClick = {
                                isRestoring = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val success = localBackupManager?.restoreBackup() == true
                                    isRestoring = false
                                    if (success) {
                                        onAuthSuccess(false)
                                    } else {
                                        errorMessage = "Failed to restore backup file"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isRestoring
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("1-Tap Restore My Store Data", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }

                // Main Auth Glass Card
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = Color.White.copy(alpha = 0.90f),
                    elevation = 8.dp
                ) {
                    Text(
                        text = if (selectedTab == 0) "Welcome Back" else "Create Store Account",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (selectedTab == 0) "Login to open your billing counter" else "Register once to sync inventory & bills to Neon Cloud",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                    )

                    // Tab Selector: Login vs Register
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
                            onClick = { selectedTab = 0; errorMessage = null },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedTab == 0) GreenPrimary else Color.Transparent,
                                contentColor = if (selectedTab == 0) Color.White else TextSecondary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = if (selectedTab == 0) 3.dp else 0.dp)
                        ) {
                            Text("User Login", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Button(
                            onClick = { selectedTab = 1; errorMessage = null },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedTab == 1) GreenPrimary else Color.Transparent,
                                contentColor = if (selectedTab == 1) Color.White else TextSecondary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = if (selectedTab == 1) 3.dp else 0.dp)
                        ) {
                            Text("Register", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // MODERN GOOGLE CREDENTIAL MANAGER SIGN-IN BUTTON
                    GoogleSignInButton(
                        onClick = { handleGoogleSignIn() },
                        isLoading = isGoogleLoading,
                        isSignUp = selectedTab == 1,
                        enabled = !isLoading && !isGoogleLoading
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE0E0E0))
                        Text(
                            text = "  OR USE MOBILE  ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE0E0E0))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // If Register Tab, show store details fields
                    if (selectedTab == 1) {
                        OutlinedTextField(
                            value = regShopName,
                            onValueChange = { regShopName = it },
                            label = { Text("Store Name *") },
                            placeholder = { Text("e.g. Balaji General Store") },
                            leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, tint = GreenPrimary) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = regOwnerName,
                            onValueChange = { regOwnerName = it },
                            label = { Text("Shopkeeper Name *") },
                            placeholder = { Text("e.g. Rajesh Kumar") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GreenPrimary) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = regAddress,
                            onValueChange = { regAddress = it },
                            label = { Text("Store Address *") },
                            placeholder = { Text("e.g. Shop #4, Main Market, Hyderabad") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = GreenPrimary) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = regUpiId,
                            onValueChange = { regUpiId = it },
                            label = { Text("Primary UPI ID (for QR Payments) *") },
                            placeholder = { Text("e.g. storename@upi") },
                            leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = GreenPrimary) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Mobile Number
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { if (it.length <= 10) phoneInput = it },
                        label = { Text("Mobile Number (10 digits) *") },
                        placeholder = { Text("e.g. 9876543210") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = GreenPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4-Digit PIN
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4) pinInput = it },
                        label = { Text(if (selectedTab == 0) "4-Digit PIN *" else "Choose 4-Digit Unlock PIN *") },
                        placeholder = { Text("e.g. 1234") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = GreenPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    LiquidGlassButton(
                        onClick = {
                            if (phoneInput.trim().length < 10) {
                                errorMessage = "Please enter a valid 10-digit mobile number."
                                return@LiquidGlassButton
                            }
                            if (pinInput.trim().length != 4) {
                                errorMessage = "Please enter your 4-digit PIN."
                                return@LiquidGlassButton
                            }

                            if (selectedTab == 1) {
                                if (regShopName.trim().isBlank()) {
                                    errorMessage = "Please enter your store name."
                                    return@LiquidGlassButton
                                }
                                if (regOwnerName.trim().isBlank()) {
                                    errorMessage = "Please enter shopkeeper name."
                                    return@LiquidGlassButton
                                }
                                if (regAddress.trim().isBlank()) {
                                    errorMessage = "Please enter store address."
                                    return@LiquidGlassButton
                                }
                                if (regUpiId.trim().isBlank()) {
                                    errorMessage = "Please enter store UPI ID."
                                    return@LiquidGlassButton
                                }

                                isLoading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    try {
                                        authRepository.createLocalShop(
                                            shopName = regShopName.trim(),
                                            ownerName = regOwnerName.trim(),
                                            phone = phoneInput.trim(),
                                            pin = pinInput.trim(),
                                            address = regAddress.trim(),
                                            upiId = regUpiId.trim()
                                        )
                                        onAuthSuccess(true)
                                    } catch (e: Exception) {
                                        errorMessage = "Registration error: ${e.localizedMessage}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            } else {
                                // Login Tab
                                isLoading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val result = authRepository.login(phoneInput.trim(), pinInput.trim())
                                    if (result.isSuccess) {
                                        val shop = result.getOrNull()
                                        val isComplete = authRepository.hasCompletedStoreProfile(shop)
                                        onAuthSuccess(!isComplete)
                                    } else {
                                        errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Invalid mobile number or PIN. Please try again."
                                    }
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                        } else {
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Default.LockOpen else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedTab == 0) "Login & Open Store" else "Register Store Online",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }


    // Backend URL Settings Dialog
    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Neon Cloud Backend URL", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Connects your counter to the FastAPI backend and Neon cloud Postgres database.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { 
                            serverUrlInput = it 
                            serverTestMessage = null
                        },
                        label = { Text("Backend Server URL") },
                        placeholder = { Text("http://10.0.2.2:8000/") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = {
                            isTestingServer = true
                            serverTestMessage = null
                            coroutineScope.launch {
                                try {
                                    // 1. Direct Cloud Health Check with Neon PostgreSQL
                                    val neonClient = authRepository.getNeonCloudClient()
                                    val neonHealth = neonClient?.checkHealth()
                                    if (neonHealth != null && neonHealth.isConnected) {
                                        serverTestMessage = "✓ Online Neon Cloud Database Connected!\nDatabase: ${neonHealth.database} (${neonHealth.provider})\nVersion: ${neonHealth.version}\nOnline Records: shops (${neonHealth.shopCount}), items (${neonHealth.itemCount}), bills (${neonHealth.billCount})"
                                    } else {
                                        // 2. Fallback to testing configured server URL
                                        retrofitClient?.setServerUrl(serverUrlInput)
                                        val response = retrofitClient?.getApiService()?.checkHealth()
                                        if (response?.isSuccessful == true) {
                                            val body = response.body()
                                            serverTestMessage = "✓ Online Database Server Connected!\nStatus: ${body?.status} | DB: ${body?.database} (${body?.provider ?: "Neon"})"
                                        } else {
                                            val errReason = neonHealth?.error ?: "Server returned HTTP ${response?.code() ?: "no response"}"
                                            serverTestMessage = "✗ Connection failed: $errReason"
                                        }
                                    }
                                } catch (e: Exception) {
                                    serverTestMessage = "✗ Connection failed: ${e.localizedMessage}"
                                } finally {
                                    isTestingServer = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isTestingServer
                    ) {
                        if (isTestingServer) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing Neon Connection...")
                        } else {
                            Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Database Connection")
                        }
                    }

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
