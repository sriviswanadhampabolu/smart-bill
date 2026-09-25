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

        val primaryEntity = EmbeddingEntity(
            id = embeddingId,
            itemId = itemId,
            vector = vectorBlob,
            source = source,
            qualityScore = qualityScore,
            createdAt = now
        )

        // 2. Generate simulated multi-lighting variants (dim low-light & bright glare/flash)
        val lowLightBmp = adjustLighting(bitmap, 0.50f)
        val highLightBmp = adjustLighting(bitmap, 1.50f)
        val lowVectorFloats = embedder.extractEmbedding(lowLightBmp)
        val highVectorFloats = embedder.extractEmbedding(highLightBmp)
        lowLightBmp.recycle()
        highLightBmp.recycle()

        // 3. Generate multi-scale center zoom variants (0.75x zoom and 0.55x tight foreground)
        val cropW75 = (bitmap.width * 0.75f).toInt()
        val cropH75 = (bitmap.height * 0.75f).toInt()
        val startX75 = ((bitmap.width - cropW75) / 2).coerceAtLeast(0)
        val startY75 = ((bitmap.height - cropH75) / 2).coerceAtLeast(0)
        val centerBmp75 = Bitmap.createBitmap(bitmap, startX75, startY75, cropW75, cropH75)
        val centerVector75 = embedder.extractEmbedding(centerBmp75)
        centerBmp75.recycle()

        val cropW55 = (bitmap.width * 0.55f).toInt()
        val cropH55 = (bitmap.height * 0.55f).toInt()
        val startX55 = ((bitmap.width - cropW55) / 2).coerceAtLeast(0)
        val startY55 = ((bitmap.height - cropH55) / 2).coerceAtLeast(0)
        val centerBmp55 = Bitmap.createBitmap(bitmap, startX55, startY55, cropW55, cropH55)
        val centerVector55 = embedder.extractEmbedding(centerBmp55)
        centerBmp55.recycle()

        val lowEntity = EmbeddingEntity(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            vector = vectorCache.floatArrayToByteArray(lowVectorFloats),
            source = "aug_low_light",
            qualityScore = 0.95f,
            createdAt = now
        )
        val highEntity = EmbeddingEntity(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            vector = vectorCache.floatArrayToByteArray(highVectorFloats),
            source = "aug_high_light",
            qualityScore = 0.95f,
            createdAt = now
        )
        val zoom75Entity = EmbeddingEntity(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            vector = vectorCache.floatArrayToByteArray(centerVector75),
            source = "aug_zoom_75",
            qualityScore = 0.95f,
            createdAt = now
        )
        val zoom55Entity = EmbeddingEntity(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            vector = vectorCache.floatArrayToByteArray(centerVector55),
            source = "aug_zoom_55",
            qualityScore = 0.95f,
            createdAt = now
        )

        val allEntities = listOf(primaryEntity, lowEntity, highEntity, zoom75Entity, zoom55Entity)

        database.withTransaction {
            embeddingDao.insertEmbeddings(allEntities)

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

        // Live update in-memory VectorCache with original + multi-lighting + multi-zoom vectors
        val currentItem = itemDao.getItemById(itemId)
        allEntities.forEach { ent ->
            vectorCache.addVector(itemId, vectorCache.byteArrayToFloatArray(ent.vector), currentItem)
        }
        localBackupManager?.triggerAutoBackup(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))

        primaryEntity
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

        // If any item has a saved thumbnail on disk but no DB vector matching current embedder dimension, auto-embed it
        val targetByteLength = embedder.embeddingDimension * 4
        for (item in allItems) {
            val path = item.imagePath
            val hasValidVector = pairs.any { it.first == item.id && it.second.size == targetByteLength }
            if (path != null && !hasValidVector) {
                try {
                    val file = File(path)
                    if (file.exists()) {
                        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        if (bmp != null) {
                            val floats = embedder.extractEmbedding(bmp)
                            val blob = vectorCache.floatArrayToByteArray(floats)
                            vectorCache.addVector(item.id, floats, item)
                            embeddingDao.insertEmbedding(
                                EmbeddingEntity(
                                    id = UUID.randomUUID().toString(),
                                    itemId = item.id,
                                    vector = blob,
                                    source = "auto_migrated_thumb",
                                    qualityScore = 1.0f,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            bmp.recycle()
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
