package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.dao.RestaurantDao
import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.entity.RestaurantEntity
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.domain.model.Restaurant
import com.ayakasir.app.core.domain.model.SyncStatus
import com.ayakasir.app.core.sync.SyncManager
import com.ayakasir.app.core.sync.SyncScheduler
import com.ayakasir.app.core.util.UuidGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestaurantRepository @Inject constructor(
    private val restaurantDao: RestaurantDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler,
    private val syncManager: SyncManager
) {
    fun getAll(): Flow<List<Restaurant>> = restaurantDao.getAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getById(id: String): Restaurant? = restaurantDao.getById(id)?.toDomain()

    suspend fun getActiveRestaurant(): Restaurant? = restaurantDao.getActiveRestaurant()?.toDomain()

    suspend fun create(
        name: String,
        ownerEmail: String,
        ownerPhone: String
    ): Restaurant {
        val entity = RestaurantEntity(
            id = UuidGenerator.generate(),
            name = name,
            ownerEmail = ownerEmail,
            ownerPhone = ownerPhone,
            isActive = false,
            syncStatus = SyncStatus.PENDING.name,
            updatedAt = System.currentTimeMillis()
        )
        restaurantDao.insert(entity)

        try {
            syncManager.pushToSupabase("restaurants", "INSERT", entity.id)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    tableName = "restaurants",
                    recordId = entity.id,
                    operation = "INSERT",
                    payload = "{\"id\":\"${entity.id}\",\"name\":\"$name\",\"owner_email\":\"$ownerEmail\",\"owner_phone\":\"$ownerPhone\"}"
                )
            )
            syncScheduler.requestImmediateSync()
        }
        return entity.toDomain()
    }

    private fun RestaurantEntity.toDomain() = Restaurant(
        id = id,
        name = name,
        ownerEmail = ownerEmail,
        ownerPhone = ownerPhone,
        isActive = isActive
    )
}
