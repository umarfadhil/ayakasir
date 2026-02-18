package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.dao.InventoryDao
import com.ayakasir.app.core.data.local.dao.ProductDao
import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.dao.VariantDao
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.domain.model.InventoryItem
import com.ayakasir.app.core.domain.model.SyncStatus
import com.ayakasir.app.core.session.SessionManager
import com.ayakasir.app.core.sync.SyncManager
import com.ayakasir.app.core.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val inventoryDao: InventoryDao,
    private val productDao: ProductDao,
    private val variantDao: VariantDao,
    private val syncQueueDao: SyncQueueDao,
    private val categoryDao: com.ayakasir.app.core.data.local.dao.CategoryDao,
    private val syncScheduler: SyncScheduler,
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager
) {
    private val restaurantId: String get() = sessionManager.currentRestaurantId ?: ""

    fun getAllInventory(): Flow<List<InventoryItem>> =
        combine(
            inventoryDao.getRawMaterialInventory(restaurantId),
            productDao.getAll(restaurantId),
            variantDao.let { productDao.getAllActiveRawMaterialsWithVariants(restaurantId) },
            categoryDao.getAllRawMaterialCategories(restaurantId)
        ) { invList, _, productsWithVariants, categories ->
            val productMap = productsWithVariants.associateBy { it.product.id }
            val categoryMap = categories.associateBy { it.id }
            invList.mapNotNull { inv ->
                val pw = productMap[inv.productId] ?: return@mapNotNull null
                val category = categoryMap[pw.product.categoryId] ?: return@mapNotNull null
                val variantName = if (inv.variantId.isNotEmpty()) {
                    pw.variants.find { it.id == inv.variantId }?.name
                } else null
                InventoryItem(
                    productId = inv.productId,
                    variantId = inv.variantId,
                    productName = pw.product.name,
                    variantName = variantName,
                    categoryId = category.id,
                    categoryName = category.name,
                    currentQty = inv.currentQty,
                    minQty = inv.minQty
                )
            }
        }

    fun getLowStockItems(): Flow<List<InventoryItem>> =
        getAllInventory().map { items -> items.filter { it.isLowStock } }

    fun getLowStockCount(): Flow<Int> = getLowStockItems().map { it.size }

    suspend fun adjustStock(productId: String, variantId: String, newQty: Int) {
        inventoryDao.setStock(productId, variantId, newQty)

        try {
            syncManager.pushToSupabase("inventory", "UPDATE", "$productId:$variantId")
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(tableName = "inventory", recordId = "$productId:$variantId", operation = "UPDATE",
                    payload = "{\"product_id\":\"$productId\",\"variant_id\":\"$variantId\",\"qty\":$newQty}")
            )
            syncScheduler.requestImmediateSync()
        }
    }

    suspend fun setMinQty(productId: String, variantId: String, minQty: Int) {
        inventoryDao.setMinQty(productId, variantId, minQty)

        try {
            syncManager.pushToSupabase("inventory", "UPDATE", "$productId:$variantId")
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(tableName = "inventory", recordId = "$productId:$variantId", operation = "UPDATE",
                    payload = "{\"product_id\":\"$productId\",\"variant_id\":\"$variantId\",\"min_qty\":$minQty}")
            )
            syncScheduler.requestImmediateSync()
        }
    }
}
