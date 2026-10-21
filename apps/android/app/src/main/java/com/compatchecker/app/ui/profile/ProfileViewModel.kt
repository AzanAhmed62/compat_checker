package com.compatchecker.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.compatchecker.app.domain.model.Reputation
import com.compatchecker.app.domain.model.UserProfile
import com.compatchecker.app.domain.repository.AuthRepository
import com.compatchecker.app.domain.repository.ProfileRepository
import com.compatchecker.app.ui.displayMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfile? = null,
    val reputation: Reputation? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val actionBusy: Boolean = false,
    val message: String? = null,
    val signedOut: Boolean = false,
)

class ProfileViewModel(
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (auth.currentSession() == null) {
                _state.update { ProfileUiState(profile = null, loading = false) }
                return@launch
            }
            _state.update { it.copy(loading = true, error = null) }
            runCatching { profiles.me() }
                .onSuccess { profile ->
                    _state.update { it.copy(profile = profile, loading = false) }
                    runCatching { profiles.reputation() }
                        .onSuccess { reputation ->
                            _state.update { it.copy(reputation = reputation) }
                        }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.displayMessage()) }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _state.value = ProfileUiState(profile = null, loading = false, signedOut = true)
        }
    }

    fun requestDeletion() {
        viewModelScope.launch {
            _state.update { it.copy(actionBusy = true) }
            runCatching { profiles.requestAccountDeletion() }
                .onSuccess { message ->
                    _state.update { it.copy(actionBusy = false, message = message) }
                    load()
                }
                .onFailure { error ->
                    _state.update { it.copy(actionBusy = false, message = error.displayMessage()) }
                }
        }
    }

    fun cancelDeletion() {
        viewModelScope.launch {
            _state.update { it.copy(actionBusy = true) }
            runCatching { profiles.cancelAccountDeletion() }
                .onSuccess {
                    _state.update {
                        it.copy(actionBusy = false, message = "Deletion request cancelled")
                    }
                    load()
                }
                .onFailure { error ->
                    _state.update { it.copy(actionBusy = false, message = error.displayMessage()) }
                }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
