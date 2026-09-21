package com.grocer.billing.core.vision

import android.graphics.Bitmap
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.grocer.billing.core.data.repository.ItemRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CameraXAnalyzer(
    private val embedder: ImageEmbedder,
    private val vectorCache: VectorCache,
    private val itemRepository: ItemRepository? = null,
    private val scope: CoroutineScope,
    private val onCandidatesDetected: (matches: List<RecognitionMatch>) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalysisTimestamp = 0L
    private val minAnalysisIntervalMs = 250L // ~4 FPS for snappy responsiveness
    private var isBusy = false
    private val barcodeReader = MultiFormatReader()

    @kotlin.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalysisTimestamp < minAnalysisIntervalMs || isBusy) {
            imageProxy.close()
            return
        }

        val rawBitmap = try {
            imageProxy.toBitmap()
        } catch (_: Throwable) {
            null
        }
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()

        if (rawBitmap == null) {
            isBusy = false
            return
        }

        lastAnalysisTimestamp = currentTimestamp
        isBusy = true

        scope.launch(Dispatchers.Default) {
            try {
                val finalBitmap = if (rotationDegrees != 0) {
                    val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                } else {
                    rawBitmap
                }

                // 1. Crop to the central reticle area where the item is positioned (0.65f)
                val cropSize = (minOf(finalBitmap.width, finalBitmap.height) * 0.65f).toInt()
                val startX = ((finalBitmap.width - cropSize) / 2).coerceAtLeast(0)
                val startY = ((finalBitmap.height - cropSize) / 2).coerceAtLeast(0)
                val safeW = cropSize.coerceAtMost(finalBitmap.width - startX)
                val safeH = cropSize.coerceAtMost(finalBitmap.height - startY)
                val croppedBitmap = Bitmap.createBitmap(finalBitmap, startX, startY, safeW, safeH)

                // 2. Fast Barcode Scanning check (instant 100% confidence for packaged goods)
                var barcodeMatch: RecognitionMatch? = null
                val detectedBarcode = scanBarcode(croppedBitmap) ?: scanBarcode(finalBitmap)
                if (detectedBarcode != null && itemRepository != null) {
                    val item = itemRepository.getItemByBarcode(detectedBarcode)
                    if (item != null) {
                        barcodeMatch = RecognitionMatch(
                            item = item,
                            confidence = 1.0f,
                            isHighConfidence = true
                        )
                    }
                }

                // 3. Visual Embedding Extraction for trained products & unpackaged goods
                val queryVector = embedder.extractEmbedding(croppedBitmap)
                val visualMatches = vectorCache.match(queryVector, topK = 3)

                val combinedMatches = if (barcodeMatch != null) {
                    listOf(barcodeMatch) + visualMatches.filter { it.item.id != barcodeMatch.item.id }
                } else {
                    visualMatches
                }

                if (combinedMatches.isNotEmpty()) {
                    onCandidatesDetected(combinedMatches)
                }
            } catch (_: Throwable) {
                // Ignore transient frame conversion glitches or memory limits
            } finally {
                isBusy = false
            }
        }
    }

    private fun scanBarcode(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = barcodeReader.decodeWithState(binaryBitmap)
            result.text
        } catch (_: Exception) {
            null
        } finally {
            barcodeReader.reset()
        }
    }
}
