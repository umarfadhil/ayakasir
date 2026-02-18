package com.ayakasir.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ayakasir.app.core.data.local.entity.VendorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vendor: VendorEntity)

    @Update
    suspend fun update(vendor: VendorEntity)

    @Query("SELECT * FROM vendors WHERE restaurant_id = :restaurantId ORDER BY name ASC")
    fun getAll(restaurantId: String): Flow<List<VendorEntity>>

    @Query("SELECT * FROM vendors WHERE restaurant_id = :restaurantId ORDER BY name ASC")
    suspend fun getAllDirect(restaurantId: String): List<VendorEntity>

    @Query("SELECT * FROM vendors WHERE id = :id")
    suspend fun getById(id: String): VendorEntity?

    @Query("DELETE FROM vendors WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE vendors SET sync_status = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)
}
