package com.grocer.billing.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grocer.billing.core.data.repository.BillingRepository
import com.grocer.billing.core.data.repository.ExecutiveReport
import com.grocer.billing.core.data.repository.ItemRepository
import com.grocer.billing.core.data.repository.ReportsRepository
import com.grocer.billing.core.data.sync.SyncManager
import com.grocer.billing.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    shopId: String,
    currencySymbol: String = "₹",
    reportsRepository: ReportsRepository,
    billingRepository: BillingRepository,
    itemRepository: ItemRepository,
    syncManager: SyncManager,
    onNavigateBack: () -> Unit,
    onNavigateToInventory: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var report by remember { mutableStateOf<ExecutiveReport?>(null) }
    var pendingSyncCount by remember { mutableStateOf(0) }
    var isSyncing by remember { mutableStateOf(false) }

    val todaySales by billingRepository.observeTodaySales(shopId).collectAsState(initial = 0.0)
    val todayBillsCount by billingRepository.observeTodayBillsCount(shopId).collectAsState(initial = 0)
    val monthSales by billingRepository.observeMonthSales(shopId).collectAsState(initial = 0.0)
    val monthBillsCount by billingRepository.observeMonthBillsCount(shopId).collectAsState(initial = 0)

    val currentMonthName = remember {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    }

    fun refreshData() {
        coroutineScope.launch {
            report = reportsRepository.generateExecutiveReport(shopId)
            pendingSyncCount = syncManager.getPendingCount()
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Business Reports", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 20.sp)
                            Text("Executive Analytics & Monthly Sales", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                isSyncing = true
                                coroutineScope.launch {
                                    syncManager.flushPendingQueue(shopId)
                                    pendingSyncCount = syncManager.getPendingCount()
                                    isSyncing = false
                                }
                            },
                            enabled = !isSyncing
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary.copy(alpha = 0.92f))
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cloud Sync Status Pill
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = if (pendingSyncCount == 0) GlassGreenSurface else Color(0xFFFFF3E0).copy(alpha = 0.85f),
                    elevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (pendingSyncCount == 0) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = if (pendingSyncCount == 0) GreenPrimary else OrangeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (pendingSyncCount == 0) "Cloud Backup: Up to date" else "$pendingSyncCount bills waiting for sync",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (pendingSyncCount == 0) GreenPrimary else OrangeAccent
                            )
                        }

                        if (pendingSyncCount > 0) {
                            Text(
                                text = "Auto-syncs online",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Monthly Sales Highlight Hero Card
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = Color.White.copy(alpha = 0.92f),
                    elevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEEF2FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = Color(0xFF4338CA),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "MONTHLY SALES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = currentMonthName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4338CA)
                                )
                            }
                        }

                        LiquidGlassBadge(tint = Color(0xFF4338CA)) {
                            Text(
                                text = "$monthBillsCount Bills",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4338CA)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "$currencySymbol%.2f".format(monthSales),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E1B4B)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val avgBill = if (monthBillsCount > 0) monthSales / monthBillsCount else 0.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Avg Bill: $currencySymbol%.2f".format(avgBill),
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "•",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Today: $currencySymbol%.2f ($todayBillsCount bills)".format(todaySales),
                            fontSize = 12.sp,
                            color = GreenPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 4 Executive KPI Metric Cards (2x2 Grid)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card 1: Today's Revenue
                    LiquidGlassCard(
                        modifier = Modifier.weight(1f),
                        tint = Color.White.copy(alpha = 0.84f),
                        elevation = 4.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GreenLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("TODAY'S SALES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("$currencySymbol%.2f".format(todaySales), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = GreenPrimary)
                        Text("$todayBillsCount receipts", fontSize = 12.sp, color = TextSecondary)
                    }

                    // Card 2: Stock Valuation
                    val valuation = report?.totalStockValuation ?: 0.0
                    LiquidGlassCard(
                        modifier = Modifier.weight(1f),
                        tint = Color.White.copy(alpha = 0.84f),
                        elevation = 4.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("STOCK WORTH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("$currencySymbol%.0f".format(valuation), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                        Text("Total inventory", fontSize = 12.sp, color = TextSecondary)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card 3: Out of stock
                    val outCount = report?.outOfStockCount ?: 0
                    LiquidGlassCard(
                        modifier = Modifier.weight(1f),
                        tint = if (outCount > 0) AlertRedLight.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.84f),
                        elevation = 4.dp
                    ) {
                        Text("OUT OF STOCK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (outCount > 0) AlertRed else TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("$outCount items", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = if (outCount > 0) AlertRed else TextPrimary)
                        Text(if (outCount > 0) "Needs restocking" else "Inventory healthy", fontSize = 12.sp, color = TextSecondary)
                    }

                    // Card 4: Low stock count
                    val lowCount = report?.lowStockCount ?: 0
                    LiquidGlassCard(
                        modifier = Modifier.weight(1f),
                        tint = Color.White.copy(alpha = 0.84f),
                        elevation = 4.dp
                    ) {
                        Text("LOW STOCK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("$lowCount items", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = OrangeAccent)
                        Text("At/below alert limit", fontSize = 12.sp, color = TextSecondary)
                    }
                }

                // 7-Day Visual Bar Chart with Real Revenue Data
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = Color.White.copy(alpha = 0.88f),
                    elevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(GreenLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("7-Day Sales Trend", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val dailyTrend = report?.dailyTrend ?: emptyList()
                    val maxVal = (dailyTrend.maxOfOrNull { it.amount } ?: 100.0).coerceAtLeast(100.0)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        dailyTrend.forEachIndexed { i, point ->
                            val heightFraction = if (maxVal > 0) ((point.amount / maxVal).toFloat()).coerceIn(0.12f, 1f) else 0.12f
                            val isToday = (i == dailyTrend.size - 1)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (point.amount > 0) {
                                    Text(
                                        text = "$currencySymbol%.0f".format(point.amount),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isToday) GreenPrimary else TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .fillMaxHeight(heightFraction)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(if (isToday) GreenPrimary else Color(0xFFCFD8DC))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = point.dayLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isToday) GreenPrimary else TextSecondary
                                )
                            }
                        }
                    }
                }

                // Top Selling Items Leaderboard
                // Dual-Mode Performance Reports (Price/Revenue vs Quantity vs Categories)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    var reportTab by remember { mutableStateOf(0) } // 0: By Revenue, 1: By Quantity, 2: By Category

                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sales Performance Breakdown",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                        }

                        // 3-Way Pill Switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Tab 0: Revenue based
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (reportTab == 0) GreenPrimary else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { reportTab = 0 }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "By Revenue",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (reportTab == 0) Color.White else TextSecondary
                                    )
                                }
                            }

                            // Tab 1: Quantity based
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (reportTab == 1) Color(0xFF4338CA) else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { reportTab = 1 }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "By Quantity",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (reportTab == 1) Color.White else TextSecondary
                                    )
                                }
                            }

                            // Tab 2: Category volume
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (reportTab == 2) Color(0xFF0F766E) else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { reportTab = 2 }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Categories",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (reportTab == 2) Color.White else TextSecondary
                                    )
                                }
                            }
                        }

                        when (reportTab) {
                            0 -> {
                                // 1. By Revenue (Highest price / sales value products)
                                val items = report?.topSellingItemsByRevenue ?: emptyList()
                                if (items.isEmpty()) {
                                    Text("No bills recorded yet. Top revenue products will appear here.", color = TextSecondary, fontSize = 13.sp)
                                } else {
                                    Text("Ranked by highest sales value (Price × Qty):", fontSize = 12.sp, color = TextSecondary)
                                    items.forEachIndexed { index, item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = when (index) {
                                                        0 -> Color(0xFFFFD700)
                                                        1 -> Color(0xFFCFD8DC)
                                                        2 -> Color(0xFFD7CCC8)
                                                        else -> Color(0xFFECEFF1)
                                                    },
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("${index + 1}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                    Text(
                                                        if (item.unitType == "weight") "%.2f kg sold".format(item.totalQty) else "${item.totalQty.toInt()} pcs sold",
                                                        fontSize = 12.sp,
                                                        color = TextSecondary
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "$currencySymbol%.2f".format(item.totalRevenue),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp,
                                                color = GreenPrimary
                                            )
                                        }
                                        if (index < items.size - 1) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp), color = Color(0xFFF1F5F9))
                                        }
                                    }
                                }
                            }
                            1 -> {
                                // 2. By Quantity (Volume based, regardless of price)
                                val items = report?.topSellingItemsByQuantity ?: emptyList()
                                if (items.isEmpty()) {
                                    Text("No bills recorded yet. Highest quantity products will appear here.", color = TextSecondary, fontSize = 13.sp)
                                } else {
                                    Text("Ranked by highest units/volume sold (Quantity based):", fontSize = 12.sp, color = TextSecondary)
                                    items.forEachIndexed { index, item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = Color(0xFFEEF2FF),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("${index + 1}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF4338CA))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                    Text("Revenue: $currencySymbol%.2f".format(item.totalRevenue), fontSize = 12.sp, color = TextSecondary)
                                                }
                                            }

                                            Surface(
                                                color = Color(0xFFEEF2FF),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = if (item.unitType == "weight") "%.2f kg".format(item.totalQty) else "${item.totalQty.toInt()} pcs",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF4338CA),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        if (index < items.size - 1) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp), color = Color(0xFFF1F5F9))
                                        }
                                    }
                                }
                            }
                            2 -> {
                                // 3. Top Selling Categories (Quantity based)
                                val cats = report?.topSellingCategoriesByQuantity ?: emptyList()
                                if (cats.isEmpty()) {
                                    Text("No category sales recorded yet.", color = TextSecondary, fontSize = 13.sp)
                                } else {
                                    Text("Product categories ranked by total quantity sold:", fontSize = 12.sp, color = TextSecondary)
                                    cats.forEachIndexed { index, cat ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = Color(0xFFE8F5E9),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("${index + 1}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F766E))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(cat.category, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                    Text("${cat.itemCount} products · $currencySymbol%.2f".format(cat.totalRevenue), fontSize = 12.sp, color = TextSecondary)
                                                }
                                            }

                                            Surface(
                                                color = Color(0xFFE0F2F1),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "%.1f units".format(cat.totalQty),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF0F766E),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        if (index < cats.size - 1) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp), color = Color(0xFFF1F5F9))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Action: View All Inventory
                LiquidGlassSecondaryButton(
                    onClick = onNavigateToInventory,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Manage Full Inventory & Restock", fontWeight = FontWeight.Bold, color = GreenPrimary)
                }
            }
        }
    }
}
