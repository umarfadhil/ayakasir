package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeneralLedgerDto(
    val id: String,
    @SerialName("restaurant_id") val restaurantId: String = "",
    val type: String,
    val amount: Long,
    @SerialName("reference_id") val referenceId: String? = null,
    val description: String,
    val date: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("sync_status") val syncStatus: String = "SYNCED",
    @SerialName("updated_at") val updatedAt: Long
)