package com.ayakasir.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ayakasir.app.core.data.local.entity.GeneralLedgerEntity
import com.ayakasir.app.core.data.local.relation.GeneralLedgerExportRow
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneralLedgerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GeneralLedgerEntity)

    @Query("SELECT * FROM general_ledger WHERE restaurant_id = :restaurantId ORDER BY date DESC")
    fun getAll(restaurantId: String): Flow<List<GeneralLedgerEntity>>

    @Query("SELECT * FROM general_ledger WHERE id = :id")
    suspend fun getById(id: String): GeneralLedgerEntity?

    @Query("SELECT COALESCE(SUM(amount), 0) FROM general_ledger WHERE restaurant_id = :restaurantId AND type IN ('INITIAL_BALANCE', 'SALE', 'WITHDRAWAL', 'ADJUSTMENT')")
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

    @Query("SELECT * FROM general_ledger WHERE reference_id = :referenceId AND type = :type")
    suspend fun getByReferenceIdAndType(referenceId: String, type: String): List<GeneralLedgerEntity>

    @Query(
        """
        SELECT
            gl.id AS id,
            gl.type AS type,
            CASE
                WHEN gl.type IN ('SALE', 'SALE_QRIS') THEN ti.product_name
                WHEN gl.type = 'COGS' THEN p.name
                ELSE NULL
            END AS productName,
            CASE
                WHEN gl.type IN ('SALE', 'SALE_QRIS') THEN ti.variant_name
                WHEN gl.type = 'COGS' THEN v.name
                ELSE NULL
            END AS variantName,
            gl.amount AS amount,
            CASE
                WHEN gl.type IN ('SALE', 'SALE_QRIS') THEN ti.qty
                WHEN gl.type = 'COGS' THEN gri.qty
                ELSE NULL
            END AS qty,
            gl.description AS description
        FROM general_ledger gl
        LEFT JOIN transaction_items ti
            ON gl.reference_id = ti.transaction_id
            AND gl.type IN ('SALE', 'SALE_QRIS')
            AND ti.restaurant_id = gl.restaurant_id
        LEFT JOIN goods_receiving_items gri
            ON gl.reference_id = gri.receiving_id
            AND gl.type = 'COGS'
            AND gri.restaurant_id = gl.restaurant_id
        LEFT JOIN products p
            ON p.id = gri.product_id
            AND p.restaurant_id = gl.restaurant_id
        LEFT JOIN variants v
            ON v.id = gri.variant_id
            AND v.restaurant_id = gl.restaurant_id
        WHERE gl.restaurant_id = :restaurantId
        ORDER BY gl.date DESC, gl.id DESC
        """
    )
    suspend fun getExportRows(restaurantId: String): List<GeneralLedgerExportRow>
}
