package com.ayakasir.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ayakasir.app.core.data.local.entity.ProductComponentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductComponentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(component: ProductComponentEntity)

    @Update
    suspend fun update(component: ProductComponentEntity)

    @Query("SELECT * FROM product_components WHERE parent_product_id = :productId ORDER BY sort_order")
    fun getByProductId(productId: String): Flow<List<ProductComponentEntity>>

    @Query("SELECT * FROM product_components WHERE parent_product_id = :productId ORDER BY sort_order")
    suspend fun getByProductIdDirect(productId: String): List<ProductComponentEntity>

    @Query("SELECT * FROM product_components WHERE id = :id")
    suspend fun getById(id: String): ProductComponentEntity?

    @Query("DELETE FROM product_components WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM product_components WHERE parent_product_id = :productId")
    suspend fun deleteByProductId(productId: String)

    @Query("UPDATE product_components SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
