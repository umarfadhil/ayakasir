package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoodsReceivingItemDto(
    val id: String,
    @SerialName("receiving_id") val receivingId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("variant_id") val variantId: String,
    val qty: Int,
    @SerialName("cost_per_unit") val costPerUnit: Long,
    val unit: String = "pcs",
    val synced: Boolean = true,
    @SerialName("updated_at") val updatedAt: Long
)
