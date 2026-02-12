package com.ayakasir.app.core.domain.model

data class Transaction(
    val id: String,
    val userId: String,
    val date: Long,
    val total: Long,
    val paymentMethod: PaymentMethod,
    val status: TransactionStatus,
    val items: List<TransactionItem> = emptyList()
)
