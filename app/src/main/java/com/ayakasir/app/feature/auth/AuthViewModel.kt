package com.ayakasir.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.repository.UserRepository
import com.ayakasir.app.core.domain.model.User
import com.ayakasir.app.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    data class AuthUiState(
        val pin: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val isAuthenticated: Boolean = false,
        val user: User? = null
    )

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.seedDefaultOwner()
        }
    }

    fun onDigitEntered(digit: String) {
        val current = _uiState.value.pin
        if (current.length >= 6) return
        _uiState.update { it.copy(pin = current + digit, error = null) }

        // Auto-submit when 6 digits entered
        if (current.length + 1 == 6) {
            authenticate(current + digit)
        }
    }

    fun onBackspace() {
        val current = _uiState.value.pin
        if (current.isEmpty()) return
        _uiState.update { it.copy(pin = current.dropLast(1), error = null) }
    }

    fun clearPin() {
        _uiState.update { it.copy(pin = "", error = null) }
    }

    private fun authenticate(pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = userRepository.authenticateByPinDirect(pin)
                if (user != null) {
                    sessionManager.login(user)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            user = user,
                            error = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pin = "",
                            error = "PIN salah, coba lagi"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pin = "",
                        error = "Terjadi kesalahan"
                    )
                }
            }
        }
    }
}
