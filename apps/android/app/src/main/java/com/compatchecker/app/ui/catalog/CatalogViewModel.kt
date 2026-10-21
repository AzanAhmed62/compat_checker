package com.compatchecker.app.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.compatchecker.app.domain.model.Category
import com.compatchecker.app.domain.model.ProductSummary
import com.compatchecker.app.domain.repository.CatalogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.compatchecker.app.ui.displayMessage

data class CatalogUiState(
    val query: String = "",
    val products: List<ProductSummary> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
)

class CatalogViewModel(private val catalog: CatalogRepository) : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState(loading = true))
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val categories = catalog.categories()
            _state.update { it.copy(categories = categories) }
        }
        search(immediate = true)
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            search()
        }
    }

    fun selectCategory(slug: String?) {
        if (slug == _state.value.selectedCategory) return
        _state.update { it.copy(selectedCategory = slug) }
        search(immediate = true)
    }

    fun retry() = search(immediate = true)

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.offline) return
        if (current.products.size >= current.total) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching {
                catalog.search(
                    query = current.query,
                    categorySlug = current.selectedCategory,
                    limit = PAGE_SIZE,
                    offset = current.products.size,
                )
            }.onSuccess { page ->
                _state.update {
                    it.copy(
                        products = it.products + page.items.filter { item -> it.products.none { p -> p.id == item.id } },
                        total = page.total,
                        loadingMore = false,
                        offline = page.offline,
                        error = null,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(loadingMore = false, error = error.displayMessage()) }
            }
        }
    }

    private fun search(immediate: Boolean = false) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!immediate) delay(350)
            _state.update { it.copy(loading = true, error = null) }
            val current = _state.value
            runCatching {
                catalog.search(
                    query = current.query,
                    categorySlug = current.selectedCategory,
                    limit = PAGE_SIZE,
                    offset = 0,
                )
            }.onSuccess { page ->
                _state.update {
                    it.copy(
                        products = page.items,
                        total = page.total,
                        offline = page.offline,
                        loading = false,
                        error = null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(loading = false, error = error.displayMessage())
                }
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
