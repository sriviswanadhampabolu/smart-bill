package com.grocer.billing.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["retry_count", "created_at"], name = "idx_sync_queue_retry")
    ]
)
data class SyncQueueEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "entity_type")
    val entityType: String, // 'shop', 'item', 'embedding', 'bill', 'stock_log'
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    val operation: String, // 'INSERT', 'UPDATE', 'DELETE'
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "retry_count", defaultValue = "0")
    val retryCount: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
