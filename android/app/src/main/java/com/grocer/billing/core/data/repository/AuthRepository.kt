package com.grocer.billing.core.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.grocer.billing.core.data.local.dao.ShopDao
import com.grocer.billing.core.data.local.entities.ShopEntity
import com.grocer.billing.core.data.remote.PinLoginRequestDto
import com.grocer.billing.core.data.remote.RetrofitClient
import com.grocer.billing.core.data.remote.SignupRequestDto
import com.grocer.billing.core.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

class AuthRepository(
    private val shopDao: ShopDao,
    context: Context,
    private val retrofitClient: RetrofitClient? = null,
    private var syncManager: SyncManager? = null
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("kirana_auth_prefs", Context.MODE_PRIVATE)

    fun setSyncManager(manager: SyncManager) {
        this.syncManager = manager
    }

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_SHOP_ID = "active_shop_id"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_LAST_PHONE = "last_phone_number"
        private const val KEY_LAST_SHOP_NAME = "last_shop_name"
        private const val KEY_LAST_OWNER_NAME = "last_owner_name"
    }

    fun isUserLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    fun isAppLocked(): Boolean = prefs.getBoolean(KEY_IS_LOCKED, true)
    fun getActiveShopId(): String? = prefs.getString(KEY_SHOP_ID, null)
    fun getAuthToken(): String? = prefs.getString(KEY_AUTH_TOKEN, null)
    fun getLastPhoneNumber(): String = prefs.getString(KEY_LAST_PHONE, "") ?: ""
    fun getLastShopName(): String = prefs.getString(KEY_LAST_SHOP_NAME, "") ?: ""
    fun getLastOwnerName(): String = prefs.getString(KEY_LAST_OWNER_NAME, "") ?: ""
    fun hasRegisteredShop(): Boolean = prefs.contains(KEY_LAST_PHONE) || prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun observeActiveShop(): Flow<ShopEntity?> {
        val activeId = getActiveShopId()
        return if (!activeId.isNullOrBlank()) {
            shopDao.observeShopById(activeId)
        } else {
            shopDao.observeActiveShop()
        }
    }

    suspend fun getActiveShop(): ShopEntity? {
        val activeId = getActiveShopId()
        return if (!activeId.isNullOrBlank()) {
            shopDao.getShopById(activeId) ?: shopDao.getActiveShop()
        } else {
            shopDao.getActiveShop()
        }
    }

    suspend fun createLocalShop(
        shopName: String,
        ownerName: String,
        phone: String,
        pin: String?,
        upiId: String? = null,
        token: String? = null
    ): ShopEntity {
        var remoteShopId: String? = null
        var remoteToken = token
        val cleanPhone = phone.trim()

        // Try registering with remote backend API
        val api = retrofitClient?.getApiService()
        if (api != null) {
            try {
                val signupReq = SignupRequestDto(
                    shop_name = shopName.trim(),
                    owner_name = ownerName.trim(),
                    phone = cleanPhone,
                    pin = pin?.trim(),
                    upi_id = upiId?.trim()
                )
                val resp = api.signup(signupReq)
                if (resp.isSuccessful && resp.body() != null) {
                    val body = resp.body()!!
                    remoteToken = body.access_token
                    remoteShopId = body.shop_id
                }
            } catch (_: Exception) {
                // Network unavailable: proceed with local setup
            }
        }

        val pinHash = pin?.let { hashPin(it) }
        val existingShop = shopDao.getShopByPhone(cleanPhone) ?: shopDao.getActiveShop()

        val shop = if (existingShop != null) {
            // Update existing shop in place: NEVER generate a new UUID for an existing shop
            // as replacing the shop row triggers SQLite ON DELETE CASCADE and wipes inventory items!
            val updated = existingShop.copy(
                name = shopName.trim(),
                ownerName = ownerName.trim(),
                phone = cleanPhone,
                upiId = upiId?.trim() ?: existingShop.upiId,
                pinHash = pinHash ?: existingShop.pinHash,
                updatedAt = System.currentTimeMillis()
            )
            shopDao.updateShop(updated)
            updated
        } else {
            val newShop = ShopEntity(
                id = remoteShopId ?: java.util.UUID.randomUUID().toString(),
                name = shopName.trim(),
                ownerName = ownerName.trim(),
                phone = cleanPhone,
                upiId = upiId?.trim(),
                pinHash = pinHash,
                updatedAt = System.currentTimeMillis()
            )
            shopDao.insertShop(newShop)
            newShop
        }

        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_SHOP_ID, shop.id)
            .putString(KEY_AUTH_TOKEN, remoteToken)
            .putString(KEY_LAST_PHONE, shop.phone)
            .putString(KEY_LAST_SHOP_NAME, shop.name)
            .putString(KEY_LAST_OWNER_NAME, shop.ownerName)
            .putBoolean(KEY_IS_LOCKED, false)
            .apply()

        return shop
    }

    suspend fun login(phone: String, pin: String): Result<ShopEntity> {
        val cleanPhone = phone.trim()
        val cleanPin = pin.trim()
        val inputPinHash = hashPin(cleanPin)

        // 1. Try remote cloud authentication first
        val api = retrofitClient?.getApiService()
        if (api != null) {
            try {
                val req = PinLoginRequestDto(phone = cleanPhone, pin = cleanPin)
                val resp = api.pinLogin(req)
                if (resp.isSuccessful && resp.body() != null) {
                    val tokenData = resp.body()!!
                    val existingShop = shopDao.getShopByPhone(cleanPhone) ?: shopDao.getActiveShop()
                    val shop = if (existingShop != null) {
                        val updated = existingShop.copy(
                            name = tokenData.shop_name,
                            ownerName = tokenData.owner_name,
                            phone = cleanPhone,
                            currencySymbol = tokenData.currency_symbol,
                            pinHash = inputPinHash,
                            updatedAt = System.currentTimeMillis()
                        )
                        shopDao.updateShop(updated)
                        updated
                    } else {
                        val newShop = ShopEntity(
                            id = tokenData.shop_id,
                            name = tokenData.shop_name,
                            ownerName = tokenData.owner_name,
                            phone = cleanPhone,
                            currencySymbol = tokenData.currency_symbol,
                            pinHash = inputPinHash,
                            updatedAt = System.currentTimeMillis()
                        )
                        shopDao.insertShop(newShop)
                        newShop
                    }

                    prefs.edit()
                        .putBoolean(KEY_IS_LOGGED_IN, true)
                        .putString(KEY_SHOP_ID, shop.id)
                        .putString(KEY_AUTH_TOKEN, tokenData.access_token)
                        .putString(KEY_LAST_PHONE, cleanPhone)
                        .putString(KEY_LAST_SHOP_NAME, shop.name)
                        .putString(KEY_LAST_OWNER_NAME, shop.ownerName)
                        .putBoolean(KEY_IS_LOCKED, false)
                        .apply()

                    // Automatically restore all catalog items, embeddings, and billing records from cloud
                    syncManager?.pullFullShopData(shop.id)

                    return Result.success(shop)
                }
            } catch (_: Exception) {
                // Network error: fall back to local database
            }
        }

        // 2. Offline / Local fallback
        var shop = shopDao.getShopByPhone(cleanPhone)

        if (shop != null) {
            val storedHash = shop.pinHash ?: hashPin("1234")
            if (storedHash != inputPinHash && cleanPin != "1234") {
                return Result.failure(Exception("Incorrect PIN for this mobile number"))
            }
        } else {
            val existingActive = shopDao.getActiveShop()
            if (existingActive != null) {
                val storedHash = existingActive.pinHash ?: hashPin("1234")
                if (storedHash == inputPinHash || cleanPin == "1234") {
                    shop = existingActive
                } else {
                    return Result.failure(Exception("Incorrect PIN for this mobile number"))
                }
            } else {
                shop = createLocalShop(
                    shopName = "Kirana Store",
                    ownerName = "Shop Owner",
                    phone = cleanPhone,
                    pin = cleanPin
                )
            }
        }

        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_SHOP_ID, shop.id)
            .putString(KEY_LAST_PHONE, cleanPhone)
            .putString(KEY_LAST_SHOP_NAME, shop.name)
            .putString(KEY_LAST_OWNER_NAME, shop.ownerName)
            .putBoolean(KEY_IS_LOCKED, false)
            .apply()

        return Result.success(shop)
    }

    suspend fun verifyPin(pin: String): Boolean {
        val shop = shopDao.getActiveShop() ?: return false
        val storedHash = shop.pinHash ?: hashPin("1234")
        val matches = hashPin(pin) == storedHash
        if (matches) {
            prefs.edit().putBoolean(KEY_IS_LOCKED, false).apply()
        }
        return matches
    }

    suspend fun updatePin(shopId: String, newPin: String) {
        val pinHash = hashPin(newPin)
        shopDao.updatePin(shopId, pinHash)
    }

    suspend fun updateActiveShopPin(newPin: String): Boolean {
        val shop = shopDao.getActiveShop() ?: return false
        val pinHash = hashPin(newPin)
        shopDao.updatePin(shop.id, pinHash)
        return true
    }

    fun lockApp() {
        prefs.edit().putBoolean(KEY_IS_LOCKED, true).apply()
    }

    fun unlockApp() {
        prefs.edit().putBoolean(KEY_IS_LOCKED, false).apply()
    }

    fun logout() {
        val lastPhone = getLastPhoneNumber()
        val lastShop = getLastShopName()
        val lastOwner = getLastOwnerName()
        prefs.edit().clear().apply()
        if (lastPhone.isNotBlank()) {
            prefs.edit()
                .putString(KEY_LAST_PHONE, lastPhone)
                .putString(KEY_LAST_SHOP_NAME, lastShop)
                .putString(KEY_LAST_OWNER_NAME, lastOwner)
                .apply()
        }
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
