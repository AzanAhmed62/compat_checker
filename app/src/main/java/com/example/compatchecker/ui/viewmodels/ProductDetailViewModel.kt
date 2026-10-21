package com.example.compatchecker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.compatchecker.core.config.DataSourceType
import com.example.compatchecker.data.repository.CommunityRepository
import com.example.compatchecker.data.repository.CompatibilityRepository
import com.example.compatchecker.data.repository.FavoritesRepository
import com.example.compatchecker.data.repository.ProductRepository
import com.example.compatchecker.data.repository.RepairGuideRepository
import com.example.compatchecker.domain.model.Comment
import com.example.compatchecker.domain.model.CommentType
import com.example.compatchecker.domain.model.CompatibilityRule
import com.example.compatchecker.domain.model.Product
import com.example.compatchecker.domain.model.RepairGuide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductDetailUiState(
    val product: Product? = null,
    val compatibilityRules: List<CompatibilityRule> = emptyList(),
    val guides: List<RepairGuide> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val isFavorite: Boolean = false,
    val dataSourceType: DataSourceType = DataSourceType.DEMONSTRATION,
    val isLoading: Boolean = true
)

class ProductDetailViewModel(
    private val productId: String,
    private val productRepository: ProductRepository,
    private val compatibilityRepository: CompatibilityRepository,
    private val communityRepository: CommunityRepository,
    private val repairGuideRepository: RepairGuideRepository,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductDetailUiState())
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    init {
        loadProduct()
        loadRules()
        loadGuides()
        loadComments()
        observeFavorite()
    }

    private fun loadProduct() {
        viewModelScope.launch {
            productRepository.getProductById(productId).collect { envelope ->
                _uiState.update { current ->
                    current.copy(
                        product = envelope.data,
                        dataSourceType = envelope.sourceType,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun loadRules() {
        viewModelScope.launch {
            compatibilityRepository.getRulesForProduct(productId).collect { envelope ->
                _uiState.update { current ->
                    current.copy(compatibilityRules = envelope.data)
                }
            }
        }
    }

    private fun loadGuides() {
        viewModelScope.launch {
            repairGuideRepository.getGuidesForProduct(productId).collect { envelope ->
                _uiState.update { current ->
                    current.copy(guides = envelope.data)
                }
            }
        }
    }

    private fun loadComments() {
        viewModelScope.launch {
            communityRepository.getCommentsForProduct(productId).collect { envelope ->
                _uiState.update { current ->
                    current.copy(comments = envelope.data)
                }
            }
        }
    }

    private fun observeFavorite() {
        viewModelScope.launch {
            favoritesRepository.isFavorite(productId).collect { isFav ->
                _uiState.update { it.copy(isFavorite = isFav) }
            }
        }
    }

    fun toggleFavorite() {
        val prod = _uiState.value.product ?: return
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(prod)
        }
    }

    fun addComment(content: String, type: CommentType, authorName: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val comment = Comment(
                id = "com_${System.currentTimeMillis()}",
                productId = productId,
                authorName = authorName.ifBlank { "Repair Technician" },
                authorRole = if (type == CommentType.WARNING) "Safety Moderator" else "Verified Tech",
                content = content.trim(),
                type = type,
                upvotes = 0,
                hasUserUpvoted = false
            )
            communityRepository.submitComment(comment)
        }
    }
}
