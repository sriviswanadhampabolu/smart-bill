package com.grocer.billing

import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.vision.VectorCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class VisionAndMatchingTest {

    private fun normalize(v: FloatArray): FloatArray {
        var sumSq = 0f
        for (x in v) sumSq += x * x
        val norm = sqrt(sumSq.toDouble()).toFloat()
        return FloatArray(v.size) { v[it] / norm }
    }

    @Test
    fun testVectorCache_highConfidenceAutoAdd() {
        val cache = VectorCache()
        val item = ItemEntity(
            id = "item-123",
            shopId = "shop-1",
            name = "Parle-G 100g",
            category = "Snacks",
            unitType = "piece",
            price = 10.0
        )

        val refVec = normalize(floatArrayOf(1.0f, 0.0f, 0.5f, 0.2f))
        val refBlob = cache.floatArrayToByteArray(refVec)

        cache.updateCatalog(listOf(item), listOf("item-123" to refBlob))

        // Query with an identical vector -> confidence 1.0
        val matches = cache.match(refVec, topK = 3)
        assertEquals(1, matches.size)
        assertEquals("item-123", matches[0].item.id)
        assertTrue(matches[0].confidence >= 0.99f)
        assertTrue(matches[0].isHighConfidence)
    }

    @Test
    fun testVectorCache_lowConfidenceCandidates() {
        val cache = VectorCache()
        val itemA = ItemEntity(id = "a", shopId = "s", name = "Atta 5kg", category = "Staples", unitType = "piece", price = 220.0)
        val itemB = ItemEntity(id = "b", shopId = "s", name = "Maida 1kg", category = "Staples", unitType = "piece", price = 45.0)

        val vecA = normalize(floatArrayOf(0.7f, 0.7f, 0.0f, 0.0f))
        val vecB = normalize(floatArrayOf(0.6f, 0.8f, 0.0f, 0.0f))

        cache.updateCatalog(
            listOf(itemA, itemB),
            listOf(
                "a" to cache.floatArrayToByteArray(vecA),
                "b" to cache.floatArrayToByteArray(vecB)
            )
        )

        // Query vector that has moderate similarity to both (~0.65 - 0.72)
        val queryVec = normalize(floatArrayOf(0.65f, 0.75f, 0.1f, 0.0f))
        val matches = cache.match(queryVec, topK = 3)

        // Both are candidates (above 0.50)
        assertTrue(matches.size >= 2)
        // Descending order of confidence
        assertTrue(matches[0].confidence >= matches[1].confidence)
    }

    @Test
    fun testVectorCache_noMatchBelowThreshold() {
        val cache = VectorCache()
        val item = ItemEntity(id = "x", shopId = "s", name = "Soap", category = "Care", unitType = "piece", price = 30.0)
        val vec = normalize(floatArrayOf(1.0f, 0.0f, 0.0f))

        cache.updateCatalog(listOf(item), listOf("x" to cache.floatArrayToByteArray(vec)))

        // Query with orthogonal vector (similarity = 0.0)
        val orthogonalQuery = normalize(floatArrayOf(0.0f, 1.0f, 0.0f))
        val matches = cache.match(orthogonalQuery, topK = 3)

        // Orthogonal match should be filtered out because confidence < 0.50
        assertTrue(matches.isEmpty())
    }

    @Test
    fun testFloatArrayToByteArrayRoundtrip() {
        val cache = VectorCache()
        val original = floatArrayOf(0.123f, -0.456f, 0.789f, 1.0f)
        val bytes = cache.floatArrayToByteArray(original)
        val decoded = cache.run {
            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val floats = FloatArray(bytes.size / 4)
            buffer.asFloatBuffer().get(floats)
            floats
        }

        assertEquals(original.size, decoded.size)
        for (i in original.indices) {
            assertEquals(original[i], decoded[i], 0.0001f)
        }
    }
}
