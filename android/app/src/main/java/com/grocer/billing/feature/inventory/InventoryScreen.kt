package com.grocer.billing.feature.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.repository.ItemRepository
import com.grocer.billing.ui.theme.*

import com.grocer.billing.core.data.repository.OnboardingRepository
import androidx.compose.material.icons.filled.PlaylistAdd
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
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var restockItem by remember { mutableStateOf<ItemEntity?>(null) }
    var itemToTrainPhotos by remember { mutableStateOf<ItemEntity?>(null) }

    LaunchedEffect(itemToTrainPhotos) {
        itemToTrainPhotos?.let {
            onNavigateToTrainPhotos(it)
            itemToTrainPhotos = null
        }
    }

    // Observe all items sorted by lowest stock first
    val allItems by itemRepository.observeItems(shopId, lowestStockFirst = true)
        .collectAsState(initial = emptyList())

    val filteredItems = remember(allItems, searchQuery, selectedCategory) {
        allItems.filter { item ->
            val matchesCategory = (selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true))
            val matchesQuery = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    (item.nameRegional?.contains(searchQuery, ignoreCase = true) == true) ||
                    (item.barcode == searchQuery.trim())
            matchesCategory && matchesQuery
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
                            Text("Stock Tracking & Vision Training", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
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
                                Icon(Icons.Default.PlaylistAdd, contentDescription = "Add Popular Items", tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GreenPrimary.copy(alpha = 0.90f)
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = GreenPrimary,
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = { Text("Search by item name or barcode...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Category Strip
            val categoriesWithAll = listOf("All") + CATEGORIES
            ScrollableTabRow(
                selectedTabIndex = categoriesWithAll.indexOf(selectedCategory).coerceAtLeast(0),
                edgePadding = 16.dp,
                divider = {},
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                categoriesWithAll.forEach { cat ->
                    Tab(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        text = { Text(cat, fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            // Item count & sort notice
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredItems.size} items found",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Text(
                    text = "Sorted: lowest stock first",
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
                            onTrainPhotos = { itemToTrainPhotos = item }
                        )
                    }
            }
        }
    }
    }

    if (showAddDialog) {
        AddItemDialog(
            shopId = shopId,
            itemRepository = itemRepository,
            onDismiss = { showAddDialog = false },
            onItemAdded = { showAddDialog = false }
        )
    }

    if (restockItem != null) {
        RestockDialog(
            item = restockItem!!,
            itemRepository = itemRepository,
            onDismiss = { restockItem = null },
            onRestocked = { restockItem = null }
        )
    }

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
    onTrainPhotos: () -> Unit = {}
) {
    val isOutOfStock = item.stockQty <= 0
    val isLowStock = !isOutOfStock && item.stockQty <= item.lowStockThreshold
    val unitLabel = if (item.unitType == "piece") "pcs" else "kg"

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = Color.White.copy(alpha = 0.84f),
        elevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
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

                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "₹%.2f / %s".format(item.price, if (item.unitType == "piece") "pc" else "kg"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GreenPrimary
                    )
                    Text(
                        text = "• ${item.category}",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Stock Badge
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
                                text = "LOW STOCK: ${item.stockQty} $unitLabel left",
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
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onRestock,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenLight, contentColor = GreenPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("+ Restock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onTrainPhotos,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Photos", fontSize = 11.sp)
                }
            }
        }
    }
}
