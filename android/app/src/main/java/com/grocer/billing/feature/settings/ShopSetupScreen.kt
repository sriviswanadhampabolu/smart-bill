package com.grocer.billing.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grocer.billing.core.data.repository.ShopRepository
import com.grocer.billing.ui.theme.GreenPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSetupScreen(
    shopRepository: ShopRepository,
    retrofitClient: com.grocer.billing.core.data.remote.RetrofitClient? = null,
    onFinished: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val shop by shopRepository.observeShop().collectAsState(initial = null)

    var shopName by remember(shop) { mutableStateOf(shop?.name ?: "") }
    var ownerName by remember(shop) { mutableStateOf(shop?.ownerName ?: "") }
    var upiId by remember(shop) { mutableStateOf(shop?.upiId ?: "") }
    var currencySymbol by remember(shop) { mutableStateOf(shop?.currencySymbol ?: "₹") }
    var newPin by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(retrofitClient?.getServerUrl() ?: "http://10.0.2.2:8000/") }
    var selectedLanguage by remember { mutableStateOf(com.grocer.billing.core.lang.AppLanguageManager.getLanguage()) }
    var isSaving by remember { mutableStateOf(false) }

    val languages = listOf(
        "hi" to "Hindi (हिंदी)",
        "en" to "English",
        "ta" to "Tamil (தமிழ்)",
        "te" to "Telugu (తెలుగు)",
        "kn" to "Kannada (ಕನ್ನಡ)",
        "mr" to "Marathi (मराठी)",
        "gu" to "Gujarati (ગુજરાતી)",
        "bn" to "Bengali (বাংলা)"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shop Setup & Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onFinished) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GreenPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Counter & Receipt Details",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = shopName,
                onValueChange = { shopName = it },
                label = { Text("Shop Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = ownerName,
                onValueChange = { ownerName = it },
                label = { Text("Owner Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = upiId,
                onValueChange = { upiId = it },
                label = { Text("UPI ID (for Customer QR payment)") },
                placeholder = { Text("e.g. shopname@okaxis or 9876543210@paytm") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = currencySymbol,
                onValueChange = { currencySymbol = it },
                label = { Text("Currency Symbol") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Counter Security PIN",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = newPin,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                label = { Text("Change 4-Digit Security PIN (Leave blank to keep unchanged)") },
                placeholder = { Text("e.g. 1234") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Regional Language for Item Names",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            languages.forEach { (code, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedLanguage == code),
                        onClick = {
                            selectedLanguage = code
                            com.grocer.billing.core.lang.AppLanguageManager.setLanguage(code)
                        }
                    )
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Cloud Backend Server URL",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Backend Server URL (FastAPI / Cloud)") },
                placeholder = { Text("http://10.0.2.2:8000/ or your server IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val currentShop = shop ?: return@Button
                    isSaving = true
                    coroutineScope.launch {
                        com.grocer.billing.core.lang.AppLanguageManager.setLanguage(selectedLanguage)
                        retrofitClient?.setServerUrl(serverUrl)
                        shopRepository.updateShopProfile(
                            currentShop.copy(
                                name = shopName.trim(),
                                ownerName = ownerName.trim(),
                                upiId = upiId.trim().ifBlank { null },
                                currencySymbol = currencySymbol.trim()
                            )
                        )
                        if (newPin.length == 4) {
                            shopRepository.updatePin(currentShop.id, newPin)
                        }
                        shopRepository.updateSettingsJson(
                            currentShop.id,
                            """{"language":"$selectedLanguage","regional_language":"$selectedLanguage"}"""
                        )
                        isSaving = false
                        onFinished()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                enabled = !isSaving
            ) {
                Text(
                    text = if (isSaving) "Saving..." else "Save & Go to Dashboard",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
