package com.grocer.billing.feature.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNotifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.repository.ItemRepository
import com.grocer.billing.core.data.repository.OnboardingRepository
import com.grocer.billing.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    shopId: String,
    itemRepository: ItemRepository,
    onboardingRepository: OnboardingRepository? = null,
    onNavigateBack: () -> Unit,
    onNavigateToTrainPhotos: (ItemEntity) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showLowStockOnly by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var restockItem by remember { mutableStateOf<ItemEntity?>(null) }
    var itemToTrainPhotos by remember { mutableStateOf<ItemEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<ItemEntity?>(null) }
    var itemToEditAlert by remember { mutableStateOf<ItemEntity?>(null) }

    var effectiveShopId by remember(shopId) { mutableStateOf(shopId) }
    LaunchedEffect(shopId) {
        if (effectiveShopId.isBlank()) {
            effectiveShopId = itemRepository.getActiveShopId() ?: ""
        }
    }

    LaunchedEffect(itemToTrainPhotos) {
        itemToTrainPhotos?.let {
            onNavigateToTrainPhotos(it)
            itemToTrainPhotos = null
        }
    }

    // Observe all items sorted by lowest stock first
    val allItems by itemRepository.observeItems(effectiveShopId, lowestStockFirst = true)
        .collectAsState(initial = emptyList())

    val lowStockCount = remember(allItems) {
        allItems.count { it.stockQty <= it.lowStockThreshold }
    }

    val filteredItems = remember(allItems, searchQuery, selectedCategory, showLowStockOnly) {
        allItems.filter { item ->
            val matchesCategory = (selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true))
            val matchesQuery = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    (item.nameRegional?.contains(searchQuery, ignoreCase = true) == true) ||
                    (item.barcode == searchQuery.trim())
            val matchesLowStock = !showLowStockOnly || (item.stockQty <= item.lowStockThreshold)
            matchesCategory && matchesQuery && matchesLowStock
        }
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Inventory Management", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 20.sp)
                            Text("Stock Tracking & Low-Stock Alerts", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        if (onboardingRepository != null) {
                            IconButton(onClick = { showImportConfirm = true }) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add Popular Items", tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GreenPrimary.copy(alpha = 0.92f)
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = GreenPrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search by item name or barcode...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GreenPrimary) },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                // Stock View Toggles (All Items vs Low Stock Only)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !showLowStockOnly,
                        onClick = { showLowStockOnly = false },
                        label = { Text("All Items (${allItems.size})", fontWeight = if (!showLowStockOnly) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(10.dp)
                    )

                    FilterChip(
                        selected = showLowStockOnly,
                        onClick = { showLowStockOnly = true },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (lowStockCount > 0) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = if (showLowStockOnly) AlertRed else OrangeAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    "Low Stock Only ($lowStockCount)",
                                    fontWeight = if (showLowStockOnly) FontWeight.Bold else FontWeight.Normal,
                                    color = if (showLowStockOnly) AlertRed else TextPrimary
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AlertRedLight,
                            selectedLabelColor = AlertRed
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Category Strip
                val categoriesWithAll = listOf("All") + CATEGORIES
                ScrollableTabRow(
                    selectedTabIndex = categoriesWithAll.indexOf(selectedCategory).coerceAtLeast(0),
                    edgePadding = 16.dp,
                    divider = {},
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    categoriesWithAll.forEach { cat ->
                        Tab(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            text = { Text(cat, fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                // Item count & status notice
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredItems.size} items ${if (showLowStockOnly) "in low stock" else "found"}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (showLowStockOnly && filteredItems.isNotEmpty()) AlertRed else TextSecondary
                    )
                    Text(
                        text = "Lowest stock first",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                // Items List
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (showLowStockOnly) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = GreenLight,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "🎉 All Items Healthy!",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = GreenPrimary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "No items are currently at or below their low-stock alert limit.",
                                            fontSize = 13.sp,
                                            color = TextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedButton(onClick = { showLowStockOnly = false }) {
                                            Text("Show All Items")
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = if (searchQuery.isBlank()) "No items in catalog yet" else "No matching items",
                                    color = TextSecondary,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { showAddDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                                ) {
                                    Text("+ Add First Item")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            ItemRowCard(
                                item = item,
                                onRestock = { restockItem = item },
                                onTrainPhotos = { itemToTrainPhotos = item },
                                onDelete = { itemToDelete = item },
                                onEditAlertLimit = { itemToEditAlert = item }
                            )
                        }
                    }
                }
            }
        }

        // Add Item Dialog
        if (showAddDialog) {
            AddItemDialog(
                shopId = effectiveShopId,
                itemRepository = itemRepository,
                initialCategory = if (selectedCategory != "All") selectedCategory else CATEGORIES.first(),
                onDismiss = { showAddDialog = false },
                onItemAdded = { newItem: ItemEntity ->
                    showAddDialog = false
                    if (selectedCategory != "All" && !selectedCategory.equals(newItem.category, ignoreCase = true)) {
                        selectedCategory = "All"
                    }
                }
            )
        }

        // Restock Dialog
        if (restockItem != null) {
            RestockDialog(
                item = restockItem!!,
                itemRepository = itemRepository,
                onDismiss = { restockItem = null },
                onRestocked = { restockItem = null }
            )
        }

        // Set / Edit Low Stock Alert Limit Dialog
        if (itemToEditAlert != null) {
            SetLowStockAlertDialog(
                item = itemToEditAlert!!,
                itemRepository = itemRepository,
                onDismiss = { itemToEditAlert = null },
                onUpdated = {
                    itemToEditAlert = null
                }
            )
        }

        // Delete Item Confirmation Dialog
        if (itemToDelete != null) {
            val target = itemToDelete!!
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text("Delete \"${target.name}\"?") },
                text = {
                    Text("Are you sure you want to delete this item from your inventory? This will permanently remove it from your stock and visual catalog.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                itemRepository.deleteItem(target.id)
                                itemToDelete = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Import Popular Items Dialog
        if (showImportConfirm && onboardingRepository != null) {
            AlertDialog(
                onDismissRequest = { if (!isImporting) showImportConfirm = false },
                title = { Text("Add Popular Inventory Items?") },
                text = {
                    Text("Quickly add popular chocolates (Dairy Milk, KitKat, 5 Star), biscuits (Marie Gold, Hide & Seek, Bourbon), notebooks, pens, stationery, and grocery staples to your inventory.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isImporting = true
                            coroutineScope.launch {
                                try {
                                    onboardingRepository.importAllStarterItems(shopId)
                                } finally {
                                    isImporting = false
                                    showImportConfirm = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        enabled = !isImporting
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Adding Items...")
                        } else {
                            Text("Import Popular Items")
                        }
                    }
                },
                dismissButton = {
                    if (!isImporting) {
                        TextButton(onClick = { showImportConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ItemRowCard(
    item: ItemEntity,
    onRestock: () -> Unit,
    onTrainPhotos: () -> Unit = {},
    onDelete: () -> Unit,
    onEditAlertLimit: () -> Unit
) {
    val isOutOfStock = item.stockQty <= 0
    val isLowStock = !isOutOfStock && item.stockQty <= item.lowStockThreshold
    val unitLabel = if (item.unitType == "piece") "pcs" else "kg"
    val thresholdFormatted = if (item.lowStockThreshold % 1.0 == 0.0) item.lowStockThreshold.toInt().toString() else item.lowStockThreshold.toString()

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = Color.White.copy(alpha = 0.88f),
        elevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Item Details Left Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (!item.nameRegional.isNullOrBlank()) {
                            Text(
                                text = " (${item.nameRegional})",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "₹%.2f / %s".format(item.price, if (item.unitType == "piece") "pc" else "kg"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GreenPrimary
                        )
                        Text(
                            text = "• ${item.category}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Stock Badges & Low Stock Alert Limit
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Current Stock Status Badge
                        when {
                            isOutOfStock -> {
                                Surface(
                                    color = AlertRedLight,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "OUT OF STOCK",
                                        color = AlertRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            isLowStock -> {
                                Surface(
                                    color = AlertRedLight,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "⚠️ LOW: ${item.stockQty} $unitLabel",
                                        color = AlertRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            else -> {
                                Surface(
                                    color = GreenLight,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Stock: ${item.stockQty} $unitLabel",
                                        color = GreenPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Interactive Low-Stock Threshold Chip
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier.clickable { onEditAlertLimit() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNotifications,
                                    contentDescription = "Set Alert Limit",
                                    tint = OrangeAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Alert ≤ $thresholdFormatted $unitLabel",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                // Delete Button on Top-Right
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Item",
                        tint = AlertRed.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEditAlertLimit,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = OrangeAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Set Alert", fontSize = 11.sp, color = TextPrimary)
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onTrainPhotos,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = GreenDark
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Photos", fontSize = 11.sp, color = TextPrimary)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onRestock,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenLight, contentColor = GreenPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("+ Restock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
