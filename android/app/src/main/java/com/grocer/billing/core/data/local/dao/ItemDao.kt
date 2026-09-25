package com.grocer.billing.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.grocer.billing.core.data.local.entities.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE shop_id = :shopId AND is_active = 1 ORDER BY name ASC")
    fun observeActiveItems(shopId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE is_active = 1 ORDER BY name ASC")
    fun observeAllActiveItemsByName(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE shop_id = :shopId AND is_active = 1 ORDER BY stock_qty ASC")
    fun observeItemsLowStockFirst(shopId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE is_active = 1 ORDER BY stock_qty ASC")
    fun observeAllItemsLowStockFirst(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE shop_id = :shopId AND stock_qty <= low_stock_threshold AND is_active = 1")
    fun observeLowStockAlerts(shopId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE stock_qty <= low_stock_threshold AND is_active = 1")
    fun observeAllLowStockAlerts(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: String): ItemEntity?

    @Query("SELECT * FROM items WHERE barcode = :barcode AND is_active = 1 LIMIT 1")
    suspend fun getItemByBarcode(barcode: String): ItemEntity?

    @Query("SELECT * FROM items WHERE shop_id = :shopId AND (name LIKE '%' || :query || '%' OR name_regional LIKE '%' || :query || '%') AND is_active = 1")
    suspend fun searchItems(shopId: String, query: String): List<ItemEntity>

    @Query("SELECT * FROM items WHERE shop_id = :shopId")
    suspend fun getAllItems(shopId: String): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ItemEntity>)

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Query("UPDATE items SET stock_qty = stock_qty + :deltaQty, updated_at = :updatedAt WHERE id = :itemId")
    suspend fun adjustStock(itemId: String, deltaQty: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE items SET is_active = 0, updated_at = :updatedAt WHERE id = :itemId")
    suspend fun softDeleteItem(itemId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteItemPermanently(itemId: String)

    @Query("UPDATE items SET low_stock_threshold = :threshold, updated_at = :updatedAt WHERE id = :itemId")
    suspend fun updateLowStockThreshold(itemId: String, threshold: Double, updatedAt: Long = System.currentTimeMillis())
}
