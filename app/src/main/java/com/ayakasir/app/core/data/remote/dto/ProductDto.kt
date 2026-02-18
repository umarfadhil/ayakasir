package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    val id: String,
    @SerialName("category_id") val categoryId: String?,
    val name: String,
    val description: String? = null,
    val price: Long,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("product_type") val productType: String? = null,
    @SerialName("restaurant_id") val restaurantId: String = "",
    @SerialName("sync_status") val syncStatus: String = "SYNCED",
    @SerialName("updated_at") val updatedAt: Long
)
