package com.ayakasir.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ayakasir.app.core.data.local.entity.ProductEntity
import com.ayakasir.app.core.data.local.relation.ProductWithVariants
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity)

    @Update
    suspend fun update(product: ProductEntity)

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE restaurant_id = :restaurantId AND is_active = 1 ORDER BY name ASC")
    fun getAllActive(restaurantId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE restaurant_id = :restaurantId ORDER BY name ASC")
    fun getAll(restaurantId: String): Flow<List<ProductEntity>>

    @Transaction
    @Query("SELECT * FROM products WHERE restaurant_id = :restaurantId AND is_active = 1 ORDER BY name ASC")
    fun getAllActiveWithVariants(restaurantId: String): Flow<List<ProductWithVariants>>

    @Transaction
    @Query("SELECT * FROM products WHERE restaurant_id = :restaurantId AND category_id = :categoryId AND is_active = 1 ORDER BY name ASC")
    fun getActiveWithVariantsByCategory(restaurantId: String, categoryId: String): Flow<List<ProductWithVariants>>

    @Transaction
    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getWithVariantsById(id: String): ProductWithVariants?

    // Type-filtered queries for menu items
    @Transaction
    @Query("""
        SELECT p.* FROM products p
        LEFT JOIN categories c ON p.category_id = c.id
        WHERE p.restaurant_id = :restaurantId
        AND p.is_active = 1
        AND p.product_type = 'MENU_ITEM'
        AND (c.category_type IS NULL OR c.category_type = 'MENU')
        ORDER BY p.name ASC
    """)
    fun getAllActiveMenuItemsWithVariants(restaurantId: String): Flow<List<ProductWithVariants>>

    @Transaction
    @Query("""
        SELECT p.* FROM products p
        LEFT JOIN categories c ON p.category_id = c.id
        WHERE p.restaurant_id = :restaurantId
        AND p.category_id = :categoryId
        AND p.is_active = 1
        AND p.product_type = 'MENU_ITEM'
        AND (c.category_type IS NULL OR c.category_type = 'MENU')
        ORDER BY p.name ASC
    """)
    fun getActiveMenuItemsWithVariantsByCategory(restaurantId: String, categoryId: String): Flow<List<ProductWithVariants>>

    // Type-filtered queries for raw materials
    @Transaction
    @Query("SELECT * FROM products WHERE restaurant_id = :restaurantId AND is_active = 1 AND product_type = 'RAW_MATERIAL' ORDER BY name ASC")
    fun getAllActiveRawMaterialsWithVariants(restaurantId: String): Flow<List<ProductWithVariants>>

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE products SET sync_status = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)
}
