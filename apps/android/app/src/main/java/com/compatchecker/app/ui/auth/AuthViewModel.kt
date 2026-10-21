package com.compatchecker.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.compatchecker.app.domain.repository.AuthRepository
import com.compatchecker.app.ui.displayMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val register: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onDisplayNameChange(value: String) = _state.update { it.copy(displayName = value) }
    fun toggleMode() = _state.update {
        it.copy(register = !it.register, error = null)
    }

    fun submit() {
        val current = _state.value
        if (!current.email.contains('@') || current.email.length < 5) {
            _state.update { it.copy(error = "Enter a valid email address.") }
            return
        }
        if (current.password.length < 12) {
            _state.update { it.copy(error = "Use a password of at least 12 characters.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                if (current.register) {
                    auth.signUp(current.email, current.password, current.displayName.ifBlank { null })
                } else {
                    auth.signIn(current.email, current.password)
                }
            }.onSuccess {
                _state.update { it.copy(loading = false, success = true) }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.displayMessage()) }
            }
        }
    }
}
