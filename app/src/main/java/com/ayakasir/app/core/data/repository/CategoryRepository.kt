package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.dao.CategoryDao
import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.entity.CategoryEntity
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.domain.model.Category
import com.ayakasir.app.core.domain.model.CategoryType
import com.ayakasir.app.core.util.UuidGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val syncQueueDao: SyncQueueDao
) {
    fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAll().map { list -> list.map { it.toDomain() } }

    fun getMenuCategories(): Flow<List<Category>> =
        categoryDao.getAllMenuCategories().map { list -> list.map { it.toDomain() } }

    fun getRawMaterialCategories(): Flow<List<Category>> =
        categoryDao.getAllRawMaterialCategories().map { list -> list.map { it.toDomain() } }

    suspend fun getCategoryById(id: String): Category? =
        categoryDao.getById(id)?.toDomain()

    suspend fun createCategory(name: String, sortOrder: Int, categoryType: CategoryType = CategoryType.MENU): Category {
        val id = UuidGenerator.generate()
        val entity = CategoryEntity(
            id = id,
            name = name,
            sortOrder = sortOrder,
            categoryType = categoryType.name
        )
        categoryDao.insert(entity)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                tableName = "categories",
                recordId = id,
                operation = "INSERT",
                payload = "{\"id\":\"$id\"}"
            )
        )
        return entity.toDomain()
    }

    suspend fun updateCategory(categoryId: String, name: String, sortOrder: Int, categoryType: CategoryType = CategoryType.MENU) {
        val existing = categoryDao.getById(categoryId) ?: return
        categoryDao.update(
            existing.copy(
                name = name,
                sortOrder = sortOrder,
                categoryType = categoryType.name,
                synced = false,
                updatedAt = System.currentTimeMillis()
            )
        )
        syncQueueDao.enqueue(
            SyncQueueEntity(
                tableName = "categories",
                recordId = categoryId,
                operation = "UPDATE",
                payload = "{\"id\":\"$categoryId\"}"
            )
        )
    }

    suspend fun deleteCategory(categoryId: String) {
        categoryDao.deleteById(categoryId)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                tableName = "categories",
                recordId = categoryId,
                operation = "DELETE",
                payload = "{\"id\":\"$categoryId\"}"
            )
        )
    }

    private fun CategoryEntity.toDomain() = Category(
        id = id,
        name = name,
        sortOrder = sortOrder,
        categoryType = CategoryType.valueOf(categoryType)
    )
}
