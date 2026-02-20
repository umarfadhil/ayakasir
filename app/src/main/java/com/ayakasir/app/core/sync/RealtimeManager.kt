package com.ayakasir.app.core.sync

import android.util.Log
import com.ayakasir.app.core.data.local.dao.CashWithdrawalDao
import com.ayakasir.app.core.data.local.dao.CategoryDao
import com.ayakasir.app.core.data.local.dao.GeneralLedgerDao
import com.ayakasir.app.core.data.local.dao.GoodsReceivingDao
import com.ayakasir.app.core.data.local.dao.InventoryDao
import com.ayakasir.app.core.data.local.dao.ProductComponentDao
import com.ayakasir.app.core.data.local.dao.ProductDao
import com.ayakasir.app.core.data.local.dao.TransactionDao
import com.ayakasir.app.core.data.local.dao.RestaurantDao
import com.ayakasir.app.core.data.local.dao.UserDao
import com.ayakasir.app.core.data.local.dao.VariantDao
import com.ayakasir.app.core.data.local.dao.VendorDao
import com.ayakasir.app.core.data.local.datastore.QrisSettingsDataStore
import com.ayakasir.app.core.data.remote.dto.CashWithdrawalDto
import com.ayakasir.app.core.data.remote.dto.CategoryDto
import com.ayakasir.app.core.data.remote.dto.GeneralLedgerDto
import com.ayakasir.app.core.data.remote.dto.GoodsReceivingDto
import com.ayakasir.app.core.data.remote.dto.GoodsReceivingItemDto
import com.ayakasir.app.core.data.remote.dto.InventoryDto
import com.ayakasir.app.core.data.remote.dto.ProductComponentDto
import com.ayakasir.app.core.data.remote.dto.ProductDto
import com.ayakasir.app.core.data.remote.dto.RestaurantDto
import com.ayakasir.app.core.data.remote.dto.TransactionDto
import com.ayakasir.app.core.data.remote.dto.TransactionItemDto
import com.ayakasir.app.core.data.remote.dto.UserDto
import com.ayakasir.app.core.data.remote.dto.VariantDto
import com.ayakasir.app.core.data.remote.dto.VendorDto
import com.ayakasir.app.core.data.remote.mapper.toEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeOldRecord
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeManager @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val json: Json,
    private val categoryDao: CategoryDao,
    private val productDao: ProductDao,
    private val variantDao: VariantDao,
    private val vendorDao: VendorDao,
    private val inventoryDao: InventoryDao,
    private val goodsReceivingDao: GoodsReceivingDao,
    private val transactionDao: TransactionDao,
    private val productComponentDao: ProductComponentDao,
    private val cashWithdrawalDao: CashWithdrawalDao,
    private val generalLedgerDao: GeneralLedgerDao,
    private val userDao: UserDao,
    private val restaurantDao: RestaurantDao,
    private val qrisSettingsDataStore: QrisSettingsDataStore
) {
    companion object {
        private const val TAG = "RealtimeManager"
    }

    private var scope: CoroutineScope? = null
    private var channel: RealtimeChannel? = null

    fun connect(restaurantId: String) {
        disconnect()
        Log.d(TAG, "Connecting realtime for restaurant $restaurantId")

        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope

        newScope.launch {
            try {
                val ch = supabaseClient.channel("tenant-$restaurantId")
                channel = ch

                // Set up all table listeners BEFORE subscribing
                setupCategoriesListener(ch, restaurantId, newScope)
                setupProductsListener(ch, restaurantId, newScope)
                setupVariantsListener(ch, restaurantId, newScope)
                setupVendorsListener(ch, restaurantId, newScope)
                setupInventoryListener(ch, restaurantId, newScope)
                setupGoodsReceivingListener(ch, restaurantId, newScope)
                setupGoodsReceivingItemsListener(ch, restaurantId, newScope)
                setupTransactionsListener(ch, restaurantId, newScope)
                setupTransactionItemsListener(ch, restaurantId, newScope)
                setupProductComponentsListener(ch, restaurantId, newScope)
                setupCashWithdrawalsListener(ch, restaurantId, newScope)
                setupGeneralLedgerListener(ch, restaurantId, newScope)
                setupRestaurantsListener(ch, restaurantId, newScope)

                ch.subscribe()
                Log.d(TAG, "Realtime channel subscribed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect realtime: ${e.message}", e)
            }
        }
    }

    fun disconnect() {
        val currentScope = scope
        val currentChannel = channel
        if (currentScope != null && currentChannel != null) {
            currentScope.launch {
                try {
                    currentChannel.unsubscribe()
                    supabaseClient.realtime.removeChannel(currentChannel)
                } catch (e: Exception) {
                    Log.w(TAG, "Error disconnecting realtime: ${e.message}")
                }
            }
        }
        scope?.cancel()
        scope = null
        channel = null
        Log.d(TAG, "Realtime disconnected")
    }

    private fun setupCategoriesListener(ch: RealtimeChannel, restaurantId: String, scope: CoroutineScope) {
        val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "categories"
            filter(column = "restaurant_id", operator = FilterOperator.EQ, value = restaurantId)
        }
        scope.launch {
            flow.collect { action ->
                runCatching {
                    when (action) {
                        is PostgresAction.Insert -> categoryDao.insert(action.decodeRecord<CategoryDto>().toEntity())
                        is PostgresAction.Update -> categoryDao.insert(action.decodeRecord<CategoryDto>().toEntity())
                        is PostgresAction.Delete -> action.decodeOldRecord<CategoryDto>().let { categoryDao.deleteById(it.id) }
                        else -> {}
                    }
                }.onFailure { Log.e(TAG, "Realtime categories error: ${it.message}") }
            }
        }
    }

    private fun setupProductsListener(ch: RealtimeChannel, restaurantId: String, scope: CoroutineScope) {
        val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "products"
            filter(column = "restaurant_id", operator = FilterOperator.EQ, value = restaurantId)
        }
        scope.launch {
            flow.collect { action ->
                runCatching {
                    when (action) {
                        is PostgresAction.Insert -> productDao.insert(action.decodeRecord<ProductDto>().toEntity())
                        is PostgresAction.Update -> productDao.insert(action.decodeRecord<ProductDto>().toEntity())
                        is PostgresAction.Delete -> action.decodeOldRecord<ProductDto>().let { productDao.deleteById(it.id) }
                        else -> {}
                    }
                }.onFailure { Log.e(TAG, "Realtime products error: ${it.message}") }
            }
        }
    }

    private fun setupVariantsListener(ch: RealtimeChannel, restaurantId: String, scope: CoroutineScope) {
        val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "variants"
            filter(column = "restaurant_id", operator = FilterOperator.EQ, value = restaurantId)
        }
        scope.launch {
            flow.collect { action ->
                runCatching {
                    when (action) {
                        is PostgresAction.Insert -> variantDao.insert(action.decodeRecord<VariantDto>().toEntity())
                        is PostgresAction.Update -> variantDao.insert(action.decodeRecord<VariantDto>().toEntity())
                        is PostgresAction.Delete -> action.decodeOldRecord<VariantDto>().let { variantDao.deleteById(it.id) }
                        else -> {}
                    }
                }.onFailure { Log.e(TAG, "Realtime variants error: ${it.message}") }
            }
        }
    }

    private fun setupVendorsListener(ch: RealtimeChannel, restaurantId: String, scope: CoroutineScope) {
        val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "vendors"
            filter(column = "restaurant_id", operator = FilterOperator.EQ, value = restaurantId)
        }
        scope.launch {
            flow.collect { action ->
                runCatching {
                    when (action) {
                        is PostgresAction.Insert -> vendorDao.insert(action.decodeRecord<VendorDto>().toEntity())
                        is PostgresAction.Update -> vendorDao.insert(action.decodeRecord<VendorDto>().toEntity())
                        is PostgresAction.Delete -> action.decodeOldRecord<VendorDto>().let { vendorDao.deleteById(it.id) }
                        else -> {}
                    }
                }.onFailure { Log.e(TAG, "Realtime vendors error: ${it.message}") }
            }
        }
    }

    private fun setupInventoryListener(ch: RealtimeChannel, restaurantId: String, scope: CoroutineScope) {
        val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "inventory"
            filter(column = "restaurant_id", operator = FilterOperator.EQ, value = restaurantId)
        }
        scope.launch {
            flow.collect { action ->
                runCatching {
                    when (action) {
                        is PostgresAction.Insert -> inventoryDao.insert(action.decodeRecord<InventoryDto>().toEntity())
                        is PostgresAction.Update -> inventoryDao.insert(action.decodeRecord<InventoryDto>().toEntity())
                        else -> {}
                    }
                }.onFailure { Log.e(TAG, "Realtime inventory error: ${it.message}") }
            }
        }
    }

    private fun setupGoodsReceivingListener(ch: RealtimeChannel, restaurantId: String, scope: CoroutineScope) {
        val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "goods_receiving"
            filter(column = "restaurant_id", operator = FilterOperator.EQ, value = restaurantId)
        }
        scope.launch {
            flow.collect { action ->
                runCatching {
                    when (action) {
                        is PostgresAction.Insert -> goodsReceivingDao.insert(action.decodeRecord<GoodsReceivingDto>().toEntity())
                        is PostgresAction.Update -> goodsReceivingDao.insert(action.decodeRecord<GoodsReceivingDto>().toEntity())
                        is PostgresAction.Delete -> action.decodeOldRecord<GoodsReceivingDto>().let { goodsReceivingDao.delete(it.id) }
                        else -> {}
                    }
                }.onFailure { Log.e(TAG, "Realtime goods_receiving error: ${it.message}") }
            }
        }
    }

    private fun setupGoodsReceivingItemsListener(ch: RealtimeChannel, restaurantId: String, scope: CoroutineScope) {
        val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "goods_receiving_items"
            filter(column = "restaurant_id", operator = FilterOperator.EQ, value = restaurantId)
        }
        scope.launch {
            flow.collect { action ->
                runCatching {
                    when (action) {
                        is PostgresAction.Insert -> goodsReceivingDao.insertItem(action.decodeRecord<GoodsReceivingItemDto>().toEntity())
                        is PostgresAction.Update -> goodsReceivingDao.insertItem(action.decodeRecord<GoodsReceivingItemDto>().toEntity())
                        else -> {}
                    }
                }.onFailure { Log.e(TAG, "Realtime goods_receiving_items error: ${it.message}") }
            }
        }
    }

    private fun setupTransactionsListener(ch: RealtimeChannel, restaurantId: String, scope: CoroutineScope) {
        val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "transactions"
            filter(column = "restaurant_id", operator = FilterOperator.EQ, value = restaurantId)
        }
        scope.launch {
            flow.collect { action ->
                runCatching {
                    when (action) {
                        is PostgresAction.Insert -> transactionDao.insert(action.decodeRecord<TransactionDto>().toEntity())
                        is PostgresAction.Update -> transactionDao.insert(action.decodeRecord<TransactionDto>().toEntity())
                        else -> {}
                    }
                }.onFailure { Log.e(TAG, "Realtime transactions error: ${it.message}") }
            }
        }
    }

    private fun setupTransactionItemsListener(ch: RealtimeChannel, restaurantId: String, scope: CoroutineScope) {
        val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "transaction_items"
            filter(column = "restaurant_id", operator = FilterOperator.EQ, value = restaurantId)
        }
        scope.launch {
            flow.collect { action ->
                runCatching {
                    when (action) {
                        is PostgresAction.Insert -> transactionDao.insertItem(action.decodeRecord<TransactionItemDto>().toEntity())
                        is PostgresAction.Update -> transactionDao.insertItem(action.decodeRecord<TransactionItemDto>().toEntity())
                        else -> {}
                    }
                }.onFailure { Log.e(TAG, "Realtime transaction_items error: ${it.message}") }
            }
        }
    }

    private fun setupProductComponentsListener(ch: RealtimeChannel, restaurantId: String, scope: CoroutineScope) {
        val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "product_components"
            filter(column = "restaurant_id", operator = FilterOperator.EQ, value = restaurantId)
        }
        scope.launch {
            flow.collect { action ->
                runCatching {
                    when (action) {
                        is PostgresAction.Insert -> productComponentDao.insert(action.decodeRecord<ProductComponentDto>().toEntity())
                        is PostgresAction.Update -> productComponentDao.insert(action.decodeRecord<ProductComponentDto>().toEntity())
                        is PostgresAction.Delete -> action.decodeOldRecord<ProductComponentDto>().let { productComponentDao.deleteById(it.id) }
                        else -> {}
                    }
                }.onFailure { Log.e(TAG, "Realtime product_components error: ${it.message}") }
            }
        }
    }

    private fun setupCashWithdrawalsListener(ch: RealtimeChannel, restaurantId: String, scope: CoroutineScope) {
        val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "cash_withdrawals"
            filter(column = "restaurant_id", operator = FilterOperator.EQ, value = restaurantId)
        }
        scope.launch {
            flow.collect { action ->
                runCatching {
                    when (action) {
                        is PostgresAction.Insert -> cashWithdrawalDao.insert(action.decodeRecord<CashWithdrawalDto>().toEntity())
                        is PostgresAction.Update -> cashWithdrawalDao.insert(action.decodeRecord<CashWithdrawalDto>().toEntity())
                        else -> {}
                    }
                }.onFailure { Log.e(TAG, "Realtime cash_withdrawals error: ${it.message}") }
            }
        }
    }

    private fun setupGeneralLedgerListener(ch: RealtimeChannel, restaurantId: String, scope: CoroutineScope) {
        val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "general_ledger"
            filter(column = "restaurant_id", operator = FilterOperator.EQ, value = restaurantId)
        }
        scope.launch {
            flow.collect { action ->
                runCatching {
                    when (action) {
                        is PostgresAction.Insert -> generalLedgerDao.insert(action.decodeRecord<GeneralLedgerDto>().toEntity())
                        is PostgresAction.Update -> generalLedgerDao.insert(action.decodeRecord<GeneralLedgerDto>().toEntity())
                        is PostgresAction.Delete -> action.decodeOldRecord<GeneralLedgerDto>().let { generalLedgerDao.deleteById(it.id) }
                        else -> {}
                    }
                }.onFailure { Log.e(TAG, "Realtime general_ledger error: ${it.message}") }
            }
        }
    }

    private fun setupRestaurantsListener(ch: RealtimeChannel, restaurantId: String, scope: CoroutineScope) {
        val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "restaurants"
            filter(column = "id", operator = FilterOperator.EQ, value = restaurantId)
        }
        scope.launch {
            flow.collect { action ->
                runCatching {
                    when (action) {
                        is PostgresAction.Update -> {
                            val dto = action.decodeRecord<RestaurantDto>()
                            restaurantDao.insert(dto.toEntity())
                            qrisSettingsDataStore.saveSettings(
                                dto.qrisImageUrl.orEmpty(),
                                dto.qrisMerchantName.orEmpty()
                            )
                        }
                        else -> {}
                    }
                }.onFailure { Log.e(TAG, "Realtime restaurants error: ${it.message}") }
            }
        }
    }
}
