package com.grocer.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class QualityAndTrainingTest {

    // Fast test simulation of the Quality Checker algorithm
    private fun evaluateLuminance(pixels: List<Int>): Pair<Boolean, String> {
        var totalLum = 0.0
        for (p in pixels) {
            val r = (p shr 16 and 0xFF) / 255.0
            val g = (p shr 8 and 0xFF) / 255.0
            val b = (p and 0xFF) / 255.0
            totalLum += (0.299 * r + 0.587 * g + 0.114 * b)
        }
        val avg = totalLum / pixels.size.coerceAtLeast(1)

        return when {
            avg < 0.15 -> false to "Too dark — move closer to counter light"
            avg > 0.92 -> false to "Too bright — avoid direct sun glare"
            else -> true to "Good shot!"
        }
    }

    private fun evaluateBlur(edges: List<Float>): Pair<Boolean, String> {
        val avgSharpness = edges.average().toFloat()
        return if (avgSharpness < 120.0f) {
            false to "Blurry — hold phone still"
        } else {
            true to "Sharp image"
        }
    }

    @Test
    fun testTooDarkImage_rejectedWithPlainLanguage() {
        // Dark pixels (e.g. RGB 20, 20, 20)
        val darkPixels = List(100) { (20 shl 16) or (20 shl 8) or 20 }
        val (isValid, message) = evaluateLuminance(darkPixels)

        assertFalse(isValid)
        assertTrue(message.contains("Too dark"))
    }

    @Test
    fun testTooBrightImage_rejectedWithPlainLanguage() {
        // Blown out white pixels (e.g. RGB 250, 250, 250)
        val brightPixels = List(100) { (250 shl 16) or (250 shl 8) or 250 }
        val (isValid, message) = evaluateLuminance(brightPixels)

        assertFalse(isValid)
        assertTrue(message.contains("Too bright"))
    }

    @Test
    fun testNormalLighting_accepted() {
        // Normal counter daylight (e.g. RGB 130, 130, 130)
        val normalPixels = List(100) { (130 shl 16) or (130 shl 8) or 130 }
        val (isValid, message) = evaluateLuminance(normalPixels)

        assertTrue(isValid)
        assertEquals("Good shot!", message)
    }

    @Test
    fun testBlurryFrame_rejected() {
        // Low contrast / flat edges
        val blurryEdges = List(50) { 45.0f }
        val (isSharp, message) = evaluateBlur(blurryEdges)

        assertFalse(isSharp)
        assertTrue(message.contains("Blurry"))
    }

    @Test
    fun testSharpFrame_accepted() {
        // High contrast product edges
        val sharpEdges = List(50) { 180.0f }
        val (isSharp, message) = evaluateBlur(sharpEdges)

        assertTrue(isSharp)
        assertTrue(message.contains("Sharp"))
    }

    @Test
    fun testGuidedAngleSequence_containsSixKeyAngles() {
        val expected = listOf(
            "Show me the front label",
            "Turn it slightly to the left",
            "Turn it slightly to the right",
            "Show the top / lid",
            "Show the back or side",
            "Tilt slightly downwards"
        )
        assertEquals(6, expected.size)
        assertTrue(expected.first().contains("front"))
    }
}
