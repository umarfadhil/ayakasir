package com.ayakasir.app.feature.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.repository.UserRepository
import com.ayakasir.app.core.domain.model.User
import com.ayakasir.app.core.session.SessionManager
import com.ayakasir.app.core.sync.SyncManager
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
    private val sessionManager: SessionManager,
    private val syncManager: SyncManager
) : ViewModel() {

    // --- PIN unlock state ---
    data class AuthUiState(
        val pin: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val isAuthenticated: Boolean = false,
        val user: User? = null
    )

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // --- Email/password login state ---
    data class EmailLoginUiState(
        val email: String = "",
        val password: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val isAuthenticated: Boolean = false
    )

    private val _emailLoginState = MutableStateFlow(EmailLoginUiState())
    val emailLoginState: StateFlow<EmailLoginUiState> = _emailLoginState.asStateFlow()

    // --- PIN unlock methods ---
    fun onDigitEntered(digit: String) {
        val current = _uiState.value.pin
        if (current.length >= 6) return
        _uiState.update { it.copy(pin = current + digit, error = null) }

        if (current.length + 1 == 6) {
            authenticatePin(current + digit)
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

    private fun authenticatePin(pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val persistedUserId = sessionManager.getPersistedUserId()
                val user = if (persistedUserId != null) {
                    // Only authenticate against the persisted user
                    userRepository.authenticateByPinForUser(persistedUserId, pin)
                } else {
                    userRepository.authenticateByPinDirect(pin)
                }
                if (user != null) {
                    val restaurantId = sessionManager.getPersistedRestaurantId()
                    sessionManager.loginPin(user, restaurantId)
                    // Pull latest data in background (catches changes made while app was closed)
                    if (!restaurantId.isNullOrEmpty()) {
                        viewModelScope.launch {
                            try {
                                syncManager.pullAllFromSupabase(restaurantId)
                            } catch (e: Exception) {
                                Log.w("AuthViewModel", "PIN login pull failed: ${e.message}")
                            }
                        }
                    }
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

    // --- Email/password login methods ---
    fun onEmailChanged(email: String) {
        _emailLoginState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChanged(password: String) {
        _emailLoginState.update { it.copy(password = password, error = null) }
    }

    fun loginWithEmail() {
        val state = _emailLoginState.value
        if (state.email.isBlank() || !state.email.contains("@")) {
            _emailLoginState.update { it.copy(error = "Email tidak valid") }
            return
        }
        if (state.password.isBlank()) {
            _emailLoginState.update { it.copy(error = "Password harus diisi") }
            return
        }

        viewModelScope.launch {
            _emailLoginState.update { it.copy(isLoading = true) }
            try {
                when (val result = userRepository.authenticateByEmail(state.email, state.password)) {
                    is UserRepository.AuthResult.Success -> {
                        val restaurantId = result.user.restaurantId
                        sessionManager.loginFull(result.user, restaurantId)
                        if (!restaurantId.isNullOrEmpty()) {
                            try {
                                syncManager.pullAllFromSupabase(restaurantId)
                            } catch (e: Exception) {
                                Log.w("AuthViewModel", "Initial pull failed: ${e.message}")
                            }
                        }
                        _emailLoginState.update {
                            it.copy(isLoading = false, isAuthenticated = true, error = null)
                        }
                    }
                    is UserRepository.AuthResult.NotFound -> {
                        _emailLoginState.update {
                            it.copy(isLoading = false, error = "Akun tidak ditemukan. Silakan daftar terlebih dahulu.")
                        }
                    }
                    is UserRepository.AuthResult.Inactive -> {
                        _emailLoginState.update {
                            it.copy(isLoading = false, error = "Akun belum diaktivasi oleh admin. Silakan hubungi administrator.")
                        }
                    }
                    is UserRepository.AuthResult.WrongPassword -> {
                        _emailLoginState.update {
                            it.copy(isLoading = false, error = "Email atau password salah.")
                        }
                    }
                    is UserRepository.AuthResult.NoPassword -> {
                        _emailLoginState.update {
                            it.copy(isLoading = false, error = "Akun ini belum memiliki password. Silakan daftar ulang.")
                        }
                    }
                }
            } catch (e: Exception) {
                _emailLoginState.update {
                    it.copy(isLoading = false, error = "Terjadi kesalahan: ${e.message}")
                }
            }
        }
    }
}
