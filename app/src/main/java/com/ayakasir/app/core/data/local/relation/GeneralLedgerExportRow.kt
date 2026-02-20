package com.ayakasir.app.core.data.local.relation

data class GeneralLedgerExportRow(
    val id: String,
    val type: String,
    val productName: String?,
    val variantName: String?,
    val amount: Long,
    val qty: Int?,
    val description: String
)
