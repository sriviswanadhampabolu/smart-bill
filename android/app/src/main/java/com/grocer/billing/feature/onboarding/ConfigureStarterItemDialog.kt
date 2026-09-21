package com.grocer.billing.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.repository.OnboardingRepository
import com.grocer.billing.core.data.repository.StarterCatalogItem
import com.grocer.billing.ui.theme.GreenLight
import com.grocer.billing.ui.theme.GreenPrimary
import com.grocer.billing.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun ConfigureStarterItemDialog(
    shopId: String,
    starterItem: StarterCatalogItem,
    onboardingRepository: OnboardingRepository,
    onDismiss: () -> Unit,
    onItemConfigured: (item: ItemEntity, openCamera: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var priceStr by remember { mutableStateOf(starterItem.suggested_price.toString()) }
    var stockQtyStr by remember { mutableStateOf("20") }
    var thresholdStr by remember { mutableStateOf(starterItem.default_threshold.toString()) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = starterItem.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                if (!starterItem.name_regional.isNullOrBlank()) {
                    Text(
                        text = starterItem.name_regional,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }

                Surface(
                    color = GreenLight,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${starterItem.category} • Sold by ${if (starterItem.unit_type == "piece") "Piece" else "Weight (per kg)"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GreenPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Your Selling Price (₹)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = stockQtyStr,
                    onValueChange = {
                        stockQtyStr = it
                        val stock = it.toDoubleOrNull()
                        if (stock != null) {
                            // Auto default to 20% of entered stock
                            val autoThreshold = (stock * 0.20).coerceAtLeast(2.0)
                            thresholdStr = "%.0f".format(autoThreshold)
                        }
                    },
                    label = { Text("Quantity in Stock (${if (starterItem.unit_type == "piece") "pcs" else "kg"})") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = thresholdStr,
                    onValueChange = { thresholdStr = it },
                    label = { Text("Low-stock Alert Threshold") },
                    supportingText = { Text("Alerts you when stock falls to this amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                if (errorMessage != null) {
                    Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Actions
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val price = priceStr.toDoubleOrNull()
                            val stock = stockQtyStr.toDoubleOrNull()
                            val threshold = thresholdStr.toDoubleOrNull() ?: 5.0
                            if (price == null || price <= 0) {
                                errorMessage = "Please enter a valid price"
                            } else if (stock == null || stock < 0) {
                                errorMessage = "Please enter current stock"
                            } else {
                                isSaving = true
                                coroutineScope.launch {
                                    try {
                                        val entity = onboardingRepository.setupStarterItem(
                                            shopId = shopId,
                                            starter = starterItem,
                                            price = price,
                                            stockQty = stock,
                                            lowStockThreshold = threshold
                                        )
                                        onItemConfigured(entity, true) // Open camera to train photos!
                                    } catch (e: Exception) {
                                        errorMessage = e.localizedMessage
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save & Take 5 Angles", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val price = priceStr.toDoubleOrNull()
                            val stock = stockQtyStr.toDoubleOrNull()
                            val threshold = thresholdStr.toDoubleOrNull() ?: 5.0
                            if (price == null || price <= 0) {
                                errorMessage = "Please enter a valid price"
                            } else if (stock == null || stock < 0) {
                                errorMessage = "Please enter current stock"
                            } else {
                                isSaving = true
                                coroutineScope.launch {
                                    try {
                                        val entity = onboardingRepository.setupStarterItem(
                                            shopId = shopId,
                                            starter = starterItem,
                                            price = price,
                                            stockQty = stock,
                                            lowStockThreshold = threshold
                                        )
                                        onItemConfigured(entity, false) // Save without photos right now
                                    } catch (e: Exception) {
                                        errorMessage = e.localizedMessage
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save & Next (Add Photos Later)")
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            }
        }
    }
}
