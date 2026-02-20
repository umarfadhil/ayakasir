package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.domain.model.CashBalance
import com.ayakasir.app.core.domain.model.LedgerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CashBalanceRepository @Inject constructor(
    private val generalLedgerRepository: GeneralLedgerRepository
) {
    /**
     * Get current cumulative cash balance (reactive, auto-updating).
     * Balance is computed entirely from the general_ledger table.
     */
    fun getCurrentBalance(): Flow<CashBalance> {
        val cashFlows = combine(
            generalLedgerRepository.getTotalByType(LedgerType.INITIAL_BALANCE),
            generalLedgerRepository.getTotalByType(LedgerType.SALE),
            generalLedgerRepository.getTotalByType(LedgerType.WITHDRAWAL),
            generalLedgerRepository.getTotalByType(LedgerType.ADJUSTMENT),
            generalLedgerRepository.getBalance()
        ) { initialBalance, sales, withdrawals, adjustments, currentBalance ->
            CashBalance(
                initialBalance = initialBalance,
                totalCashSales = sales,
                totalWithdrawals = withdrawals,
                totalAdjustments = adjustments,
                currentBalance = currentBalance
            )
        }

        return combine(
            cashFlows,
            generalLedgerRepository.getTotalByType(LedgerType.SALE_QRIS),
            generalLedgerRepository.getTotalByType(LedgerType.COGS)
        ) { base, qrisSales, cogs ->
            base.copy(totalQrisSales = qrisSales, totalCogs = cogs)
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
