package com.ayakasir.app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionItemDto(
    val id: String,
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("variant_id") val variantId: String,
    @SerialName("product_name") val productName: String,
    @SerialName("variant_name") val variantName: String?,
    val qty: Int,
    @SerialName("unit_price") val unitPrice: Long,
    val subtotal: Long,
    val synced: Boolean = true,
    @SerialName("updated_at") val updatedAt: Long
)
