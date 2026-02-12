package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("category_type") val categoryType: String? = null,
    val synced: Boolean = true,
    @SerialName("updated_at") val updatedAt: Long
)
