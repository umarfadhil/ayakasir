package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RestaurantDto(
    val id: String,
    val name: String,
    @SerialName("owner_email") val ownerEmail: String,
    @SerialName("owner_phone") val ownerPhone: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("sync_status") val syncStatus: String,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("created_at") val createdAt: Long
)
