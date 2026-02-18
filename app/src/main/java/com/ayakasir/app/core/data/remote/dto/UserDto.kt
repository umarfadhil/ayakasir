package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("pin_hash") val pinHash: String,
    @SerialName("pin_salt") val pinSalt: String,
    @SerialName("password_hash") val passwordHash: String? = null,
    @SerialName("password_salt") val passwordSalt: String? = null,
    val role: String,
    @SerialName("restaurant_id") val restaurantId: String? = null,
    @SerialName("feature_access") val featureAccess: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("sync_status") val syncStatus: String = "SYNCED",
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("created_at") val createdAt: Long
)
