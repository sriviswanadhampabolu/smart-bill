package com.grocer.billing.core.vision

import android.graphics.Bitmap
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

interface ImageEmbedder {
    val embeddingDimension: Int
    suspend fun extractEmbedding(bitmap: Bitmap): FloatArray
}

/**
 * Illumination-Invariant & Background-Resilient Feature Extractor.
 * Computes 512-D L2-normalized float embeddings resilient to:
 * - Varied backgrounds (table, hand, shelf, counter) via border sampling & saliency suppression
 * - Dynamic lighting (low light, bright light, glare, shadows) via Gray World color constancy and log-contrast tone mapping
 */
class MobileNetV3Embedder(
    override val embeddingDimension: Int = 512
) : ImageEmbedder {

    override suspend fun extractEmbedding(bitmap: Bitmap): FloatArray {
        val targetSize = 128
        val scaled = if (bitmap.width != targetSize || bitmap.height != targetSize) {
            Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
        } else {
            bitmap
        }

        val embedding = FloatArray(embeddingDimension)
        val pixels = IntArray(targetSize * targetSize)
        scaled.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)

        val half = targetSize / 2f
        val maxDist = sqrt((half * half + half * half).toDouble()).toFloat()

        val rArr = FloatArray(pixels.size)
        val gArr = FloatArray(pixels.size)
        val bArr = FloatArray(pixels.size)
        val lumArr = FloatArray(pixels.size)
        val satArr = FloatArray(pixels.size)
        val hueArr = FloatArray(pixels.size)
        val distNormArr = FloatArray(pixels.size)
        val quadArr = IntArray(pixels.size)
        val ringArr = IntArray(pixels.size)

        // 1. Unpack RGB, Hue, Saturation, Raw Luminance, and Geometry
        var borderRSum = 0f
        var borderGSum = 0f
        var borderBSum = 0f
        var borderCount = 0

        val borderMargin = 8 // Outer 8 pixels around frame perimeter represent background surface

        for (y in 0 until targetSize) {
            val dy = y - half
            val rowOff = y * targetSize
            val qY = if (y < half) 0 else 1
            val isBorderY = (y < borderMargin || y >= targetSize - borderMargin)

            for (x in 0 until targetSize) {
                val dx = x - half
                val idx = rowOff + x
                val pixel = pixels[idx]
                val qX = if (x < half) 0 else 1
                quadArr[idx] = qY * 2 + qX

                val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                val distNorm = (dist / maxDist).coerceIn(0f, 1f)
                distNormArr[idx] = distNorm
                ringArr[idx] = (distNorm * 4).toInt().coerceIn(0, 3)

                val r = (pixel shr 16 and 0xFF) / 255f
                val g = (pixel shr 8 and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f
                rArr[idx] = r
                gArr[idx] = g
                bArr[idx] = b

                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                lumArr[idx] = lum

                val maxC = maxOf(r, maxOf(g, b))
                val minC = minOf(r, minOf(g, b))
                val delta = maxC - minC
                val sat = if (maxC > 1e-4f) delta / maxC else 0f
                satArr[idx] = sat

                var hue = 0f
                if (delta > 1e-4f) {
                    hue = when {
                        maxC == r -> ((g - b) / delta) % 6f
                        maxC == g -> ((b - r) / delta) + 2f
                        else -> ((r - g) / delta) + 4f
                    }
                    hue *= 60f
                    if (hue < 0f) hue += 360f
                }
                hueArr[idx] = hue / 360f // Normalized to 0..1

                // Sample perimeter border to model the surrounding background surface
                if (isBorderY || x < borderMargin || x >= targetSize - borderMargin) {
                    borderRSum += r
                    borderGSum += g
                    borderBSum += b
                    borderCount++
                }
            }
        }

        val bgR = if (borderCount > 0) borderRSum / borderCount else 0.5f
        val bgG = if (borderCount > 0) borderGSum / borderCount else 0.5f
        val bgB = if (borderCount > 0) borderBSum / borderCount else 0.5f

        // 2. Compute Sobel Edge Magnitude & Gradient Orientation
        val gxArr = FloatArray(pixels.size)
        val gyArr = FloatArray(pixels.size)
        val magArr = FloatArray(pixels.size)
        val angleBinArr = IntArray(pixels.size)
        var totalMag = 0f
        val quadMagSum = FloatArray(4)

        for (y in 1 until targetSize - 1) {
            val rowPrev = (y - 1) * targetSize
            val rowCurr = y * targetSize
            val rowNext = (y + 1) * targetSize

            for (x in 1 until targetSize - 1) {
                val idx = rowCurr + x
                val gx = (lumArr[rowPrev + x + 1] + 2f * lumArr[rowCurr + x + 1] + lumArr[rowNext + x + 1]) -
                         (lumArr[rowPrev + x - 1] + 2f * lumArr[rowCurr + x - 1] + lumArr[rowNext + x - 1])
                val gy = (lumArr[rowNext + x - 1] + 2f * lumArr[rowNext + x] + lumArr[rowNext + x + 1]) -
                         (lumArr[rowPrev + x - 1] + 2f * lumArr[rowPrev + x] + lumArr[rowPrev + x + 1])

                val mag = sqrt((gx * gx + gy * gy).toDouble()).toFloat()
                var angle = atan2(gy.toDouble(), gx.toDouble()).toFloat()
                if (angle < 0f) angle += (2f * Math.PI.toFloat())
                val aBin = ((angle / (2f * Math.PI.toFloat())) * 15.99f).toInt().coerceIn(0, 15)

                gxArr[idx] = gx
                gyArr[idx] = gy
                magArr[idx] = mag
                angleBinArr[idx] = aBin
                totalMag += mag
                quadMagSum[quadArr[idx]] += mag
            }
        }

        // 3. Compute Adaptive Foreground Saliency & Background Suppression Weights
        val weightArr = FloatArray(pixels.size)
        var totalWeight = 0f
        var fgRSum = 0f
        var fgGSum = 0f
        var fgBSum = 0f

        for (i in pixels.indices) {
            val distNorm = distNormArr[i]
            // Center Sigmoid falloff: 1.0 within central 46% radius, drops sharply outside
            val wCenter = (1.0f / (1.0f + exp(9.0f * (distNorm - 0.46f)))).coerceIn(0.01f, 1.0f)

            // Background Color Dissimilarity
            val dr = rArr[i] - bgR
            val dg = gArr[i] - bgG
            val db = bArr[i] - bgB
            val distFromBg = sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()

            // If away from absolute center and matches background color, strongly suppress
            val wBg = if (distNorm > 0.28f) {
                (distFromBg / 0.16f).coerceIn(0.08f, 1.0f)
            } else {
                1.0f
            }

            // Saliency boost for packaging edges / text
            val wEdge = 1.0f + 2.0f * (magArr[i] / 0.08f).coerceIn(0f, 1.0f)

            val w = wCenter * wBg * wEdge
            weightArr[i] = w
            totalWeight += w

            fgRSum += rArr[i] * w
            fgGSum += gArr[i] * w
            fgBSum += bArr[i] * w
        }

        val invTotalWeight = if (totalWeight > 0f) 1f / totalWeight else 1f
        val meanFgR = (fgRSum * invTotalWeight).coerceAtLeast(0.06f)
        val meanFgG = (fgGSum * invTotalWeight).coerceAtLeast(0.06f)
        val meanFgB = (fgBSum * invTotalWeight).coerceAtLeast(0.06f)
        val grayWorldTarget = (meanFgR + meanFgG + meanFgB) / 3f

        // 4. Illumination Normalization (Gray World White Balance + Logarithmic Contrast Mapping)
        val rNorm = FloatArray(pixels.size)
        val gNorm = FloatArray(pixels.size)
        val bNorm = FloatArray(pixels.size)
        val lNorm = FloatArray(pixels.size)
        val hueWeight = FloatArray(pixels.size)

        val rGain = (grayWorldTarget / meanFgR).coerceIn(0.40f, 2.50f)
        val gGain = (grayWorldTarget / meanFgG).coerceIn(0.40f, 2.50f)
        val bGain = (grayWorldTarget / meanFgB).coerceIn(0.40f, 2.50f)

        for (i in pixels.indices) {
            val rCorrected = (rArr[i] * rGain).coerceIn(0f, 1f)
            val gCorrected = (gArr[i] * gGain).coerceIn(0f, 1f)
            val bCorrected = (bArr[i] * bGain).coerceIn(0f, 1f)

            // Logarithmic tone mapping for resilient low-light contrast without highlight blowouts
            val rawL = 0.299f * rCorrected + 0.587f * gCorrected + 0.114f * bCorrected
            val logL = (ln(1.0 + 8.0 * rawL) / ln(9.0)).toFloat().coerceIn(0f, 1f)
            val boost = if (rawL > 1e-4f) (logL / rawL).coerceIn(0.5f, 2.2f) else 1.0f

            rNorm[i] = (rCorrected * boost).coerceIn(0f, 1f)
            gNorm[i] = (gCorrected * boost).coerceIn(0f, 1f)
            bNorm[i] = (bCorrected * boost).coerceIn(0f, 1f)
            lNorm[i] = logL

            // Gated hue weight: suppress hue noise in dim / desaturated pixels
            hueWeight[i] = (satArr[i] / 0.14f).coerceIn(0f, 1f)
        }

        // -------------------------------------------------------------
        // Section 1: Foreground Color & Chromatic Distribution (128 dims: 0..127)
        // -------------------------------------------------------------
        for (i in pixels.indices) {
            val w = weightArr[i] * invTotalWeight
            val rBin = (rNorm[i] * 15.99f).toInt().coerceIn(0, 15)
            val gBin = (gNorm[i] * 15.99f).toInt().coerceIn(0, 15)
            val bBin = (bNorm[i] * 15.99f).toInt().coerceIn(0, 15)
            val hBin = (hueArr[i] * 31.99f).toInt().coerceIn(0, 31)
            val sBin = (satArr[i] * 15.99f).toInt().coerceIn(0, 15)
            val lBin = (lNorm[i] * 15.99f).toInt().coerceIn(0, 15)

            embedding[0 + rBin] += w
            embedding[16 + gBin] += w
            embedding[32 + bBin] += w
            // Hue channel weighted by saturation to neutralize low-light chromatic noise
            embedding[48 + hBin] += w * hueWeight[i]
            embedding[80 + sBin] += w
            embedding[96 + lBin] += w
        }

        // Global illumination-invariant moments (RG opponent & YB opponent)
        var meanR = 0f; var meanG = 0f; var meanB = 0f
        var meanH = 0f; var meanS = 0f; var meanL = 0f
        for (i in pixels.indices) {
            val w = weightArr[i] * invTotalWeight
            meanR += rNorm[i] * w
            meanG += gNorm[i] * w
            meanB += bNorm[i] * w
            meanH += hueArr[i] * w * hueWeight[i]
            meanS += satArr[i] * w
            meanL += lNorm[i] * w
        }
        var varR = 0f; var varG = 0f; var varB = 0f; var varL = 0f
        for (i in pixels.indices) {
            val w = weightArr[i] * invTotalWeight
            val dr = rNorm[i] - meanR; varR += dr * dr * w
            val dg = gNorm[i] - meanG; varG += dg * dg * w
            val db = bNorm[i] - meanB; varB += db * db * w
            val dl = lNorm[i] - meanL; varL += dl * dl * w
        }

        embedding[112] = meanR
        embedding[113] = meanG
        embedding[114] = meanB
        embedding[115] = meanH
        embedding[116] = meanS
        embedding[117] = meanL
        embedding[118] = sqrt(varR.toDouble()).toFloat()
        embedding[119] = sqrt(varG.toDouble()).toFloat()
        embedding[120] = sqrt(varB.toDouble()).toFloat()
        embedding[121] = sqrt(varL.toDouble()).toFloat()
        embedding[122] = (meanR - meanG + 1f) * 0.5f // RG chromatic opponent (invariant to brightness)
        embedding[123] = (meanR + meanG - 2f * meanB + 2f) * 0.25f // YB opponent
        embedding[124] = meanS * (1f - meanL) // Vividness contrast
        embedding[125] = if (varL > 1e-4f) varR / varL else 0f
        embedding[126] = if (varL > 1e-4f) varB / varL else 0f
        embedding[127] = maxOf(0f, meanL - meanS)

        // -------------------------------------------------------------
        // Section 2: Concentric Circular Ring Features (128 dims: 128..255)
        // Rotation-invariant foreground layout
        // -------------------------------------------------------------
        val ringWeights = FloatArray(4)
        for (i in pixels.indices) {
            ringWeights[ringArr[i]] += weightArr[i]
        }

        for (i in pixels.indices) {
            val ring = ringArr[i]
            val rw = if (ringWeights[ring] > 0f) weightArr[i] / ringWeights[ring] else 0f
            val base = 128 + ring * 32

            val rBin = (rNorm[i] * 7.99f).toInt().coerceIn(0, 7)
            val gBin = (gNorm[i] * 7.99f).toInt().coerceIn(0, 7)
            val bBin = (bNorm[i] * 7.99f).toInt().coerceIn(0, 7)
            val hBin = (hueArr[i] * 7.99f).toInt().coerceIn(0, 7)

            embedding[base + 0 + rBin] += rw
            embedding[base + 8 + gBin] += rw
            embedding[base + 16 + bBin] += rw
            embedding[base + 24 + hBin] += rw * hueWeight[i]
        }

        // -------------------------------------------------------------
        // Section 3: Sobel Edge & Gradient Orientation (128 dims: 256..383)
        // High-frequency branding, packaging text, geometric contours
        // Inherently invariant to illumination and background
        // -------------------------------------------------------------
        val totalEdgePixels = (targetSize - 2) * (targetSize - 2)
        val avgMag = totalMag / totalEdgePixels.coerceAtLeast(1)
        val adaptiveEdgeThreshold = (0.45f * avgMag).coerceIn(0.006f, 0.035f)
        val invTotalMag = if (totalMag > 0f) 1f / totalMag else 1f

        for (y in 1 until targetSize - 1) {
            val rowOff = y * targetSize
            for (x in 1 until targetSize - 1) {
                val idx = rowOff + x
                val mag = magArr[idx]
                if (mag > adaptiveEdgeThreshold) {
                    val aBin = angleBinArr[idx]
                    val mBin = (mag * 3.99f).toInt().coerceIn(0, 15)
                    val effectiveMag = mag * weightArr[idx] * invTotalMag

                    embedding[256 + aBin] += effectiveMag
                    embedding[272 + mBin] += effectiveMag

                    val quad = quadArr[idx]
                    val qInv = if (quadMagSum[quad] > 0f) 1f / quadMagSum[quad] else 0f
                    val qBase = 288 + quad * 24
                    val qAngleBin = (aBin % 16).coerceIn(0, 15)
                    val qMagBin = (mBin % 8).coerceIn(0, 7)
                    embedding[qBase + qAngleBin] += mag * qInv
                    embedding[qBase + 16 + qMagBin] += mag * qInv
                }
            }
        }

        // -------------------------------------------------------------
        // Section 4: Foreground Spatial Macro-Quadrants (128 dims: 384..511)
        // -------------------------------------------------------------
        val quadWeights = FloatArray(4)
        for (i in pixels.indices) quadWeights[quadArr[i]] += weightArr[i]

        for (i in pixels.indices) {
            val quad = quadArr[i]
            val qw = if (quadWeights[quad] > 0f) weightArr[i] / quadWeights[quad] else 0f
            val base = 384 + quad * 32

            val rBin = (rNorm[i] * 7.99f).toInt().coerceIn(0, 7)
            val gBin = (gNorm[i] * 7.99f).toInt().coerceIn(0, 7)
            val bBin = (bNorm[i] * 7.99f).toInt().coerceIn(0, 7)
            val hBin = (hueArr[i] * 7.99f).toInt().coerceIn(0, 7)

            embedding[base + 0 + rBin] += qw
            embedding[base + 8 + gBin] += qw
            embedding[base + 16 + bBin] += qw
            embedding[base + 24 + hBin] += qw * hueWeight[i]
        }

        // L2 Normalization (so dot product equals cosine similarity)
        var sumSquares = 0f
        for (v in embedding) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares.toDouble()).toFloat().coerceAtLeast(1e-6f)
        for (i in embedding.indices) {
            embedding[i] /= norm
        }

        return embedding
    }
}
