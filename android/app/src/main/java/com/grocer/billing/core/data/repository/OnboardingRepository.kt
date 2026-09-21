package com.grocer.billing.core.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.grocer.billing.core.data.local.entities.ItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StarterCatalogItem(
    val id: String,
    val name: String,
    val name_regional: String?,
    val category: String,
    val unit_type: String,
    val suggested_price: Double,
    val default_threshold: Double,
    val barcode: String?
)

class OnboardingRepository(
    private val context: Context,
    private val itemRepository: ItemRepository
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("kirana_onboarding_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }

    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun markOnboardingCompleted(completed: Boolean = true) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    suspend fun loadStarterCatalog(): List<StarterCatalogItem> = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open("starter_catalog.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<StarterCatalogItem>>() {}.type
            Gson().fromJson<List<StarterCatalogItem>>(json, type)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Checks which starter items have already been configured/added to the shop inventory.
     */
    suspend fun getAlreadyConfiguredNames(shopId: String): Set<String> = withContext(Dispatchers.IO) {
        val existing = itemRepository.searchItems(shopId, "")
        existing.map { it.name.lowercase().trim() }.toSet()
    }

    suspend fun setupStarterItem(
        shopId: String,
        starter: StarterCatalogItem,
        price: Double,
        stockQty: Double,
        lowStockThreshold: Double
    ): ItemEntity {
        return itemRepository.addItem(
            shopId = shopId,
            name = starter.name,
            nameRegional = starter.name_regional,
            category = starter.category,
            barcode = starter.barcode,
            unitType = starter.unit_type,
            price = price,
            stockQty = stockQty,
            lowStockThreshold = lowStockThreshold
        )
    }

    suspend fun importAllStarterItems(shopId: String): Int = withContext(Dispatchers.IO) {
        val starters = loadStarterCatalog()
        val configured = getAlreadyConfiguredNames(shopId)
        var count = 0
        for (starter in starters) {
            if (starter.name.lowercase().trim() !in configured) {
                setupStarterItem(
                    shopId = shopId,
                    starter = starter,
                    price = starter.suggested_price,
                    stockQty = if (starter.unit_type == "weight") 25.0 else 30.0,
                    lowStockThreshold = starter.default_threshold
                )
                count++
            }
        }
        count
    }
}
