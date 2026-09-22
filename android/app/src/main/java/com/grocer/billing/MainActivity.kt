package com.grocer.billing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.grocer.billing.core.data.repository.BillingRepository
import com.grocer.billing.core.data.repository.ItemRepository
import com.grocer.billing.feature.auth.AuthScreen
import com.grocer.billing.feature.auth.PinUnlockScreen
import com.grocer.billing.feature.billing.ManualBillingScreen
import com.grocer.billing.feature.inventory.InventoryScreen
import com.grocer.billing.feature.settings.ShopSetupScreen
import com.grocer.billing.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as BillingApplication
        val authRepo = app.authRepository
        val shopRepo = app.shopRepository
        val itemRepo = app.itemRepository
        val billingRepo = app.billingRepository
        val onboardingRepo = app.onboardingRepository

        setContent {
            KiranaBillingTheme {
                val navController = rememberNavController()
                val isUserLoggedIn = remember { authRepo.isUserLoggedIn() }
                val isAppLocked = remember { authRepo.isAppLocked() }
                val activeShop by authRepo.observeActiveShop().collectAsState(initial = null)
                val currentShopId = activeShop?.id ?: authRepo.getActiveShopId() ?: ""

                val startDestination = when {
                    !isUserLoggedIn -> "auth"
                    else -> "pin_unlock"
                }

                NavHost(navController = navController, startDestination = startDestination) {
                    composable("auth") {
                        AuthScreen(
                            authRepository = authRepo,
                            localBackupManager = app.localBackupManager,
                            retrofitClient = app.retrofitClient,
                            onAuthSuccess = {
                                val dest = if (activeShop == null) "shop_setup" else if (!onboardingRepo.isOnboardingCompleted()) "onboarding_wizard" else "dashboard"
                                navController.navigate(dest) {
                                    popUpTo("auth") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("pin_unlock") {
                        PinUnlockScreen(
                            authRepository = authRepo,
                            onUnlocked = {
                                val dest = if (!onboardingRepo.isOnboardingCompleted()) "onboarding_wizard" else "dashboard"
                                navController.navigate(dest) {
                                    popUpTo("pin_unlock") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("shop_setup") {
                        ShopSetupScreen(
                            shopRepository = shopRepo,
                            retrofitClient = app.retrofitClient,
                            onFinished = {
                                val dest = if (!onboardingRepo.isOnboardingCompleted()) "onboarding_wizard" else "dashboard"
                                navController.navigate(dest) {
                                    popUpTo("shop_setup") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("onboarding_wizard") {
                        val shop = activeShop
                        com.grocer.billing.feature.onboarding.OnboardingWizardScreen(
                            shopId = shop?.id ?: "",
                            onboardingRepository = onboardingRepo,
                            itemRepository = itemRepo,
                            onFinishSetup = {
                                navController.navigate("dashboard") {
                                    popUpTo("onboarding_wizard") { inclusive = true }
                                }
                            },
                            onSkipToDashboard = {
                                navController.navigate("dashboard") {
                                    popUpTo("onboarding_wizard") { inclusive = true }
                                }
                            },
                            onCapturePhotosForItem = { item ->
                                navController.navigate("guided_capture/${item.id}")
                            }
                        )
                    }

                    composable("dashboard") {
                        val shop = activeShop
                        val shopId = currentShopId.ifBlank { shop?.id ?: "" }
                        val todaySales by billingRepo.observeTodaySales(shopId).collectAsState(initial = 0.0)
                        val todayBillsCount by billingRepo.observeTodayBillsCount(shopId).collectAsState(initial = 0)
                        val lowStockItems by itemRepo.observeLowStockAlerts(shopId).collectAsState(initial = emptyList())

                        // Preload catalog into VectorCache
                        val items by itemRepo.observeItems(shopId).collectAsState(initial = emptyList())
                        LaunchedEffect(items, shopId) {
                            app.embeddingRepository.preloadAllVectorsToCache(shopId)
                        }

                        DashboardScreen(
                            appName = shop?.name ?: "Smart Bill",
                            currencySymbol = shop?.currencySymbol ?: "₹",
                            todaySales = todaySales,
                            todayBillsCount = todayBillsCount,
                            lowStockCount = lowStockItems.size,
                            showResumeSetup = !onboardingRepo.isOnboardingCompleted(),
                            onResumeSetupClicked = { navController.navigate("onboarding_wizard") },
                            onNewBillClicked = { navController.navigate("camera_billing") },
                            onInventoryClicked = { navController.navigate("inventory") },
                            onReportsClicked = { navController.navigate("reports") },
                            onPastBillsClicked = { navController.navigate("previous_bills") },
                            onSettingsClicked = { navController.navigate("shop_setup") },
                            onLockClicked = {
                                authRepo.lockApp()
                                navController.navigate("pin_unlock") {
                                    popUpTo("dashboard") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("camera_billing") {
                        val shop = activeShop
                        val shopId = currentShopId.ifBlank { shop?.id ?: "" }
                        com.grocer.billing.feature.billing.CameraBillingScreen(
                            shopId = shopId,
                            shopName = shop?.name ?: "Smart Bill",
                            currencySymbol = shop?.currencySymbol ?: "₹",
                            embedder = app.imageEmbedder,
                            vectorCache = app.vectorCache,
                            itemRepository = itemRepo,
                            billingRepository = billingRepo,
                            embeddingRepository = app.embeddingRepository,
                            onNavigateBack = { navController.popBackStack() },
                            onOpenManualPicker = { navController.navigate("billing") }
                        )
                    }

                    composable("inventory") {
                        val shop = activeShop
                        val shopId = currentShopId.ifBlank { shop?.id ?: "" }
                        InventoryScreen(
                            shopId = shopId,
                            itemRepository = itemRepo,
                            onboardingRepository = onboardingRepo,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToTrainPhotos = { item ->
                                navController.navigate("guided_capture/${item.id}")
                            }
                        )
                    }

                    composable("guided_capture/{itemId}") { backStackEntry ->
                        val itemId = backStackEntry.arguments?.getString("itemId") ?: return@composable
                        val item by produceState<com.grocer.billing.core.data.local.entities.ItemEntity?>(initialValue = null, itemId) {
                            value = itemRepo.getItemById(itemId)
                        }
                        item?.let { validItem ->
                            com.grocer.billing.feature.onboarding.GuidedCaptureScreen(
                                item = validItem,
                                qualityChecker = app.imageQualityChecker,
                                embeddingRepository = app.embeddingRepository,
                                onNavigateBack = { navController.popBackStack() },
                                onTrainingCompleted = { navController.popBackStack() }
                            )
                        }
                    }

                    composable("billing") {
                        val shop = activeShop
                        ManualBillingScreen(
                            shopId = shop?.id ?: "",
                            shopName = shop?.name ?: "Smart Bill",
                            currencySymbol = shop?.currencySymbol ?: "₹",
                            itemRepository = itemRepo,
                            billingRepository = billingRepo,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("reports") {
                        val shop = activeShop
                        com.grocer.billing.feature.reports.ReportsScreen(
                            shopId = shop?.id ?: "",
                            currencySymbol = shop?.currencySymbol ?: "₹",
                            reportsRepository = app.reportsRepository,
                            billingRepository = billingRepo,
                            itemRepository = itemRepo,
                            syncManager = app.syncManager,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToInventory = { navController.navigate("inventory") }
                        )
                    }

                    composable("previous_bills") {
                        val shop = activeShop
                        com.grocer.billing.feature.billing.PreviousBillsScreen(
                            shopId = shop?.id ?: "",
                            shopName = shop?.name ?: "Smart Bill",
                            currencySymbol = shop?.currencySymbol ?: "₹",
                            billingRepository = billingRepo,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    appName: String,
    currencySymbol: String,
    todaySales: Double,
    todayBillsCount: Int,
    lowStockCount: Int,
    showResumeSetup: Boolean = false,
    onResumeSetupClicked: () -> Unit = {},
    onNewBillClicked: () -> Unit,
    onInventoryClicked: () -> Unit,
    onReportsClicked: () -> Unit = {},
    onPastBillsClicked: () -> Unit = {},
    onSettingsClicked: () -> Unit,
    onLockClicked: () -> Unit
) {
    val todaySalesLabel = com.grocer.billing.core.lang.AppLanguageManager.getString("today_sales")
    val receiptsTodayLabel = com.grocer.billing.core.lang.AppLanguageManager.getString("receipts_today")
    val newBillLabel = com.grocer.billing.core.lang.AppLanguageManager.getString("new_bill")
    val inventoryLabel = com.grocer.billing.core.lang.AppLanguageManager.getString("inventory")
    val reportsLabel = com.grocer.billing.core.lang.AppLanguageManager.getString("reports")
    val pastBillsLabel = com.grocer.billing.core.lang.AppLanguageManager.getString("past_bills")
    val liveSyncLabel = com.grocer.billing.core.lang.AppLanguageManager.getString("live_sync")
    val tagline = com.grocer.billing.core.lang.AppLanguageManager.getString("app_tagline")

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = appName,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 20.sp
                            )
                            Text(
                                text = tagline,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GreenPrimary.copy(alpha = 0.90f)
                    ),
                    actions = {
                        IconButton(onClick = onLockClicked) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Counter", tint = Color.White)
                        }
                        IconButton(onClick = onSettingsClicked) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Resume Setup Glass Card if first-run setup was skipped
                if (showResumeSetup) {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tint = Color(0xFFFFFBEB).copy(alpha = 0.88f),
                        onClick = onResumeSetupClicked
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(OrangeAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = OrangeAccent)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Starter Catalog Setup (Incomplete)",
                                    fontWeight = FontWeight.Bold,
                                    color = OrangeAccent,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Tap to resume adding common grocery items",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                // Today's Sales - Liquid Glass Hero Card
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = Color.White.copy(alpha = 0.82f),
                    elevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = todaySalesLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$currencySymbol%.2f".format(todaySales),
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = GreenPrimary
                            )
                        }

                        LiquidGlassBadge(tint = GreenPrimary) {
                            Text(
                                text = liveSyncLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.8f))
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(GreenPrimary)
                        )
                        Text(
                            text = "$todayBillsCount $receiptsTodayLabel",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Low-Stock Alert Glass Card if any
                if (lowStockCount > 0) {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tint = AlertRedLight.copy(alpha = 0.90f),
                        onClick = onInventoryClicked
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "⚠️ $lowStockCount items low on stock",
                                    fontWeight = FontWeight.Bold,
                                    color = AlertRed,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Tap to review inventory & restock",
                                    fontSize = 13.sp,
                                    color = AlertRed.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                // Big Primary Action: Liquid Glass Pill Button
                LiquidGlassButton(
                    onClick = onNewBillClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = newBillLabel,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Quick Actions Liquid Glass Grid (3-column)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidGlassSecondaryButton(
                        onClick = onInventoryClicked,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Inventory,
                                contentDescription = null,
                                tint = GreenDark,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = inventoryLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    LiquidGlassSecondaryButton(
                        onClick = onReportsClicked,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = null,
                                tint = GreenDark,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = reportsLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    LiquidGlassSecondaryButton(
                        onClick = onPastBillsClicked,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = GreenDark,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = pastBillsLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
