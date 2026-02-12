package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.datastore.CashBalanceDataStore
import com.ayakasir.app.core.domain.model.CashBalance
import com.ayakasir.app.core.domain.model.PaymentMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CashBalanceRepository @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val cashWithdrawalRepository: CashWithdrawalRepository,
    private val cashBalanceDataStore: CashBalanceDataStore
) {
    /**
     * Get current cumulative cash balance (reactive, auto-updating)
     * Calculates: Initial Balance + All Cash Sales - All Withdrawals
     */
    fun getCurrentBalance(): Flow<CashBalance> {
        // Use timestamp 0 to Long.MAX_VALUE to get ALL transactions/withdrawals (all-time)
        val allTimeStart = 0L
        val allTimeEnd = Long.MAX_VALUE

        return combine(
            cashBalanceDataStore.initialBalance,
            transactionRepository.getTotalByMethod(PaymentMethod.CASH, allTimeStart, allTimeEnd),
            cashWithdrawalRepository.getTotalByDateRange(allTimeStart, allTimeEnd)
        ) { initialBalance, cashSales, withdrawals ->
            val current = initialBalance + cashSales - withdrawals

            CashBalance(
                initialBalance = initialBalance,
                totalCashSales = cashSales,
                totalWithdrawals = withdrawals,
                currentBalance = current
            )
        }
    }

    /**
     * Check if withdrawal is allowed (prevents negative balance)
     */
    suspend fun canWithdraw(amount: Long): Boolean {
        val balance = getCurrentBalance().first()
        return balance.canWithdraw(amount)
    }
}
