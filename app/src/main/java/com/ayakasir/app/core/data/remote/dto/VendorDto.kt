package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VendorDto(
    val id: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val synced: Boolean = true,
    @SerialName("updated_at") val updatedAt: Long
)
