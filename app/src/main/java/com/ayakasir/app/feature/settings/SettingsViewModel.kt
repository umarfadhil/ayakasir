package com.ayakasir.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.domain.model.UserFeature
import com.ayakasir.app.core.domain.model.UserRole
import com.ayakasir.app.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    sessionManager: SessionManager
) : ViewModel() {

    /**
     * True if current user is OWNER or has SETTINGS feature access.
     * False means cashier with no settings access — only show logout.
     */
    val showFullSettings: StateFlow<Boolean> = sessionManager.currentUser
        .map { user ->
            user == null || user.role == UserRole.OWNER || user.featureAccess.contains(UserFeature.SETTINGS)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /**
     * True if current user is OWNER. Used to show owner-only settings cards
     * (Saldo Awal, User Mgmt, Kategori, Vendor, Barang, QRIS).
     */
    val isOwner: StateFlow<Boolean> = sessionManager.currentUser
        .map { user -> user == null || user.role == UserRole.OWNER }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
}