package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoodsReceivingDto(
    val id: String,
    @SerialName("vendor_id") val vendorId: String?,
    val date: Long,
    val notes: String? = null,
    val synced: Boolean = true,
    @SerialName("updated_at") val updatedAt: Long
)
