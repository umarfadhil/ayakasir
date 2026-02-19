package com.ayakasir.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ayakasir.app.core.data.local.entity.GoodsReceivingEntity
import com.ayakasir.app.core.data.local.entity.GoodsReceivingItemEntity
import com.ayakasir.app.core.data.local.relation.GoodsReceivingItemWithProduct
import com.ayakasir.app.core.data.local.relation.GoodsReceivingWithItems
import com.ayakasir.app.core.data.local.relation.GoodsReceivingWithVendorAndItems
import kotlinx.coroutines.flow.Flow

@Dao
interface GoodsReceivingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receiving: GoodsReceivingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: GoodsReceivingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<GoodsReceivingItemEntity>)

    @Transaction
    @Query("SELECT * FROM goods_receiving WHERE restaurant_id = :restaurantId ORDER BY date DESC")
    fun getAllWithItems(restaurantId: String): Flow<List<GoodsReceivingWithVendorAndItems>>

    @Transaction
    @Query("SELECT * FROM goods_receiving WHERE id = :id")
    suspend fun getWithItemsById(id: String): GoodsReceivingWithVendorAndItems?

    @Query("""
        SELECT
            gri.id,
            gri.receiving_id AS receivingId,
            gri.product_id AS productId,
            gri.variant_id AS variantId,
            gri.qty,
            gri.cost_per_unit AS costPerUnit,
            gri.unit,
            p.name AS productName,
            v.name AS variantName
        FROM goods_receiving_items gri
        LEFT JOIN products p ON gri.product_id = p.id
        LEFT JOIN variants v ON gri.variant_id = v.id AND gri.variant_id != ''
        WHERE gri.receiving_id = :receivingId
    """)
    suspend fun getItemsWithProductInfo(receivingId: String): List<GoodsReceivingItemWithProduct>

    @Query("SELECT * FROM goods_receiving WHERE id = :id")
    suspend fun getById(id: String): GoodsReceivingEntity?

    @Query("SELECT * FROM goods_receiving_items WHERE receiving_id = :receivingId")
    suspend fun getItemsByReceivingId(receivingId: String): List<GoodsReceivingItemEntity>

    @Query("UPDATE goods_receiving SET sync_status = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE goods_receiving_items SET sync_status = 'SYNCED' WHERE receiving_id = :receivingId")
    suspend fun markItemsSynced(receivingId: String)

    @Query("DELETE FROM goods_receiving WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM goods_receiving_items WHERE receiving_id = :receivingId")
    suspend fun deleteItems(receivingId: String)

    @Query("SELECT * FROM goods_receiving_items WHERE restaurant_id = :restaurantId")
    fun getAllItems(restaurantId: String): Flow<List<GoodsReceivingItemEntity>>
}
