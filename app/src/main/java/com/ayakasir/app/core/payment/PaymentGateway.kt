package com.ayakasir.app.core.payment

interface PaymentGateway {
    suspend fun initiatePayment(amount: Long, referenceId: String): PaymentResult
    suspend fun checkStatus(referenceId: String): PaymentResult
    fun getProviderName(): String
}
