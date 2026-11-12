package com.compatchecker.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.compatchecker.app.domain.model.AppNotification
import com.compatchecker.app.domain.model.Brand
import com.compatchecker.app.domain.model.Category
import com.compatchecker.app.domain.model.Contribution
import com.compatchecker.app.domain.model.ProductSummary
import com.compatchecker.app.domain.repository.CatalogRepository
import com.compatchecker.app.domain.repository.ProfileRepository
import com.compatchecker.app.ui.displayMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

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

data class ContributionsUiState(
    val items: List<Contribution> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val brands: List<Brand> = emptyList(),
    val categories: List<Category> = emptyList(),
    val products: List<ProductSummary> = emptyList(),
    val optionsLoading: Boolean = true,
    val submitting: Boolean = false,
    val submitError: String? = null,
    val submitted: Boolean = false,
)

class ContributionsViewModel(
    private val profiles: ProfileRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ContributionsUiState())
    val state: StateFlow<ContributionsUiState> = _state.asStateFlow()

    init {
        load()
        loadOptions()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { profiles.contributions() }
                .onSuccess { items -> _state.update { it.copy(items = items, loading = false) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.displayMessage()) } }
        }
    }

    private fun loadOptions() {
        viewModelScope.launch {
            _state.update { it.copy(optionsLoading = true) }
            runCatching {
                coroutineScope {
                    val brands = async { catalog.brands() }
                    val categories = async { catalog.categories() }
                    val products = async { catalog.search("", null, null, 50, 0).items }
                    Triple(brands.await(), categories.await(), products.await())
                }
            }
                .onSuccess { (brands, categories, products) ->
                    _state.update {
                        it.copy(
                            brands = brands,
                            categories = categories,
                            products = products,
                            optionsLoading = false,
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(optionsLoading = false) }
                }
        }
    }

    fun submit(kind: ContributionKind, payload: JsonObject) {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, submitError = null, submitted = false) }
            runCatching { profiles.submitContribution(kind.wire, payload) }
                .onSuccess {
                    _state.update { it.copy(submitting = false, submitted = true) }
                    load()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(submitting = false, submitError = error.displayMessage())
                    }
                }
        }
    }

    fun consumeSubmitted() = _state.update { it.copy(submitted = false) }
    fun clearSubmitError() = _state.update { it.copy(submitError = null) }
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
