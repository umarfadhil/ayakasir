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

    @Query("SELECT * FROM cash_withdrawals WHERE restaurant_id = :restaurantId ORDER BY date DESC")
    fun getAll(restaurantId: String): Flow<List<CashWithdrawalEntity>>

    @Query("SELECT * FROM cash_withdrawals WHERE restaurant_id = :restaurantId AND date BETWEEN :startTime AND :endTime ORDER BY date DESC")
    fun getByDateRange(restaurantId: String, startTime: Long, endTime: Long): Flow<List<CashWithdrawalEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM cash_withdrawals WHERE restaurant_id = :restaurantId AND date BETWEEN :startTime AND :endTime")
    fun getTotalByDateRange(restaurantId: String, startTime: Long, endTime: Long): Flow<Long>

    @Query("SELECT * FROM cash_withdrawals WHERE id = :id")
    suspend fun getById(id: String): CashWithdrawalEntity?

    @Query("UPDATE cash_withdrawals SET sync_status = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)
}
