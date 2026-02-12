package com.ayakasir.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.repository.UserRepository
import com.ayakasir.app.core.domain.model.User
import com.ayakasir.app.core.domain.model.UserFeature
import com.ayakasir.app.core.domain.model.UserRole
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
class UserManagementViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    data class UiState(
        val isSaving: Boolean = false,
        val error: String? = null,
        val saveSuccessCounter: Int = 0
    )

    val users: StateFlow<List<User>> = userRepository
        .getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun createUser(name: String, pin: String, role: UserRole, featureAccess: Set<UserFeature>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val normalizedName = name.trim()
                userRepository.createUser(
                    name = normalizedName,
                    pin = pin,
                    role = role,
                    featureAccess = if (role == UserRole.CASHIER) featureAccess else emptySet()
                )
                _uiState.update {
                    it.copy(isSaving = false, saveSuccessCounter = it.saveSuccessCounter + 1)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "Gagal menambahkan user") }
            }
        }
    }

    fun updateUser(
        userId: String,
        name: String,
        role: UserRole,
        featureAccess: Set<UserFeature>,
        newPin: String?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val normalizedName = name.trim()
                val updated = userRepository.updateUser(
                    userId = userId,
                    name = normalizedName,
                    role = role,
                    featureAccess = if (role == UserRole.CASHIER) featureAccess else emptySet()
                )
                if (updated == null) {
                    throw IllegalStateException("User not found")
                }
                if (!newPin.isNullOrBlank()) {
                    userRepository.changePin(userId, newPin)
                }
                _uiState.update {
                    it.copy(isSaving = false, saveSuccessCounter = it.saveSuccessCounter + 1)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "Gagal memperbarui user") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
