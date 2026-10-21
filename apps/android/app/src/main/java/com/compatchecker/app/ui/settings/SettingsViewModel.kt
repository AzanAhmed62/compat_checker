package com.compatchecker.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.compatchecker.app.domain.model.UserProfile
import com.compatchecker.app.domain.repository.PreferencesRepository
import com.compatchecker.app.domain.repository.ProfileRepository
import com.compatchecker.app.domain.repository.UserPreferences
import com.compatchecker.app.ui.displayMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val profile: UserProfile? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class SettingsViewModel(
    private val preferences: PreferencesRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.preferences.collect { values ->
                _state.update { it.copy(preferences = values) }
            }
        }
        viewModelScope.launch {
            runCatching { profiles.me() }
                .onSuccess { profile -> _state.update { it.copy(profile = profile, loading = false) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.displayMessage()) } }
        }
    }

    fun setTheme(mode: String) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch { preferences.setLanguage(language) }
    }

    fun saveProfile(displayName: String, bio: String) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, message = null) }
            runCatching {
                profiles.updateProfile(
                    displayName = displayName.ifBlank { null },
                    bio = bio,
                    locale = null,
                )
            }.onSuccess { profile ->
                _state.update {
                    it.copy(profile = profile, saving = false, message = "Profile updated")
                }
            }.onFailure { error ->
                _state.update { it.copy(saving = false, message = error.displayMessage()) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
