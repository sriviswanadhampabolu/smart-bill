package com.grocer.billing.core.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// --- Auth DTOs ---

data class SignupRequestDto(
    val shop_name: String,
    val owner_name: String,
    val phone: String,
    val pin: String? = null,
    val password: String? = null,
    val upi_id: String? = null,
    val currency_symbol: String = "₹"
)

data class PinLoginRequestDto(
    val phone: String,
    val pin: String
)

data class TokenResponseDto(
    val access_token: String,
    val token_type: String,
    val shop_id: String,
    val owner_name: String,
    val shop_name: String,
    val currency_symbol: String,
    val has_pin: Boolean
)

// --- Sync Push DTOs ---

data class SyncQueueItemDto(
    val id: String,
    val entity_type: String,
    val entity_id: String,
    val operation: String,
    val payload_json: String,
    val created_at: Long
)

data class SyncPushRequestDto(
    val shop_id: String,
    val items: List<SyncQueueItemDto>
)

data class SyncPushResponseDto(
    val status: String,
    val processed_count: Int,
    val acknowledged_ids: List<String>,
    val server_time: String
)

// --- Sync Pull (Cloud Restore) DTOs ---

data class SyncPullShopDto(
    val id: String,
    val name: String,
    val owner_name: String,
    val phone: String,
    val upi_id: String? = null,
    val currency_symbol: String? = "₹",
    val pin_hash: String? = null,
    val settings_json: String? = null
)

data class SyncPullItemDto(
    val id: String,
    val shop_id: String,
    val name: String,
    val name_regional: String? = null,
    val category: String = "General",
    val barcode: String? = null,
    val unit_type: String = "piece",
    val price: Double,
    val stock_qty: Double = 0.0,
    val low_stock_threshold: Double = 5.0,
    val is_active: Boolean = true,
    val image_path: String? = null
)

data class SyncPullEmbeddingDto(
    val id: String,
    val item_id: String,
    val vector_base64: String,
    val source: String = "setup",
    val quality_score: Float = 1.0f,
    val created_at: Long = System.currentTimeMillis()
)

data class SyncPullBillDto(
    val id: String,
    val shop_id: String,
    val bill_number: String,
    val subtotal: Double,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double,
    val payment_method: String = "cash",
    val created_at: Long
)

data class SyncPullBillItemDto(
    val id: String? = null,
    val bill_id: String,
    val item_id: String,
    val name_snapshot: String,
    val qty: Double,
    val unit_type: String = "piece",
    val unit_price_snapshot: Double,
    val line_total: Double
)

data class SyncPullResponseDto(
    val server_time: String,
    val has_more: Boolean = false,
    val shop: SyncPullShopDto? = null,
    val items: List<SyncPullItemDto> = emptyList(),
    val embeddings: List<SyncPullEmbeddingDto> = emptyList(),
    val bills: List<SyncPullBillDto> = emptyList(),
    val bill_items: List<SyncPullBillItemDto> = emptyList()
)

interface SyncApiService {
    @POST("api/v1/auth/signup")
    suspend fun signup(@Body request: SignupRequestDto): Response<TokenResponseDto>

    @POST("api/v1/auth/pin-login")
    suspend fun pinLogin(@Body request: PinLoginRequestDto): Response<TokenResponseDto>

    @POST("api/v1/sync/push")
    suspend fun pushSyncQueue(@Body request: SyncPushRequestDto): Response<SyncPushResponseDto>

    @GET("api/v1/sync/pull")
    suspend fun pullSyncData(): Response<SyncPullResponseDto>
}
