package com.ayakasir.app.core.sync

import com.ayakasir.app.core.data.local.dao.CashWithdrawalDao
import com.ayakasir.app.core.data.local.dao.CategoryDao
import com.ayakasir.app.core.data.local.dao.GoodsReceivingDao
import com.ayakasir.app.core.data.local.dao.InventoryDao
import com.ayakasir.app.core.data.local.dao.ProductComponentDao
import com.ayakasir.app.core.data.local.dao.ProductDao
import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.dao.TransactionDao
import com.ayakasir.app.core.data.local.dao.UserDao
import com.ayakasir.app.core.data.local.dao.VariantDao
import com.ayakasir.app.core.data.local.dao.VendorDao
import com.ayakasir.app.core.data.remote.mapper.toDto
import com.ayakasir.app.core.util.NetworkMonitor
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject

class SyncManager @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val supabaseClient: SupabaseClient,
    private val networkMonitor: NetworkMonitor,
    private val conflictResolver: ConflictResolver,
    private val json: Json,
    private val userDao: UserDao,
    private val categoryDao: CategoryDao,
    private val productDao: ProductDao,
    private val variantDao: VariantDao,
    private val vendorDao: VendorDao,
    private val inventoryDao: InventoryDao,
    private val goodsReceivingDao: GoodsReceivingDao,
    private val transactionDao: TransactionDao,
    private val productComponentDao: ProductComponentDao,
    private val cashWithdrawalDao: CashWithdrawalDao
) {
    data class SyncResult(
        val pushed: Int,
        val failed: Int
    ) {
        val hasFailures: Boolean get() = failed > 0
    }

    suspend fun syncAll(): SyncResult {
        if (!networkMonitor.isOnline()) {
            return SyncResult(pushed = 0, failed = 0)
        }

        var pushed = 0
        var failed = 0

        // Phase 1: Push local changes to Supabase
        val batch = syncQueueDao.getNextBatch(limit = 50)
        for (entry in batch) {
            try {
                pushToSupabase(entry.tableName, entry.operation, entry.recordId)
                syncQueueDao.delete(entry.id)
                pushed++
            } catch (e: Exception) {
                syncQueueDao.markRetry(id = entry.id)
                failed++
            }
        }

        return SyncResult(pushed = pushed, failed = failed)
    }

    private suspend fun pushToSupabase(
        tableName: String,
        operation: String,
        recordId: String
    ) {
        when (operation) {
            "INSERT", "UPDATE" -> pushUpsert(tableName, recordId, operation)
            "DELETE" -> pushDelete(tableName, recordId)
        }
    }

    private suspend fun pushUpsert(tableName: String, recordId: String, operation: String) {
        when (tableName) {
            "users" -> {
                val entity = userDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(synced = true)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                userDao.markSynced(recordId)
            }
            "categories" -> {
                val entity = categoryDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(synced = true)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                categoryDao.markSynced(recordId)
            }
            "products" -> {
                val entity = productDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(synced = true)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                productDao.markSynced(recordId)
            }
            "variants" -> {
                val entity = variantDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(synced = true)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                variantDao.markSynced(recordId)
            }
            "vendors" -> {
                val entity = vendorDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(synced = true)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                vendorDao.markSynced(recordId)
            }
            "inventory" -> {
                val (productId, variantId) = parseInventoryKey(recordId)
                val entity = inventoryDao.get(productId, variantId) ?: return
                val dto = entity.toDto().copy(synced = true)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                inventoryDao.markSynced(productId, variantId)
            }
            "goods_receiving" -> {
                val withItems = goodsReceivingDao.getWithItemsById(recordId) ?: return
                val receivingDto = withItems.receiving.toDto().copy(synced = true)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(receivingDto))
                supabaseClient.from("goods_receiving_items").delete {
                    filter { eq("receiving_id", recordId) }
                }
                val items = withItems.items.map { it.toDto().copy(synced = true) }
                if (items.isNotEmpty()) {
                    supabaseClient.from("goods_receiving_items").upsert(json.encodeToJsonElement(items))
                }
                goodsReceivingDao.markSynced(recordId)
                goodsReceivingDao.markItemsSynced(recordId)
            }
            "transactions" -> {
                val withItems = transactionDao.getWithItemsById(recordId) ?: return
                val transactionDto = withItems.transaction.toDto().copy(synced = true)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(transactionDto))
                if (operation == "INSERT") {
                    val items = withItems.items.map { it.toDto().copy(synced = true) }
                    if (items.isNotEmpty()) {
                        supabaseClient.from("transaction_items").upsert(json.encodeToJsonElement(items))
                    }
                    transactionDao.markItemsSynced(recordId)
                }
                transactionDao.markSynced(recordId)
            }
            "product_components" -> {
                val entity = productComponentDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(synced = true)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                productComponentDao.markSynced(recordId)
            }
            "cash_withdrawals" -> {
                val entity = cashWithdrawalDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(synced = true)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                cashWithdrawalDao.markSynced(recordId)
            }
        }
    }

    private suspend fun pushDelete(tableName: String, recordId: String) {
        when (tableName) {
            "inventory" -> {
                val (productId, variantId) = parseInventoryKey(recordId)
                supabaseClient.from(tableName).delete {
                    filter {
                        eq("product_id", productId)
                        eq("variant_id", variantId)
                    }
                }
            }
            "goods_receiving" -> {
                supabaseClient.from("goods_receiving_items").delete {
                    filter { eq("receiving_id", recordId) }
                }
                supabaseClient.from(tableName).delete {
                    filter { eq("id", recordId) }
                }
            }
            "transactions" -> {
                supabaseClient.from("transaction_items").delete {
                    filter { eq("transaction_id", recordId) }
                }
                supabaseClient.from(tableName).delete {
                    filter { eq("id", recordId) }
                }
            }
            else -> {
                supabaseClient.from(tableName).delete {
                    filter { eq("id", recordId) }
                }
            }
        }
    }

    private fun parseInventoryKey(recordId: String): Pair<String, String> {
        val parts = recordId.split(":", limit = 2)
        val productId = parts.firstOrNull().orEmpty()
        val variantId = parts.getOrNull(1).orEmpty()
        return productId to variantId
    }
}
