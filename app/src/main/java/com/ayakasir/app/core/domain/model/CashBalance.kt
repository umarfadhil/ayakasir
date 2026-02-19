package com.ayakasir.app.core.domain.model

data class CashBalance(
    val initialBalance: Long,       // Sum of INITIAL_BALANCE ledger entries
    val totalCashSales: Long,       // Sum of SALE ledger entries (positive)
    val totalWithdrawals: Long,     // Sum of WITHDRAWAL ledger entries (negative)
    val totalAdjustments: Long = 0, // Sum of ADJUSTMENT ledger entries
    val currentBalance: Long        // SUM of all ledger entries
) {
    val hasSufficientBalance: Boolean get() = currentBalance >= 0

    fun canWithdraw(amount: Long): Boolean = currentBalance >= amount
}
