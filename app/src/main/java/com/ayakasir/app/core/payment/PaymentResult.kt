package com.ayakasir.app.core.payment

sealed interface PaymentResult {
    data class Success(
        val referenceId: String,
        val providerTransactionId: String?,
        val amount: Long
    ) : PaymentResult

    data class Pending(
        val referenceId: String,
        val qrCodeData: String?,
        val expiresAt: Long?
    ) : PaymentResult

    data class Failed(
        val referenceId: String,
        val errorCode: String?,
        val message: String
    ) : PaymentResult

    data object Cancelled : PaymentResult
}
