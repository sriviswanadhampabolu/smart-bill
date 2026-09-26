package com.grocer.billing.core.data.model

import java.util.UUID

data class UpiQrCode(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val upiId: String,
    val imagePath: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
