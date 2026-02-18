package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.dao.CashWithdrawalDao
import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.entity.CashWithdrawalEntity
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.domain.model.CashWithdrawal
import com.ayakasir.app.core.domain.model.SyncStatus
import com.ayakasir.app.core.session.SessionManager
import com.ayakasir.app.core.sync.SyncManager
import com.ayakasir.app.core.sync.SyncScheduler
import com.ayakasir.app.core.util.UuidGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CashWithdrawalRepository @Inject constructor(
    private val cashWithdrawalDao: CashWithdrawalDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler,
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager
) {
    private val restaurantId: String get() = sessionManager.currentRestaurantId ?: ""

    fun getAll(): Flow<List<CashWithdrawal>> =
        cashWithdrawalDao.getAll(restaurantId).map { list -> list.map { it.toDomain() } }

    fun getByDateRange(startTime: Long, endTime: Long): Flow<List<CashWithdrawal>> =
        cashWithdrawalDao.getByDateRange(restaurantId, startTime, endTime).map { list -> list.map { it.toDomain() } }

    fun getTotalByDateRange(startTime: Long, endTime: Long): Flow<Long> =
        cashWithdrawalDao.getTotalByDateRange(restaurantId, startTime, endTime)

    suspend fun recordWithdrawal(
        userId: String,
        amount: Long,
        reason: String
    ): String {
        val id = UuidGenerator.generate()
        val now = System.currentTimeMillis()

        val entity = CashWithdrawalEntity(
            id = id,
            userId = userId,
            amount = amount,
            reason = reason,
            date = now,
            restaurantId = restaurantId,
            syncStatus = SyncStatus.PENDING.name,
            updatedAt = now
        )

        cashWithdrawalDao.insert(entity)

        try {
            syncManager.pushToSupabase("cash_withdrawals", "INSERT", id)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    tableName = "cash_withdrawals",
                    recordId = id,
                    operation = "INSERT",
                    payload = "{\"id\":\"$id\"}"
                )
            )
            syncScheduler.requestImmediateSync()
        }

        return id
    }

    private fun CashWithdrawalEntity.toDomain() = CashWithdrawal(
        id = id,
        userId = userId,
        amount = amount,
        reason = reason,
        date = date,
        syncStatus = SyncStatus.valueOf(syncStatus)
    )
}
