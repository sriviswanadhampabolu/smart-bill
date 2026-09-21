package com.grocer.billing.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "embeddings",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["item_id"], name = "idx_embeddings_item_id")
    ]
)
data class EmbeddingEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "item_id")
    val itemId: String,
    val vector: ByteArray,
    val source: String = "setup", // 'setup' or 'correction'
    @ColumnInfo(name = "quality_score", defaultValue = "1.0")
    val qualityScore: Float = 1.0f,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmbeddingEntity
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
