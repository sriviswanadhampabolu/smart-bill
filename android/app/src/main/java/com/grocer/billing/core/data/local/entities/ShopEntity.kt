package com.grocer.billing.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "shops")
data class ShopEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    @ColumnInfo(name = "owner_name")
    val ownerName: String,
    val phone: String,
    @ColumnInfo(name = "upi_id")
    val upiId: String? = null,
    @ColumnInfo(name = "currency_symbol", defaultValue = "₹")
    val currencySymbol: String = "₹",
    @ColumnInfo(name = "pin_hash")
    val pinHash: String? = null,
    @ColumnInfo(name = "settings_json", defaultValue = "{}")
    val settingsJson: String = "{}",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
