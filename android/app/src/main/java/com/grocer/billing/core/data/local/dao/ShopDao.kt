package com.grocer.billing.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.grocer.billing.core.data.local.entities.ShopEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {
    @Query("SELECT * FROM shops ORDER BY updated_at DESC LIMIT 1")
    suspend fun getActiveShop(): ShopEntity?

    @Query("SELECT * FROM shops ORDER BY updated_at DESC LIMIT 1")
    fun observeActiveShop(): Flow<ShopEntity?>

    @Query("SELECT * FROM shops WHERE id = :shopId LIMIT 1")
    suspend fun getShopById(shopId: String): ShopEntity?

    @Query("SELECT * FROM shops WHERE id = :shopId LIMIT 1")
    fun observeShopById(shopId: String): Flow<ShopEntity?>

    @Query("SELECT * FROM shops WHERE phone = :phone ORDER BY updated_at DESC LIMIT 1")
    suspend fun getShopByPhone(phone: String): ShopEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShop(shop: ShopEntity)

    @Update
    suspend fun updateShop(shop: ShopEntity)

    @Query("UPDATE shops SET pin_hash = :pinHash, updated_at = :updatedAt WHERE id = :shopId")
    suspend fun updatePin(shopId: String, pinHash: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE shops SET upi_id = :upiId, updated_at = :updatedAt WHERE id = :shopId")
    suspend fun updateUpiId(shopId: String, upiId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE shops SET settings_json = :settingsJson, updated_at = :updatedAt WHERE id = :shopId")
    suspend fun updateSettings(shopId: String, settingsJson: String, updatedAt: Long = System.currentTimeMillis())
}
