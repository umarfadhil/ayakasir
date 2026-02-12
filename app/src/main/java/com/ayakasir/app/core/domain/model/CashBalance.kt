package com.ayakasir.app.core.domain.model

data class CashBalance(
    val initialBalance: Long,      // Starting balance from settings
    val totalCashSales: Long,       // Sum of all completed CASH transactions
    val totalWithdrawals: Long,     // Sum of all cash withdrawals
    val currentBalance: Long        // Computed: initial + sales - withdrawals
) {
    val hasSufficientBalance: Boolean get() = currentBalance >= 0

    fun canWithdraw(amount: Long): Boolean = currentBalance >= amount
}
