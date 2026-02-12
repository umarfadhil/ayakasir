package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VariantDto(
    val id: String,
    @SerialName("product_id") val productId: String,
    val name: String,
    @SerialName("price_adjustment") val priceAdjustment: Long = 0,
    val synced: Boolean = true,
    @SerialName("updated_at") val updatedAt: Long
)
