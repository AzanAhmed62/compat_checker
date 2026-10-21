package com.compatchecker.app.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.compatchecker.app.domain.model.Comment
import com.compatchecker.app.domain.model.ProductDetail
import com.compatchecker.app.domain.repository.AuthRepository
import com.compatchecker.app.domain.repository.CatalogRepository
import com.compatchecker.app.domain.repository.CommunityRepository
import com.compatchecker.app.domain.repository.ProfileRepository
import com.compatchecker.app.ui.displayMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductUiState(
    val product: ProductDetail? = null,
    val comments: List<Comment> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val favoriteBusy: Boolean = false,
    val commentBusy: Boolean = false,
    val correctionBusy: Boolean = false,
    val message: String? = null,
    val authPrompt: Boolean = false,
)

class ProductViewModel(
    private val productId: String,
    private val catalog: CatalogRepository,
    private val community: CommunityRepository,
    private val profiles: ProfileRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProductUiState())
    val state: StateFlow<ProductUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { catalog.product(productId) }
                .onSuccess { product ->
                    _state.update { it.copy(product = product, loading = false, error = null) }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.displayMessage()) }
                }
            runCatching { community.comments("product", productId) }
                .onSuccess { comments -> _state.update { it.copy(comments = comments) } }
        }
    }

    fun toggleFavorite() {
        if (_state.value.favoriteBusy) return
        viewModelScope.launch {
            if (auth.currentSession() == null) {
                _state.update { it.copy(authPrompt = true) }
                return@launch
            }
            _state.update { it.copy(favoriteBusy = true) }
            runCatching { catalog.toggleFavorite(productId) }
                .onSuccess { isFavorite ->
                    _state.update {
                        it.copy(
                            product = it.product?.copy(isFavorite = isFavorite),
                            favoriteBusy = false,
                            message = if (isFavorite) "Saved to favorites" else "Removed from favorites",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(favoriteBusy = false, message = error.displayMessage()) }
                }
        }
    }

    fun addComment(body: String) {
        if (body.isBlank() || _state.value.commentBusy) return
        viewModelScope.launch {
            if (auth.currentSession() == null) {
                _state.update { it.copy(authPrompt = true) }
                return@launch
            }
            _state.update { it.copy(commentBusy = true) }
            runCatching { community.addComment("product", productId, body) }
                .onSuccess {
                    val comments = runCatching { community.comments("product", productId) }
                        .getOrDefault(_state.value.comments)
                    _state.update {
                        it.copy(comments = comments, commentBusy = false, message = "Comment posted")
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(commentBusy = false, message = error.displayMessage()) }
                }
        }
    }

    fun submitCorrection(name: String, summary: String, notes: String) {
        if (name.isBlank() && summary.isBlank() && notes.isBlank()) {
            _state.update { it.copy(message = "Enter at least one correction.") }
            return
        }
        viewModelScope.launch {
            if (auth.currentSession() == null) {
                _state.update { it.copy(authPrompt = true) }
                return@launch
            }
            _state.update { it.copy(correctionBusy = true) }
            val patch = buildCorrectionPatch(name, summary, notes)
            runCatching { profiles.submitProductCorrection(productId, patch) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            correctionBusy = false,
                            message = "Correction submitted for review",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(correctionBusy = false, message = error.displayMessage()) }
                }
        }
    }

    fun report(reason: String, details: String?) {
        viewModelScope.launch {
            if (auth.currentSession() == null) {
                _state.update { it.copy(authPrompt = true) }
                return@launch
            }
            runCatching { community.report("product", productId, reason, details) }
                .onSuccess {
                    _state.update { it.copy(message = "Report submitted for moderation") }
                }
                .onFailure { error ->
                    _state.update { it.copy(message = error.displayMessage()) }
                }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeAuthPrompt() = _state.update { it.copy(authPrompt = false) }
}
