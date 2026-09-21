package com.grocer.billing.core.data.repository

import androidx.room.withTransaction
import com.grocer.billing.core.data.local.AppDatabase
import com.grocer.billing.core.data.local.dao.ItemDao
import com.grocer.billing.core.data.local.dao.StockLogDao
import com.grocer.billing.core.data.local.dao.SyncQueueDao
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.local.entities.StockLogEntity
import com.grocer.billing.core.data.local.entities.SyncQueueEntity
import com.grocer.billing.core.data.sync.SyncManager
import com.grocer.billing.core.vision.VectorCache
import kotlinx.coroutines.flow.Flow

class ItemRepository(
    private val database: AppDatabase,
    private val itemDao: ItemDao,
    private val stockLogDao: StockLogDao,
    private val localBackupManager: com.grocer.billing.core.data.backup.LocalBackupManager? = null,
    private val syncQueueDao: SyncQueueDao? = null,
    private var syncManager: SyncManager? = null,
    private var vectorCache: VectorCache? = null
) {
    fun setSyncManager(manager: SyncManager) {
        this.syncManager = manager
    }

    fun setVectorCache(cache: VectorCache) {
        this.vectorCache = cache
    }

    fun observeItems(shopId: String, lowestStockFirst: Boolean = true): Flow<List<ItemEntity>> {
        return if (lowestStockFirst) {
            itemDao.observeItemsLowStockFirst(shopId)
        } else {
            itemDao.observeActiveItems(shopId)
        }
    }

    fun observeLowStockAlerts(shopId: String): Flow<List<ItemEntity>> {
        return itemDao.observeLowStockAlerts(shopId)
    }

    suspend fun searchItems(shopId: String, query: String): List<ItemEntity> {
        return if (query.isBlank()) {
            emptyList()
        } else {
            itemDao.searchItems(shopId, query.trim())
        }
    }

    suspend fun getItemById(itemId: String): ItemEntity? = itemDao.getItemById(itemId)

    suspend fun getItemByBarcode(barcode: String): ItemEntity? = itemDao.getItemByBarcode(barcode)

    suspend fun addItem(
        shopId: String,
        name: String,
        nameRegional: String?,
        category: String,
        barcode: String?,
        unitType: String,
        price: Double,
        stockQty: Double,
        lowStockThreshold: Double
    ): ItemEntity {
        val item = ItemEntity(
            shopId = shopId,
            name = name.trim(),
            nameRegional = nameRegional?.trim()?.ifBlank { null },
            category = category.trim(),
            barcode = barcode?.trim()?.ifBlank { null },
            unitType = unitType,
            price = price,
            stockQty = stockQty,
            lowStockThreshold = lowStockThreshold
        )

        database.withTransaction {
            itemDao.insertItem(item)
            if (stockQty > 0) {
                stockLogDao.insertLog(
                    StockLogEntity(
                        itemId = item.id,
                        changeQty = stockQty,
                        reason = "restock"
                    )
                )
            }

            val regionalVal = if (item.nameRegional != null) "\"${item.nameRegional!!.replace("\"", "\\\"")}\"" else "null"
            val barcodeVal = if (item.barcode != null) "\"${item.barcode}\"" else "null"
            val payload = """{"name":"${item.name.replace("\"", "\\\"")}","name_regional":$regionalVal,"category":"${item.category}","barcode":$barcodeVal,"unit_type":"${item.unitType}","price":${item.price},"stock_qty":${item.stockQty},"low_stock_threshold":${item.lowStockThreshold},"is_active":true}"""

            syncQueueDao?.enqueue(
                SyncQueueEntity(
                    entityType = "item",
                    entityId = item.id,
                    operation = "INSERT",
                    payloadJson = payload,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        // Instantly register item with vector cache for 0ms visual lookup
        vectorCache?.registerItem(item)
        localBackupManager?.triggerAutoBackup(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
        syncManager?.triggerAutoSync(shopId)

        return item
    }

    suspend fun updateItem(item: ItemEntity) {
        val updated = item.copy(updatedAt = System.currentTimeMillis())
        database.withTransaction {
            itemDao.updateItem(updated)
            val regionalVal = if (updated.nameRegional != null) "\"${updated.nameRegional!!.replace("\"", "\\\"")}\"" else "null"
            val barcodeVal = if (updated.barcode != null) "\"${updated.barcode}\"" else "null"
            val payload = """{"name":"${updated.name.replace("\"", "\\\"")}","name_regional":$regionalVal,"category":"${updated.category}","barcode":$barcodeVal,"unit_type":"${updated.unitType}","price":${updated.price},"stock_qty":${updated.stockQty},"low_stock_threshold":${updated.lowStockThreshold},"is_active":${updated.isActive}}"""

            syncQueueDao?.enqueue(
                SyncQueueEntity(
                    entityType = "item",
                    entityId = updated.id,
                    operation = "UPDATE",
                    payloadJson = payload,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        vectorCache?.registerItem(updated)
        localBackupManager?.triggerAutoBackup(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
        syncManager?.triggerAutoSync(item.shopId)
    }

    suspend fun adjustStock(itemId: String, deltaQty: Double, reason: String) {
        database.withTransaction {
            itemDao.adjustStock(itemId, deltaQty)
            stockLogDao.insertLog(
                StockLogEntity(
                    itemId = itemId,
                    changeQty = deltaQty,
                    reason = reason
                )
            )
        }
        val currentItem = itemDao.getItemById(itemId)
        if (currentItem != null) {
            val payload = """{"stock_qty":${currentItem.stockQty}}"""
            syncQueueDao?.enqueue(
                SyncQueueEntity(
                    entityType = "item",
                    entityId = itemId,
                    operation = "UPDATE",
                    payloadJson = payload,
                    createdAt = System.currentTimeMillis()
                )
            )
            syncManager?.triggerAutoSync(currentItem.shopId)
        }
    }

    suspend fun deactivateItem(itemId: String) {
        itemDao.softDeleteItem(itemId)
        val currentItem = itemDao.getItemById(itemId)
        if (currentItem != null) {
            val payload = """{"is_active":false}"""
            syncQueueDao?.enqueue(
                SyncQueueEntity(
                    entityType = "item",
                    entityId = itemId,
                    operation = "UPDATE",
                    payloadJson = payload,
                    createdAt = System.currentTimeMillis()
                )
            )
            syncManager?.triggerAutoSync(currentItem.shopId)
        }
    }

    fun observeStockHistory(itemId: String): Flow<List<StockLogEntity>> {
        return stockLogDao.observeStockLogsForItem(itemId)
    }
}
