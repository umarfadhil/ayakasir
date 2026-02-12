package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.dao.VendorDao
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.data.local.entity.VendorEntity
import com.ayakasir.app.core.domain.model.Vendor
import com.ayakasir.app.core.util.UuidGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VendorRepository @Inject constructor(
    private val vendorDao: VendorDao,
    private val syncQueueDao: SyncQueueDao
) {
    fun getAllVendors(): Flow<List<Vendor>> = vendorDao.getAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getVendorById(id: String): Vendor? = vendorDao.getById(id)?.toDomain()

    suspend fun getAllVendorsDirect(): List<Vendor> = vendorDao.getAllDirect().map { it.toDomain() }

    suspend fun createVendor(name: String, phone: String?, address: String?): Vendor {
        val entity = VendorEntity(
            id = UuidGenerator.generate(),
            name = name,
            phone = phone,
            address = address
        )
        vendorDao.insert(entity)
        syncQueueDao.enqueue(
            SyncQueueEntity(tableName = "vendors", recordId = entity.id, operation = "INSERT", payload = "{\"id\":\"${entity.id}\"}")
        )
        return entity.toDomain()
    }

    suspend fun updateVendor(id: String, name: String, phone: String?, address: String?) {
        val existing = vendorDao.getById(id) ?: return
        vendorDao.update(existing.copy(
            name = name, phone = phone, address = address,
            synced = false, updatedAt = System.currentTimeMillis()
        ))
        syncQueueDao.enqueue(
            SyncQueueEntity(tableName = "vendors", recordId = id, operation = "UPDATE", payload = "{\"id\":\"$id\"}")
        )
    }

    suspend fun deleteVendor(id: String) {
        vendorDao.deleteById(id)
        syncQueueDao.enqueue(
            SyncQueueEntity(tableName = "vendors", recordId = id, operation = "DELETE", payload = "{\"id\":\"$id\"}")
        )
    }

    private fun VendorEntity.toDomain() = Vendor(
        id = id, name = name, phone = phone, address = address
    )
}
