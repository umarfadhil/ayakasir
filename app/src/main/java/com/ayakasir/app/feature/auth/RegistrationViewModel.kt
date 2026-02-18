package com.ayakasir.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayakasir.app.core.data.repository.RestaurantRepository
import com.ayakasir.app.core.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    data class RegistrationUiState(
        val name: String = "",
        val email: String = "",
        val phone: String = "",
        val businessName: String = "",
        val password: String = "",
        val pin: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val isRegistered: Boolean = false,
        val registrationMessage: String? = null
    )

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPhoneChanged(phone: String) {
        _uiState.update { it.copy(phone = phone, error = null) }
    }

    fun onBusinessNameChanged(businessName: String) {
        _uiState.update { it.copy(businessName = businessName, error = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun onPinChanged(pin: String) {
        if (pin.all { it.isDigit() } && pin.length <= 6) {
            _uiState.update { it.copy(pin = pin, error = null) }
        }
    }

    fun register() {
        val state = _uiState.value

        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Nama harus diisi") }
            return
        }
        if (state.email.isBlank() || !state.email.contains("@")) {
            _uiState.update { it.copy(error = "Email tidak valid") }
            return
        }
        if (state.phone.isBlank()) {
            _uiState.update { it.copy(error = "Nomor telepon harus diisi") }
            return
        }
        if (state.businessName.isBlank()) {
            _uiState.update { it.copy(error = "Nama usaha harus diisi") }
            return
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(error = "Password minimal 6 karakter") }
            return
        }
        if (state.pin.length != 6) {
            _uiState.update { it.copy(error = "PIN harus 6 digit") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Create restaurant (is_active = false)
                val restaurant = restaurantRepository.create(
                    name = state.businessName,
                    ownerEmail = state.email,
                    ownerPhone = state.phone
                )

                // Create owner user (is_active = false, password hashed locally)
                userRepository.registerOwner(
                    name = state.name,
                    email = state.email,
                    phone = state.phone,
                    pin = state.pin,
                    password = state.password,
                    restaurantId = restaurant.id
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRegistered = true,
                        registrationMessage = "Pendaftaran berhasil! Akun Anda menunggu aktivasi oleh admin.",
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Terjadi kesalahan: ${e.message}"
                    )
                }
            }
        }
    }
}
