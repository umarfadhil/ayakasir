package com.ayakasir.app.core.payment

import com.ayakasir.app.core.data.local.datastore.QrisSettingsDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class QrisPaymentGateway @Inject constructor(
    private val qrisSettingsDataStore: QrisSettingsDataStore
) : PaymentGateway {

    override suspend fun initiatePayment(amount: Long, referenceId: String): PaymentResult {
        val imageUri = qrisSettingsDataStore.qrisImageUri.first().trim()
        if (imageUri.isBlank()) {
            return PaymentResult.Failed(
                referenceId = referenceId,
                errorCode = "QRIS_NOT_CONFIGURED",
                message = "QRIS belum dikonfigurasi"
            )
        }

        return PaymentResult.Pending(
            referenceId = referenceId,
            qrCodeData = imageUri,
            expiresAt = null
        )
    }

    override suspend fun checkStatus(referenceId: String): PaymentResult {
        val imageUri = qrisSettingsDataStore.qrisImageUri.first().trim()
        return if (imageUri.isBlank()) {
            PaymentResult.Failed(
                referenceId = referenceId,
                errorCode = "QRIS_NOT_CONFIGURED",
                message = "QRIS belum dikonfigurasi"
            )
        } else {
            PaymentResult.Pending(
                referenceId = referenceId,
                qrCodeData = imageUri,
                expiresAt = null
            )
        }
    }

    override fun getProviderName(): String = "QRIS (Statis)"
}
