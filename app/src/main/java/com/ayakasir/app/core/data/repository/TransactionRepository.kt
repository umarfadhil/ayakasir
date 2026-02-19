package com.ayakasir.app.core.data.repository

import com.ayakasir.app.core.data.local.dao.InventoryDao
import com.ayakasir.app.core.data.local.dao.ProductComponentDao
import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.dao.TransactionDao
import com.ayakasir.app.core.data.local.entity.SyncQueueEntity
import com.ayakasir.app.core.data.local.entity.TransactionEntity
import com.ayakasir.app.core.data.local.entity.TransactionItemEntity
import com.ayakasir.app.core.data.local.relation.TransactionWithItems
import com.ayakasir.app.core.domain.model.CartItem
import com.ayakasir.app.core.domain.model.LedgerType
import com.ayakasir.app.core.domain.model.PaymentMethod
import com.ayakasir.app.core.domain.model.SyncStatus
import com.ayakasir.app.core.domain.model.Transaction
import com.ayakasir.app.core.domain.model.TransactionItem
import com.ayakasir.app.core.domain.model.TransactionStatus
import com.ayakasir.app.core.session.SessionManager
import com.ayakasir.app.core.sync.SyncManager
import com.ayakasir.app.core.sync.SyncScheduler
import com.ayakasir.app.core.util.UnitConverter
import com.ayakasir.app.core.util.UuidGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val inventoryDao: InventoryDao,
    private val productComponentDao: ProductComponentDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler,
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager,
    private val generalLedgerRepository: GeneralLedgerRepository
) {
    private val restaurantId: String get() = sessionManager.currentRestaurantId ?: ""

    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<Transaction>> =
        transactionDao.getByDateRange(restaurantId, startTime, endTime).map { list -> list.map { it.toDomain() } }

    fun getTotalByDateRange(startTime: Long, endTime: Long): Flow<Long> =
        transactionDao.getTotalByDateRange(restaurantId, startTime, endTime)

    fun getTotalByMethod(method: PaymentMethod, startTime: Long, endTime: Long): Flow<Long> =
        transactionDao.getTotalByMethodAndDateRange(restaurantId, method.name, startTime, endTime)

    fun getCountByDateRange(startTime: Long, endTime: Long): Flow<Int> =
        transactionDao.getCountByDateRange(restaurantId, startTime, endTime)

    suspend fun createTransaction(
        userId: String,
        cartItems: List<CartItem>,
        paymentMethod: PaymentMethod,
        total: Long
    ): String {
        val txnId = UuidGenerator.generate()
        val now = System.currentTimeMillis()

        val txnEntity = TransactionEntity(
            id = txnId,
            userId = userId,
            date = now,
            total = total,
            paymentMethod = paymentMethod.name,
            status = TransactionStatus.COMPLETED.name,
            restaurantId = restaurantId,
            syncStatus = SyncStatus.PENDING.name,
            updatedAt = now
        )

        val itemEntities = cartItems.map { item ->
            TransactionItemEntity(
                id = UuidGenerator.generate(),
                transactionId = txnId,
                productId = item.productId,
                variantId = item.variantId ?: "",
                productName = item.productName,
                variantName = item.variantName,
                qty = item.qty,
                unitPrice = item.discountedUnitPrice,
                subtotal = item.subtotal,
                restaurantId = restaurantId
            )
        }

        // Atomic: insert transaction + items
        transactionDao.insertFullTransaction(txnEntity, itemEntities)

        // Decrement stock for each item and enqueue inventory sync
        val inventorySyncEntries = mutableListOf<SyncQueueEntity>()
        cartItems.forEach { item ->
            // Check if item has components (is a recipe menu)
            val components = productComponentDao.getByProductIdDirect(item.productId)

            if (components.isEmpty()) {
                // Simple product: deduct its own inventory
                val variantId = item.variantId ?: ""
                inventoryDao.decrementStock(item.productId, variantId, item.qty, now)
                inventorySyncEntries.add(
                    SyncQueueEntity(
                        tableName = "inventory",
                        recordId = "${item.productId}:$variantId",
                        operation = "UPDATE",
                        payload = "{\"product_id\":\"${item.productId}\",\"variant_id\":\"$variantId\"}"
                    )
                )
            } else {
                // Recipe menu: deduct component inventory with unit conversion
                components.forEach { comp ->
                    val totalQtyNeeded = comp.requiredQty * item.qty
                    // Convert component unit to inventory's base unit before decrementing
                    val inventory = inventoryDao.get(comp.componentProductId, comp.componentVariantId)
                    val decrQty = if (inventory != null && UnitConverter.areCompatible(comp.unit, inventory.unit)) {
                        UnitConverter.convert(totalQtyNeeded, comp.unit, inventory.unit)
                    } else {
                        // Normalize to base unit as fallback
                        UnitConverter.normalizeToBase(totalQtyNeeded, comp.unit).first
                    }
                    inventoryDao.decrementStock(
                        comp.componentProductId,
                        comp.componentVariantId,
                        decrQty,
                        now
                    )
                    inventorySyncEntries.add(
                        SyncQueueEntity(
                            tableName = "inventory",
                            recordId = "${comp.componentProductId}:${comp.componentVariantId}",
                            operation = "UPDATE",
                            payload = "{\"product_id\":\"${comp.componentProductId}\",\"variant_id\":\"${comp.componentVariantId}\"}"
                        )
                    )
                }
            }
        }

        // Try immediate push for transaction
        try {
            syncManager.pushToSupabase("transactions", "INSERT", txnId)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(tableName = "transactions", recordId = txnId, operation = "INSERT", payload = "{\"id\":\"$txnId\"}")
            )
            // Enqueue inventory sync entries on transaction push failure
            inventorySyncEntries.forEach { syncQueueDao.enqueue(it) }
            syncScheduler.requestImmediateSync()
        }

        // Push inventory changes independently (not gated by transaction push success/failure)
        inventorySyncEntries.forEach { entry ->
            try {
                syncManager.pushToSupabase(entry.tableName, entry.operation, entry.recordId)
            } catch (e: Exception) {
                syncQueueDao.enqueue(entry)
                syncScheduler.requestImmediateSync()
            }
        }

        // Record ledger entry for CASH sales
        if (paymentMethod == PaymentMethod.CASH) {
            generalLedgerRepository.recordEntry(
                type = LedgerType.SALE,
                amount = total,
                description = "Penjualan tunai",
                userId = userId,
                referenceId = txnId
            )
        }

        return txnId
    }

    suspend fun voidTransaction(transactionId: String) {
        transactionDao.voidTransaction(transactionId)

        try {
            syncManager.pushToSupabase("transactions", "UPDATE", transactionId)
        } catch (e: Exception) {
            syncQueueDao.enqueue(
                SyncQueueEntity(tableName = "transactions", recordId = transactionId, operation = "UPDATE", payload = "{\"id\":\"$transactionId\",\"status\":\"VOIDED\"}")
            )
            syncScheduler.requestImmediateSync()
        }
    }

    private fun TransactionWithItems.toDomain() = Transaction(
        id = transaction.id,
        userId = transaction.userId,
        date = transaction.date,
        total = transaction.total,
        paymentMethod = PaymentMethod.valueOf(transaction.paymentMethod),
        status = TransactionStatus.valueOf(transaction.status),
        items = items.map { item ->
            TransactionItem(
                id = item.id,
                transactionId = item.transactionId,
                productId = item.productId,
                variantId = item.variantId,
                productName = item.productName,
                variantName = item.variantName,
                qty = item.qty,
                unitPrice = item.unitPrice,
                subtotal = item.subtotal
            )
        }
    )
}
