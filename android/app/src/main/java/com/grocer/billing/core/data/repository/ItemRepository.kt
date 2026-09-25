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
    private var vectorCache: VectorCache? = null,
    private val context: android.content.Context? = null
) {
    fun setSyncManager(manager: SyncManager) {
        this.syncManager = manager
    }

    fun setVectorCache(cache: VectorCache) {
        this.vectorCache = cache
    }

    fun observeItems(shopId: String, lowestStockFirst: Boolean = true): Flow<List<ItemEntity>> {
        return if (shopId.isBlank()) {
            if (lowestStockFirst) itemDao.observeAllItemsLowStockFirst() else itemDao.observeAllActiveItemsByName()
        } else {
            if (lowestStockFirst) itemDao.observeItemsLowStockFirst(shopId) else itemDao.observeActiveItems(shopId)
        }
    }

    fun observeLowStockAlerts(shopId: String): Flow<List<ItemEntity>> {
        return if (shopId.isBlank()) {
            itemDao.observeAllLowStockAlerts()
        } else {
            itemDao.observeLowStockAlerts(shopId)
        }
    }

    suspend fun getActiveShopId(): String? = database.shopDao().getActiveShop()?.id

    suspend fun searchItems(shopId: String, query: String): List<ItemEntity> {
        return if (query.isBlank()) {
            if (shopId.isBlank()) {
                val activeId = getActiveShopId() ?: ""
                if (activeId.isNotBlank()) itemDao.getAllItems(activeId) else emptyList()
            } else {
                itemDao.getAllItems(shopId)
            }
        } else {
            val effectiveShopId = if (shopId.isNotBlank()) shopId else (getActiveShopId() ?: "")
            itemDao.searchItems(effectiveShopId, query.trim())
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
        // Resolve a guaranteed non-blank shopId.
        // If shopId is empty or no shop exists, fetch or create a default shop to satisfy foreign key constraints.
        val effectiveShopId = if (shopId.isNotBlank()) {
            shopId
        } else {
            database.shopDao().getActiveShop()?.id ?: run {
                val defaultShop = com.grocer.billing.core.data.local.entities.ShopEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "My Grocery Store",
                    ownerName = "Shop Owner",
                    phone = "0000000000"
                )
                database.shopDao().insertShop(defaultShop)
                defaultShop.id
            }
        }

        val item = ItemEntity(
            shopId = effectiveShopId,
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
        syncManager?.triggerAutoSync(effectiveShopId)

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

            if (deltaQty < 0 && currentItem.stockQty <= currentItem.lowStockThreshold) {
                context?.let { ctx ->
                    com.grocer.billing.core.notification.StockNotificationManager.sendLowStockNotification(
                        context = ctx,
                        itemId = currentItem.id,
                        itemName = currentItem.name,
                        currentStock = currentItem.stockQty,
                        threshold = currentItem.lowStockThreshold,
                        unitType = currentItem.unitType
                    )
                }
            }
        }
    }

    suspend fun updateLowStockThreshold(itemId: String, newThreshold: Double) {
        val now = System.currentTimeMillis()
        itemDao.updateLowStockThreshold(itemId, newThreshold, now)
        val currentItem = itemDao.getItemById(itemId)
        if (currentItem != null) {
            val payload = """{"low_stock_threshold":$newThreshold}"""
            syncQueueDao?.enqueue(
                SyncQueueEntity(
                    entityType = "item",
                    entityId = itemId,
                    operation = "UPDATE",
                    payloadJson = payload,
                    createdAt = now
                )
            )
            vectorCache?.registerItem(currentItem)
            syncManager?.triggerAutoSync(currentItem.shopId)

            if (currentItem.stockQty <= newThreshold) {
                context?.let { ctx ->
                    com.grocer.billing.core.notification.StockNotificationManager.sendLowStockNotification(
                        context = ctx,
                        itemId = currentItem.id,
                        itemName = currentItem.name,
                        currentStock = currentItem.stockQty,
                        threshold = newThreshold,
                        unitType = currentItem.unitType
                    )
                }
            }
        }
    }

    suspend fun deleteItem(itemId: String) {
        val currentItem = itemDao.getItemById(itemId)
        val shopId = currentItem?.shopId ?: getActiveShopId() ?: ""
        database.withTransaction {
            itemDao.deleteItemPermanently(itemId)
            syncQueueDao?.enqueue(
                SyncQueueEntity(
                    entityType = "item",
                    entityId = itemId,
                    operation = "DELETE",
                    payloadJson = """{"id":"$itemId"}""",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        vectorCache?.removeItem(itemId)
        localBackupManager?.triggerAutoBackup(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
        if (shopId.isNotBlank()) {
            syncManager?.triggerAutoSync(shopId)
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
