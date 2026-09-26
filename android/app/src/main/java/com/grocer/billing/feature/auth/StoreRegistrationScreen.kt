package com.grocer.billing.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.grocer.billing.core.data.repository.AuthRepository
import com.grocer.billing.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreRegistrationScreen(
    authRepository: AuthRepository,
    onRegistrationCompleted: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val activeShop by authRepository.observeActiveShop().collectAsState(initial = null)

    var shopkeeperName by remember { mutableStateOf("") }
    var storeName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("1234") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(activeShop) {
        activeShop?.let { shop ->
            if (shopkeeperName.isBlank() && shop.ownerName.isNotBlank() && shop.ownerName != "Shop Owner") {
                shopkeeperName = shop.ownerName
            }
            if (storeName.isBlank() && shop.name.isNotBlank() && !shop.name.contains("'s Store") && shop.name != "Kirana Store") {
                storeName = shop.name
            }
            if (phone.isBlank() && shop.phone.isNotBlank() && !shop.phone.startsWith("g_")) {
                phone = shop.phone
            }
            if (address.isBlank() && shop.address.isNotBlank()) {
                address = shop.address
            }
            if (upiId.isBlank() && !shop.upiId.isNullOrBlank()) {
                upiId = shop.upiId
            }
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
                                text = "Register Your Store",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Setup your shopkeeper & business profile",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary.copy(alpha = 0.92f))
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = Color.White.copy(alpha = 0.90f),
                    elevation = 6.dp
                ) {
                    Text(
                        text = "Store Details",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "These details will appear on your customer receipts, account dashboard, and QR payment counter.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                    )

                    // 1. Shopkeeper Name
                    OutlinedTextField(
                        value = shopkeeperName,
                        onValueChange = { shopkeeperName = it },
                        label = { Text("Shopkeeper Name *") },
                        placeholder = { Text("e.g. Ramesh Kumar") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GreenPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Store Name
                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        label = { Text("Store Name *") },
                        placeholder = { Text("e.g. Shree Balaji Super Market") },
                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, tint = GreenPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Phone Number
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { if (it.length <= 10) phone = it },
                        label = { Text("Mobile Number (10 digits) *") },
                        placeholder = { Text("e.g. 9876543210") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = GreenPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. Store Address
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Store Address *") },
                        placeholder = { Text("e.g. Shop #4, Market Road, Near Gandhi Chowk") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = GreenPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        minLines = 2,
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5. UPI ID
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("Primary UPI ID (for Customer Payments) *") },
                        placeholder = { Text("e.g. storename@okaxis or 9876543210@upi") },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = GreenPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 6. 4-Digit Security PIN
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4) pin = it },
                        label = { Text("4-Digit Counter Security PIN *") },
                        placeholder = { Text("e.g. 1234") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = GreenPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
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
                            if (shopkeeperName.trim().isBlank()) {
                                errorMessage = "Please enter the shopkeeper name."
                                return@LiquidGlassButton
                            }
                            if (storeName.trim().isBlank()) {
                                errorMessage = "Please enter the store name."
                                return@LiquidGlassButton
                            }
                            if (phone.trim().length < 10) {
                                errorMessage = "Please enter a valid 10-digit mobile number."
                                return@LiquidGlassButton
                            }
                            if (address.trim().isBlank()) {
                                errorMessage = "Please enter the store address."
                                return@LiquidGlassButton
                            }
                            if (upiId.trim().isBlank()) {
                                errorMessage = "Please enter your store's UPI ID."
                                return@LiquidGlassButton
                            }
                            if (pin.trim().length != 4) {
                                errorMessage = "Please enter a 4-digit PIN for counter unlock."
                                return@LiquidGlassButton
                            }

                            isLoading = true
                            errorMessage = null
                            coroutineScope.launch {
                                try {
                                    val currentShop = authRepository.getActiveShop()
                                    if (currentShop != null) {
                                        authRepository.updateShopProfileDetails(
                                            shopName = storeName.trim(),
                                            ownerName = shopkeeperName.trim(),
                                            phone = phone.trim(),
                                            address = address.trim(),
                                            upiId = upiId.trim()
                                        )
                                        authRepository.updateActiveShopPin(pin.trim())
                                    } else {
                                        authRepository.createLocalShop(
                                            shopName = storeName.trim(),
                                            ownerName = shopkeeperName.trim(),
                                            phone = phone.trim(),
                                            pin = pin.trim(),
                                            address = address.trim(),
                                            upiId = upiId.trim()
                                        )
                                    }
                                    onRegistrationCompleted()
                                } catch (e: Exception) {
                                    errorMessage = "Registration failed: ${e.localizedMessage}"
                                } finally {
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
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Complete Registration & Open Store",
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
}
