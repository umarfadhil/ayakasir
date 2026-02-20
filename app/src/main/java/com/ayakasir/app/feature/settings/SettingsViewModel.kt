package com.ayakasir.app.feature.settings

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.repository.LedgerExportRepository
import com.ayakasir.app.core.domain.model.UserFeature
import com.ayakasir.app.core.domain.model.UserRole
import com.ayakasir.app.core.session.SessionManager
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
class SettingsViewModel @Inject constructor(
    sessionManager: SessionManager,
    private val ledgerExportRepository: LedgerExportRepository
) : ViewModel() {

    data class UiState(
        val isExporting: Boolean = false,
        val exportMessage: String? = null,
        val exportError: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * True if current user is OWNER or has SETTINGS feature access.
     * False means cashier with no settings access and only show logout.
     */
    val showFullSettings: StateFlow<Boolean> = sessionManager.currentUser
        .map { user ->
            user == null || user.role == UserRole.OWNER || user.featureAccess.contains(UserFeature.SETTINGS)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /**
     * True if current user is OWNER. Used to show owner-only settings cards.
     */
    val isOwner: StateFlow<Boolean> = sessionManager.currentUser
        .map { user -> user == null || user.role == UserRole.OWNER }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportMessage = null, exportError = null) }
            try {
                val result = ledgerExportRepository.exportToCsv(uri)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportMessage = "Berhasil unduh data (${result.rowCount} baris)"
                    )
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "exportData failed: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportError = "Gagal unduh data: ${e.message ?: "Terjadi kesalahan"}"
                    )
                }
            }
        }
    }

    fun clearExportMessage() {
        _uiState.update { it.copy(exportMessage = null, exportError = null) }
    }

    suspend fun resolveDefaultExportFileName(): String {
        return ledgerExportRepository.buildDefaultFileName()
    }
}
