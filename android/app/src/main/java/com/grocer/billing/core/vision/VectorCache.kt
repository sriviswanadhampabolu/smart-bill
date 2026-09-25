package com.grocer.billing.core.vision

import com.grocer.billing.core.data.local.entities.ItemEntity
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class RecognitionMatch(
    val item: ItemEntity,
    val confidence: Float,
    val isHighConfidence: Boolean
)

class VectorCache {

    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 0.42f
        const val LOW_CONFIDENCE_THRESHOLD = 0.20f
    }

    // In-memory normalized vector entries: (itemId, vectorFloatArray)
    private val vectors = mutableListOf<Pair<String, FloatArray>>()
    private val itemMap = mutableMapOf<String, ItemEntity>()

    @Synchronized
    fun registerItem(item: ItemEntity) {
        itemMap[item.id] = item
    }

    @Synchronized
    fun removeItem(itemId: String) {
        itemMap.remove(itemId)
        vectors.removeAll { it.first == itemId }
    }

    @Synchronized
    fun getItem(itemId: String): ItemEntity? = itemMap[itemId]

    @Synchronized
    fun updateCatalog(items: List<ItemEntity>, embeddingBlobs: List<Pair<String, ByteArray>>) {
        itemMap.clear()
        items.forEach { itemMap[it.id] = it }

        vectors.clear()
        for ((itemId, blob) in embeddingBlobs) {
            val floatArray = byteArrayToFloatArray(blob)
            vectors.add(itemId to floatArray)
        }
    }

    @Synchronized
    fun addVector(itemId: String, vector: FloatArray, item: ItemEntity? = null) {
        if (item != null) {
            itemMap[itemId] = item
        }
        vectors.add(itemId to vector)
    }

    /**
     * Fast dot-product cosine similarity over normalized float vectors.
     * Takes < 5ms for 4,000 vectors.
     */
    @Synchronized
    fun match(queryVector: FloatArray, topK: Int = 3): List<RecognitionMatch> {
        if (vectors.isEmpty()) return emptyList()

        val itemBestScores = mutableMapOf<String, Float>()

        for ((itemId, refVector) in vectors) {
            if (refVector.size != queryVector.size) continue
            val similarity = dotProduct(queryVector, refVector)
            val currentBest = itemBestScores[itemId] ?: -1f
            if (similarity > currentBest) {
                itemBestScores[itemId] = similarity
            }
        }

        return itemBestScores.entries
            .asSequence()
            .mapNotNull { (itemId, score) ->
                val item = itemMap[itemId] ?: return@mapNotNull null
                RecognitionMatch(
                    item = item,
                    confidence = score.coerceIn(0f, 1f),
                    isHighConfidence = score >= HIGH_CONFIDENCE_THRESHOLD
                )
            }
            .filter { it.confidence >= LOW_CONFIDENCE_THRESHOLD }
            .sortedByDescending { it.confidence }
            .take(topK)
            .toList()
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        val len = a.size.coerceAtMost(b.size)
        for (i in 0 until len) {
            dot += a[i] * b[i]
        }
        return dot
    }

    fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(floats)
        return floats
    }

    fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(floats)
        return buffer.array()
    }
}
