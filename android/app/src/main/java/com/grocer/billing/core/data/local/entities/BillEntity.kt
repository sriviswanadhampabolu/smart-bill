package com.grocer.billing.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "bills",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["id"],
            childColumns = ["shop_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["shop_id", "created_at"], name = "idx_bills_shop_date"),
        Index(value = ["synced_at"], name = "idx_bills_synced")
    ]
)
data class BillEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "shop_id")
    val shopId: String,
    @ColumnInfo(name = "bill_number")
    val billNumber: String,
    val subtotal: Double,
    @ColumnInfo(defaultValue = "0")
    val discount: Double = 0.0,
    @ColumnInfo(defaultValue = "0")
    val tax: Double = 0.0,
    val total: Double,
    @ColumnInfo(name = "payment_method")
    val paymentMethod: String, // 'cash', 'upi', 'credit'
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "synced_at")
    val syncedAt: Long? = null
)
