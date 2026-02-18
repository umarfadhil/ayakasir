package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val date: Long,
    val total: Long,
    @SerialName("payment_method") val paymentMethod: String,
    val status: String,
    @SerialName("restaurant_id") val restaurantId: String = "",
    @SerialName("sync_status") val syncStatus: String = "SYNCED",
    @SerialName("updated_at") val updatedAt: Long
)
