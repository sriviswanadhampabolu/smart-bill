package com.grocer.billing.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import androidx.room.withTransaction
import com.grocer.billing.core.data.local.AppDatabase
import com.grocer.billing.core.data.local.dao.EmbeddingDao
import com.grocer.billing.core.data.local.dao.ItemDao
import com.grocer.billing.core.data.local.dao.SyncQueueDao
import com.grocer.billing.core.data.local.entities.EmbeddingEntity
import com.grocer.billing.core.data.local.entities.SyncQueueEntity
import com.grocer.billing.core.vision.ImageEmbedder
import com.grocer.billing.core.vision.VectorCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class EmbeddingRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val embeddingDao: EmbeddingDao,
    private val itemDao: ItemDao,
    private val syncQueueDao: SyncQueueDao,
    private val embedder: ImageEmbedder,
    private val vectorCache: VectorCache,
    private val localBackupManager: com.grocer.billing.core.data.backup.LocalBackupManager? = null
) {
    /**
     * Extracts embedding vector from image, stores it in Room, updates VectorCache,
     * and optionally sets the primary item image thumbnail.
     */
    suspend fun saveAnglePhoto(
        itemId: String,
        bitmap: Bitmap,
        qualityScore: Float,
        source: String = "setup",
        isPrimaryThumbnail: Boolean = false
    ): EmbeddingEntity = withContext(Dispatchers.IO) {
        // 1. Primary embedding
        val vectorFloats = embedder.extractEmbedding(bitmap)
        val vectorBlob = vectorCache.floatArrayToByteArray(vectorFloats)
        val embeddingId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        var savedImagePath: String? = null
        if (isPrimaryThumbnail) {
            savedImagePath = saveThumbnailToDisk(itemId, bitmap)
        }

        val entity = EmbeddingEntity(
            id = embeddingId,
            itemId = itemId,
            vector = vectorBlob,
            source = source,
            qualityScore = qualityScore,
            createdAt = now
        )

        // 2. Generate simulated multi-lighting variants (low light & high light)
        val lowLightBmp = adjustLighting(bitmap, 0.65f)
        val highLightBmp = adjustLighting(bitmap, 1.40f)
        val lowVectorFloats = embedder.extractEmbedding(lowLightBmp)
        val highVectorFloats = embedder.extractEmbedding(highLightBmp)
        lowLightBmp.recycle()
        highLightBmp.recycle()

        database.withTransaction {
            embeddingDao.insertEmbedding(entity)

            if (savedImagePath != null) {
                val item = itemDao.getItemById(itemId)
                if (item != null) {
                    itemDao.updateItem(item.copy(imagePath = savedImagePath))
                }
            }

            syncQueueDao.enqueue(
                SyncQueueEntity(
                    entityType = "embedding",
                    entityId = embeddingId,
                    operation = "INSERT",
                    payloadJson = """{"item_id":"$itemId","source":"$source","score":$qualityScore}""",
                    createdAt = now
                )
            )
        }

        // Live update in-memory VectorCache with original + multi-lighting vectors
        val currentItem = itemDao.getItemById(itemId)
        vectorCache.addVector(itemId, vectorFloats, currentItem)
        vectorCache.addVector(itemId, lowVectorFloats, currentItem)
        vectorCache.addVector(itemId, highVectorFloats, currentItem)
        localBackupManager?.triggerAutoBackup(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))

        entity
    }

    private fun adjustLighting(source: Bitmap, factor: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint()
        val cm = android.graphics.ColorMatrix(floatArrayOf(
            factor, 0f, 0f, 0f, 0f,
            0f, factor, 0f, 0f, 0f,
            0f, 0f, factor, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    suspend fun preloadAllVectorsToCache(shopId: String = "") = withContext(Dispatchers.IO) {
        val tuples = embeddingDao.getAllVectors()
        val pairs = tuples.map { it.item_id to it.vector }
        val allItems = if (shopId.isNotBlank()) itemDao.getAllItems(shopId) else itemDao.searchItems("", "")
        vectorCache.updateCatalog(allItems, pairs)

        // If any item has a saved thumbnail on disk but no DB vector yet, auto-embed it
        for (item in allItems) {
            val path = item.imagePath
            if (path != null && pairs.none { it.first == item.id }) {
                try {
                    val file = File(path)
                    if (file.exists()) {
                        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        if (bmp != null) {
                            val floats = embedder.extractEmbedding(bmp)
                            vectorCache.addVector(item.id, floats)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun saveThumbnailToDisk(itemId: String, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "item_photos")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${itemId}_thumb.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }
}
