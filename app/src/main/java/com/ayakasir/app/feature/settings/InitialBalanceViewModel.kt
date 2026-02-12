package com.ayakasir.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.local.datastore.CashBalanceDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InitialBalanceViewModel @Inject constructor(
    private val cashBalanceDataStore: CashBalanceDataStore
) : ViewModel() {

    val initialBalance: StateFlow<Long> = cashBalanceDataStore.initialBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun saveInitialBalance(amount: Long) {
        viewModelScope.launch {
            cashBalanceDataStore.setInitialBalance(amount)
        }
    }
}
