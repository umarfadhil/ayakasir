package com.ayakasir.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Insert
    suspend fun enqueue(entry: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue ORDER BY id ASC LIMIT :limit")
    suspend fun getNextBatch(limit: Int = 50): List<SyncQueueEntity>

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sync_queue SET retry_count = retry_count + 1 WHERE id = :id")
    suspend fun markRetry(id: Long)

    @Query("DELETE FROM sync_queue WHERE retry_count >= :maxRetries")
    suspend fun deleteFailedEntries(maxRetries: Int = 3)

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun getPendingSyncCount(): Flow<Int>
}
