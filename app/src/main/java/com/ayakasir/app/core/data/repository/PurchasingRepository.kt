package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.dao.GoodsReceivingDao
import com.ayakasir.app.core.data.local.dao.InventoryDao
import com.ayakasir.app.core.data.local.dao.ProductDao
import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.dao.VendorDao
import com.ayakasir.app.core.data.local.entity.GoodsReceivingEntity
import com.ayakasir.app.core.data.local.entity.GoodsReceivingItemEntity
import com.ayakasir.app.core.data.local.entity.InventoryEntity
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.domain.model.GoodsReceiving
import com.ayakasir.app.core.domain.model.GoodsReceivingItem
import com.ayakasir.app.core.util.UuidGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchasingRepository @Inject constructor(
    private val goodsReceivingDao: GoodsReceivingDao,
    private val inventoryDao: InventoryDao,
    private val productDao: ProductDao,
    private val vendorDao: VendorDao,
    private val syncQueueDao: SyncQueueDao
) {
    fun getAllReceiving(): Flow<List<GoodsReceiving>> =
        goodsReceivingDao.getAllWithItems().transform { list ->
            val receivingList = list.map { withItems ->
                val itemsWithProduct = goodsReceivingDao.getItemsWithProductInfo(withItems.receiving.id)

                GoodsReceiving(
                    id = withItems.receiving.id,
                    vendorId = withItems.receiving.vendorId ?: "",
                    vendorName = withItems.vendor?.name,
                    date = withItems.receiving.date,
                    notes = withItems.receiving.notes,
                    items = itemsWithProduct.map { item ->
                        GoodsReceivingItem(
                            id = item.id,
                            receivingId = item.receivingId,
                            productId = item.productId,
                            variantId = item.variantId,
                            productName = item.productName,
                            variantName = item.variantName,
                            qty = item.qty,
                            costPerUnit = item.costPerUnit,
                            unit = item.unit
                        )
                    }
                )
            }
            emit(receivingList)
        }

    suspend fun getReceivingById(receivingId: String): GoodsReceiving? {
        val withItems = goodsReceivingDao.getWithItemsById(receivingId) ?: return null
        val itemsWithProduct = goodsReceivingDao.getItemsWithProductInfo(withItems.receiving.id)

        return GoodsReceiving(
            id = withItems.receiving.id,
            vendorId = withItems.receiving.vendorId ?: "",
            vendorName = withItems.vendor?.name,
            date = withItems.receiving.date,
            notes = withItems.receiving.notes,
            items = itemsWithProduct.map { item ->
                GoodsReceivingItem(
                    id = item.id,
                    receivingId = item.receivingId,
                    productId = item.productId,
                    variantId = item.variantId,
                    productName = item.productName,
                    variantName = item.variantName,
                    qty = item.qty,
                    costPerUnit = item.costPerUnit,
                    unit = item.unit
                )
            }
        )
    }

    suspend fun createReceiving(
        vendorId: String,
        notes: String?,
        items: List<GoodsReceivingItem>
    ): String {
        val receivingId = UuidGenerator.generate()
        val now = System.currentTimeMillis()

        val entity = GoodsReceivingEntity(
            id = receivingId,
            vendorId = vendorId,
            date = now,
            notes = notes
        )
        goodsReceivingDao.insert(entity)

        val itemEntities = items.map { item ->
            GoodsReceivingItemEntity(
                id = UuidGenerator.generate(),
                receivingId = receivingId,
                productId = item.productId,
                variantId = item.variantId,
                qty = item.qty,
                costPerUnit = item.costPerUnit,
                unit = item.unit
            )
        }
        goodsReceivingDao.insertItems(itemEntities)

        // Increment inventory for each item
        items.forEach { item ->
            val existing = inventoryDao.get(item.productId, item.variantId)
            if (existing != null) {
                inventoryDao.incrementStock(item.productId, item.variantId, item.qty, now)
            } else {
                inventoryDao.insert(InventoryEntity(
                    productId = item.productId,
                    variantId = item.variantId,
                    currentQty = item.qty
                ))
            }
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    tableName = "inventory",
                    recordId = "${item.productId}:${item.variantId}",
                    operation = "UPDATE",
                    payload = "{\"product_id\":\"${item.productId}\",\"variant_id\":\"${item.variantId}\"}"
                )
            )
        }

        syncQueueDao.enqueue(
            SyncQueueEntity(tableName = "goods_receiving", recordId = receivingId, operation = "INSERT", payload = "{\"id\":\"$receivingId\"}")
        )

        return receivingId
    }

    suspend fun updateReceiving(
        receivingId: String,
        vendorId: String,
        notes: String?,
        newItems: List<GoodsReceivingItem>
    ) {
        val now = System.currentTimeMillis()

        // Get old items to calculate inventory delta
        val oldReceiving = goodsReceivingDao.getWithItemsById(receivingId) ?: return

        // Revert old inventory changes
        oldReceiving.items.forEach { oldItem ->
            val existing = inventoryDao.get(oldItem.productId, oldItem.variantId)
            if (existing != null) {
                inventoryDao.incrementStock(oldItem.productId, oldItem.variantId, -oldItem.qty, now)
                syncQueueDao.enqueue(
                    SyncQueueEntity(
                        tableName = "inventory",
                        recordId = "${oldItem.productId}:${oldItem.variantId}",
                        operation = "UPDATE",
                        payload = "{\"product_id\":\"${oldItem.productId}\",\"variant_id\":\"${oldItem.variantId}\"}"
                    )
                )
            }
        }

        // Update receiving entity
        val entity = GoodsReceivingEntity(
            id = receivingId,
            vendorId = vendorId,
            date = oldReceiving.receiving.date, // Keep original date
            notes = notes,
            synced = false,
            updatedAt = now
        )
        goodsReceivingDao.insert(entity)

        // Delete old items before inserting new ones
        goodsReceivingDao.deleteItems(receivingId)

        // Insert new items
        val itemEntities = newItems.map { item ->
            GoodsReceivingItemEntity(
                id = UuidGenerator.generate(),
                receivingId = receivingId,
                productId = item.productId,
                variantId = item.variantId,
                qty = item.qty,
                costPerUnit = item.costPerUnit,
                unit = item.unit
            )
        }
        goodsReceivingDao.insertItems(itemEntities)

        // Apply new inventory changes
        newItems.forEach { item ->
            val existing = inventoryDao.get(item.productId, item.variantId)
            if (existing != null) {
                inventoryDao.incrementStock(item.productId, item.variantId, item.qty, now)
            } else {
                inventoryDao.insert(InventoryEntity(
                    productId = item.productId,
                    variantId = item.variantId,
                    currentQty = item.qty
                ))
            }
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    tableName = "inventory",
                    recordId = "${item.productId}:${item.variantId}",
                    operation = "UPDATE",
                    payload = "{\"product_id\":\"${item.productId}\",\"variant_id\":\"${item.variantId}\"}"
                )
            )
        }

        syncQueueDao.enqueue(
            SyncQueueEntity(tableName = "goods_receiving", recordId = receivingId, operation = "UPDATE", payload = "{\"id\":\"$receivingId\"}")
        )
    }

    suspend fun deleteReceiving(receivingId: String) {
        val now = System.currentTimeMillis()

        // Get receiving with items before deleting
        val receiving = goodsReceivingDao.getWithItemsById(receivingId) ?: return

        // Decrement inventory for each item
        receiving.items.forEach { itemEntity ->
            val existing = inventoryDao.get(itemEntity.productId, itemEntity.variantId)
            if (existing != null) {
                val newQty = (existing.currentQty - itemEntity.qty).coerceAtLeast(0)
                if (newQty > 0) {
                    inventoryDao.incrementStock(itemEntity.productId, itemEntity.variantId, -itemEntity.qty, now)
                } else {
                    // If quantity reaches 0, we can either delete or keep it at 0
                    inventoryDao.incrementStock(itemEntity.productId, itemEntity.variantId, -itemEntity.qty, now)
                }
                syncQueueDao.enqueue(
                    SyncQueueEntity(
                        tableName = "inventory",
                        recordId = "${itemEntity.productId}:${itemEntity.variantId}",
                        operation = "UPDATE",
                        payload = "{\"product_id\":\"${itemEntity.productId}\",\"variant_id\":\"${itemEntity.variantId}\"}"
                    )
                )
            }
        }

        // Delete the receiving (cascade will delete items)
        goodsReceivingDao.delete(receivingId)

        // Enqueue sync operation
        syncQueueDao.enqueue(
            SyncQueueEntity(tableName = "goods_receiving", recordId = receivingId, operation = "DELETE", payload = "{\"id\":\"$receivingId\"}")
        )
    }
}
