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
                // Multi-scale crops: tight foreground (0.45f), central reticle (0.65f), and wide (0.80f)
                val queryVectorReticle = embedder.extractEmbedding(croppedBitmap)
                val matchesReticle = vectorCache.match(queryVectorReticle, topK = 3)

                val tightSize = (minOf(finalBitmap.width, finalBitmap.height) * 0.45f).toInt()
                val tightStartX = ((finalBitmap.width - tightSize) / 2).coerceAtLeast(0)
                val tightStartY = ((finalBitmap.height - tightSize) / 2).coerceAtLeast(0)
                val safeTightW = tightSize.coerceAtMost(finalBitmap.width - tightStartX)
                val safeTightH = tightSize.coerceAtMost(finalBitmap.height - tightStartY)
                val tightBitmap = Bitmap.createBitmap(finalBitmap, tightStartX, tightStartY, safeTightW, safeTightH)
                val queryVectorTight = embedder.extractEmbedding(tightBitmap)
                val matchesTight = vectorCache.match(queryVectorTight, topK = 3)

                val wideSize = (minOf(finalBitmap.width, finalBitmap.height) * 0.80f).toInt()
                val wideStartX = ((finalBitmap.width - wideSize) / 2).coerceAtLeast(0)
                val wideStartY = ((finalBitmap.height - wideSize) / 2).coerceAtLeast(0)
                val safeWideW = wideSize.coerceAtMost(finalBitmap.width - wideStartX)
                val safeWideH = wideSize.coerceAtMost(finalBitmap.height - wideStartY)
                val wideBitmap = Bitmap.createBitmap(finalBitmap, wideStartX, wideStartY, safeWideW, safeWideH)
                val queryVectorWide = embedder.extractEmbedding(wideBitmap)
                val matchesWide = vectorCache.match(queryVectorWide, topK = 3)

                // Combine multi-crop candidates taking the highest confidence score per item
                val bestScoreMap = mutableMapOf<String, RecognitionMatch>()
                for (match in (matchesReticle + matchesTight + matchesWide)) {
                    val current = bestScoreMap[match.item.id]
                    if (current == null || match.confidence > current.confidence) {
                        bestScoreMap[match.item.id] = match
                    }
                }
                val visualMatches = bestScoreMap.values.sortedByDescending { it.confidence }.take(3)

                val combinedMatches = if (barcodeMatch != null) {
                    listOf(barcodeMatch) + visualMatches.filter { it.item.id != barcodeMatch.item.id }
                } else {
                    visualMatches
                }

                onCandidatesDetected(combinedMatches)

                if (croppedBitmap != finalBitmap) croppedBitmap.recycle()
                if (tightBitmap != finalBitmap) tightBitmap.recycle()
                if (wideBitmap != finalBitmap) wideBitmap.recycle()
                if (finalBitmap != rawBitmap) finalBitmap.recycle()
                rawBitmap.recycle()
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
