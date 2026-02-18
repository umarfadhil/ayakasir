package com.ayakasir.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ayakasir.app.core.data.local.entity.InventoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(inventory: InventoryEntity)

    @Query("SELECT * FROM inventory WHERE product_id = :productId AND variant_id = :variantId")
    suspend fun get(productId: String, variantId: String): InventoryEntity?

    @Query("SELECT * FROM inventory WHERE restaurant_id = :restaurantId")
    fun getAll(restaurantId: String): Flow<List<InventoryEntity>>

    @Query("""
        SELECT i.* FROM inventory i
        INNER JOIN products p ON i.product_id = p.id
        WHERE i.restaurant_id = :restaurantId
        AND p.product_type = 'RAW_MATERIAL'
    """)
    fun getRawMaterialInventory(restaurantId: String): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventory WHERE restaurant_id = :restaurantId AND current_qty <= min_qty")
    fun getLowStock(restaurantId: String): Flow<List<InventoryEntity>>

    @Query("SELECT COUNT(*) FROM inventory WHERE restaurant_id = :restaurantId AND current_qty <= min_qty AND min_qty > 0")
    fun getLowStockCount(restaurantId: String): Flow<Int>

    @Query("UPDATE inventory SET current_qty = current_qty - :qty, sync_status = 'PENDING', updated_at = :now WHERE product_id = :productId AND variant_id = :variantId")
    suspend fun decrementStock(productId: String, variantId: String, qty: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE inventory SET current_qty = current_qty + :qty, sync_status = 'PENDING', updated_at = :now WHERE product_id = :productId AND variant_id = :variantId")
    suspend fun incrementStock(productId: String, variantId: String, qty: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE inventory SET current_qty = :qty, sync_status = 'PENDING', updated_at = :now WHERE product_id = :productId AND variant_id = :variantId")
    suspend fun setStock(productId: String, variantId: String, qty: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE inventory SET min_qty = :minQty, sync_status = 'PENDING', updated_at = :now WHERE product_id = :productId AND variant_id = :variantId")
    suspend fun setMinQty(productId: String, variantId: String, minQty: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE inventory SET sync_status = 'SYNCED' WHERE product_id = :productId AND variant_id = :variantId")
    suspend fun markSynced(productId: String, variantId: String)
}
