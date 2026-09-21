package com.grocer.billing.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
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
import com.grocer.billing.core.data.repository.StarterCatalogItem
import com.grocer.billing.feature.inventory.AddItemDialog
import com.grocer.billing.feature.inventory.CATEGORIES
import com.grocer.billing.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingWizardScreen(
    shopId: String,
    onboardingRepository: OnboardingRepository,
    itemRepository: ItemRepository,
    onFinishSetup: () -> Unit,
    onSkipToDashboard: () -> Unit,
    onCapturePhotosForItem: (item: ItemEntity) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var starterItems by remember { mutableStateOf<List<StarterCatalogItem>>(emptyList()) }
    var configuredNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    var selectedItemToConfigure by remember { mutableStateOf<StarterCatalogItem?>(null) }
    var showCustomItemDialog by remember { mutableStateOf(false) }

    fun reloadCatalog() {
        coroutineScope.launch {
            starterItems = onboardingRepository.loadStarterCatalog()
            configuredNames = onboardingRepository.getAlreadyConfiguredNames(shopId)
        }
    }

    LaunchedEffect(Unit) {
        reloadCatalog()
    }

    val filteredItems = remember(starterItems, searchQuery, selectedCategory) {
        starterItems.filter { item ->
            val matchesCategory = (selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true))
            val matchesQuery = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    (item.name_regional?.contains(searchQuery, ignoreCase = true) == true)
            matchesCategory && matchesQuery
        }
    }

    val totalCount = starterItems.size.coerceAtLeast(1)
    val configuredCount = remember(starterItems, configuredNames) {
        starterItems.count { it.name.lowercase().trim() in configuredNames }
    }
    val progressFraction = (configuredCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Stock Setup", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary),
                actions = {
                    TextButton(
                        onClick = onSkipToDashboard,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("Skip for Now")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$configuredCount of $totalCount configured",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "You can add more anytime",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Button(
                        onClick = {
                            onboardingRepository.markOnboardingCompleted(true)
                            onFinishSetup()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Finish Setup & Start Billing", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundLight)
        ) {
            // Persistent Progress Bar & Info Banner
            Surface(
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$configuredCount of $totalCount items set up",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = GreenPrimary
                        )
                        Text(
                            text = "${(progressFraction * 100).toInt()}%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = GreenPrimary,
                        trackColor = Color(0xFFE0E0E0)
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                placeholder = { Text("Search common items (Atta, Rice, Maggi...)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Category Strip
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
                        text = { Text(cat, fontSize = 13.sp, fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            // If search has no results, offer Add Custom Item
            if (filteredItems.isEmpty() && searchQuery.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable { showCustomItemDialog = true },
                    colors = CardDefaults.cardColors(containerColor = GreenLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Item not found in starter list?",
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Add \"$searchQuery\" as a new custom item",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Grid of Starter Catalog Items
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    val isConfigured = item.name.lowercase().trim() in configuredNames

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedItemToConfigure = item
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isConfigured) Color(0xFFF1F8F3) else Color.White
                        ),
                        border = if (isConfigured) androidx.compose.foundation.BorderStroke(1.5.dp, GreenPrimary) else null,
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (isConfigured) GreenPrimary else Color(0xFFEEEEEE),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = item.category,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isConfigured) Color.White else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (isConfigured) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(GreenPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = item.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 2,
                                color = TextPrimary
                            )

                            if (!item.name_regional.isNullOrBlank()) {
                                Text(
                                    text = item.name_regional,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "₹%.0f / %s".format(item.suggested_price, if (item.unit_type == "piece") "pc" else "kg"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isConfigured) GreenPrimary else TextSecondary
                                )

                                Text(
                                    text = if (isConfigured) "Added ✓" else "+ Setup",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConfigured) GreenPrimary else Color(0xFF1976D2)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Configure dialog
    if (selectedItemToConfigure != null) {
        ConfigureStarterItemDialog(
            shopId = shopId,
            starterItem = selectedItemToConfigure!!,
            onboardingRepository = onboardingRepository,
            onDismiss = { selectedItemToConfigure = null },
            onItemConfigured = { createdEntity, openCamera ->
                selectedItemToConfigure = null
                reloadCatalog()
                if (openCamera) {
                    onCapturePhotosForItem(createdEntity)
                }
            }
        )
    }

    // Add Custom Item Dialog
    if (showCustomItemDialog) {
        AddItemDialog(
            shopId = shopId,
            itemRepository = itemRepository,
            onDismiss = { showCustomItemDialog = false },
            onItemAdded = {
                showCustomItemDialog = false
                reloadCatalog()
            }
        )
    }
}
