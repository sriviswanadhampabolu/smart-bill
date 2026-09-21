package com.grocer.billing.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "bill_items",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bill_id"], name = "idx_bill_items_bill_id")
    ]
)
data class BillItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "bill_id")
    val billId: String,
    @ColumnInfo(name = "item_id")
    val itemId: String,
    @ColumnInfo(name = "name_snapshot")
    val nameSnapshot: String,
    val qty: Double,
    @ColumnInfo(name = "unit_type")
    val unitType: String,
    @ColumnInfo(name = "unit_price_snapshot")
    val unitPriceSnapshot: Double,
    @ColumnInfo(name = "line_total")
    val lineTotal: Double
)
