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
import com.grocer.billing.ui.theme.GreenPrimary
import kotlinx.coroutines.launch

@Composable
fun RestockDialog(
    item: ItemEntity,
    itemRepository: ItemRepository,
    onDismiss: () -> Unit,
    onRestocked: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var deltaStr by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val quickChips = if (item.unitType == "piece") {
        listOf(5.0, 10.0, 20.0, 50.0)
    } else {
        listOf(1.0, 5.0, 10.0, 25.0)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Restock Item",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${item.name} (Current: ${item.stockQty} ${if (item.unitType == "piece") "pcs" else "kg"})",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickChips.forEach { amount ->
                        FilterChip(
                            selected = deltaStr == amount.toString(),
                            onClick = { deltaStr = amount.toString() },
                            label = { Text("+${if (item.unitType == "piece") amount.toInt() else amount}") }
                        )
                    }
                }

                OutlinedTextField(
                    value = deltaStr,
                    onValueChange = { deltaStr = it },
                    label = { Text("Add Quantity (${if (item.unitType == "piece") "pcs" else "kg"})") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

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
                            val delta = deltaStr.toDoubleOrNull() ?: return@Button
                            if (delta <= 0) return@Button
                            isSaving = true
                            coroutineScope.launch {
                                itemRepository.adjustStock(item.id, delta, "restock")
                                onRestocked()
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        enabled = !isSaving && (deltaStr.toDoubleOrNull() ?: 0.0) > 0
                    ) {
                        Text(if (isSaving) "Updating..." else "Confirm Restock")
                    }
                }
            }
        }
    }
}
