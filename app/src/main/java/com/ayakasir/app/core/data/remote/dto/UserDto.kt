package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    @SerialName("pin_hash") val pinHash: String,
    @SerialName("pin_salt") val pinSalt: String,
    val role: String,
    @SerialName("feature_access") val featureAccess: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    val synced: Boolean = true,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("created_at") val createdAt: Long
)
