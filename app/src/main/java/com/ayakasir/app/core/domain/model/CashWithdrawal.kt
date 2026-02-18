package com.ayakasir.app.core.domain.model

data class CashWithdrawal(
    val id: String,
    val userId: String,
    val amount: Long,
    val reason: String,
    val date: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
