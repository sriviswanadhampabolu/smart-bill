package com.grocer.billing.core.data.sync

import com.grocer.billing.core.data.backup.SafeBase64
import com.grocer.billing.core.data.local.dao.BillDao
import com.grocer.billing.core.data.local.dao.EmbeddingDao
import com.grocer.billing.core.data.local.dao.ItemDao
import com.grocer.billing.core.data.local.dao.ShopDao
import com.grocer.billing.core.data.local.dao.SyncQueueDao
import com.grocer.billing.core.data.local.entities.BillEntity
import com.grocer.billing.core.data.local.entities.BillItemEntity
import com.grocer.billing.core.data.local.entities.EmbeddingEntity
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.local.entities.ShopEntity
import com.grocer.billing.core.data.remote.RetrofitClient
import com.grocer.billing.core.data.remote.SyncApiService
import com.grocer.billing.core.data.remote.SyncPushRequestDto
import com.grocer.billing.core.data.remote.SyncQueueItemDto
import com.grocer.billing.core.data.repository.EmbeddingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncManager(
    private val syncQueueDao: SyncQueueDao,
    private val billDao: BillDao,
    private val itemDao: ItemDao? = null,
    private val shopDao: ShopDao? = null,
    private val embeddingDao: EmbeddingDao? = null,
    private val retrofitClient: RetrofitClient? = null,
    private val syncApiService: SyncApiService? = null,
    private var embeddingRepository: EmbeddingRepository? = null
) {
    private val syncScope = CoroutineScope(Dispatchers.IO)

    fun setEmbeddingRepository(repo: EmbeddingRepository) {
        this.embeddingRepository = repo
    }

    private fun getApi(): SyncApiService? {
        return syncApiService ?: retrofitClient?.getApiService()
    }

    /**
     * Auto-sync trigger: non-blocking, safe to call on any thread.
     * Fires whenever a new bill is completed or an item is created/updated.
     */
    fun triggerAutoSync(shopId: String) {
        if (shopId.isBlank()) return
        syncScope.launch {
            try {
                flushPendingQueue(shopId)
            } catch (_: Exception) {}
        }
    }

    /**
     * Flushes local pending mutations to the remote backend idempotently.
     * Guaranteed to never double-post bills or drop data during network disruptions.
     */
    suspend fun flushPendingQueue(shopId: String): Int = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext 0
        val pendingItems = syncQueueDao.getPendingSyncItems(limit = 50)
        if (pendingItems.isEmpty()) return@withContext 0

        val request = SyncPushRequestDto(
            shop_id = shopId,
            items = pendingItems.map { entity ->
                SyncQueueItemDto(
                    id = entity.id,
                    entity_type = entity.entityType,
                    entity_id = entity.entityId,
                    operation = entity.operation,
                    payload_json = entity.payloadJson,
                    created_at = entity.createdAt
                )
            }
        )

        try {
            val response = api.pushSyncQueue(request)
            if (response.isSuccessful && response.body() != null) {
                val acknowledged = response.body()!!.acknowledged_ids
                val now = System.currentTimeMillis()

                for (syncId in acknowledged) {
                    val entity = pendingItems.find { it.id == syncId }
                    if (entity?.entityType == "bill") {
                        billDao.markBillSynced(entity.entityId, now)
                    }
                    syncQueueDao.deleteById(syncId)
                }
                return@withContext acknowledged.size
            } else {
                for (item in pendingItems) {
                    syncQueueDao.recordRetry(item.id, "Server status: ${response.code()}")
                }
                return@withContext 0
            }
        } catch (e: Exception) {
            for (item in pendingItems) {
                syncQueueDao.recordRetry(item.id, e.localizedMessage ?: "Network error")
            }
            return@withContext 0
        }
    }

    /**
     * Pulls full shop profile, active catalog items, embeddings, and billing history
     * from the cloud backend into local SQLite Room database.
     * Called on login after storage clear or fresh install.
     */
    suspend fun pullFullShopData(shopId: String): Boolean = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext false
        try {
            val response = api.pullSyncData()
            if (!response.isSuccessful || response.body() == null) return@withContext false
            val body = response.body()!!

            // 1. Restore shop profile
            val shopDto = body.shop
            if (shopDto != null && shopDao != null) {
                val shop = ShopEntity(
                    id = shopDto.id,
                    name = shopDto.name,
                    ownerName = shopDto.owner_name,
                    phone = shopDto.phone,
                    upiId = shopDto.upi_id,
                    currencySymbol = shopDto.currency_symbol ?: "₹",
                    pinHash = shopDto.pin_hash,
                    settingsJson = shopDto.settings_json ?: "{}"
                )
                shopDao.insertShop(shop)
            }

            // 2. Restore catalog items
            if (body.items.isNotEmpty() && itemDao != null) {
                val itemEntities = body.items.map { dto ->
                    ItemEntity(
                        id = dto.id,
                        shopId = dto.shop_id,
                        name = dto.name,
                        nameRegional = dto.name_regional,
                        category = dto.category,
                        barcode = dto.barcode,
                        unitType = dto.unit_type,
                        price = dto.price,
                        stockQty = dto.stock_qty,
                        lowStockThreshold = dto.low_stock_threshold,
                        isActive = dto.is_active,
                        imagePath = dto.image_path
                    )
                }
                itemDao.insertItems(itemEntities)
            }

            // 3. Restore visual embeddings
            if (body.embeddings.isNotEmpty() && embeddingDao != null) {
                val embEntities = body.embeddings.mapNotNull { dto ->
                    try {
                        val vectorBytes = SafeBase64.decode(dto.vector_base64)
                        EmbeddingEntity(
                            id = dto.id,
                            itemId = dto.item_id,
                            vector = vectorBytes,
                            source = dto.source,
                            qualityScore = dto.quality_score,
                            createdAt = dto.created_at
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                if (embEntities.isNotEmpty()) {
                    embeddingDao.insertEmbeddings(embEntities)
                }
            }

            // 4. Restore bills
            if (body.bills.isNotEmpty()) {
                val billEntities = body.bills.map { dto ->
                    BillEntity(
                        id = dto.id,
                        shopId = dto.shop_id,
                        billNumber = dto.bill_number,
                        subtotal = dto.subtotal,
                        discount = dto.discount,
                        tax = dto.tax,
                        total = dto.total,
                        paymentMethod = dto.payment_method,
                        createdAt = dto.created_at,
                        syncedAt = System.currentTimeMillis()
                    )
                }
                billDao.insertBills(billEntities)
            }

            // 5. Restore bill items
            if (body.bill_items.isNotEmpty()) {
                val billItemEntities = body.bill_items.map { dto ->
                    BillItemEntity(
                        id = dto.id ?: java.util.UUID.randomUUID().toString(),
                        billId = dto.bill_id,
                        itemId = dto.item_id,
                        nameSnapshot = dto.name_snapshot,
                        qty = dto.qty,
                        unitType = dto.unit_type,
                        unitPriceSnapshot = dto.unit_price_snapshot,
                        lineTotal = dto.line_total
                    )
                }
                billDao.restoreBillItems(billItemEntities)
            }

            // 6. Reload vector cache
            val effectiveShopId = shopDto?.id ?: shopId
            if (embeddingRepository != null && effectiveShopId.isNotBlank()) {
                try {
                    embeddingRepository?.preloadAllVectorsToCache(effectiveShopId)
                } catch (_: Exception) {}
            }

            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }

    suspend fun getPendingCount(): Int = withContext(Dispatchers.IO) {
        syncQueueDao.getPendingCount()
    }
}
