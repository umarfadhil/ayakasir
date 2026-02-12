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
import com.ayakasir.app.core.domain.model.PaymentMethod
import com.ayakasir.app.core.domain.model.Transaction
import com.ayakasir.app.core.domain.model.TransactionItem
import com.ayakasir.app.core.domain.model.TransactionStatus
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
    private val syncQueueDao: SyncQueueDao
) {
    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<Transaction>> =
        transactionDao.getByDateRange(startTime, endTime).map { list -> list.map { it.toDomain() } }

    fun getTotalByDateRange(startTime: Long, endTime: Long): Flow<Long> =
        transactionDao.getTotalByDateRange(startTime, endTime)

    fun getTotalByMethod(method: PaymentMethod, startTime: Long, endTime: Long): Flow<Long> =
        transactionDao.getTotalByMethodAndDateRange(method.name, startTime, endTime)

    fun getCountByDateRange(startTime: Long, endTime: Long): Flow<Int> =
        transactionDao.getCountByDateRange(startTime, endTime)

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
            status = TransactionStatus.COMPLETED.name
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
                subtotal = item.subtotal
            )
        }

        // Atomic: insert transaction + items
        transactionDao.insertFullTransaction(txnEntity, itemEntities)

        // Decrement stock for each item
        cartItems.forEach { item ->
            // Check if item has components (is a recipe menu)
            val components = productComponentDao.getByProductIdDirect(item.productId)

            if (components.isEmpty()) {
                // Simple product: deduct its own inventory
                val variantId = item.variantId ?: ""
                inventoryDao.decrementStock(item.productId, variantId, item.qty, now)
                syncQueueDao.enqueue(
                    SyncQueueEntity(
                        tableName = "inventory",
                        recordId = "${item.productId}:$variantId",
                        operation = "UPDATE",
                        payload = "{\"product_id\":\"${item.productId}\",\"variant_id\":\"$variantId\"}"
                    )
                )
            } else {
                // Recipe menu: deduct component inventory
                components.forEach { comp ->
                    val totalQtyNeeded = comp.requiredQty * item.qty
                    inventoryDao.decrementStock(
                        comp.componentProductId,
                        comp.componentVariantId,
                        totalQtyNeeded,
                        now
                    )
                    syncQueueDao.enqueue(
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

        // Enqueue sync
        syncQueueDao.enqueue(
            SyncQueueEntity(tableName = "transactions", recordId = txnId, operation = "INSERT", payload = "{\"id\":\"$txnId\"}")
        )

        return txnId
    }

    suspend fun voidTransaction(transactionId: String) {
        transactionDao.voidTransaction(transactionId)
        syncQueueDao.enqueue(
            SyncQueueEntity(tableName = "transactions", recordId = transactionId, operation = "UPDATE", payload = "{\"id\":\"$transactionId\",\"status\":\"VOIDED\"}")
        )
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
