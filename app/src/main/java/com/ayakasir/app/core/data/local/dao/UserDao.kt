package com.ayakasir.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ayakasir.app.core.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE is_active = 1")
    fun getAllActive(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users")
    fun getAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE pin_hash = :pinHash AND is_active = 1 LIMIT 1")
    suspend fun getByPinHash(pinHash: String): UserEntity?

    @Query("SELECT * FROM users WHERE is_active = 1")
    suspend fun getAllActiveDirect(): List<UserEntity>

    @Query("UPDATE users SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
