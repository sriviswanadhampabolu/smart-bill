package com.grocer.billing.feature.billing

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.repository.BillingRepository
import com.grocer.billing.core.data.repository.CartLine
import com.grocer.billing.core.data.repository.CompletedSale
import com.grocer.billing.core.data.repository.ItemRepository
import com.grocer.billing.feature.inventory.CATEGORIES
import com.grocer.billing.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualBillingScreen(
    shopId: String,
    shopName: String,
    currencySymbol: String = "₹",
    itemRepository: ItemRepository,
    billingRepository: BillingRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    // Running Cart state
    val cartLines = remember { mutableStateListOf<CartLine>() }
    var paymentMethod by remember { mutableStateOf("cash") } // 'cash', 'upi', 'credit'

    var weightItemToSelect by remember { mutableStateOf<ItemEntity?>(null) }
    var itemToDeleteFromCart by remember { mutableStateOf<CartLine?>(null) }
    var showClearBillConfirm by remember { mutableStateOf(false) }
    var completedSale by remember { mutableStateOf<CompletedSale?>(null) }
    var isSavingBill by remember { mutableStateOf(false) }

    // Items list
    val allItems by itemRepository.observeItems(shopId).collectAsState(initial = emptyList())

    val filteredItems = remember(allItems, searchQuery, selectedCategory) {
        allItems.filter { item ->
            val matchesCat = (selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true))
            val matchesQuery = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    (item.nameRegional?.contains(searchQuery, ignoreCase = true) == true) ||
                    (item.barcode == searchQuery.trim())
            matchesCat && matchesQuery
        }
    }

    val runningTotal = remember(cartLines.map { it.lineTotal }) {
        cartLines.sumOf { it.lineTotal }
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Manual Counter Billing", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 20.sp)
                            Text("Fast Grid & Barcode Sales", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        if (cartLines.isNotEmpty()) {
                            TextButton(
                                onClick = { showClearBillConfirm = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                            ) {
                                Text("Clear Cart", fontWeight = FontWeight.Bold)
                            }
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
            ) {
                // Upper Section: Item Picker
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    placeholder = { Text("Search catalog to add...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Category Filter Strip
                val categoriesWithAll = listOf("All") + CATEGORIES
                ScrollableTabRow(
                    selectedTabIndex = categoriesWithAll.indexOf(selectedCategory).coerceAtLeast(0),
                    edgePadding = 14.dp,
                    divider = {}
                ) {
                    categoriesWithAll.forEach { cat ->
                        Tab(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            text = { Text(cat, fontSize = 13.sp) }
                        )
                    }
                }

                // Grid of items to tap and add to bill
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (item.unitType == "weight") {
                                        weightItemToSelect = item
                                    } else {
                                        // Piece item: increment if already in cart, else add 1
                                        val existingIndex = cartLines.indexOfFirst { it.item.id == item.id }
                                        if (existingIndex >= 0) {
                                            val current = cartLines[existingIndex]
                                            cartLines[existingIndex] = current.copy(qty = current.qty + 1)
                                        } else {
                                            cartLines.add(CartLine(item = item, qty = 1.0))
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = item.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "$currencySymbol%.2f / %s".format(item.price, if (item.unitType == "piece") "pc" else "kg"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GreenPrimary
                                )
                                Text(
                                    text = "Stock: ${item.stockQty} ${if (item.unitType == "piece") "pcs" else "kg"}",
                                    fontSize = 11.sp,
                                    color = if (item.stockQty <= item.lowStockThreshold) AlertRed else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Lower Section: Live Cart & Checkout
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f),
                color = Color(0xFFF9FBF9),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Running Cart Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Running Bill (${cartLines.size} items)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$currencySymbol%.2f".format(runningTotal),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenPrimary
                        )
                    }

                    // Cart Items List
                    if (cartLines.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Tap items above to add to bill", color = TextSecondary, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(cartLines, key = { it.item.id }) { line ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(line.item.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(
                                            text = "${if (line.item.unitType == "weight") "%.3f kg".format(line.qty) else "%.0f pcs".format(line.qty)} @ $currencySymbol%.2f".format(line.unitPriceSnapshot),
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    // Quantity Adjusters
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                val idx = cartLines.indexOf(line)
                                                if (line.item.unitType == "piece") {
                                                    if (line.qty > 1) {
                                                        cartLines[idx] = line.copy(qty = line.qty - 1)
                                                    } else {
                                                        itemToDeleteFromCart = line
                                                    }
                                                } else {
                                                    if (line.qty > 0.25) {
                                                        cartLines[idx] = line.copy(qty = line.qty - 0.25)
                                                    } else {
                                                        itemToDeleteFromCart = line
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                        }

                                        Text(
                                            text = if (line.item.unitType == "weight") "%.2f".format(line.qty) else "%.0f".format(line.qty),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )

                                        IconButton(
                                            onClick = {
                                                val idx = cartLines.indexOf(line)
                                                val step = if (line.item.unitType == "piece") 1.0 else 0.5
                                                cartLines[idx] = line.copy(qty = line.qty + step)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = "$currencySymbol%.2f".format(line.lineTotal),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = GreenPrimary
                                    )

                                    IconButton(
                                        onClick = { itemToDeleteFromCart = line },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Payment Method Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("cash" to "Cash", "upi" to "UPI QR", "credit" to "Khata / Credit").forEach { (method, label) ->
                            FilterChip(
                                selected = paymentMethod == method,
                                onClick = { paymentMethod = method },
                                label = { Text(label, fontSize = 13.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Liquid Glass Checkout Button
                    LiquidGlassButton(
                        onClick = {
                            if (cartLines.isEmpty()) return@LiquidGlassButton
                            isSavingBill = true
                            coroutineScope.launch {
                                try {
                                    val sale = billingRepository.completeSale(
                                        shopId = shopId,
                                        shopName = shopName,
                                        cartLines = cartLines.toList(),
                                        paymentMethod = paymentMethod,
                                        currencySymbol = currencySymbol
                                    )
                                    completedSale = sale
                                } catch (e: Exception) {
                                    // Plain language error
                                } finally {
                                    isSavingBill = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        enabled = cartLines.isNotEmpty() && !isSavingBill
                    ) {
                        Text(
                            text = if (isSavingBill) "Completing Sale..." else "Save Bill • $currencySymbol%.2f".format(runningTotal),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
    }

    // Weight picker modal
    if (weightItemToSelect != null) {
        WeightKeypadSheet(
            item = weightItemToSelect!!,
            onDismiss = { weightItemToSelect = null },
            onWeightConfirmed = { weight ->
                val existingIndex = cartLines.indexOfFirst { it.item.id == weightItemToSelect!!.id }
                if (existingIndex >= 0) {
                    val current = cartLines[existingIndex]
                    cartLines[existingIndex] = current.copy(qty = current.qty + weight)
                } else {
                    cartLines.add(CartLine(item = weightItemToSelect!!, qty = weight))
                }
                weightItemToSelect = null
            }
        )
    }

    // Explicit Confirmation Dialog before deleting an item from cart
    if (itemToDeleteFromCart != null) {
        AlertDialog(
            onDismissRequest = { itemToDeleteFromCart = null },
            title = { Text("Remove from Bill?") },
            text = { Text("Remove ${itemToDeleteFromCart!!.item.name} from this customer's bill?") },
            confirmButton = {
                Button(
                    onClick = {
                        cartLines.remove(itemToDeleteFromCart!!)
                        itemToDeleteFromCart = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDeleteFromCart = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Explicit Confirmation Dialog before clearing entire bill
    if (showClearBillConfirm) {
        AlertDialog(
            onDismissRequest = { showClearBillConfirm = false },
            title = { Text("Clear Current Bill?") },
            text = { Text("All items in the current bill will be cleared. Do you want to proceed?") },
            confirmButton = {
                Button(
                    onClick = {
                        cartLines.clear()
                        showClearBillConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Clear Bill")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearBillConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Receipt Dialog on Sale Completion
    if (completedSale != null) {
        AlertDialog(
            onDismissRequest = {
                cartLines.clear()
                completedSale = null
            },
            title = { Text("Sale Completed! (${completedSale!!.bill.billNumber})") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = completedSale!!.receiptText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Color(0xFFF0F4F1), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, completedSale!!.receiptText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Receipt via WhatsApp"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share WhatsApp")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        cartLines.clear()
                        completedSale = null
                    }
                ) {
                    Text("New Bill")
                }
            }
        )
    }
}
