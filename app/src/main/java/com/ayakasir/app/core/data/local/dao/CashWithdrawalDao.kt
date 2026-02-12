package com.ayakasir.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ayakasir.app.core.data.local.entity.CashWithdrawalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashWithdrawalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(withdrawal: CashWithdrawalEntity)

    @Query("SELECT * FROM cash_withdrawals ORDER BY date DESC")
    fun getAll(): Flow<List<CashWithdrawalEntity>>

    @Query("SELECT * FROM cash_withdrawals WHERE date BETWEEN :startTime AND :endTime ORDER BY date DESC")
    fun getByDateRange(startTime: Long, endTime: Long): Flow<List<CashWithdrawalEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM cash_withdrawals WHERE date BETWEEN :startTime AND :endTime")
    fun getTotalByDateRange(startTime: Long, endTime: Long): Flow<Long>

    @Query("SELECT * FROM cash_withdrawals WHERE id = :id")
    suspend fun getById(id: String): CashWithdrawalEntity?

    @Query("UPDATE cash_withdrawals SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
