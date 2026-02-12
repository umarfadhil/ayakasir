package com.ayakasir.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ayakasir.app.core.data.local.entity.VariantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VariantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(variant: VariantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(variants: List<VariantEntity>)

    @Update
    suspend fun update(variant: VariantEntity)

    @Query("SELECT * FROM variants WHERE product_id = :productId")
    fun getByProductId(productId: String): Flow<List<VariantEntity>>

    @Query("SELECT * FROM variants WHERE product_id = :productId")
    suspend fun getByProductIdDirect(productId: String): List<VariantEntity>

    @Query("SELECT * FROM variants WHERE id = :id")
    suspend fun getById(id: String): VariantEntity?

    @Query("DELETE FROM variants WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM variants WHERE product_id = :productId")
    suspend fun deleteByProductId(productId: String)

    @Query("UPDATE variants SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
