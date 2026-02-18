package com.ayakasir.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ayakasir.app.core.data.local.entity.RestaurantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RestaurantDao {
    @Query("SELECT * FROM restaurants ORDER BY created_at DESC")
    fun getAll(): Flow<List<RestaurantEntity>>

    @Query("SELECT * FROM restaurants WHERE id = :id")
    suspend fun getById(id: String): RestaurantEntity?

    @Query("SELECT * FROM restaurants WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveRestaurant(): RestaurantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RestaurantEntity)

    @Update
    suspend fun update(entity: RestaurantEntity)

    @Query("DELETE FROM restaurants WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE restaurants SET sync_status = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)
}
