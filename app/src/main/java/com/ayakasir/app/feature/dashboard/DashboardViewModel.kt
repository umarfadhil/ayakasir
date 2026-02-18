package com.ayakasir.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.repository.InventoryRepository
import com.ayakasir.app.core.data.repository.TransactionRepository
import com.ayakasir.app.core.domain.model.InventoryItem
import com.ayakasir.app.core.domain.model.PaymentMethod
import com.ayakasir.app.core.domain.model.TransactionStatus
import com.ayakasir.app.core.session.SessionManager
import com.ayakasir.app.core.sync.SyncManager
import com.ayakasir.app.core.util.DateTimeUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    inventoryRepository: InventoryRepository,
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val todayRange = DateTimeUtil.todayRange()

    val todayTotal: StateFlow<Long> = transactionRepository
        .getTotalByDateRange(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val todayCount: StateFlow<Int> = transactionRepository
        .getCountByDateRange(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayCash: StateFlow<Long> = transactionRepository
        .getTotalByMethod(PaymentMethod.CASH, todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val todayQris: StateFlow<Long> = transactionRepository
        .getTotalByMethod(PaymentMethod.QRIS, todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val lowStockItems: StateFlow<List<InventoryItem>> = inventoryRepository
        .getLowStockItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockCount: StateFlow<Int> = lowStockItems
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todaySales = transactionRepository
        .getTransactionsByDateRange(todayRange.first, todayRange.second)
        .map { transactions -> transactions.filter { it.status == TransactionStatus.COMPLETED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh() {
        val restaurantId = sessionManager.currentRestaurantId ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncManager.pullAllFromSupabase(restaurantId)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
