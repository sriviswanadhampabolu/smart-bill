package com.grocer.billing.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "stock_log",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["item_id", "created_at"], name = "idx_stock_log_item"),
        Index(value = ["bill_id"], name = "idx_stock_log_bill")
    ]
)
data class StockLogEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "item_id")
    val itemId: String,
    @ColumnInfo(name = "change_qty")
    val changeQty: Double, // negative for sale, positive for restock/correction
    val reason: String, // 'sale', 'restock', 'correction'
    @ColumnInfo(name = "bill_id")
    val billId: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
