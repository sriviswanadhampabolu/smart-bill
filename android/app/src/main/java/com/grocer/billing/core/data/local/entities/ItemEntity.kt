package com.grocer.billing.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["id"],
            childColumns = ["shop_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["shop_id", "is_active"], name = "idx_items_shop_active"),
        Index(value = ["barcode"], name = "idx_items_barcode"),
        Index(value = ["stock_qty", "low_stock_threshold"], name = "idx_items_low_stock")
    ]
)
data class ItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "shop_id")
    val shopId: String,
    val name: String,
    @ColumnInfo(name = "name_regional")
    val nameRegional: String? = null,
    val category: String,
    val barcode: String? = null,
    @ColumnInfo(name = "unit_type")
    val unitType: String, // 'piece' or 'weight'
    val price: Double,
    @ColumnInfo(name = "stock_qty", defaultValue = "0")
    val stockQty: Double = 0.0,
    @ColumnInfo(name = "low_stock_threshold", defaultValue = "5")
    val lowStockThreshold: Double = 5.0,
    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,
    @ColumnInfo(name = "image_path")
    val imagePath: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
