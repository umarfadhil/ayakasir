package com.ayakasir.app.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.local.datastore.QrisSettingsDataStore
import com.ayakasir.app.core.data.repository.QrisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QrisSettingsViewModel @Inject constructor(
    private val qrisSettingsDataStore: QrisSettingsDataStore,
    private val qrisRepository: QrisRepository
) : ViewModel() {

    data class UiState(
        val isUploading: Boolean = false,
        val error: String? = null,
        val savedSuccessfully: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val qrisImageUri: StateFlow<String> = qrisSettingsDataStore.qrisImageUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val merchantName: StateFlow<String> = qrisSettingsDataStore.merchantName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val isConfigured: StateFlow<Boolean> = qrisSettingsDataStore.isConfigured
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun saveSettings(imageUri: String, merchantName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null, savedSuccessfully = false) }
            try {
                qrisRepository.saveQrisSettings(imageUri, merchantName)
                _uiState.update { it.copy(isUploading = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                Log.e("QrisSettingsViewModel", "saveSettings failed: ${e.message}", e)
                _uiState.update { it.copy(isUploading = false, error = "Gagal menyimpan: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
