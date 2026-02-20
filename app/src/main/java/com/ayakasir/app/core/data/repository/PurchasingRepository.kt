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
import com.ayakasir.app.core.domain.model.LedgerType
import com.ayakasir.app.core.domain.model.SyncStatus
import com.ayakasir.app.core.session.SessionManager
import com.ayakasir.app.core.sync.SyncManager
import com.ayakasir.app.core.sync.SyncScheduler
import com.ayakasir.app.core.util.UnitConverter
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
    private val syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler,
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager,
    private val generalLedgerRepository: GeneralLedgerRepository
) {
    private val restaurantId: String get() = sessionManager.currentRestaurantId ?: ""

    fun getAllReceiving(): Flow<List<GoodsReceiving>> =
        goodsReceivingDao.getAllWithItems(restaurantId).transform { list ->
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
        date: Long,
        notes: String?,
        items: List<GoodsReceivingItem>
    ): String {
        val receivingId = UuidGenerator.generate()
        val now = System.currentTimeMillis()

        val entity = GoodsReceivingEntity(
            id = receivingId,
            vendorId = vendorId,
            date = date,
            notes = notes,
            restaurantId = restaurantId,
            syncStatus = SyncStatus.PENDING.name,
            updatedAt = now
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
                unit = item.unit,
                restaurantId = restaurantId
            )
        }
        goodsReceivingDao.insertItems(itemEntities)

        // Increment inventory for each item (normalize to base unit)
        val inventorySyncEntries = mutableListOf<SyncQueueEntity>()
        items.forEach { item ->
            val (normalizedQty, baseUnit) = UnitConverter.normalizeToBase(item.qty, item.unit)
            val existing = inventoryDao.get(item.productId, item.variantId)
            if (existing != null) {
                if (UnitConverter.areCompatible(item.unit, existing.unit)) {
                    val addQty = UnitConverter.convert(item.qty, item.unit, existing.unit)
                    inventoryDao.incrementStock(item.productId, item.variantId, addQty, now)
                } else {
                    // Incompatible units: update inventory unit to base unit so unit column matches
                    inventoryDao.insert(existing.copy(
                        currentQty = existing.currentQty + normalizedQty,
                        unit = baseUnit,
                        syncStatus = SyncStatus.PENDING.name,
                        updatedAt = now
                    ))
                }
            } else {
                inventoryDao.insert(InventoryEntity(
                    productId = item.productId,
                    variantId = item.variantId,
                    currentQty = normalizedQty,
                    unit = baseUnit,
                    restaurantId = restaurantId
                ))
            }
            inventorySyncEntries.add(
                SyncQueueEntity(
                    tableName = "inventory",
                    recordId = "${item.productId}:${item.variantId}",
                    operation = "UPDATE",
                    payload = "{\"product_id\":\"${item.productId}\",\"variant_id\":\"${item.variantId}\"}"
                )
            )
        }

        // Push goods_receiving + items
        try {
            syncManager.pushGoodsReceivingWithItems(entity, itemEntities)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(tableName = "goods_receiving", recordId = receivingId, operation = "INSERT", payload = "{\"id\":\"$receivingId\"}")
            )
            syncScheduler.requestImmediateSync()
        }

        // Push inventory changes independently (not gated by goods_receiving success)
        inventorySyncEntries.forEach { entry ->
            try {
                syncManager.pushToSupabase(entry.tableName, entry.operation, entry.recordId)
            } catch (e: Exception) {
                syncQueueDao.enqueue(entry)
                syncScheduler.requestImmediateSync()
            }
        }

        // Record COGS ledger entry (non-cash, informational)
        val totalCost = items.sumOf { it.qty.toLong() * it.costPerUnit }
        if (totalCost > 0) {
            val userId = sessionManager.currentUser.value?.id ?: ""
            generalLedgerRepository.recordEntry(
                type = LedgerType.COGS,
                amount = -totalCost,
                description = "Pembelian barang",
                userId = userId,
                referenceId = receivingId
            )
        }

        return receivingId
    }

    suspend fun updateReceiving(
        receivingId: String,
        vendorId: String,
        date: Long,
        notes: String?,
        newItems: List<GoodsReceivingItem>
    ) {
        val now = System.currentTimeMillis()

        // Get old items via direct query to avoid @Relation empty-list bug
        val oldItems = goodsReceivingDao.getItemsByReceivingId(receivingId)

        // Revert old inventory changes (normalize to base unit)
        val inventorySyncEntries = mutableListOf<SyncQueueEntity>()
        oldItems.forEach { oldItem ->
            val existing = inventoryDao.get(oldItem.productId, oldItem.variantId)
            if (existing != null) {
                val revertQty = if (UnitConverter.areCompatible(oldItem.unit, existing.unit)) {
                    UnitConverter.convert(oldItem.qty, oldItem.unit, existing.unit)
                } else {
                    UnitConverter.normalizeToBase(oldItem.qty, oldItem.unit).first
                }
                inventoryDao.incrementStock(oldItem.productId, oldItem.variantId, -revertQty, now)
                inventorySyncEntries.add(
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
            date = date,
            notes = notes,
            restaurantId = restaurantId,
            syncStatus = SyncStatus.PENDING.name,
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
                unit = item.unit,
                restaurantId = restaurantId
            )
        }
        goodsReceivingDao.insertItems(itemEntities)

        // Apply new inventory changes (normalize to base unit)
        newItems.forEach { item ->
            val (normalizedQty, baseUnit) = UnitConverter.normalizeToBase(item.qty, item.unit)
            val existing = inventoryDao.get(item.productId, item.variantId)
            if (existing != null) {
                if (UnitConverter.areCompatible(item.unit, existing.unit)) {
                    val addQty = UnitConverter.convert(item.qty, item.unit, existing.unit)
                    inventoryDao.incrementStock(item.productId, item.variantId, addQty, now)
                } else {
                    // Incompatible units: update inventory unit to base unit so unit column matches
                    inventoryDao.insert(existing.copy(
                        currentQty = existing.currentQty + normalizedQty,
                        unit = baseUnit,
                        syncStatus = SyncStatus.PENDING.name,
                        updatedAt = now
                    ))
                }
            } else {
                inventoryDao.insert(InventoryEntity(
                    productId = item.productId,
                    variantId = item.variantId,
                    currentQty = normalizedQty,
                    unit = baseUnit,
                    restaurantId = restaurantId
                ))
            }
            inventorySyncEntries.add(
                SyncQueueEntity(
                    tableName = "inventory",
                    recordId = "${item.productId}:${item.variantId}",
                    operation = "UPDATE",
                    payload = "{\"product_id\":\"${item.productId}\",\"variant_id\":\"${item.variantId}\"}"
                )
            )
        }

        // Push goods_receiving + items
        try {
            syncManager.pushGoodsReceivingWithItems(entity, itemEntities)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(tableName = "goods_receiving", recordId = receivingId, operation = "UPDATE", payload = "{\"id\":\"$receivingId\"}")
            )
            syncScheduler.requestImmediateSync()
        }

        // Push inventory changes independently
        inventorySyncEntries.forEach { entry ->
            try {
                syncManager.pushToSupabase(entry.tableName, entry.operation, entry.recordId)
            } catch (e: Exception) {
                syncQueueDao.enqueue(entry)
                syncScheduler.requestImmediateSync()
            }
        }

        // Update COGS ledger entry: delete old + record new
        generalLedgerRepository.deleteByReferenceId(receivingId, LedgerType.COGS)
        val totalCost = newItems.sumOf { it.qty.toLong() * it.costPerUnit }
        if (totalCost > 0) {
            val userId = sessionManager.currentUser.value?.id ?: ""
            generalLedgerRepository.recordEntry(
                type = LedgerType.COGS,
                amount = -totalCost,
                description = "Pembelian barang",
                userId = userId,
                referenceId = receivingId
            )
        }
    }

    suspend fun deleteReceiving(receivingId: String) {
        val now = System.currentTimeMillis()

        // Get items before deleting (use direct query, not @Relation)
        val receivingItems = goodsReceivingDao.getItemsByReceivingId(receivingId)

        // Decrement inventory for each item (convert to inventory's base unit)
        val inventorySyncEntries = mutableListOf<SyncQueueEntity>()
        receivingItems.forEach { itemEntity ->
            val existing = inventoryDao.get(itemEntity.productId, itemEntity.variantId)
            if (existing != null) {
                val revertQty = if (UnitConverter.areCompatible(itemEntity.unit, existing.unit)) {
                    UnitConverter.convert(itemEntity.qty, itemEntity.unit, existing.unit)
                } else {
                    UnitConverter.normalizeToBase(itemEntity.qty, itemEntity.unit).first
                }
                inventoryDao.incrementStock(itemEntity.productId, itemEntity.variantId, -revertQty, now)
                inventorySyncEntries.add(
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

        // Push goods_receiving delete
        try {
            syncManager.pushToSupabase("goods_receiving", "DELETE", receivingId)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(tableName = "goods_receiving", recordId = receivingId, operation = "DELETE", payload = "{\"id\":\"$receivingId\"}")
            )
            syncScheduler.requestImmediateSync()
        }

        // Push inventory changes independently
        inventorySyncEntries.forEach { entry ->
            try {
                syncManager.pushToSupabase(entry.tableName, entry.operation, entry.recordId)
            } catch (e: Exception) {
                syncQueueDao.enqueue(entry)
                syncScheduler.requestImmediateSync()
            }
        }

        // Delete COGS ledger entry
        generalLedgerRepository.deleteByReferenceId(receivingId, LedgerType.COGS)
    }
}
