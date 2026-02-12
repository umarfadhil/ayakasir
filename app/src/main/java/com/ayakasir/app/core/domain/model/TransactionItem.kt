package com.ayakasir.app.core.domain.model

data class TransactionItem(
    val id: String,
    val transactionId: String,
    val productId: String,
    val variantId: String,
    val productName: String,
    val variantName: String?,
    val qty: Int,
    val unitPrice: Long,
    val subtotal: Long
)
