package com.ayakasir.app.core.data.repository

import android.util.Log
import com.ayakasir.app.core.data.local.dao.GeneralLedgerDao
import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.entity.GeneralLedgerEntity
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.domain.model.LedgerType
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
class GeneralLedgerRepository @Inject constructor(
    private val generalLedgerDao: GeneralLedgerDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler,
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "GeneralLedgerRepo"
    }

    private val restaurantId: String get() = sessionManager.currentRestaurantId ?: ""

    fun getAll(): Flow<List<GeneralLedgerEntity>> =
        generalLedgerDao.getAll(restaurantId)

    fun getBalance(): Flow<Long> =
        generalLedgerDao.getBalance(restaurantId)

    fun getTotalByType(type: LedgerType): Flow<Long> =
        generalLedgerDao.getTotalByType(restaurantId, type.name)

    fun getByDateRange(startTime: Long, endTime: Long): Flow<List<GeneralLedgerEntity>> =
        generalLedgerDao.getByDateRange(restaurantId, startTime, endTime)

    fun getLatestInitialBalance(): Flow<Long> =
        generalLedgerDao.getLatestInitialBalance(restaurantId).map { it?.amount ?: 0L }

    suspend fun recordEntry(
        type: LedgerType,
        amount: Long,
        description: String,
        userId: String,
        referenceId: String? = null
    ): String {
        val id = UuidGenerator.generate()
        val now = System.currentTimeMillis()

        val entity = GeneralLedgerEntity(
            id = id,
            restaurantId = restaurantId,
            type = type.name,
            amount = amount,
            referenceId = referenceId,
            description = description,
            date = now,
            userId = userId,
            syncStatus = SyncStatus.PENDING.name,
            updatedAt = now
        )

        generalLedgerDao.insert(entity)

        try {
            syncManager.pushToSupabase("general_ledger", "INSERT", id)
        } catch (e: Exception) {
            Log.w(TAG, "Push general_ledger failed: ${e.message}")
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    tableName = "general_ledger",
                    recordId = id,
                    operation = "INSERT",
                    payload = "{\"id\":\"$id\"}"
                )
            )
            syncScheduler.requestImmediateSync()
        }

        return id
    }
}
