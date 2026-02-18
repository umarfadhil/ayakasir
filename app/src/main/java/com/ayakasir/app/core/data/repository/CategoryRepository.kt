package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.dao.CategoryDao
import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.entity.CategoryEntity
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.domain.model.Category
import com.ayakasir.app.core.domain.model.CategoryType
import com.ayakasir.app.core.domain.model.SyncStatus
import com.ayakasir.app.core.session.SessionManager
import com.ayakasir.app.core.sync.SyncManager
import com.ayakasir.app.core.sync.SyncScheduler
import com.ayakasir.app.core.util.UuidGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler,
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager
) {
    private val restaurantId: String get() = sessionManager.currentRestaurantId ?: ""

    fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAll(restaurantId).map { list -> list.map { it.toDomain() } }

    fun getMenuCategories(): Flow<List<Category>> =
        categoryDao.getAllMenuCategories(restaurantId).map { list -> list.map { it.toDomain() } }

    fun getRawMaterialCategories(): Flow<List<Category>> =
        categoryDao.getAllRawMaterialCategories(restaurantId).map { list -> list.map { it.toDomain() } }

    suspend fun getCategoryById(id: String): Category? =
        categoryDao.getById(id)?.toDomain()

    suspend fun createCategory(name: String, sortOrder: Int, categoryType: CategoryType = CategoryType.MENU): Category {
        val id = UuidGenerator.generate()
        val entity = CategoryEntity(
            id = id,
            name = name,
            sortOrder = sortOrder,
            categoryType = categoryType.name,
            restaurantId = restaurantId,
            syncStatus = SyncStatus.PENDING.name,
            updatedAt = System.currentTimeMillis()
        )
        // 1. Write locally
        categoryDao.insert(entity)

        try {
            // 2. Try immediate push
            syncManager.pushToSupabase("categories", "INSERT", id)
        } catch (e: Exception) {
            // 3. Failed -> enqueue retry
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    tableName = "categories",
                    recordId = id,
                    operation = "INSERT",
                    payload = "{\"id\":\"$id\"}"
                )
            )
            syncScheduler.requestImmediateSync()
        }
        return entity.toDomain()
    }

    suspend fun updateCategory(categoryId: String, name: String, sortOrder: Int, categoryType: CategoryType = CategoryType.MENU) {
        val existing = categoryDao.getById(categoryId) ?: return
        categoryDao.update(
            existing.copy(
                name = name,
                sortOrder = sortOrder,
                categoryType = categoryType.name,
                syncStatus = SyncStatus.PENDING.name,
                updatedAt = System.currentTimeMillis()
            )
        )

        try {
            syncManager.pushToSupabase("categories", "UPDATE", categoryId)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    tableName = "categories",
                    recordId = categoryId,
                    operation = "UPDATE",
                    payload = "{\"id\":\"$categoryId\"}"
                )
            )
            syncScheduler.requestImmediateSync()
        }
    }

    suspend fun deleteCategory(categoryId: String) {
        categoryDao.deleteById(categoryId)

        try {
            syncManager.pushToSupabase("categories", "DELETE", categoryId)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    tableName = "categories",
                    recordId = categoryId,
                    operation = "DELETE",
                    payload = "{\"id\":\"$categoryId\"}"
                )
            )
            syncScheduler.requestImmediateSync()
        }
    }

    private fun CategoryEntity.toDomain() = Category(
        id = id,
        name = name,
        sortOrder = sortOrder,
        categoryType = CategoryType.valueOf(categoryType)
    )
}
