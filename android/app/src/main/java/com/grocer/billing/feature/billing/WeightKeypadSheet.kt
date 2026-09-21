package com.grocer.billing.feature.billing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.ui.theme.GreenPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightKeypadSheet(
    item: ItemEntity,
    onDismiss: () -> Unit,
    onWeightConfirmed: (weightInKg: Double) -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var enteredAmount by remember { mutableStateOf("1") }
    var selectedUnit by remember { mutableStateOf("kg") } // 'kg' or 'g'

    val quickChips = if (selectedUnit == "kg") {
        listOf("0.5", "1", "2", "5")
    } else {
        listOf("100", "250", "500", "750")
    }

    val computedKg = remember(enteredAmount, selectedUnit) {
        val raw = enteredAmount.toDoubleOrNull() ?: 0.0
        if (selectedUnit == "g") raw / 1000.0 else raw
    }

    val computedPrice = remember(computedKg, item.price) {
        (computedKg * item.price * 100).toLong() / 100.0
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Enter Weight: ${item.name}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Rate: ₹%.2f per kg".format(item.price),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Unit toggle (Kilograms vs. Grams)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = selectedUnit == "kg",
                    onClick = {
                        selectedUnit = "kg"
                        enteredAmount = "1"
                    },
                    label = { Text("Kilograms (kg)") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedUnit == "g",
                    onClick = {
                        selectedUnit = "g"
                        enteredAmount = "500"
                    },
                    label = { Text("Grams (g)") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickChips.forEach { chipValue ->
                    AssistChip(
                        onClick = { enteredAmount = chipValue },
                        label = { Text(if (selectedUnit == "kg") "$chipValue kg" else "$chipValue g") }
                    )
                }
            }

            // Numeric Display & Input
            OutlinedTextField(
                value = enteredAmount,
                onValueChange = { enteredAmount = it },
                label = { Text("Quantity in $selectedUnit") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Dynamic line total calculation
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total (%.3f kg):".format(computedKg), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("₹%.2f".format(computedPrice), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                }
            }

            Button(
                onClick = {
                    if (computedKg > 0) {
                        onWeightConfirmed(computedKg)
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                enabled = computedKg > 0
            ) {
                Text("Add to Bill", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
