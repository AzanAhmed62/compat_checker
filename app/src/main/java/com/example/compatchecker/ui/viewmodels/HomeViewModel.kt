package com.example.compatchecker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.compatchecker.core.config.DataSourceType
import com.example.compatchecker.data.repository.CompatibilityRepository
import com.example.compatchecker.data.repository.FavoritesRepository
import com.example.compatchecker.data.repository.ProductRepository
import com.example.compatchecker.domain.model.CompatibilityRule
import com.example.compatchecker.domain.model.Product
import com.example.compatchecker.domain.model.ProductCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val featuredProducts: List<Product> = emptyList(),
    val trendingDevices: List<Product> = emptyList(),
    val replacementParts: List<Product> = emptyList(),
    val verifiedRules: List<CompatibilityRule> = emptyList(),
    val savedProductIds: Set<String> = emptySet(),
    val dataSourceType: DataSourceType = DataSourceType.DEMONSTRATION,
    val isRefreshing: Boolean = false
)

class HomeViewModel(
    private val productRepository: ProductRepository,
    private val compatibilityRepository: CompatibilityRepository,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
        observeFavorites()
    }

    private fun loadData() {
        viewModelScope.launch {
            productRepository.getProducts().collect { envelope ->
                val all = envelope.data
                val smartphones = all.filter { it.category == ProductCategory.SMARTPHONE }
                val parts = all.filter { it.category != ProductCategory.SMARTPHONE }
                _uiState.update { current ->
                    current.copy(
                        featuredProducts = all.take(5),
                        trendingDevices = smartphones,
                        replacementParts = parts,
                        dataSourceType = envelope.sourceType
                    )
                }
            }
        }

        viewModelScope.launch {
            compatibilityRepository.getAllRules().collect { envelope ->
                _uiState.update { current ->
                    current.copy(
                        verifiedRules = envelope.data
                    )
                }
            }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            favoritesRepository.getFavorites().collect { favs ->
                _uiState.update { current ->
                    current.copy(savedProductIds = favs.map { it.productId }.toSet())
                }
            }
        }
    }

    fun toggleFavorite(product: Product) {
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(product)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            productRepository.refreshCatalog()
            compatibilityRepository.refreshRules()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
