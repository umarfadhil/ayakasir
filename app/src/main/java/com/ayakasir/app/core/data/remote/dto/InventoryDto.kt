package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InventoryDto(
    @SerialName("product_id") val productId: String,
    @SerialName("variant_id") val variantId: String,
    @SerialName("current_qty") val currentQty: Int = 0,
    @SerialName("min_qty") val minQty: Int = 0,
    @SerialName("restaurant_id") val restaurantId: String = "",
    @SerialName("sync_status") val syncStatus: String = "SYNCED",
    @SerialName("updated_at") val updatedAt: Long
)
