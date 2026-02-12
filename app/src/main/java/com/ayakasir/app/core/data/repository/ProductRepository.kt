package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.dao.InventoryDao
import com.ayakasir.app.core.data.local.dao.ProductDao
import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.dao.VariantDao
import com.ayakasir.app.core.data.local.entity.InventoryEntity
import com.ayakasir.app.core.data.local.entity.ProductEntity
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.data.local.entity.VariantEntity
import com.ayakasir.app.core.data.local.relation.ProductWithVariants
import com.ayakasir.app.core.domain.model.Product
import com.ayakasir.app.core.domain.model.ProductType
import com.ayakasir.app.core.domain.model.Variant
import com.ayakasir.app.core.util.UuidGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao,
    private val variantDao: VariantDao,
    private val inventoryDao: InventoryDao,
    private val syncQueueDao: SyncQueueDao
) {
    fun getAllActiveProducts(): Flow<List<Product>> =
        productDao.getAllActiveWithVariants().map { list -> list.map { it.toDomain() } }

    fun getProductsByCategory(categoryId: String): Flow<List<Product>> =
        productDao.getActiveWithVariantsByCategory(categoryId).map { list -> list.map { it.toDomain() } }

    fun getAllActiveMenuItems(): Flow<List<Product>> =
        productDao.getAllActiveMenuItemsWithVariants().map { list -> list.map { it.toDomain() } }

    fun getMenuItemsByCategory(categoryId: String): Flow<List<Product>> =
        productDao.getActiveMenuItemsWithVariantsByCategory(categoryId).map { list -> list.map { it.toDomain() } }

    fun getAllActiveRawMaterials(): Flow<List<Product>> =
        productDao.getAllActiveRawMaterialsWithVariants().map { list -> list.map { it.toDomain() } }

    suspend fun getProductById(id: String): Product? =
        productDao.getWithVariantsById(id)?.toDomain()

    suspend fun createProduct(
        name: String,
        categoryId: String?,
        description: String?,
        price: Long,
        imagePath: String?,
        variantNames: List<Pair<String, Long>>, // name to priceAdjustment
        productType: ProductType = ProductType.MENU_ITEM
    ): Product {
        val productId = UuidGenerator.generate()
        val entity = ProductEntity(
            id = productId,
            categoryId = categoryId,
            name = name,
            description = description,
            price = price,
            imagePath = imagePath,
            productType = productType.name
        )
        productDao.insert(entity)

        // Create inventory for base product
        inventoryDao.insert(InventoryEntity(productId = productId, variantId = ""))

        val variants = variantNames.map { (vName, adj) ->
            val vId = UuidGenerator.generate()
            val ve = VariantEntity(id = vId, productId = productId, name = vName, priceAdjustment = adj)
            // Create inventory per variant
            inventoryDao.insert(InventoryEntity(productId = productId, variantId = vId))
            ve
        }
        if (variants.isNotEmpty()) {
            variantDao.insertAll(variants)
        }

        syncQueueDao.enqueue(
            SyncQueueEntity(tableName = "products", recordId = productId, operation = "INSERT", payload = "{\"id\":\"$productId\"}")
        )
        return getProductById(productId)!!
    }

    suspend fun updateProduct(
        productId: String,
        name: String,
        categoryId: String?,
        description: String?,
        price: Long,
        imagePath: String?,
        variantNames: List<Pair<String, Long>>,
        productType: ProductType = ProductType.MENU_ITEM
    ) {
        val existing = productDao.getById(productId) ?: return
        productDao.update(existing.copy(
            name = name, categoryId = categoryId, description = description, price = price,
            imagePath = imagePath, productType = productType.name, synced = false, updatedAt = System.currentTimeMillis()
        ))

        // Replace variants
        variantDao.deleteByProductId(productId)
        val variants = variantNames.map { (vName, adj) ->
            VariantEntity(id = UuidGenerator.generate(), productId = productId, name = vName, priceAdjustment = adj)
        }
        if (variants.isNotEmpty()) {
            variantDao.insertAll(variants)
            variants.forEach { v ->
                val inv = inventoryDao.get(productId, v.id)
                if (inv == null) inventoryDao.insert(InventoryEntity(productId = productId, variantId = v.id))
            }
        }

        syncQueueDao.enqueue(
            SyncQueueEntity(tableName = "products", recordId = productId, operation = "UPDATE", payload = "{\"id\":\"$productId\"}")
        )
    }

    suspend fun deleteProduct(productId: String) {
        productDao.deleteById(productId)
        syncQueueDao.enqueue(
            SyncQueueEntity(tableName = "products", recordId = productId, operation = "DELETE", payload = "{\"id\":\"$productId\"}")
        )
    }

    private fun ProductWithVariants.toDomain() = Product(
        id = product.id,
        categoryId = product.categoryId ?: "",
        name = product.name,
        description = product.description,
        price = product.price,
        imagePath = product.imagePath,
        isActive = product.isActive,
        productType = ProductType.valueOf(product.productType),
        variants = variants.map { v ->
            Variant(id = v.id, productId = v.productId, name = v.name, priceAdjustment = v.priceAdjustment)
        }
    )
}
