package com.ayakasir.app.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.repository.InventoryRepository
import com.ayakasir.app.core.domain.model.InventoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    data class InventoryUiState(
        val adjustItem: InventoryItem? = null,
        val adjustQty: String = "",
        val adjustMinQty: String = "",
        val isAdjusting: Boolean = false
    )

    data class CategoryGroup(
        val categoryId: String,
        val categoryName: String,
        val items: List<InventoryItem>
    )

    val inventory: StateFlow<List<InventoryItem>> = inventoryRepository
        .getAllInventory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedInventory: StateFlow<List<CategoryGroup>> = inventory
        .map { items ->
            items.groupBy { it.categoryId to it.categoryName }
                .map { (category, items) ->
                    CategoryGroup(
                        categoryId = category.first,
                        categoryName = category.second,
                        items = items
                    )
                }
                .sortedBy { it.categoryName }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    fun showAdjustDialog(item: InventoryItem) {
        _uiState.update {
            it.copy(adjustItem = item, adjustQty = item.currentQty.toString(), adjustMinQty = item.minQty.toString())
        }
    }

    fun dismissAdjustDialog() {
        _uiState.update { it.copy(adjustItem = null) }
    }

    fun onAdjustQtyChange(value: String) {
        _uiState.update { it.copy(adjustQty = value) }
    }

    fun onAdjustMinQtyChange(value: String) {
        _uiState.update { it.copy(adjustMinQty = value) }
    }

    fun saveAdjustment() {
        val state = _uiState.value
        val item = state.adjustItem ?: return
        val qty = state.adjustQty.toIntOrNull() ?: return
        val minQty = state.adjustMinQty.toIntOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isAdjusting = true) }
            inventoryRepository.adjustStock(item.productId, item.variantId, qty)
            inventoryRepository.setMinQty(item.productId, item.variantId, minQty)
            _uiState.update { it.copy(adjustItem = null, isAdjusting = false) }
        }
    }
}
