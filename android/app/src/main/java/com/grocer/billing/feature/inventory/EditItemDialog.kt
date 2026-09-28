package com.grocer.billing.feature.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.repository.ItemRepository
import com.grocer.billing.ui.theme.GreenPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemDialog(
    item: ItemEntity,
    itemRepository: ItemRepository,
    onDismiss: () -> Unit,
    onItemUpdated: (ItemEntity) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(item.name) }
    var nameRegional by remember { mutableStateOf(item.nameRegional ?: "") }
    var category by remember { mutableStateOf(item.category) }
    var unitType by remember { mutableStateOf(item.unitType) } // 'piece' or 'weight'
    var priceStr by remember { mutableStateOf(if (item.price % 1.0 == 0.0) item.price.toInt().toString() else item.price.toString()) }
    var stockQtyStr by remember { mutableStateOf(if (item.stockQty % 1.0 == 0.0) item.stockQty.toInt().toString() else item.stockQty.toString()) }
    var thresholdStr by remember { mutableStateOf(if (item.lowStockThreshold % 1.0 == 0.0) item.lowStockThreshold.toInt().toString() else item.lowStockThreshold.toString()) }
    var barcode by remember { mutableStateOf(item.barcode ?: "") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Edit Item Details",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name * (e.g. Aashirvaad Atta 5kg)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = nameRegional,
                    onValueChange = { nameRegional = it },
                    label = { Text("Regional Name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                // Unit Type Toggle
                Text("Sold by (Unit Type):", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = unitType == "piece",
                        onClick = { unitType = "piece" },
                        label = { Text("Piece / Packet (Pcs)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = unitType == "weight",
                        onClick = { unitType = "weight" },
                        label = { Text("Weight (per Kg)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Category Selection
                Text("Category:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                ScrollableTabRow(
                    selectedTabIndex = CATEGORIES.indexOf(category).coerceAtLeast(0),
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    CATEGORIES.forEach { cat ->
                        Tab(
                            selected = category == cat,
                            onClick = { category = cat },
                            text = { Text(cat, fontSize = 13.sp, fontWeight = if (category == cat) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it },
                        label = { Text(if (unitType == "piece") "Price (₹ / pc) *" else "Price (₹ / kg) *") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = stockQtyStr,
                        onValueChange = { stockQtyStr = it },
                        label = { Text(if (unitType == "piece") "Stock (pcs) *" else "Stock (kg) *") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = thresholdStr,
                        onValueChange = { thresholdStr = it },
                        label = { Text("Low-stock Alert") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode (optional)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val price = priceStr.toDoubleOrNull()
                            if (name.isBlank()) {
                                errorMessage = "Item name is required"
                                return@Button
                            }
                            if (price == null || price < 0) {
                                errorMessage = "Please enter a valid price"
                                return@Button
                            }

                            val stockQty = stockQtyStr.toDoubleOrNull() ?: 0.0
                            val threshold = thresholdStr.toDoubleOrNull() ?: 5.0

                            isSaving = true
                            errorMessage = null
                            coroutineScope.launch {
                                try {
                                    val updated = item.copy(
                                        name = name.trim(),
                                        nameRegional = nameRegional.trim().ifBlank { null },
                                        category = category.trim(),
                                        unitType = unitType,
                                        price = price,
                                        stockQty = stockQty,
                                        lowStockThreshold = threshold,
                                        barcode = barcode.trim().ifBlank { null }
                                    )
                                    itemRepository.updateItem(updated)
                                    onItemUpdated(updated)
                                    onDismiss()
                                } catch (e: Exception) {
                                    errorMessage = "Error updating item: ${e.localizedMessage}"
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        enabled = !isSaving
                    ) {
                        Text(if (isSaving) "Saving Changes..." else "Save Changes")
                    }
                }
            }
        }
    }
}
