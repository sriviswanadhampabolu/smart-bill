package com.grocer.billing.core.data.repository

import com.grocer.billing.core.data.local.dao.ShopDao
import com.grocer.billing.core.data.local.entities.ShopEntity
import kotlinx.coroutines.flow.Flow

class ShopRepository(
    private val shopDao: ShopDao
) {
    fun observeShop(): Flow<ShopEntity?> = shopDao.observeActiveShop()
    fun observeShopById(shopId: String): Flow<ShopEntity?> = shopDao.observeShopById(shopId)

    suspend fun getShop(): ShopEntity? = shopDao.getActiveShop()
    suspend fun getShopById(shopId: String): ShopEntity? = shopDao.getShopById(shopId)

    suspend fun updateShopProfile(shop: ShopEntity) {
        shopDao.updateShop(shop.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateUpiId(shopId: String, upiId: String) {
        shopDao.updateUpiId(shopId, upiId.trim())
    }

    suspend fun updateSettingsJson(shopId: String, settingsJson: String) {
        shopDao.updateSettings(shopId, settingsJson)
    }

    suspend fun updatePin(shopId: String, newPin: String) {
        val pinHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(newPin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        shopDao.updatePin(shopId, pinHash)
    }
}
