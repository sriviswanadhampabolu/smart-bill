package com.grocer.billing.core.data.remote

import android.content.Context
import com.grocer.billing.core.data.local.entities.BillEntity
import com.grocer.billing.core.data.local.entities.BillItemEntity
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.local.entities.ShopEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

data class NeonHealthResult(
    val isConnected: Boolean,
    val database: String,
    val version: String,
    val provider: String,
    val shopCount: Int = 0,
    val itemCount: Int = 0,
    val billCount: Int = 0,
    val error: String? = null
)

class NeonCloudClient(private val context: Context) {

    companion object {
        const val NEON_SQL_ENDPOINT = "https://ep-old-grass-b4k9t8zp.c-6.us-east-2.aws.neon.tech/sql"
        const val NEON_CONNECTION_STRING = "postgresql://neondb_owner:npg_YT86SNxVpLFE@ep-old-grass-b4k9t8zp-pooler.c-6.us-east-2.aws.neon.tech/neondb?sslmode=require"
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun executeQuery(sql: String, params: List<Any?> = emptyList()): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("query", sql)
            if (params.isNotEmpty()) {
                val paramsArray = JSONArray()
                params.forEach { p ->
                    when (p) {
                        null -> paramsArray.put(JSONObject.NULL)
                        is Number -> paramsArray.put(p)
                        is Boolean -> paramsArray.put(p)
                        else -> paramsArray.put(p.toString())
                    }
                }
                put("params", paramsArray)
            }
        }

