package com.ayakasir.app.core.sync

import android.util.Log
import com.ayakasir.app.core.data.local.dao.CashWithdrawalDao
import com.ayakasir.app.core.data.local.dao.CategoryDao
import com.ayakasir.app.core.data.local.dao.GoodsReceivingDao
import com.ayakasir.app.core.data.local.dao.InventoryDao
import com.ayakasir.app.core.data.local.dao.ProductComponentDao
import com.ayakasir.app.core.data.local.dao.ProductDao
import com.ayakasir.app.core.data.local.dao.RestaurantDao
import com.ayakasir.app.core.data.local.dao.SyncQueueDao
import com.ayakasir.app.core.data.local.dao.TransactionDao
import com.ayakasir.app.core.data.local.dao.UserDao
import com.ayakasir.app.core.data.local.dao.VariantDao
import com.ayakasir.app.core.data.local.dao.VendorDao
import com.ayakasir.app.core.data.local.entity.GoodsReceivingEntity
import com.ayakasir.app.core.data.local.entity.GoodsReceivingItemEntity
import com.ayakasir.app.core.data.remote.dto.CashWithdrawalDto
import com.ayakasir.app.core.data.remote.dto.CategoryDto
import com.ayakasir.app.core.data.remote.dto.GoodsReceivingDto
import com.ayakasir.app.core.data.remote.dto.GoodsReceivingItemDto
import com.ayakasir.app.core.data.remote.dto.InventoryDto
import com.ayakasir.app.core.data.remote.dto.ProductComponentDto
import com.ayakasir.app.core.data.remote.dto.ProductDto
import com.ayakasir.app.core.data.remote.dto.TransactionDto
import com.ayakasir.app.core.data.remote.dto.TransactionItemDto
import com.ayakasir.app.core.data.remote.dto.UserDto
import com.ayakasir.app.core.data.remote.dto.VariantDto
import com.ayakasir.app.core.data.remote.dto.VendorDto
import com.ayakasir.app.core.data.remote.mapper.toDto
import com.ayakasir.app.core.data.remote.mapper.toEntity
import com.ayakasir.app.core.domain.model.SyncStatus
import com.ayakasir.app.core.session.SessionManager
import com.ayakasir.app.core.util.NetworkMonitor
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject
import javax.inject.Singleton

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
    private val cashWithdrawalDao: CashWithdrawalDao,
    private val restaurantDao: RestaurantDao,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "SyncManager"
    }

    data class SyncResult(
        val pushed: Int,
        val failed: Int
    ) {
        val hasFailures: Boolean get() = failed > 0
    }

    suspend fun syncAll(): SyncResult {
        if (!networkMonitor.isOnline()) {
            Log.w(TAG, "Network offline, skipping sync")
            return SyncResult(pushed = 0, failed = 0)
        }

        var pushed = 0
        var failed = 0

        // Phase 1: Push local changes to Supabase
        val batch = syncQueueDao.getNextBatch(limit = 50)
        Log.d(TAG, "Sync started: ${batch.size} items in queue")

        for (entry in batch) {
            try {
                Log.d(TAG, "Syncing ${entry.tableName}:${entry.operation} for ${entry.recordId}")
                pushToSupabase(entry.tableName, entry.operation, entry.recordId)
                syncQueueDao.delete(entry.id)
                pushed++
                Log.d(TAG, "Successfully synced ${entry.tableName}")
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed for ${entry.tableName}: ${e.message}", e)
                syncQueueDao.markRetry(id = entry.id)
                failed++
            }
        }

        Log.d(TAG, "Sync completed: $pushed pushed, $failed failed")

        // Phase 2: Pull server data to reconcile cross-device changes
        val restaurantId = sessionManager.currentRestaurantId
        if (restaurantId != null) {
            pullAllFromSupabase(restaurantId)
        }

        return SyncResult(pushed = pushed, failed = failed)
    }

    /**
     * Pull all tenant data from Supabase into the local Room cache.
     * Called after login (cross-device sync) and during periodic reconciliation.
     * All fetches run in parallel for speed.
     */
    suspend fun pullAllFromSupabase(restaurantId: String) {
        if (!networkMonitor.isOnline()) {
            Log.w(TAG, "Network offline, skipping pull")
            return
        }
        Log.d(TAG, "Pulling all data for restaurant $restaurantId")
        try {
            // Phase 1: Parent tables with no FK dependencies
            coroutineScope {
                awaitAll(
                    async {
                        runCatching {
                            supabaseClient.from("categories")
                                .select { filter { eq("restaurant_id", restaurantId) } }
                                .decodeList<CategoryDto>()
                                .forEach { categoryDao.insert(it.toEntity()) }
                        }.onFailure { Log.e(TAG, "Pull categories failed: ${it.message}") }
                    },
                    async {
                        runCatching {
                            supabaseClient.from("vendors")
                                .select { filter { eq("restaurant_id", restaurantId) } }
                                .decodeList<VendorDto>()
                                .forEach { vendorDao.insert(it.toEntity()) }
                        }.onFailure { Log.e(TAG, "Pull vendors failed: ${it.message}") }
                    },
                    async {
                        runCatching {
                            supabaseClient.from("users")
                                .select { filter { eq("restaurant_id", restaurantId) } }
                                .decodeList<UserDto>()
                                .forEach { userDao.insert(it.toEntity()) }
                        }.onFailure { Log.e(TAG, "Pull users failed: ${it.message}") }
                    },
                    async {
                        runCatching {
                            supabaseClient.from("cash_withdrawals")
                                .select { filter { eq("restaurant_id", restaurantId) } }
                                .decodeList<CashWithdrawalDto>()
                                .forEach { cashWithdrawalDao.insert(it.toEntity()) }
                        }.onFailure { Log.e(TAG, "Pull cash_withdrawals failed: ${it.message}") }
                    }
                )
            }

            // Phase 2: Tables that depend on phase 1 parents
            coroutineScope {
                awaitAll(
                    async {
                        runCatching {
                            supabaseClient.from("products")
                                .select { filter { eq("restaurant_id", restaurantId) } }
                                .decodeList<ProductDto>()
                                .forEach { productDao.insert(it.toEntity()) }
                        }.onFailure { Log.e(TAG, "Pull products failed: ${it.message}") }
                    },
                    async {
                        runCatching {
                            supabaseClient.from("goods_receiving")
                                .select { filter { eq("restaurant_id", restaurantId) } }
                                .decodeList<GoodsReceivingDto>()
                                .forEach { goodsReceivingDao.insert(it.toEntity()) }
                        }.onFailure { Log.e(TAG, "Pull goods_receiving failed: ${it.message}") }
                    },
                    async {
                        runCatching {
                            supabaseClient.from("transactions")
                                .select { filter { eq("restaurant_id", restaurantId) } }
                                .decodeList<TransactionDto>()
                                .forEach { transactionDao.insert(it.toEntity()) }
                        }.onFailure { Log.e(TAG, "Pull transactions failed: ${it.message}") }
                    },
                    async {
                        runCatching {
                            supabaseClient.from("inventory")
                                .select { filter { eq("restaurant_id", restaurantId) } }
                                .decodeList<InventoryDto>()
                                .forEach { inventoryDao.insert(it.toEntity()) }
                        }.onFailure { Log.e(TAG, "Pull inventory failed: ${it.message}") }
                    }
                )
            }

            // Phase 3: Child tables that depend on phase 2 parents
            coroutineScope {
                awaitAll(
                    async {
                        runCatching {
                            supabaseClient.from("variants")
                                .select { filter { eq("restaurant_id", restaurantId) } }
                                .decodeList<VariantDto>()
                                .forEach { variantDao.insert(it.toEntity()) }
                        }.onFailure { Log.e(TAG, "Pull variants failed: ${it.message}") }
                    },
                    async {
                        runCatching {
                            supabaseClient.from("product_components")
                                .select { filter { eq("restaurant_id", restaurantId) } }
                                .decodeList<ProductComponentDto>()
                                .forEach { productComponentDao.insert(it.toEntity()) }
                        }.onFailure { Log.e(TAG, "Pull product_components failed: ${it.message}") }
                    },
                    async {
                        runCatching {
                            supabaseClient.from("goods_receiving_items")
                                .select { filter { eq("restaurant_id", restaurantId) } }
                                .decodeList<GoodsReceivingItemDto>()
                                .forEach { dto -> goodsReceivingDao.insertItem(dto.toEntity()) }
                        }.onFailure { Log.e(TAG, "Pull goods_receiving_items failed: ${it.message}") }
                    },
                    async {
                        runCatching {
                            supabaseClient.from("transaction_items")
                                .select { filter { eq("restaurant_id", restaurantId) } }
                                .decodeList<TransactionItemDto>()
                                .forEach { dto -> transactionDao.insertItem(dto.toEntity()) }
                        }.onFailure { Log.e(TAG, "Pull transaction_items failed: ${it.message}") }
                    }
                )
            }

            Log.d(TAG, "Pull completed for restaurant $restaurantId")
        } catch (e: Exception) {
            Log.e(TAG, "Pull failed: ${e.message}", e)
        }
    }

    /**
     * Attempt immediate push to Supabase.
     * Called by repositories for network-first writes, and by SyncWorker for queue processing.
     */
    suspend fun pushToSupabase(
        tableName: String,
        operation: String,
        recordId: String
    ) {
        when (operation) {
            "INSERT", "UPDATE" -> pushUpsert(tableName, recordId, operation)
            "DELETE" -> pushDelete(tableName, recordId)
        }
    }

    /**
     * Push a goods_receiving header + its items directly from in-memory entities,
     * bypassing any Room read round-trip. Call this from PurchasingRepository immediately
     * after building the entities to avoid read-after-write race conditions.
     */
    suspend fun pushGoodsReceivingWithItems(
        entity: GoodsReceivingEntity,
        items: List<GoodsReceivingItemEntity>
    ) {
        val synced = SyncStatus.SYNCED.name
        val receivingDto = entity.toDto().copy(syncStatus = synced)
        supabaseClient.from("goods_receiving").upsert(json.encodeToJsonElement(receivingDto))
        supabaseClient.from("goods_receiving_items").delete {
            filter { eq("receiving_id", entity.id) }
        }
        items.forEach { item ->
            val itemDto = item.toDto().copy(syncStatus = synced)
            supabaseClient.from("goods_receiving_items").upsert(json.encodeToJsonElement(itemDto))
        }
        goodsReceivingDao.markSynced(entity.id)
        goodsReceivingDao.markItemsSynced(entity.id)
        Log.d(TAG, "pushGoodsReceivingWithItems: pushed ${items.size} items for ${entity.id}")
    }

    private suspend fun pushUpsert(tableName: String, recordId: String, operation: String) {
        val synced = SyncStatus.SYNCED.name
        when (tableName) {
            "users" -> {
                val entity = userDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(syncStatus = synced)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                userDao.markSynced(recordId)
            }
            "categories" -> {
                val entity = categoryDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(syncStatus = synced)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                categoryDao.markSynced(recordId)
            }
            "products" -> {
                val entity = productDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(syncStatus = synced)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                productDao.markSynced(recordId)
            }
            "variants" -> {
                val entity = variantDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(syncStatus = synced)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                variantDao.markSynced(recordId)
            }
            "vendors" -> {
                val entity = vendorDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(syncStatus = synced)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                vendorDao.markSynced(recordId)
            }
            "inventory" -> {
                val (productId, variantId) = parseInventoryKey(recordId)
                val entity = inventoryDao.get(productId, variantId) ?: return
                val dto = entity.toDto().copy(syncStatus = synced)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                inventoryDao.markSynced(productId, variantId)
            }
            "goods_receiving" -> {
                val entity = goodsReceivingDao.getById(recordId) ?: return
                val receivingDto = entity.toDto().copy(syncStatus = synced)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(receivingDto))
                supabaseClient.from("goods_receiving_items").delete {
                    filter { eq("receiving_id", recordId) }
                }
                // Use direct query instead of @Relation to avoid empty items bug
                val itemEntities = goodsReceivingDao.getItemsByReceivingId(recordId)
                itemEntities.forEach { item ->
                    val itemDto = item.toDto().copy(syncStatus = synced)
                    supabaseClient.from("goods_receiving_items").upsert(json.encodeToJsonElement(itemDto))
                }
                goodsReceivingDao.markSynced(recordId)
                goodsReceivingDao.markItemsSynced(recordId)
            }
            "transactions" -> {
                val withItems = transactionDao.getWithItemsById(recordId) ?: return
                val transactionDto = withItems.transaction.toDto().copy(syncStatus = synced)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(transactionDto))
                if (operation == "INSERT") {
                    withItems.items.forEach { item ->
                        val itemDto = item.toDto().copy(syncStatus = synced)
                        supabaseClient.from("transaction_items").upsert(json.encodeToJsonElement(itemDto))
                    }
                    transactionDao.markItemsSynced(recordId)
                }
                transactionDao.markSynced(recordId)
            }
            "product_components" -> {
                val entity = productComponentDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(syncStatus = synced)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                productComponentDao.markSynced(recordId)
            }
            "cash_withdrawals" -> {
                val entity = cashWithdrawalDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(syncStatus = synced)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                cashWithdrawalDao.markSynced(recordId)
            }
            "restaurants" -> {
                val entity = restaurantDao.getById(recordId) ?: return
                val dto = entity.toDto().copy(syncStatus = synced)
                supabaseClient.from(tableName).upsert(json.encodeToJsonElement(dto))
                restaurantDao.markSynced(recordId)
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
