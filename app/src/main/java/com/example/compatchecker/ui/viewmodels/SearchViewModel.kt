package com.example.compatchecker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.compatchecker.core.config.DataSourceType
import com.example.compatchecker.data.repository.FavoritesRepository
import com.example.compatchecker.data.repository.ProductRepository
import com.example.compatchecker.domain.model.Product
import com.example.compatchecker.domain.model.ProductCategory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val selectedCategory: ProductCategory? = null,
    val selectedBrand: String? = null,
    val results: List<Product> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val savedProductIds: Set<String> = emptySet(),
    val dataSourceType: DataSourceType = DataSourceType.DEMONSTRATION,
    val isSearching: Boolean = false
)

class SearchViewModel(
    private val productRepository: ProductRepository,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadRecentSearches()
        observeFavorites()
        performSearch()
    }

    private fun loadRecentSearches() {
        viewModelScope.launch {
            productRepository.getRecentSearches().collect { list ->
                _uiState.update { it.copy(recentSearches = list) }
            }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            favoritesRepository.getFavorites().collect { favs ->
                _uiState.update { it.copy(savedProductIds = favs.map { f -> f.productId }.toSet()) }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(150)
            performSearch()
        }
    }

    fun onCategorySelect(category: ProductCategory?) {
        val next = if (_uiState.value.selectedCategory == category) null else category
        _uiState.update { it.copy(selectedCategory = next) }
        performSearch()
    }

    fun onBrandSelect(brand: String?) {
        val next = if (_uiState.value.selectedBrand == brand) null else brand
        _uiState.update { it.copy(selectedBrand = next) }
        performSearch()
    }

    fun onRecentQueryClick(query: String) {
        _uiState.update { it.copy(query = query) }
        performSearch()
    }

    fun commitSearch() {
        val q = _uiState.value.query.trim()
        if (q.isNotEmpty()) {
            viewModelScope.launch {
                productRepository.addSearchQuery(q)
            }
        }
    }

    fun clearQuery() {
        _uiState.update { it.copy(query = "") }
        performSearch()
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            productRepository.clearSearchHistory()
        }
    }

    fun toggleFavorite(product: Product) {
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(product)
        }
    }

    private fun performSearch() {
        val state = _uiState.value
        _uiState.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            productRepository.searchProducts(
                query = state.query,
                category = state.selectedCategory,
                brand = state.selectedBrand
            ).collect { envelope ->
                _uiState.update { current ->
                    current.copy(
                        results = envelope.data,
                        dataSourceType = envelope.sourceType,
                        isSearching = false
                    )
                }
            }
        }
    }
}