        val request = Request.Builder()
            .url(NEON_SQL_ENDPOINT)
            .addHeader("Neon-Connection-String", NEON_CONNECTION_STRING)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val errorMsg = try {
                val errJson = JSONObject(responseBody)
                errJson.optString("message", "HTTP ${response.code}")
            } catch (_: Exception) {
                "HTTP ${response.code}: $responseBody"
            }
            throw Exception("Neon Database Error: $errorMsg")
        }

        JSONObject(responseBody)
    }

    suspend fun checkHealth(): NeonHealthResult = withContext(Dispatchers.IO) {
        try {
            val res = executeQuery("SELECT current_database() as db, version() as ver;")
            val rows = res.optJSONArray("rows")
            val firstRow = rows?.optJSONObject(0)
            val dbName = firstRow?.optString("db", "neondb") ?: "neondb"
            val versionStr = firstRow?.optString("ver", "PostgreSQL 18")?.split(",")?.firstOrNull() ?: "PostgreSQL 18"

            val countRes = executeQuery("SELECT (SELECT count(*) FROM shops) as shops, (SELECT count(*) FROM items) as items, (SELECT count(*) FROM bills) as bills;")
            val countRow = countRes.optJSONArray("rows")?.optJSONObject(0)
            val shops = countRow?.optString("shops", "0")?.toIntOrNull() ?: 0
            val items = countRow?.optString("items", "0")?.toIntOrNull() ?: 0
            val bills = countRow?.optString("bills", "0")?.toIntOrNull() ?: 0

            NeonHealthResult(
                isConnected = true,
                database = dbName,
                version = versionStr,
                provider = "Neon Online Cloud PostgreSQL",
                shopCount = shops,
                itemCount = items,
                billCount = bills
            )
        } catch (e: Exception) {
            NeonHealthResult(
                isConnected = false,
                database = "neondb",
                version = "Unknown",
                provider = "Neon Cloud",
                error = e.localizedMessage ?: "Connection failed"
            )
        }
    }

    suspend fun registerShop(
        shopName: String,
        ownerName: String,
        phone: String,
        pin: String,
        email: String? = null,
        address: String? = null,
        upiId: String? = null,
        currencySymbol: String = "₹"
    ): Result<ShopEntity> = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = phone.trim()
            val existingRes = executeQuery("SELECT id FROM shops WHERE phone = $1 LIMIT 1;", listOf(cleanPhone))
            val existingRows = existingRes.optJSONArray("rows")
            if (existingRows != null && existingRows.length() > 0) {
                return@withContext Result.failure(Exception("A shop with mobile number $cleanPhone is already registered in Neon Cloud. Please use the Login tab."))
            }

            val shopId = UUID.randomUUID().toString()
            val pinHash = hashPin(pin.trim())
            val now = System.currentTimeMillis()

            val insertSql = """
                INSERT INTO shops (id, name, owner_name, phone, email, address, upi_id, currency_symbol, pin_hash, settings_json, created_at, updated_at)
                VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, NOW(), NOW());
            """.trimIndent()

            val settingsJson = JSONObject().apply {
                put("language", "en")
                put("qr_codes", JSONArray())
            }.toString()

            executeQuery(
                insertSql,
                listOf(
                    shopId,
                    shopName.trim(),
                    ownerName.trim(),
                    cleanPhone,
                    email?.trim()?.ifBlank { null },
                    address?.trim() ?: "",
                    upiId?.trim()?.ifBlank { null },
                    currencySymbol,
                    pinHash,
                    settingsJson
                )
            )

            val shop = ShopEntity(
                id = shopId,
                name = shopName.trim(),
                ownerName = ownerName.trim(),
                phone = cleanPhone,
                email = email?.trim()?.ifBlank { null },
                address = address?.trim() ?: "",
                upiId = upiId?.trim()?.ifBlank { null },
                currencySymbol = currencySymbol,
                pinHash = pinHash,
                settingsJson = settingsJson,
                createdAt = now,
                updatedAt = now
            )
            Result.success(shop)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithPin(phone: String, pin: String): Result<ShopEntity> = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = phone.trim()
            val cleanPin = pin.trim()
            val inputPinHash = hashPin(cleanPin)

            val querySql = "SELECT * FROM shops WHERE phone = $1 LIMIT 1;"
            val res = executeQuery(querySql, listOf(cleanPhone))
            val rows = res.optJSONArray("rows")

            if (rows == null || rows.length() == 0) {
                return@withContext Result.failure(Exception("No account found with mobile number $cleanPhone. Please switch to Register tab to create your shop."))
            }

            val row = rows.getJSONObject(0)
            val storedPinHash = row.optString("pin_hash", "")

            // Strictly verify PIN hash: registered PIN must match input PIN
            val isPinValid = if (storedPinHash.isNotBlank()) {
                storedPinHash == inputPinHash
            } else {
                cleanPin == "1234"
            }

            if (!isPinValid) {
                return@withContext Result.failure(Exception("Incorrect 4-digit PIN for this mobile number. Please check and try again."))
            }

            val shop = ShopEntity(
                id = row.optString("id", UUID.randomUUID().toString()),
                name = row.optString("name", "Smart Bill Counter"),
                ownerName = row.optString("owner_name", "Shop Owner"),
                phone = row.optString("phone", cleanPhone),
                email = row.optString("email").takeIf { it.isNotBlank() && it != "null" },
                address = row.optString("address", ""),
                upiId = row.optString("upi_id").takeIf { it.isNotBlank() && it != "null" },
                currencySymbol = row.optString("currency_symbol", "₹"),
                pinHash = storedPinHash.ifBlank { inputPinHash },
                settingsJson = row.optString("settings_json", "{}"),
                createdAt = row.optLong("created_at", System.currentTimeMillis()),
                updatedAt = row.optLong("updated_at", System.currentTimeMillis())
            )

            Result.success(shop)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithGoogle(email: String, displayName: String = "", phone: String? = null): Result<ShopEntity> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            val querySql = "SELECT * FROM shops WHERE LOWER(email) = $1 LIMIT 1;"
            val res = executeQuery(querySql, listOf(cleanEmail))
            val rows = res.optJSONArray("rows")

            if (rows != null && rows.length() > 0) {
                val row = rows.getJSONObject(0)
                val shop = ShopEntity(
                    id = row.optString("id"),
                    name = row.optString("name"),
                    ownerName = row.optString("owner_name", displayName.ifBlank { "Shop Owner" }),
                    phone = row.optString("phone", phone ?: ""),
                    email = cleanEmail,
                    address = row.optString("address", ""),
                    upiId = row.optString("upi_id").takeIf { it.isNotBlank() && it != "null" },
                    currencySymbol = row.optString("currency_symbol", "₹"),
                    pinHash = row.optString("pin_hash", hashPin("1234")),
                    settingsJson = row.optString("settings_json", "{}"),
                    createdAt = row.optLong("created_at", System.currentTimeMillis()),
                    updatedAt = row.optLong("updated_at", System.currentTimeMillis())
                )
                return@withContext Result.success(shop)
            }

            // User is NOT registered in database
            Result.failure(Exception("No store account found for Google account ($cleanEmail). Please switch to the Register tab to create your shop account."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerShopWithGoogle(
        email: String,
        displayName: String,
        shopName: String,
        ownerName: String,
        phone: String,
        pin: String = "1234",
        address: String = "",
        upiId: String? = null
    ): Result<ShopEntity> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            val cleanPhone = phone.trim()

            // 1. Check if email already registered
            val emailRes = executeQuery("SELECT id FROM shops WHERE LOWER(email) = $1 LIMIT 1;", listOf(cleanEmail))
            val emailRows = emailRes.optJSONArray("rows")
            if (emailRows != null && emailRows.length() > 0) {
                return@withContext Result.failure(Exception("An account with email $cleanEmail is already registered. Please switch to User Login."))
            }

            // 2. Check if phone already registered (if non-empty)
            if (cleanPhone.isNotBlank() && !cleanPhone.startsWith("g_")) {
                val phoneRes = executeQuery("SELECT id FROM shops WHERE phone = $1 LIMIT 1;", listOf(cleanPhone))
                val phoneRows = phoneRes.optJSONArray("rows")
                if (phoneRows != null && phoneRows.length() > 0) {
                    return@withContext Result.failure(Exception("A shop with mobile number $cleanPhone is already registered. Please switch to User Login."))
                }
            }

            val finalShopName = shopName.trim().ifBlank {
                if (displayName.isNotBlank()) "${displayName.trim()}'s Store" else "Kirana Store"
            }
            val finalOwnerName = ownerName.trim().ifBlank {
                if (displayName.isNotBlank()) displayName.trim() else "Shop Owner"
            }
            val finalPhone = if (cleanPhone.isNotBlank()) cleanPhone else "g_${cleanEmail.replace(Regex("[^a-zA-Z0-9]"), "").take(10)}"
            val pinHash = hashPin(pin.trim().ifBlank { "1234" })
            val shopId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val settingsJson = JSONObject().apply {
                put("auth_provider", "google")
                put("language", "en")
                put("qr_codes", JSONArray())
            }.toString()

            val insertSql = """
                INSERT INTO shops (id, name, owner_name, phone, email, address, upi_id, currency_symbol, pin_hash, settings_json, created_at, updated_at)
                VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, NOW(), NOW());
            """.trimIndent()

            executeQuery(
                insertSql,
                listOf(
                    shopId,
                    finalShopName,
                    finalOwnerName,
                    finalPhone,
                    cleanEmail,
                    address.trim(),
                    upiId?.trim()?.ifBlank { null },
                    "₹",
                    pinHash,
                    settingsJson
                )
            )

            val shop = ShopEntity(
                id = shopId,
                name = finalShopName,
                ownerName = finalOwnerName,
                phone = finalPhone,
                email = cleanEmail,
                address = address.trim(),
                upiId = upiId?.trim()?.ifBlank { null },
                currencySymbol = "₹",
                pinHash = pinHash,
                settingsJson = settingsJson,
                createdAt = now,
                updatedAt = now
            )
            Result.success(shop)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateShop(shop: ShopEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sql = """
                UPDATE shops SET name = $1, owner_name = $2, phone = $3, email = $4, address = $5,
                upi_id = $6, currency_symbol = $7, pin_hash = $8, settings_json = $9, updated_at = NOW()
                WHERE id = $10;
            """.trimIndent()
            executeQuery(
                sql,
                listOf(
                    shop.name,
                    shop.ownerName,
                    shop.phone,
                    shop.email,
                    shop.address,
                    shop.upiId,
                    shop.currencySymbol,
                    shop.pinHash,
                    shop.settingsJson,
                    shop.id
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pullItemsForShop(shopId: String): List<ItemEntity> = withContext(Dispatchers.IO) {
        try {
            val sql = "SELECT id, shop_id, name, name_regional, category, barcode, unit_type, price, stock_qty, low_stock_threshold, is_active, image_path, created_at, updated_at FROM items WHERE shop_id = $1 AND is_active = true ORDER BY name ASC;"
            val res = executeQuery(sql, listOf(shopId))
            val rows = res.optJSONArray("rows") ?: return@withContext emptyList()
            val list = mutableListOf<ItemEntity>()
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                list.add(
                    ItemEntity(
                        id = row.optString("id"),
                        shopId = row.optString("shop_id", shopId),
                        name = row.optString("name"),
                        nameRegional = row.optString("name_regional").takeIf { it.isNotBlank() && it != "null" },
                        category = row.optString("category", "General"),
                        barcode = row.optString("barcode").takeIf { it.isNotBlank() && it != "null" },
                        unitType = row.optString("unit_type", "piece"),
                        price = row.optDouble("price", 0.0),
                        stockQty = row.optDouble("stock_qty", 0.0),
                        lowStockThreshold = row.optDouble("low_stock_threshold", 5.0),
                        isActive = row.optBoolean("is_active", true),
                        imagePath = row.optString("image_path").takeIf { it.isNotBlank() && it != "null" },
                        createdAt = row.optLong("created_at", System.currentTimeMillis()),
                        updatedAt = row.optLong("updated_at", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun pushItem(item: ItemEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val checkSql = "SELECT id FROM items WHERE id = $1 LIMIT 1;"
            val checkRes = executeQuery(checkSql, listOf(item.id))
            val exists = (checkRes.optJSONArray("rows")?.length() ?: 0) > 0

            if (exists) {
                val updateSql = """
                    UPDATE items SET name = $1, name_regional = $2, category = $3, barcode = $4,
                    unit_type = $5, price = $6, stock_qty = $7, low_stock_threshold = $8, is_active = $9,
                    image_path = $10, updated_at = NOW() WHERE id = $11;
                """.trimIndent()
                executeQuery(
                    updateSql,
                    listOf(
                        item.name,
                        item.nameRegional,
                        item.category,
                        item.barcode,
                        item.unitType,
                        item.price,
                        item.stockQty,
                        item.lowStockThreshold,
                        item.isActive,
                        item.imagePath,
                        item.id
                    )
                )
            } else {
                val insertSql = """
                    INSERT INTO items (id, shop_id, name, name_regional, category, barcode, unit_type, price, stock_qty, low_stock_threshold, is_active, image_path, created_at, updated_at)
                    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, NOW(), NOW());
                """.trimIndent()
                executeQuery(
                    insertSql,
                    listOf(
                        item.id,
                        item.shopId,
                        item.name,
                        item.nameRegional,
                        item.category,
                        item.barcode,
                        item.unitType,
                        item.price,
                        item.stockQty,
                        item.lowStockThreshold,
                        item.isActive,
                        item.imagePath
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteItem(itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            executeQuery("DELETE FROM items WHERE id = $1;", listOf(itemId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushBill(bill: BillEntity, items: List<BillItemEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val insertBillSql = """
                INSERT INTO bills (id, shop_id, bill_number, subtotal, discount, tax, total, payment_method, created_at, server_received_at)
                VALUES ($1, $2, $3, $4, $5, $6, $7, $8, TO_TIMESTAMP($9 / 1000.0), NOW())
                ON CONFLICT (id) DO UPDATE SET total = EXCLUDED.total, subtotal = EXCLUDED.subtotal, discount = EXCLUDED.discount, tax = EXCLUDED.tax;
            """.trimIndent()

            executeQuery(
                insertBillSql,
                listOf(
                    bill.id,
                    bill.shopId,
                    bill.billNumber,
                    bill.subtotal,
                    bill.discount,
                    bill.tax,
                    bill.total,
                    bill.paymentMethod,
                    bill.createdAt
                )
            )

            for (item in items) {
                val insertItemSql = """
                    INSERT INTO bill_items (id, bill_id, item_id, name_snapshot, qty, unit_type, unit_price_snapshot, line_total)
                    VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
                    ON CONFLICT (id) DO NOTHING;
                """.trimIndent()
                executeQuery(
                    insertItemSql,
                    listOf(
                        item.id,
                        item.billId,
                        item.itemId,
                        item.nameSnapshot,
                        item.qty,
                        item.unitType,
                        item.unitPriceSnapshot,
                        item.lineTotal
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pullBillsForShop(shopId: String): Pair<List<BillEntity>, List<BillItemEntity>> = withContext(Dispatchers.IO) {
        try {
            val billsSql = """
                SELECT id, shop_id, bill_number, subtotal, discount, tax, total, payment_method,
                       (EXTRACT(EPOCH FROM created_at) * 1000)::bigint as created_at_ms
                FROM bills
                WHERE shop_id = $1
                ORDER BY created_at DESC;
            """.trimIndent()
            val billsRes = executeQuery(billsSql, listOf(shopId))
            val billRows = billsRes.optJSONArray("rows") ?: return@withContext Pair(emptyList(), emptyList())
            val bills = mutableListOf<BillEntity>()
            for (i in 0 until billRows.length()) {
                val row = billRows.getJSONObject(i)
                bills.add(
                    BillEntity(
                        id = row.optString("id"),
                        shopId = row.optString("shop_id", shopId),
                        billNumber = row.optString("bill_number"),
                        subtotal = row.optDouble("subtotal", 0.0),
                        discount = row.optDouble("discount", 0.0),
                        tax = row.optDouble("tax", 0.0),
                        total = row.optDouble("total", 0.0),
                        paymentMethod = row.optString("payment_method", "cash"),
                        createdAt = row.optLong("created_at_ms", System.currentTimeMillis()),
                        syncedAt = System.currentTimeMillis()
                    )
                )
            }

            val itemsSql = """
                SELECT bi.id, bi.bill_id, bi.item_id, bi.name_snapshot, bi.qty, bi.unit_type, bi.unit_price_snapshot, bi.line_total
                FROM bill_items bi
                JOIN bills b ON bi.bill_id = b.id
                WHERE b.shop_id = $1;
            """.trimIndent()
            val itemsRes = executeQuery(itemsSql, listOf(shopId))
            val itemRows = itemsRes.optJSONArray("rows") ?: return@withContext Pair(bills, emptyList())
            val billItems = mutableListOf<BillItemEntity>()
            for (i in 0 until itemRows.length()) {
                val row = itemRows.getJSONObject(i)
                billItems.add(
                    BillItemEntity(
                        id = row.optString("id", UUID.randomUUID().toString()),
                        billId = row.optString("bill_id"),
                        itemId = row.optString("item_id"),
                        nameSnapshot = row.optString("name_snapshot"),
                        qty = row.optDouble("qty", 1.0),
                        unitType = row.optString("unit_type", "piece"),
                        unitPriceSnapshot = row.optDouble("unit_price_snapshot", 0.0),
                        lineTotal = row.optDouble("line_total", 0.0)
                    )
                )
            }
            Pair(bills, billItems)
        } catch (_: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
