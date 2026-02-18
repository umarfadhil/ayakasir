package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VariantDto(
    val id: String,
    @SerialName("product_id") val productId: String,
    val name: String,
    @SerialName("price_adjustment") val priceAdjustment: Long = 0,
    @SerialName("restaurant_id") val restaurantId: String = "",
    @SerialName("sync_status") val syncStatus: String = "SYNCED",
    @SerialName("updated_at") val updatedAt: Long
)
