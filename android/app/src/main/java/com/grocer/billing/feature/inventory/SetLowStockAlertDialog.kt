package com.grocer.billing.feature.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.repository.ItemRepository
import com.grocer.billing.ui.theme.AlertRed
import com.grocer.billing.ui.theme.GreenPrimary
import kotlinx.coroutines.launch

@Composable
fun SetLowStockAlertDialog(
    item: ItemEntity,
    itemRepository: ItemRepository,
    onDismiss: () -> Unit,
    onUpdated: (Double) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var thresholdStr by remember {
        mutableStateOf(
            if (item.lowStockThreshold % 1.0 == 0.0) item.lowStockThreshold.toInt().toString()
            else item.lowStockThreshold.toString()
        )
    }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val unitLabel = if (item.unitType == "piece") "pcs" else "kg"
    val presetOptions = if (item.unitType == "piece") {
        listOf(2.0, 5.0, 10.0, 15.0, 20.0)
    } else {
        listOf(1.0, 2.0, 5.0, 10.0)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Set Low-Stock Alert Limit",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "When stock of \"${item.name}\" reaches or drops below this quantity, you will receive an automatic alert.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Current Stock:", fontSize = 13.sp)
                        Text(
                            "${item.stockQty} $unitLabel",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (item.stockQty <= item.lowStockThreshold) AlertRed else GreenPrimary
                        )
                    }
                }

                // Quick preset chips
                Text("Suggested limits:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetOptions.forEach { amount ->
                        val amountText = if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()
                        FilterChip(
                            selected = thresholdStr == amountText,
                            onClick = { thresholdStr = amountText },
                            label = { Text("$amountText $unitLabel", fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = thresholdStr,
                    onValueChange = {
                        thresholdStr = it
                        errorMessage = null
                    },
                    label = { Text("Alert Threshold Quantity ($unitLabel)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val newThreshold = thresholdStr.toDoubleOrNull()
                            if (newThreshold == null || newThreshold < 0) {
                                errorMessage = "Please enter a valid positive number"
                                return@Button
                            }
                            isSaving = true
                            coroutineScope.launch {
                                try {
                                    itemRepository.updateLowStockThreshold(item.id, newThreshold)
                                    onUpdated(newThreshold)
                                    onDismiss()
                                } catch (e: Exception) {
                                    errorMessage = "Error saving: ${e.localizedMessage}"
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        enabled = !isSaving
                    ) {
                        Text(if (isSaving) "Saving..." else "Save Alert Limit")
                    }
                }
            }
        }
    }
}
