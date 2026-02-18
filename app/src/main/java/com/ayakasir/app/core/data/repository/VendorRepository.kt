package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.dao.VendorDao
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.data.local.entity.VendorEntity
import com.ayakasir.app.core.domain.model.SyncStatus
import com.ayakasir.app.core.domain.model.Vendor
import com.ayakasir.app.core.session.SessionManager
import com.ayakasir.app.core.sync.SyncManager
import com.ayakasir.app.core.sync.SyncScheduler
import com.ayakasir.app.core.util.UuidGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VendorRepository @Inject constructor(
    private val vendorDao: VendorDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler,
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager
) {
    private val restaurantId: String get() = sessionManager.currentRestaurantId ?: ""

    fun getAllVendors(): Flow<List<Vendor>> = vendorDao.getAll(restaurantId).map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getVendorById(id: String): Vendor? = vendorDao.getById(id)?.toDomain()

    suspend fun getAllVendorsDirect(): List<Vendor> = vendorDao.getAllDirect(restaurantId).map { it.toDomain() }

    suspend fun createVendor(name: String, phone: String?, address: String?): Vendor {
        val entity = VendorEntity(
            id = UuidGenerator.generate(),
            name = name,
            phone = phone,
            address = address,
            restaurantId = restaurantId,
            syncStatus = SyncStatus.PENDING.name,
            updatedAt = System.currentTimeMillis()
        )
        vendorDao.insert(entity)

        try {
            syncManager.pushToSupabase("vendors", "INSERT", entity.id)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(tableName = "vendors", recordId = entity.id, operation = "INSERT", payload = "{\"id\":\"${entity.id}\"}")
            )
            syncScheduler.requestImmediateSync()
        }
        return entity.toDomain()
    }

    suspend fun updateVendor(id: String, name: String, phone: String?, address: String?) {
        val existing = vendorDao.getById(id) ?: return
        vendorDao.update(existing.copy(
            name = name, phone = phone, address = address,
            syncStatus = SyncStatus.PENDING.name, updatedAt = System.currentTimeMillis()
        ))

        try {
            syncManager.pushToSupabase("vendors", "UPDATE", id)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(tableName = "vendors", recordId = id, operation = "UPDATE", payload = "{\"id\":\"$id\"}")
            )
            syncScheduler.requestImmediateSync()
        }
    }

    suspend fun deleteVendor(id: String) {
        vendorDao.deleteById(id)

        try {
            syncManager.pushToSupabase("vendors", "DELETE", id)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(tableName = "vendors", recordId = id, operation = "DELETE", payload = "{\"id\":\"$id\"}")
            )
            syncScheduler.requestImmediateSync()
        }
    }

    private fun VendorEntity.toDomain() = Vendor(
        id = id, name = name, phone = phone, address = address
    )
}
