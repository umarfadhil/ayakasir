package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductComponentDto(
    val id: String,
    @SerialName("parent_product_id") val parentProductId: String,
    @SerialName("component_product_id") val componentProductId: String,
    @SerialName("component_variant_id") val componentVariantId: String,
    @SerialName("required_qty") val requiredQty: Int,
    val unit: String = "pcs",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("restaurant_id") val restaurantId: String = "",
    @SerialName("sync_status") val syncStatus: String = "SYNCED",
    @SerialName("updated_at") val updatedAt: Long
)
