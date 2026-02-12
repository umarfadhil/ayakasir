package com.ayakasir.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ayakasir.app.core.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY sort_order ASC, name ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sort_order ASC, name ASC")
    suspend fun getAllDirect(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    // Type-filtered queries
    @Query("SELECT * FROM categories WHERE category_type = 'MENU' ORDER BY sort_order ASC, name ASC")
    fun getAllMenuCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE category_type = 'RAW_MATERIAL' ORDER BY sort_order ASC, name ASC")
    fun getAllRawMaterialCategories(): Flow<List<CategoryEntity>>

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE categories SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
