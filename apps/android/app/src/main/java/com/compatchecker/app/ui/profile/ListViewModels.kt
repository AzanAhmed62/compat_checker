package com.compatchecker.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.compatchecker.app.domain.model.AppNotification
import com.compatchecker.app.domain.model.Contribution
import com.compatchecker.app.domain.model.ProductSummary
import com.compatchecker.app.domain.repository.CatalogRepository
import com.compatchecker.app.domain.repository.ProfileRepository
import com.compatchecker.app.ui.displayMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SimpleListUiState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
)

class FavoritesViewModel(private val catalog: CatalogRepository) : ViewModel() {
    private val _state = MutableStateFlow(SimpleListUiState<ProductSummary>())
    val state: StateFlow<SimpleListUiState<ProductSummary>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { catalog.favorites() }
                .onSuccess { items -> _state.update { it.copy(items = items, loading = false) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.displayMessage()) } }
        }
    }
}

class ContributionsViewModel(private val profiles: ProfileRepository) : ViewModel() {
    private val _state = MutableStateFlow(SimpleListUiState<Contribution>())
    val state: StateFlow<SimpleListUiState<Contribution>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { profiles.contributions() }
                .onSuccess { items -> _state.update { it.copy(items = items, loading = false) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.displayMessage()) } }
        }
    }
}

class NotificationsViewModel(private val profiles: ProfileRepository) : ViewModel() {
    private val _state = MutableStateFlow(SimpleListUiState<AppNotification>())
    val state: StateFlow<SimpleListUiState<AppNotification>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { profiles.notifications() }
                .onSuccess { items -> _state.update { it.copy(items = items, loading = false) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.displayMessage()) } }
        }
    }

    fun markRead(notification: AppNotification) {
        if (notification.readAt != null) return
        viewModelScope.launch {
            runCatching { profiles.markNotificationRead(notification.id) }
                .onSuccess { load() }
                .onFailure { error -> _state.update { it.copy(message = error.displayMessage()) } }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            runCatching { profiles.markAllNotificationsRead() }
                .onSuccess { load() }
                .onFailure { error -> _state.update { it.copy(message = error.displayMessage()) } }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
