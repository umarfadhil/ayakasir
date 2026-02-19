package com.ayakasir.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ayakasir.app.core.data.local.entity.GeneralLedgerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneralLedgerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GeneralLedgerEntity)

    @Query("SELECT * FROM general_ledger WHERE restaurant_id = :restaurantId ORDER BY date DESC")
    fun getAll(restaurantId: String): Flow<List<GeneralLedgerEntity>>

    @Query("SELECT * FROM general_ledger WHERE id = :id")
    suspend fun getById(id: String): GeneralLedgerEntity?

    @Query("SELECT COALESCE(SUM(amount), 0) FROM general_ledger WHERE restaurant_id = :restaurantId")
    fun getBalance(restaurantId: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM general_ledger WHERE restaurant_id = :restaurantId AND type = :type")
    fun getTotalByType(restaurantId: String, type: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM general_ledger WHERE restaurant_id = :restaurantId AND type = :type AND date BETWEEN :startTime AND :endTime")
    fun getTotalByTypeAndDateRange(restaurantId: String, type: String, startTime: Long, endTime: Long): Flow<Long>

    @Query("SELECT * FROM general_ledger WHERE restaurant_id = :restaurantId AND date BETWEEN :startTime AND :endTime ORDER BY date DESC")
    fun getByDateRange(restaurantId: String, startTime: Long, endTime: Long): Flow<List<GeneralLedgerEntity>>

    @Query("UPDATE general_ledger SET sync_status = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM general_ledger WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM general_ledger WHERE restaurant_id = :restaurantId AND type = 'INITIAL_BALANCE' ORDER BY date DESC LIMIT 1")
    fun getLatestInitialBalance(restaurantId: String): Flow<GeneralLedgerEntity?>
}