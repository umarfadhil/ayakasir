package com.ayakasir.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ayakasir.app.core.data.local.entity.TransactionEntity
import com.ayakasir.app.core.data.local.entity.TransactionItemEntity
import com.ayakasir.app.core.data.local.relation.TransactionWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: TransactionItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<TransactionItemEntity>)

    @Transaction
    suspend fun insertFullTransaction(transaction: TransactionEntity, items: List<TransactionItemEntity>) {
        insert(transaction)
        insertItems(items)
    }

    @Transaction
    @Query("SELECT * FROM transactions WHERE restaurant_id = :restaurantId ORDER BY date DESC")
    fun getAllWithItems(restaurantId: String): Flow<List<TransactionWithItems>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getWithItemsById(id: String): TransactionWithItems?

    @Transaction
    @Query("SELECT * FROM transactions WHERE restaurant_id = :restaurantId AND date BETWEEN :startTime AND :endTime ORDER BY date DESC")
    fun getByDateRange(restaurantId: String, startTime: Long, endTime: Long): Flow<List<TransactionWithItems>>

    @Query("SELECT * FROM transactions WHERE restaurant_id = :restaurantId AND date BETWEEN :startTime AND :endTime ORDER BY date DESC")
    fun getTransactionsByDateRange(restaurantId: String, startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Query("SELECT COALESCE(SUM(total), 0) FROM transactions WHERE restaurant_id = :restaurantId AND status = 'COMPLETED' AND date BETWEEN :startTime AND :endTime")
    fun getTotalByDateRange(restaurantId: String, startTime: Long, endTime: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(total), 0) FROM transactions WHERE restaurant_id = :restaurantId AND status = 'COMPLETED' AND payment_method = :method AND date BETWEEN :startTime AND :endTime")
    fun getTotalByMethodAndDateRange(restaurantId: String, method: String, startTime: Long, endTime: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE restaurant_id = :restaurantId AND status = 'COMPLETED' AND date BETWEEN :startTime AND :endTime")
    fun getCountByDateRange(restaurantId: String, startTime: Long, endTime: Long): Flow<Int>

    @Query("UPDATE transactions SET status = 'VOIDED', sync_status = 'PENDING', updated_at = :now WHERE id = :id")
    suspend fun voidTransaction(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET sync_status = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE transaction_items SET sync_status = 'SYNCED' WHERE transaction_id = :transactionId")
    suspend fun markItemsSynced(transactionId: String)
}
