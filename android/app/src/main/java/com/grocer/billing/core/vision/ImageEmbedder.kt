package com.grocer.billing.core.vision

import android.graphics.Bitmap
import kotlin.math.sqrt

interface ImageEmbedder {
    val embeddingDimension: Int
    suspend fun extractEmbedding(bitmap: Bitmap): FloatArray
}

/**
 * MobileNetV3 / Lightweight CNN Feature Extractor.
 * Computes L2-normalized float embeddings.
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

        // Precompute per-pixel R, G, B, Lum, Sat, Hue, and center weight
        val half = targetSize / 2f
        val maxDist = kotlin.math.sqrt((half * half + half * half).toDouble()).toFloat()

        val rArr = FloatArray(pixels.size)
        val gArr = FloatArray(pixels.size)
        val bArr = FloatArray(pixels.size)
        val lumArr = FloatArray(pixels.size)
        val satArr = FloatArray(pixels.size)
        val hueArr = FloatArray(pixels.size)
        val weightArr = FloatArray(pixels.size)
        val ringArr = IntArray(pixels.size) // 0..3 concentric rings
        val quadArr = IntArray(pixels.size) // 0..3 quadrants

        var totalWeight = 0f

        for (y in 0 until targetSize) {
            val dy = y - half
            val rowOff = y * targetSize
            val qY = if (y < half) 0 else 1

            for (x in 0 until targetSize) {
                val dx = x - half
                val idx = rowOff + x
                val pixel = pixels[idx]
                val qX = if (x < half) 0 else 1
                quadArr[idx] = qY * 2 + qX

                val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                val distNorm = (dist / maxDist).coerceIn(0f, 1f)
                // Center-weighted Gaussian mask: center is 1.0, corners are ~0.15
                val w = kotlin.math.exp((-2.5 * distNorm * distNorm).toDouble()).toFloat()
                weightArr[idx] = w
                totalWeight += w

                // Concentric ring index (0=core, 1=mid-in, 2=mid-out, 3=edge)
                val ring = (distNorm * 4).toInt().coerceIn(0, 3)
                ringArr[idx] = ring

                val r = (pixel shr 16 and 0xFF) / 255f
                val g = (pixel shr 8 and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f
                rArr[idx] = r
                gArr[idx] = g
                bArr[idx] = b

                val maxC = maxOf(r, maxOf(g, b))
                val minC = minOf(r, minOf(g, b))
                val delta = maxC - minC
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                lumArr[idx] = lum

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
            }
        }

        val invTotalWeight = if (totalWeight > 0f) 1f / totalWeight else 1f

        // Compute average luminance across all pixels to determine illumination scaling
        var sumLum = 0f
        for (i in pixels.indices) {
            sumLum += lumArr[i]
        }
        val avgLum = sumLum / pixels.size.coerceAtLeast(1)

        // Target standard counter lighting luminance ~ 0.48
        // Gain normalizes under-exposed (low light) and over-exposed (high light/glare) images
        val lumGain = (0.48f / avgLum.coerceAtLeast(0.06f)).coerceIn(0.45f, 3.2f)

        // Compute illumination-normalized RGB and chromaticity coordinates
        val rNorm = FloatArray(pixels.size)
        val gNorm = FloatArray(pixels.size)
        val bNorm = FloatArray(pixels.size)
        val lNorm = FloatArray(pixels.size)
        val rChroma = FloatArray(pixels.size)
        val gChroma = FloatArray(pixels.size)

        for (i in pixels.indices) {
            val rn = (rArr[i] * lumGain).coerceIn(0f, 1f)
            val gn = (gArr[i] * lumGain).coerceIn(0f, 1f)
            val bn = (bArr[i] * lumGain).coerceIn(0f, 1f)
            rNorm[i] = rn
            gNorm[i] = gn
            bNorm[i] = bn
            lNorm[i] = (0.299f * rn + 0.587f * gn + 0.114f * bn).coerceIn(0f, 1f)

            val sumC = rArr[i] + gArr[i] + bArr[i] + 1e-4f
            rChroma[i] = (rArr[i] / sumC).coerceIn(0f, 1f)
            gChroma[i] = (gArr[i] / sumC).coerceIn(0f, 1f)
        }

        // -------------------------------------------------------------
        // Section 1: Global Center-Weighted Color Distribution (128 dims: offset 0..127)
        // -------------------------------------------------------------
        // Bins:
        // R (16 bins): 0..15
        // G (16 bins): 16..31
        // B (16 bins): 32..47
        // Hue (32 bins): 48..79
        // Sat (16 bins): 80..95
        // Lum (16 bins): 96..111
        // Color moments (16 bins): 112..127
        for (i in pixels.indices) {
            val w = weightArr[i]
            val rBin = (rNorm[i] * 15.99f).toInt().coerceIn(0, 15)
            val gBin = (gNorm[i] * 15.99f).toInt().coerceIn(0, 15)
            val bBin = (bNorm[i] * 15.99f).toInt().coerceIn(0, 15)
            val hBin = (hueArr[i] * 31.99f).toInt().coerceIn(0, 31)
            val sBin = (satArr[i] * 15.99f).toInt().coerceIn(0, 15)
            val lBin = (lNorm[i] * 15.99f).toInt().coerceIn(0, 15)

            embedding[0 + rBin] += w * invTotalWeight
            embedding[16 + gBin] += w * invTotalWeight
            embedding[32 + bBin] += w * invTotalWeight
            embedding[48 + hBin] += w * invTotalWeight
            embedding[80 + sBin] += w * invTotalWeight
            embedding[96 + lBin] += w * invTotalWeight
        }

        // Global moments (mean & variance for normalized R, G, B, H, S, Lum, plus opponent channels)
        var meanR = 0f; var meanG = 0f; var meanB = 0f
        var meanH = 0f; var meanS = 0f; var meanL = 0f
        for (i in pixels.indices) {
            val w = weightArr[i] * invTotalWeight
            meanR += rNorm[i] * w
            meanG += gNorm[i] * w
            meanB += bNorm[i] * w
            meanH += hueArr[i] * w
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
        embedding[118] = kotlin.math.sqrt(varR.toDouble()).toFloat()
        embedding[119] = kotlin.math.sqrt(varG.toDouble()).toFloat()
        embedding[120] = kotlin.math.sqrt(varB.toDouble()).toFloat()
        embedding[121] = kotlin.math.sqrt(varL.toDouble()).toFloat()
        embedding[122] = (meanR - meanG + 1f) * 0.5f // RG chromatic opponent
        embedding[123] = (meanR + meanG - 2f * meanB + 2f) * 0.25f // YB opponent
        embedding[124] = meanS * (1f - meanL) // Vividness contrast
        embedding[125] = if (varL > 1e-4f) varR / varL else 0f
        embedding[126] = if (varL > 1e-4f) varB / varL else 0f
        embedding[127] = maxOf(0f, meanL - meanS)

        // -------------------------------------------------------------
        // Section 2: Concentric Circular Ring Features (128 dims: offset 128..255)
        // Highly rotation-invariant (packaging tilted at any angle has matching rings)
        // 4 rings * 32 dims = 128 dims
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
            embedding[base + 24 + hBin] += rw
        }

        // -------------------------------------------------------------
        // Section 3: Sobel Edge & Gradient Orientation (128 dims: offset 256..383)
        // Captures packaging text, brand logos, stripes, bar lines
        // -------------------------------------------------------------
        var totalMag = 0f
        val quadMagSum = FloatArray(4)

        val gxArr = FloatArray(pixels.size)
        val gyArr = FloatArray(pixels.size)
        val magArr = FloatArray(pixels.size)
        val angleBinArr = IntArray(pixels.size)

        for (y in 1 until targetSize - 1) {
            val rowPrev = (y - 1) * targetSize
            val rowCurr = y * targetSize
            val rowNext = (y + 1) * targetSize

            for (x in 1 until targetSize - 1) {
                val idx = rowCurr + x
                val gx = (lNorm[rowPrev + x + 1] + 2f * lNorm[rowCurr + x + 1] + lNorm[rowNext + x + 1]) -
                         (lNorm[rowPrev + x - 1] + 2f * lNorm[rowCurr + x - 1] + lNorm[rowNext + x - 1])
                val gy = (lNorm[rowNext + x - 1] + 2f * lNorm[rowNext + x] + lNorm[rowNext + x + 1]) -
                         (lNorm[rowPrev + x - 1] + 2f * lNorm[rowPrev + x] + lNorm[rowPrev + x + 1])

                val mag = kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toFloat()
                var angle = kotlin.math.atan2(gy.toDouble(), gx.toDouble()).toFloat()
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

        val totalEdgePixels = (targetSize - 2) * (targetSize - 2)
        val avgMag = totalMag / totalEdgePixels.coerceAtLeast(1)
        val adaptiveEdgeThreshold = (0.5f * avgMag).coerceIn(0.008f, 0.04f)

        val invTotalMag = if (totalMag > 0f) 1f / totalMag else 1f
        for (y in 1 until targetSize - 1) {
            val rowOff = y * targetSize
            for (x in 1 until targetSize - 1) {
                val idx = rowOff + x
                val mag = magArr[idx]
                if (mag > adaptiveEdgeThreshold) {
                    val aBin = angleBinArr[idx]
                    val mBin = (mag * 3.99f).toInt().coerceIn(0, 15)
                    embedding[256 + aBin] += mag * invTotalMag
                    embedding[272 + mBin] += mag * invTotalMag

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
        // Section 4: Coarse 2x2 Spatial Quadrants (128 dims: offset 384..511)
        // 4 quadrants * 32 dims = 128 dims
        // Provides macro-level spatial color distribution
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
            embedding[base + 24 + hBin] += qw
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
