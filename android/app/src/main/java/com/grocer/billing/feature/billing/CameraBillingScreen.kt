package com.grocer.billing.feature.billing

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import com.grocer.billing.feature.inventory.CATEGORIES
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.repository.BillingRepository
import com.grocer.billing.core.data.repository.CartLine
import com.grocer.billing.core.data.repository.CompletedSale
import com.grocer.billing.core.data.repository.EmbeddingRepository
import com.grocer.billing.core.data.repository.ItemRepository
import com.grocer.billing.core.vision.CameraXAnalyzer
import com.grocer.billing.core.vision.ImageEmbedder
import com.grocer.billing.core.vision.RecognitionMatch
import com.grocer.billing.core.vision.VectorCache
import com.grocer.billing.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraBillingScreen(
    shopId: String,
    shopName: String,
    currencySymbol: String = "₹",
    embedder: ImageEmbedder,
    vectorCache: VectorCache,
    itemRepository: ItemRepository,
    billingRepository: BillingRepository,
    embeddingRepository: EmbeddingRepository,
    onNavigateBack: () -> Unit,
    onOpenManualPicker: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(shopId) {
        try {
            embeddingRepository.preloadAllVectorsToCache(shopId)
        } catch (_: Exception) {}
    }

    val allCatalogItems by itemRepository.observeItems(shopId, lowestStockFirst = false)
        .collectAsState(initial = emptyList())

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraControlRef by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }

    val cartLines = remember { mutableStateListOf<CartLine>() }
    var paymentMethod by remember { mutableStateOf("cash") }

    // Live Camera candidate recognition states
    var detectedCandidates by remember { mutableStateOf<List<RecognitionMatch>>(emptyList()) }
    var lastAddedItemId by remember { mutableStateOf<String?>(null) }
    var lastAddedTime by remember { mutableStateOf(0L) }

    // Non-blocking low-stock alert banner
    var lowStockBannerText by remember { mutableStateOf<String?>(null) }

    var weightItemToSelect by remember { mutableStateOf<ItemEntity?>(null) }
    var itemToDeleteFromCart by remember { mutableStateOf<CartLine?>(null) }
    var showClearBillConfirm by remember { mutableStateOf(false) }
    var completedSale by remember { mutableStateOf<CompletedSale?>(null) }
    var isSavingBill by remember { mutableStateOf(false) }

    var showManualAddDialog by remember { mutableStateOf(false) }
    var editingCartLine by remember { mutableStateOf<CartLine?>(null) }
    var showDiscardCartConfirm by remember { mutableStateOf(false) }
    var scanActionToast by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                if (isFlashOn) {
                    cameraControlRef?.enableTorch(false)
                    isFlashOn = false
                }
                cameraProviderRef?.unbindAll()
                cameraExecutor.shutdown()
            } catch (_: Throwable) {}
        }
    }

    BackHandler(enabled = cartLines.isNotEmpty()) {
        showDiscardCartConfirm = true
    }

    LaunchedEffect(scanActionToast) {
        if (scanActionToast != null) {
            delay(2500)
            scanActionToast = null
        }
    }

    // Haptic vibration helper
    fun triggerHaptic() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(70)
            }
        } catch (_: Exception) {}
    }

    // Function to add or increment item in cart
    fun addItemToCart(item: ItemEntity, quantityToAdd: Double = 1.0, overrideUnitPrice: Double? = null) {
        val existingIndex = cartLines.indexOfFirst { it.item.id == item.id }
        if (existingIndex >= 0) {
            val current = cartLines[existingIndex]
            val newQty = current.qty + quantityToAdd
            val newUnitPrice = overrideUnitPrice ?: current.unitPriceSnapshot
            cartLines[existingIndex] = current.copy(
                qty = newQty,
                unitPriceSnapshot = newUnitPrice
            )
        } else {
            cartLines.add(
                CartLine(
                    item = item,
                    qty = quantityToAdd,
                    unitPriceSnapshot = overrideUnitPrice ?: item.price
                )
            )
        }

        lastAddedItemId = item.id
        lastAddedTime = System.currentTimeMillis()
        triggerHaptic()

        // Check if item remaining stock is low
        val totalCartQty = cartLines.filter { it.item.id == item.id }.sumOf { it.qty }
        val remainingStock = item.stockQty - totalCartQty
        if (remainingStock <= item.lowStockThreshold) {
            lowStockBannerText = "${item.name} is running low (${if (item.unitType == "weight") "%.1f kg".format(remainingStock) else "${remainingStock.toInt()} pcs"} left)."
        }
    }

    // Handle incoming candidates from Camera analyzer without automatic adding
    fun onCandidatesFromCamera(matches: List<RecognitionMatch>) {
        detectedCandidates = matches
    }

    fun addRecognizedMatch(match: RecognitionMatch) {
        if (match.item.unitType == "weight") {
            weightItemToSelect = match.item
        } else {
            addItemToCart(match.item, 1.0)
            scanActionToast = "Added: ${match.item.name} ($currencySymbol%.2f)".format(match.item.price)
        }
    }

    fun onScanButtonClicked() {
        val topMatch = detectedCandidates.firstOrNull()
        if (topMatch != null) {
            addRecognizedMatch(topMatch)
        } else {
            scanActionToast = "Point reticle at grocery item or barcode"
        }
    }

    val runningTotal = remember(cartLines.map { it.lineTotal }) {
        cartLines.sumOf { it.lineTotal }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Point & Bill (Scan)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (cartLines.isNotEmpty()) {
                            showDiscardCartConfirm = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isFlashOn = !isFlashOn
                        cameraControlRef?.enableTorch(isFlashOn)
                    }) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = if (isFlashOn) "Turn Flash Off" else "Turn Flash On",
                            tint = if (isFlashOn) Color(0xFFFFEB3B) else Color.White
                        )
                    }
                    IconButton(onClick = { showManualAddDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Manual Search / Add", tint = Color.White)
                    }
                    if (cartLines.isNotEmpty()) {
                        IconButton(onClick = { showClearBillConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GreenPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Upper Portion: Camera Viewfinder with HUD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f)
                    .background(Color.Black)
            ) {
                if (hasCameraPermission) {
                    // CameraX Preview View
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                cameraProviderRef = cameraProvider

                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                                val analyzer = CameraXAnalyzer(
                                    embedder = embedder,
                                    vectorCache = vectorCache,
                                    itemRepository = itemRepository,
                                    scope = coroutineScope,
                                    onCandidatesDetected = { matches ->
                                        coroutineScope.launch {
                                            onCandidatesFromCamera(matches)
                                        }
                                    }
                                )

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setTargetResolution(android.util.Size(640, 480))
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also {
                                        it.setAnalyzer(cameraExecutor, analyzer)
                                    }

                                try {
                                    cameraProvider.unbindAll()
                                    val camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                    cameraControlRef = camera.cameraControl
                                    if (isFlashOn) {
                                        camera.cameraControl.enableTorch(true)
                                    }
                                } catch (_: Exception) {}
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Floating Flash / Torch Toggle Button on Viewfinder HUD
                    Surface(
                        shape = CircleShape,
                        color = if (isFlashOn) Color(0xFFF59E0B) else Color.Black.copy(alpha = 0.60f),
                        border = BorderStroke(1.5.dp, if (isFlashOn) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.5f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                            .size(46.dp)
                            .clickable {
                                isFlashOn = !isFlashOn
                                cameraControlRef?.enableTorch(isFlashOn)
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = if (isFlashOn) "Turn Flash Off" else "Turn Flash On",
                                tint = if (isFlashOn) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    val topCandidate = detectedCandidates.firstOrNull()

                    // Central Reticle Aiming Frame
                    Box(
                        modifier = Modifier
                            .size(210.dp)
                            .align(Alignment.Center)
                            .border(
                                2.dp,
                                if (topCandidate != null) GreenPrimary else Color.White.copy(alpha = 0.75f),
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = if (topCandidate != null) "✓ Recognized • Tap button to add" else "Point at product or barcode",
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Live Recognized Item Banner (Top HUD)
                    if (topCandidate != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(10.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { addRecognizedMatch(topCandidate) },
                            color = Color(0xEE1E293B),
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = GreenPrimary,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = topCandidate.item.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "$currencySymbol%.2f ${if (topCandidate.item.unitType == "weight") "/ kg" else "/ pc"} • ${(topCandidate.confidence * 100).toInt()}% match",
                                            color = GreenLight,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Button(
                                    onClick = { addRecognizedMatch(topCandidate) },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("+ Add", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Bottom Viewfinder Controls Bar (Tap to Scan & Manual Add)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onScanButtonClicked() },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (topCandidate != null) GreenPrimary else Color(0xFF334155)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (topCandidate != null) "Add ${topCandidate.item.name.take(10)}" else "Scan / Add Item",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = { showManualAddDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Manual", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Permission Prompt UI
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E293B))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = GreenPrimary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Camera Permission Required",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Camera access is needed to scan items and products for your bill.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Enable Camera", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Temporary scan action toast banner
                if (scanActionToast != null) {
                    Surface(
                        color = Color(0xEE1B5E20),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = scanActionToast!!,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // Non-blocking Low Stock Alert Banner (Does not steal focus)
                if (lowStockBannerText != null) {
                    Surface(
                        color = Color(0xFFD32F2F),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "⚠️ $lowStockBannerText",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { lowStockBannerText = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Lower Portion: Live Bill List Scrolling Below Viewfinder
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f),
                color = Color(0xFFF9FBF9)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Bill (${cartLines.size} items)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$currencySymbol%.2f".format(runningTotal),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenPrimary
                        )
                    }

                    // Live Cart items
                    if (cartLines.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Point camera at item and tap 'Scan / Add Item'",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { showManualAddDialog = true }) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("+ Add Item Manually")
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(cartLines, key = { it.item.id }) { line ->
                                val isOutOfStock = line.item.stockQty <= 0
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { editingCartLine = line }
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(line.item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            if (isOutOfStock) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(color = AlertRedLight, shape = RoundedCornerShape(4.dp)) {
                                                    Text("0 stock", color = AlertRed, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                                }
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${if (line.item.unitType == "weight") "%.3f kg".format(line.qty) else "%.0f pcs".format(line.qty)} @ $currencySymbol%.2f".format(line.unitPriceSnapshot),
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit quantity",
                                                tint = GreenPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }

                                    // Quick count increment/decrement
                                    IconButton(
                                        onClick = {
                                            val idx = cartLines.indexOf(line)
                                            if (line.item.unitType == "piece") {
                                                if (line.qty > 1) cartLines[idx] = line.copy(qty = line.qty - 1)
                                                else itemToDeleteFromCart = line
                                            } else {
                                                if (line.qty > 0.25) cartLines[idx] = line.copy(qty = line.qty - 0.25)
                                                else itemToDeleteFromCart = line
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
                                        modifier = Modifier.padding(horizontal = 2.dp)
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

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = "$currencySymbol%.2f".format(line.lineTotal),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = GreenPrimary
                                    )

                                    IconButton(
                                        onClick = { itemToDeleteFromCart = line },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Payment Method selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("cash" to "Cash", "upi" to "UPI QR", "credit" to "Khata").forEach { (method, label) ->
                            FilterChip(
                                selected = paymentMethod == method,
                                onClick = { paymentMethod = method },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

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
                                } finally {
                                    isSavingBill = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        enabled = cartLines.isNotEmpty() && !isSavingBill
                    ) {
                        Text(
                            text = if (isSavingBill) "Saving Bill..." else "Complete Sale • $currencySymbol%.2f".format(runningTotal),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Weight selection dialog
    if (weightItemToSelect != null) {
        WeightKeypadSheet(
            item = weightItemToSelect!!,
            onDismiss = { weightItemToSelect = null },
            onWeightConfirmed = { weight ->
                addItemToCart(weightItemToSelect!!, weight)
                weightItemToSelect = null
            }
        )
    }

    // Delete item confirmation
    if (itemToDeleteFromCart != null) {
        AlertDialog(
            onDismissRequest = { itemToDeleteFromCart = null },
            title = { Text("Remove from Bill?") },
            text = { Text("Remove ${itemToDeleteFromCart!!.item.name} from current bill?") },
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
                TextButton(onClick = { itemToDeleteFromCart = null }) { Text("Cancel") }
            }
        )
    }

    // Clear bill confirmation
    if (showClearBillConfirm) {
        AlertDialog(
            onDismissRequest = { showClearBillConfirm = false },
            title = { Text("Clear Bill?") },
            text = { Text("Remove all items from this customer's bill?") },
            confirmButton = {
                Button(
                    onClick = {
                        cartLines.clear()
                        showClearBillConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearBillConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Receipt Dialog
    if (completedSale != null) {
        AlertDialog(
            onDismissRequest = {
                cartLines.clear()
                completedSale = null
            },
            title = { Text("Sale Done! (${completedSale!!.bill.billNumber})") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = completedSale!!.receiptText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .background(Color(0xFFF0F4F1), RoundedCornerShape(8.dp))
                            .padding(10.dp)
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
                        context.startActivity(Intent.createChooser(shareIntent, "Share Receipt"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share WhatsApp")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    cartLines.clear()
                    completedSale = null
                }) {
                    Text("Next Customer")
                }
            }
        )
    }

    // Edit cart line dialog
    if (editingCartLine != null) {
        val lineToEdit = editingCartLine!!
        EditCartLineDialog(
            line = lineToEdit,
            currencySymbol = currencySymbol,
            onDismiss = { editingCartLine = null },
            onConfirm = { newQty, newPrice ->
                val idx = cartLines.indexOfFirst { it.item.id == lineToEdit.item.id }
                if (idx >= 0) {
                    cartLines[idx] = lineToEdit.copy(qty = newQty, unitPriceSnapshot = newPrice)
                }
                editingCartLine = null
            },
            onRemove = {
                cartLines.removeAll { it.item.id == lineToEdit.item.id }
                editingCartLine = null
            }
        )
    }

    // Manual item add dialog
    if (showManualAddDialog) {
        ManualAddItemDialog(
            shopId = shopId,
            catalogItems = allCatalogItems,
            currencySymbol = currencySymbol,
            onDismiss = { showManualAddDialog = false },
            onAddItem = { item, qty ->
                if (item.unitType == "weight") {
                    weightItemToSelect = item
                } else {
                    addItemToCart(item, qty)
                    scanActionToast = "Added: ${item.name} ($currencySymbol%.2f)".format(item.price)
                }
                showManualAddDialog = false
            }
        )
    }

    // Discard cart confirmation dialog on back press
    if (showDiscardCartConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardCartConfirm = false },
            title = { Text("Discard Current Bill?", fontWeight = FontWeight.Bold) },
            text = { Text("You have ${cartLines.size} items in this customer's bill. Are you sure you want to go back and clear this bill?") },
            confirmButton = {
                Button(
                    onClick = {
                        cartLines.clear()
                        showDiscardCartConfirm = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Discard & Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardCartConfirm = false }) {
                    Text("Stay on Bill")
                }
            }
        )
    }
}

@Composable
fun EditCartLineDialog(
    line: CartLine,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (newQty: Double, newUnitPrice: Double) -> Unit,
    onRemove: () -> Unit
) {
    var qtyText by remember { mutableStateOf(if (line.item.unitType == "weight") "%.3f".format(line.qty) else line.qty.toInt().toString()) }
    var priceText by remember { mutableStateOf("%.2f".format(line.unitPriceSnapshot)) }
    var inputError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Item: ${line.item.name}",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Unit: ${if (line.item.unitType == "weight") "Kilograms (kg)" else "Pieces (pcs)"}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it; inputError = null },
                    label = { Text("Quantity (${if (line.item.unitType == "weight") "kg" else "pcs"})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Quick quantity increment buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val stepOptions = if (line.item.unitType == "weight") listOf("+0.25", "+0.5", "+1.0") else listOf("+1", "+2", "+5")
                    stepOptions.forEach { stepStr ->
                        OutlinedButton(
                            onClick = {
                                val current = qtyText.toDoubleOrNull() ?: 1.0
                                val addVal = stepStr.replace("+", "").toDoubleOrNull() ?: 1.0
                                val res = current + addVal
                                qtyText = if (line.item.unitType == "weight") "%.3f".format(res) else res.toInt().toString()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stepStr, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it; inputError = null },
                    label = { Text("Unit Price ($currencySymbol)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                val calcQty = qtyText.toDoubleOrNull() ?: 0.0
                val calcPrice = priceText.toDoubleOrNull() ?: 0.0
                Text(
                    text = "Line Total: $currencySymbol%.2f".format(calcQty * calcPrice),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = GreenPrimary
                )

                if (inputError != null) {
                    Text(inputError!!, color = AlertRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedQty = qtyText.toDoubleOrNull()
                    val parsedPrice = priceText.toDoubleOrNull()
                    if (parsedQty == null || parsedQty <= 0) {
                        inputError = "Please enter a valid quantity > 0"
                        return@Button
                    }
                    if (parsedPrice == null || parsedPrice < 0) {
                        inputError = "Please enter a valid price"
                        return@Button
                    }
                    onConfirm(parsedQty, parsedPrice)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Text("Update", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) {
                    Text("Remove", color = AlertRed)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun ManualAddItemDialog(
    shopId: String,
    catalogItems: List<ItemEntity>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onAddItem: (ItemEntity, Double) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    var customName by remember { mutableStateOf("") }
    var customPrice by remember { mutableStateOf("") }
    var customQty by remember { mutableStateOf("1") }
    var customUnitType by remember { mutableStateOf("piece") }
    var customError by remember { mutableStateOf<String?>(null) }

    val filteredItems = remember(catalogItems, searchQuery, selectedCategory) {
        catalogItems.filter { item ->
            val matchesCat = (selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true))
            val matchesQuery = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    (item.nameRegional?.contains(searchQuery, ignoreCase = true) == true) ||
                    (item.barcode == searchQuery.trim())
            matchesCat && matchesQuery
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Add Item to Current Bill", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Catalog Items", fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("+ Custom Item", fontSize = 13.sp) }
                    )
                }
            }
        },
        text = {
            Box(modifier = Modifier.height(360.dp).fillMaxWidth()) {
                if (selectedTab == 0) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search catalog / barcode...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        )

                        val categoriesWithAll = listOf("All") + CATEGORIES
                        ScrollableTabRow(
                            selectedTabIndex = categoriesWithAll.indexOf(selectedCategory).coerceAtLeast(0),
                            edgePadding = 0.dp,
                            divider = {}
                        ) {
                            categoriesWithAll.forEach { cat ->
                                Tab(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    text = { Text(cat, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (filteredItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No items found in catalog.\nSwitch to '+ Custom Item' to add directly.",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredItems, key = { it.id }) { item ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onAddItem(item, 1.0) },
                                        color = Color(0xFFF1F5F2)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(
                                                    text = "$currencySymbol%.2f / ${if (item.unitType == "weight") "kg" else "pc"}",
                                                    color = GreenPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Button(
                                                onClick = { onAddItem(item, 1.0) },
                                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("+ Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Custom Quick Item Form
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Add a quick loose or custom grocery item directly to this bill.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it; customError = null },
                            label = { Text("Item Name") },
                            placeholder = { Text("e.g. Loose Sugar, Dhaniya, Egg") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = customUnitType == "piece",
                                onClick = { customUnitType = "piece" },
                                label = { Text("Piece (pc)") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = customUnitType == "weight",
                                onClick = { customUnitType = "weight" },
                                label = { Text("Weight (kg)") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = customPrice,
                            onValueChange = { customPrice = it; customError = null },
                            label = { Text("Price ($currencySymbol)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = customQty,
                            onValueChange = { customQty = it; customError = null },
                            label = { Text("Quantity (${if (customUnitType == "weight") "kg" else "pcs"})") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        if (customError != null) {
                            Text(customError!!, color = AlertRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                if (customName.isBlank()) {
                                    customError = "Please enter an item name"
                                    return@Button
                                }
                                val price = customPrice.toDoubleOrNull()
                                if (price == null || price < 0) {
                                    customError = "Please enter a valid price"
                                    return@Button
                                }
                                val qty = customQty.toDoubleOrNull()
                                if (qty == null || qty <= 0) {
                                    customError = "Please enter a valid quantity"
                                    return@Button
                                }

                                val customItem = ItemEntity(
                                    shopId = shopId,
                                    name = customName.trim(),
                                    price = price,
                                    unitType = customUnitType,
                                    category = "General",
                                    stockQty = 999.0
                                )
                                onAddItem(customItem, qty)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                        ) {
                            Text("Add to Current Bill", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

