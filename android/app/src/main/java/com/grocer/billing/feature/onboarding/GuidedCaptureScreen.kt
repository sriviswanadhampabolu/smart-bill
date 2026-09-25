package com.grocer.billing.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.repository.EmbeddingRepository
import com.grocer.billing.core.vision.ImageQualityChecker
import com.grocer.billing.core.vision.QualityResult
import com.grocer.billing.ui.theme.AlertRed
import com.grocer.billing.ui.theme.GreenLight
import com.grocer.billing.ui.theme.GreenPrimary
import com.grocer.billing.ui.theme.TextPrimary
import com.grocer.billing.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

val ANGLE_PROMPTS = listOf(
    "Show me the front label",
    "Turn it slightly to the left",
    "Turn it slightly to the right",
    "Show the top / lid",
    "Show the back or side",
    "Tilt slightly downwards"
)

@kotlin.OptIn(ExperimentalMaterial3Api::class, ExperimentalGetImage::class)
@Composable
fun GuidedCaptureScreen(
    item: ItemEntity,
    qualityChecker: ImageQualityChecker,
    embeddingRepository: EmbeddingRepository,
    onNavigateBack: () -> Unit,
    onTrainingCompleted: () -> Unit
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

    val capturedBitmaps = remember { mutableStateListOf<Bitmap>() }
    var currentStep by remember { mutableStateOf(0) }
    var liveQuality by remember {
        mutableStateOf(QualityResult(isValid = true, feedback = "Position item in frame", qualityScore = 1f, luminance = 0.5f, sharpnessScore = 150f))
    }
    var isSaving by remember { mutableStateOf(false) }
    var latestFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    DisposableEffect(cameraControl) {
        onDispose {
            try {
                cameraControl?.enableTorch(false)
            } catch (_: Exception) {}
        }
    }

    // CameraX Capture Use Case
    val imageCapture = remember { ImageCapture.Builder().build() }

    val currentPrompt = if (currentStep < ANGLE_PROMPTS.size) {
        ANGLE_PROMPTS[currentStep]
    } else {
        "All angles captured! Ready to train."
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Setup: ${item.name}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isFlashOn = !isFlashOn
                        try {
                            cameraControl?.enableTorch(isFlashOn)
                            imageCapture.flashMode = if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                        } catch (_: Exception) {}
                    }) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = if (isFlashOn) "Turn Flash Off" else "Turn Flash On",
                            tint = if (isFlashOn) Color(0xFFFFD600) else Color.White
                        )
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
                .background(Color(0xFF121212))
        ) {
            // Step Guide Header
            Surface(
                color = Color(0xFF1E1E1E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Angle ${capturedBitmaps.size + 1} of ${ANGLE_PROMPTS.size}",
                            color = GreenLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(capturedBitmaps.size * 100) / ANGLE_PROMPTS.size}% Complete",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "👉 $currentPrompt",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Camera Viewfinder with Live Quality Check
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            val cameraExecutor = Executors.newSingleThreadExecutor()

                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                                // Frame analyzer for live quality feedback (light + blur)
                                val qualityAnalyzer = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also {
                                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                                            val rawBmp = try {
                                                imageProxy.toBitmap()
                                            } catch (_: Exception) {
                                                null
                                            }
                                            val rotation = imageProxy.imageInfo.rotationDegrees
                                            imageProxy.close()

                                            if (rawBmp != null) {
                                                val finalBmp = if (rotation != 0) {
                                                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                                    Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
                                                } else {
                                                    rawBmp
                                                }
                                                val quality = qualityChecker.evaluateFrame(finalBmp)
                                                latestFrameBitmap = finalBmp
                                                coroutineScope.launch {
                                                    liveQuality = quality
                                                }
                                            }
                                        }
                                    }

                                try {
                                    cameraProvider.unbindAll()
                                    val camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageCapture,
                                        qualityAnalyzer
                                    )
                                    cameraControl = camera.cameraControl
                                    if (isFlashOn) {
                                        try {
                                            camera.cameraControl.enableTorch(true)
                                        } catch (_: Exception) {}
                                    }
                                } catch (_: Exception) {}
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E293B))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Camera,
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
                            text = "Camera access is needed to capture reference photos for item training.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp
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

                // Reticle Guide
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .align(Alignment.Center)
                        .border(2.dp, if (liveQuality.isValid) GreenPrimary else AlertRed, RoundedCornerShape(16.dp))
                )

                // Quality Badge Pill
                Surface(
                    color = if (liveQuality.isValid) GreenPrimary.copy(alpha = 0.9f) else AlertRed.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = if (liveQuality.isValid) "✓ ${liveQuality.feedback}" else "⚠️ ${liveQuality.feedback}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // Flash On/Off Pill Button Overlay
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isFlashOn) Color(0xFFFFD600) else Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            isFlashOn = !isFlashOn
                            try {
                                cameraControl?.enableTorch(isFlashOn)
                                imageCapture.flashMode = if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                            } catch (_: Exception) {}
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash Toggle",
                            tint = if (isFlashOn) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isFlashOn) "Flash ON" else "Flash OFF",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFlashOn) Color.Black else Color.White
                        )
                    }
                }
            }

            // Bottom Section: Thumbnails & Capture Button
            Surface(
                color = Color(0xFF1E1E1E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Thumbnail Carousel
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(ANGLE_PROMPTS) { index, _ ->
                            val bitmap = capturedBitmaps.getOrNull(index)
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (bitmap != null) Color.DarkGray else Color.Black)
                                    .border(
                                        2.dp,
                                        if (index == currentStep) GreenPrimary else Color.Gray,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = GreenLight,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Text("${index + 1}", color = Color.LightGray, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // Action Controls
                    if (capturedBitmaps.size < ANGLE_PROMPTS.size) {
                        Button(
                            onClick = {
                                val currentFrame = latestFrameBitmap
                                if (currentFrame != null) {
                                    val cropped = cropCenterReticle(currentFrame)
                                    capturedBitmaps.add(cropped)
                                    currentStep++
                                } else {
                                    val executor = ContextCompat.getMainExecutor(context)
                                    imageCapture.takePicture(
                                        executor,
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                                val rawBmp = try {
                                                    imageProxy.toBitmap()
                                                } catch (_: Exception) {
                                                    null
                                                }
                                                val rotation = imageProxy.imageInfo.rotationDegrees
                                                imageProxy.close()

                                                if (rawBmp != null) {
                                                    val finalBmp = if (rotation != 0) {
                                                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                                        Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
                                                    } else {
                                                        rawBmp
                                                    }
                                                    val cropped = cropCenterReticle(finalBmp)
                                                    capturedBitmaps.add(cropped)
                                                    currentStep++
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {}
                                        }
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            enabled = !isSaving
                        ) {
                            Icon(Icons.Default.Camera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Capture Angle ${capturedBitmaps.size + 1}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                isSaving = true
                                coroutineScope.launch {
                                    try {
                                        // Process each captured angle into an embedding
                                        capturedBitmaps.forEachIndexed { idx, bmp ->
                                            embeddingRepository.saveAnglePhoto(
                                                itemId = item.id,
                                                bitmap = bmp,
                                                qualityScore = 1.0f,
                                                source = "setup",
                                                isPrimaryThumbnail = (idx == 0)
                                            )
                                        }
                                        embeddingRepository.preloadAllVectorsToCache(item.shopId)
                                        onTrainingCompleted()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Training Catalog Vectors...", fontSize = 16.sp)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Finish & Train Recognition", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun cropCenterReticle(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val cropSize = (minOf(width, height) * 0.65f).toInt()
    val startX = ((width - cropSize) / 2).coerceAtLeast(0)
    val startY = ((height - cropSize) / 2).coerceAtLeast(0)
    val safeWidth = cropSize.coerceAtMost(width - startX)
    val safeHeight = cropSize.coerceAtMost(height - startY)
    return Bitmap.createBitmap(bitmap, startX, startY, safeWidth, safeHeight)
}
