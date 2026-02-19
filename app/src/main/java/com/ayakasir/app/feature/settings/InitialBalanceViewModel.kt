package com.ayakasir.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.repository.GeneralLedgerRepository
import com.ayakasir.app.core.domain.model.LedgerType
import com.ayakasir.app.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InitialBalanceViewModel @Inject constructor(
    private val generalLedgerRepository: GeneralLedgerRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    val initialBalance: StateFlow<Long> = generalLedgerRepository.getLatestInitialBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun saveInitialBalance(amount: Long) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            generalLedgerRepository.recordEntry(
                type = LedgerType.INITIAL_BALANCE,
                amount = amount,
                description = "Saldo awal kas",
                userId = userId
            )
        }
    }
}
