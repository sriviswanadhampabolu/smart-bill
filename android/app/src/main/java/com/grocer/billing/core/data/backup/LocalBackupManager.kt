package com.grocer.billing.core.data.backup

import android.content.Context
import android.os.Environment
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.grocer.billing.core.data.local.AppDatabase
import com.grocer.billing.core.data.local.dao.BillDao
import com.grocer.billing.core.data.local.dao.EmbeddingDao
import com.grocer.billing.core.data.local.dao.ItemDao
import com.grocer.billing.core.data.local.dao.ShopDao
import com.grocer.billing.core.data.local.entities.BillEntity
import com.grocer.billing.core.data.local.entities.BillItemEntity
import com.grocer.billing.core.data.local.entities.EmbeddingEntity
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.local.entities.ShopEntity
import com.grocer.billing.core.data.repository.EmbeddingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SafeBase64 {
    fun encode(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Throwable) {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }

    fun decode(str: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(str)
        } catch (_: Throwable) {
            android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
        }
    }
}

data class EmbeddingBackupDto(
    val id: String,
    val itemId: String,
    val vectorBase64: String,
    val source: String,
    val qualityScore: Float,
    val createdAt: Long
)

data class KiranaBackupDto(
    val version: Int = 1,
    val backupTime: Long,
    val backupDateFormatted: String,
    val shop: ShopEntity?,
    val items: List<ItemEntity> = emptyList(),
    val embeddings: List<EmbeddingBackupDto> = emptyList(),
    val bills: List<BillEntity> = emptyList(),
    val billItems: List<BillItemEntity> = emptyList()
)

data class BackupMetadata(
    val backupDateFormatted: String,
    val shopName: String,
    val phone: String,
    val itemCount: Int,
    val billCount: Int,
    val fileSizeBytes: Long
)

class LocalBackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val shopDao: ShopDao,
    private val itemDao: ItemDao,
    private val embeddingDao: EmbeddingDao,
    private val billDao: BillDao,
    private val embeddingRepository: EmbeddingRepository? = null
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private fun getPublicBackupDirectories(): List<File> {
        val dirs = mutableListOf<File>()

        try {
            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "KiranaBilling")
            if (!downloadDir.exists()) downloadDir.mkdirs()
            dirs.add(downloadDir)
        } catch (_: Exception) {}

        try {
            val documentsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "KiranaBilling")
            if (!documentsDir.exists()) documentsDir.mkdirs()
            dirs.add(documentsDir)
        } catch (_: Exception) {}

        return dirs
    }

    private fun findLatestBackupFile(): File? {
        val dirs = getPublicBackupDirectories()
        for (dir in dirs) {
            val file = File(dir, "kirana_backup.json")
            if (file.exists() && file.length() > 0) {
                return file
            }
        }
        return null
    }

    /**
     * Creates an offline JSON snapshot + SQLite file backup in public storage.
     * Survives Android OS "Clear Storage" because public storage is not wiped.
     */
    suspend fun createBackup(): BackupMetadata? = withContext(Dispatchers.IO) {
        val shop = shopDao.getActiveShop() ?: return@withContext null
        val items = itemDao.getAllItems(shop.id)
        val rawEmbeddings = embeddingDao.getAllEmbeddings()
        val bills = billDao.getAllBills(shop.id)
        val billItems = billDao.getAllBillItems()

        val embeddingDtos = rawEmbeddings.map {
            EmbeddingBackupDto(
                id = it.id,
                itemId = it.itemId,
                vectorBase64 = SafeBase64.encode(it.vector),
                source = it.source,
                qualityScore = it.qualityScore,
                createdAt = it.createdAt
            )
        }

        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateFormatted = dateFormat.format(Date(now))

        val backupDto = KiranaBackupDto(
            version = 1,
            backupTime = now,
            backupDateFormatted = dateFormatted,
            shop = shop,
            items = items,
            embeddings = embeddingDtos,
            bills = bills,
            billItems = billItems
        )

        val jsonString = gson.toJson(backupDto)
        var lastFileLength = 0L

        // Write to all reachable public directories
        for (dir in getPublicBackupDirectories()) {
            try {
                val jsonFile = File(dir, "kirana_backup.json")
                jsonFile.writeText(jsonString)
                lastFileLength = jsonFile.length()

                // Also copy the raw Room DB file if available
                val dbFile = context.getDatabasePath("kirana_counter.db")
                if (dbFile.exists()) {
                    dbFile.copyTo(File(dir, "kirana_counter_backup.db"), overwrite = true)
                }
            } catch (_: Exception) {}
        }

        BackupMetadata(
            backupDateFormatted = dateFormatted,
            shopName = shop.name,
            phone = shop.phone,
            itemCount = items.size,
            billCount = bills.size,
            fileSizeBytes = lastFileLength
        )
    }

    /**
     * Checks if a public backup file is available on the device.
     */
    suspend fun getAvailableBackup(): BackupMetadata? = withContext(Dispatchers.IO) {
        val file = findLatestBackupFile() ?: return@withContext null
        try {
            val json = file.readText()
            val backup = gson.fromJson(json, KiranaBackupDto::class.java) ?: return@withContext null
            BackupMetadata(
                backupDateFormatted = backup.backupDateFormatted,
                shopName = backup.shop?.name ?: "Kirana Shop",
                phone = backup.shop?.phone ?: "",
                itemCount = backup.items.size,
                billCount = backup.bills.size,
                fileSizeBytes = file.length()
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Restores all shop profiles, catalog items, visual AI embeddings, and billing records
     * from the public offline backup into Room SQLite.
     */
    suspend fun restoreBackup(): Boolean = withContext(Dispatchers.IO) {
        val file = findLatestBackupFile() ?: return@withContext false
        try {
            val json = file.readText()
            val backup = gson.fromJson(json, KiranaBackupDto::class.java) ?: return@withContext false

            database.withTransaction {
                if (backup.shop != null) {
                    shopDao.insertShop(backup.shop)
                }

                if (backup.items.isNotEmpty()) {
                    itemDao.insertItems(backup.items)
                }

                if (backup.embeddings.isNotEmpty()) {
                    val entities = backup.embeddings.mapNotNull { dto ->
                        try {
                            val vectorBytes = SafeBase64.decode(dto.vectorBase64)
                            EmbeddingEntity(
                                id = dto.id,
                                itemId = dto.itemId,
                                vector = vectorBytes,
                                source = dto.source,
                                qualityScore = dto.qualityScore,
                                createdAt = dto.createdAt
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (entities.isNotEmpty()) {
                        embeddingDao.insertEmbeddings(entities)
                    }
                }

                if (backup.bills.isNotEmpty()) {
                    billDao.insertBills(backup.bills)
                }

                if (backup.billItems.isNotEmpty()) {
                    billDao.restoreBillItems(backup.billItems)
                }
            }

            if (backup.shop != null && embeddingRepository != null) {
                try {
                    embeddingRepository.preloadAllVectorsToCache(backup.shop.id)
                } catch (_: Exception) {}
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Helper to launch a background non-blocking backup.
     */
    fun triggerAutoBackup(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                createBackup()
            } catch (_: Exception) {}
        }
    }
}
