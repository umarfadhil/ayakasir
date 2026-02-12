package com.ayakasir.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.local.datastore.QrisSettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QrisSettingsViewModel @Inject constructor(
    private val qrisSettingsDataStore: QrisSettingsDataStore
) : ViewModel() {

    val qrisImageUri: StateFlow<String> = qrisSettingsDataStore.qrisImageUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val merchantName: StateFlow<String> = qrisSettingsDataStore.merchantName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val isConfigured: StateFlow<Boolean> = qrisSettingsDataStore.isConfigured
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun saveSettings(imageUri: String, merchantName: String) {
        viewModelScope.launch {
            qrisSettingsDataStore.saveSettings(imageUri, merchantName)
        }
    }
}
