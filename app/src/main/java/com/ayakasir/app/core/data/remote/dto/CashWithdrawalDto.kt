package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CashWithdrawalDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val amount: Long,
    val reason: String,
    val date: Long,
    val synced: Boolean = true,
    @SerialName("updated_at") val updatedAt: Long
)
