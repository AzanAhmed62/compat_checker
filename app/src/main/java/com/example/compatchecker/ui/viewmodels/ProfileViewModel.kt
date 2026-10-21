package com.example.compatchecker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.compatchecker.data.repository.FavoritesRepository
import com.example.compatchecker.data.repository.UserRepository
import com.example.compatchecker.domain.model.Favorite
import com.example.compatchecker.domain.model.UserProfile
import com.example.compatchecker.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val userProfile: UserProfile = UserProfile("guest", "", "Guest Technician", UserRole.GUEST),
    val favorites: List<Favorite> = emptyList(),
    val isAuthenticating: Boolean = false,
    val authError: String? = null,
    val authSuccessNotice: String? = null
)

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeProfile()
        observeFavorites()
    }

    private fun observeProfile() {
        viewModelScope.launch {
            userRepository.currentUserProfile.collect { profile ->
                _uiState.update { it.copy(userProfile = profile) }
            }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            favoritesRepository.getFavorites().collect { favs ->
                _uiState.update { it.copy(favorites = favs) }
            }
        }
    }

    fun signIn(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.update { it.copy(authError = "Please enter both email and password") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, authError = null) }
            val res = userRepository.signIn(email.trim(), pass)
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        authSuccessNotice = "Signed in as ${res.getOrNull()?.displayName}"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        authError = res.exceptionOrNull()?.message ?: "Authentication failed"
                    )
                }
            }
        }
    }

    fun signUp(email: String, pass: String, displayName: String) {
        if (email.isBlank() || pass.isBlank() || displayName.isBlank()) {
            _uiState.update { it.copy(authError = "All fields are required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, authError = null) }
            val res = userRepository.signUp(email.trim(), pass, displayName.trim())
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        authSuccessNotice = "Account registered successfully!"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        authError = res.exceptionOrNull()?.message ?: "Registration failed"
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            userRepository.signOut()
            _uiState.update { it.copy(authSuccessNotice = "Signed out of session") }
        }
    }

    fun removeFavorite(productId: String) {
        viewModelScope.launch {
            favoritesRepository.removeFavorite(productId)
        }
    }

    fun clearAuthMessages() {
        _uiState.update { it.copy(authError = null, authSuccessNotice = null) }
    }
}
