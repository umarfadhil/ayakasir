package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.dao.CashWithdrawalDao
import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.entity.CashWithdrawalEntity
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.domain.model.CashWithdrawal
import com.ayakasir.app.core.util.UuidGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CashWithdrawalRepository @Inject constructor(
    private val cashWithdrawalDao: CashWithdrawalDao,
    private val syncQueueDao: SyncQueueDao
) {
    fun getAll(): Flow<List<CashWithdrawal>> =
        cashWithdrawalDao.getAll().map { list -> list.map { it.toDomain() } }

    fun getByDateRange(startTime: Long, endTime: Long): Flow<List<CashWithdrawal>> =
        cashWithdrawalDao.getByDateRange(startTime, endTime).map { list -> list.map { it.toDomain() } }

    fun getTotalByDateRange(startTime: Long, endTime: Long): Flow<Long> =
        cashWithdrawalDao.getTotalByDateRange(startTime, endTime)

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
            synced = false,
            updatedAt = now
        )

        cashWithdrawalDao.insert(entity)

        // Enqueue sync
        syncQueueDao.enqueue(
            SyncQueueEntity(
                tableName = "cash_withdrawals",
                recordId = id,
                operation = "INSERT",
                payload = "{\"id\":\"$id\"}"
            )
        )

        return id
    }

    private fun CashWithdrawalEntity.toDomain() = CashWithdrawal(
        id = id,
        userId = userId,
        amount = amount,
        reason = reason,
        date = date,
        synced = synced
    )
}
