package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.dao.ProductComponentDao
import com.ayakasir.app.core.data.local.dao.ProductDao
import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.dao.VariantDao
import com.ayakasir.app.core.data.local.entity.ProductComponentEntity
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.domain.model.ProductComponent
import com.ayakasir.app.core.util.UuidGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductComponentRepository @Inject constructor(
    private val productComponentDao: ProductComponentDao,
    private val productDao: ProductDao,
    private val variantDao: VariantDao,
    private val syncQueueDao: SyncQueueDao
) {
    fun getComponentsByProductId(productId: String): Flow<List<ProductComponent>> =
        productComponentDao.getByProductId(productId).map { list ->
            list.map { entity -> entity.toDomain() }
        }

    suspend fun addComponent(
        parentProductId: String,
        componentProductId: String,
        componentVariantId: String,
        requiredQty: Int,
        unit: String
    ): ProductComponent {
        val entity = ProductComponentEntity(
            id = UuidGenerator.generate(),
            parentProductId = parentProductId,
            componentProductId = componentProductId,
            componentVariantId = componentVariantId,
            requiredQty = requiredQty,
            unit = unit
        )
        productComponentDao.insert(entity)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                tableName = "product_components",
                recordId = entity.id,
                operation = "INSERT",
                payload = "{\"id\":\"${entity.id}\"}"
            )
        )
        return entity.toDomain()
    }

    suspend fun removeComponent(componentId: String) {
        productComponentDao.deleteById(componentId)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                tableName = "product_components",
                recordId = componentId,
                operation = "DELETE",
                payload = "{\"id\":\"$componentId\"}"
            )
        )
    }

    suspend fun deleteByProductId(productId: String) {
        productComponentDao.deleteByProductId(productId)
    }

    private suspend fun ProductComponentEntity.toDomain(): ProductComponent {
        val product = productDao.getById(componentProductId)
        val variant = if (componentVariantId.isNotBlank()) {
            variantDao.getById(componentVariantId)
        } else null

        return ProductComponent(
            id = id,
            componentProductId = componentProductId,
            componentVariantId = componentVariantId,
            componentProductName = product?.name ?: "",
            componentVariantName = variant?.name,
            requiredQty = requiredQty,
            unit = unit
        )
    }
}
