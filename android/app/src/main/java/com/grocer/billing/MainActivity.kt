package com.grocer.billing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.grocer.billing.core.data.repository.BillingRepository
import com.grocer.billing.core.data.repository.ItemRepository
import com.grocer.billing.feature.account.AccountDashboardScreen
import com.grocer.billing.feature.auth.AuthScreen
import com.grocer.billing.feature.auth.PinUnlockScreen
import com.grocer.billing.feature.auth.StoreRegistrationScreen
import com.grocer.billing.feature.billing.ManualBillingScreen
import com.grocer.billing.feature.inventory.InventoryScreen
import com.grocer.billing.feature.settings.ShopSetupScreen
import com.grocer.billing.ui.theme.*

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as BillingApplication
        val authRepo = app.authRepository
        val shopRepo = app.shopRepository
        val itemRepo = app.itemRepository
        val billingRepo = app.billingRepository
        val onboardingRepo = app.onboardingRepository

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

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
                            onAuthSuccess = { isNewUser ->
                                val shop = activeShop
                                val isComplete = authRepo.hasCompletedStoreProfile(shop)
                                val dest = when {
                                    !isComplete -> "store_registration"
                                    !onboardingRepo.isOnboardingCompleted() -> "onboarding_wizard"
                                    else -> "dashboard"
                                }
                                navController.navigate(dest) {
                                    popUpTo("auth") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("store_registration") {
                        StoreRegistrationScreen(
                            authRepository = authRepo,
                            onRegistrationCompleted = {
                                val dest = if (!onboardingRepo.isOnboardingCompleted()) "onboarding_wizard" else "dashboard"
                                navController.navigate(dest) {
                                    popUpTo("store_registration") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("account_dashboard") {
                        AccountDashboardScreen(
                            authRepository = authRepo,
                            onNavigateBack = { navController.popBackStack() },
                            onLogout = {
                                navController.navigate("auth") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("pin_unlock") {
                        PinUnlockScreen(
                            authRepository = authRepo,
                            onUnlocked = {
                                val shop = activeShop
                                val isComplete = authRepo.hasCompletedStoreProfile(shop)
                                val dest = when {
                                    !isComplete -> "store_registration"
                                    !onboardingRepo.isOnboardingCompleted() -> "onboarding_wizard"
                                    else -> "dashboard"
                                }
                                navController.navigate(dest) {
                                    popUpTo("pin_unlock") { inclusive = true }
                                }
                            },
                            onLogout = {
                                authRepo.logout()
                                navController.navigate("auth") {
                                    popUpTo(0) { inclusive = true }
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
                        val monthSales by billingRepo.observeMonthSales(shopId).collectAsState(initial = 0.0)
                        val monthBillsCount by billingRepo.observeMonthBillsCount(shopId).collectAsState(initial = 0)
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
                            monthSales = monthSales,
                            monthBillsCount = monthBillsCount,
                            lowStockCount = lowStockItems.size,
                            showResumeSetup = !onboardingRepo.isOnboardingCompleted(),
                            onResumeSetupClicked = { navController.navigate("onboarding_wizard") },
                            onNewBillClicked = { navController.navigate("camera_billing") },
                            onInventoryClicked = { navController.navigate("inventory") },
                            onReportsClicked = { navController.navigate("reports") },
                            onPastBillsClicked = { navController.navigate("previous_bills") },
                            onAccountClicked = { navController.navigate("account_dashboard") },
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

@Composable
fun QuickActionDashboardCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    iconBgColor: Color,
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    LiquidGlassCard(
        modifier = modifier.height(98.dp),
        shape = RoundedCornerShape(18.dp),
        tint = Color.White.copy(alpha = 0.90f),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        elevation = 3.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                maxLines = 1
            )
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
    monthSales: Double = 0.0,
    monthBillsCount: Int = 0,
    lowStockCount: Int,
    showResumeSetup: Boolean = false,
    onResumeSetupClicked: () -> Unit = {},
    onNewBillClicked: () -> Unit,
    onInventoryClicked: () -> Unit,
    onReportsClicked: () -> Unit = {},
    onPastBillsClicked: () -> Unit = {},
    onAccountClicked: () -> Unit = {},
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
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GreenPrimary.copy(alpha = 0.92f)
                    ),
                    actions = {
                        IconButton(onClick = onAccountClicked) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "My Account", tint = Color.White)
                        }
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
                    tint = Color.White.copy(alpha = 0.86f),
                    elevation = 8.dp
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                        // Monthly sales highlight pill
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEEF2FF),
                            modifier = Modifier.clickable { onReportsClicked() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Month: $currencySymbol%.0f".format(monthSales),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4338CA)
                                )
                            }
                        }
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

                // Quick Actions Liquid Glass Grid (3-column with distinct, vibrant branded badges & visible operation labels)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Inventory: Fresh Kirana Emerald Badge
                    QuickActionDashboardCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Inventory2,
                        iconColor = Color(0xFF0F766E),
                        iconBgColor = Color(0xFFE8F5E9),
                        label = inventoryLabel,
                        subtitle = "Stock & Alert",
                        onClick = onInventoryClicked
                    )

                    // Reports: Royal Indigo Badge
                    QuickActionDashboardCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Assessment,
                        iconColor = Color(0xFF4338CA),
                        iconBgColor = Color(0xFFEEF2FF),
                        label = reportsLabel,
                        subtitle = "Analytics",
                        onClick = onReportsClicked
                    )

                    // Past Bills: Warm Amber Badge
                    QuickActionDashboardCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        iconColor = Color(0xFFC2410C),
                        iconBgColor = Color(0xFFFFF7ED),
                        label = pastBillsLabel,
                        subtitle = "History",
                        onClick = onPastBillsClicked
                    )
                }
            }
        }
    }
}
