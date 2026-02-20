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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DashboardDateOption {
    TODAY,
    THIS_MONTH,
    THIS_YEAR,
    CUSTOM_DATE
}

data class DashboardDateFilter(
    val option: DashboardDateOption = DashboardDateOption.TODAY,
    val selectedDateMillis: Long = DateTimeUtil.now()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    inventoryRepository: InventoryRepository,
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _dateFilter = MutableStateFlow(DashboardDateFilter())
    val dateFilter: StateFlow<DashboardDateFilter> = _dateFilter.asStateFlow()

    private val selectedDateRange: StateFlow<Pair<Long, Long>> = dateFilter
        .map { it.toDateRange() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DateTimeUtil.todayRange())

    val periodTotal: StateFlow<Long> = selectedDateRange
        .flatMapLatest { (start, end) -> transactionRepository.getTotalByDateRange(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val periodCount: StateFlow<Int> = selectedDateRange
        .flatMapLatest { (start, end) -> transactionRepository.getCountByDateRange(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val periodCash: StateFlow<Long> = selectedDateRange
        .flatMapLatest { (start, end) ->
            transactionRepository.getTotalByMethod(PaymentMethod.CASH, start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val periodQris: StateFlow<Long> = selectedDateRange
        .flatMapLatest { (start, end) ->
            transactionRepository.getTotalByMethod(PaymentMethod.QRIS, start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val lowStockItems: StateFlow<List<InventoryItem>> = inventoryRepository
        .getLowStockItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockCount: StateFlow<Int> = lowStockItems
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val periodSales = selectedDateRange
        .flatMapLatest { (start, end) -> transactionRepository.getTransactionsByDateRange(start, end) }
        .map { transactions -> transactions.filter { it.status == TransactionStatus.COMPLETED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDateOption(option: DashboardDateOption) {
        if (option == DashboardDateOption.CUSTOM_DATE) return
        _dateFilter.value = _dateFilter.value.copy(option = option)
    }

    fun selectCustomDate(dateMillis: Long) {
        _dateFilter.value = _dateFilter.value.copy(
            option = DashboardDateOption.CUSTOM_DATE,
            selectedDateMillis = dateMillis
        )
    }

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

    private fun DashboardDateFilter.toDateRange(): Pair<Long, Long> = when (option) {
        DashboardDateOption.TODAY -> DateTimeUtil.todayRange()
        DashboardDateOption.THIS_MONTH -> DateTimeUtil.monthRange()
        DashboardDateOption.THIS_YEAR -> DateTimeUtil.yearRange()
        DashboardDateOption.CUSTOM_DATE -> DateTimeUtil.dayRange(selectedDateMillis)
    }
}
