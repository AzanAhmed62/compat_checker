package com.compatchecker.app.ui.checker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.compatchecker.app.domain.model.CompatibilityResult
import com.compatchecker.app.domain.model.ProductSummary
import com.compatchecker.app.domain.repository.CatalogRepository
import com.compatchecker.app.domain.repository.CompatibilityRepository
import com.compatchecker.app.ui.displayMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PickerTarget { A, B }

data class CheckerUiState(
    val productA: ProductSummary? = null,
    val productB: ProductSummary? = null,
    val initialLoading: Boolean = false,
    val pickerTarget: PickerTarget? = null,
    val pickerQuery: String = "",
    val pickerResults: List<ProductSummary> = emptyList(),
    val pickerLoading: Boolean = false,
    val checking: Boolean = false,
    val result: CompatibilityResult? = null,
    val error: String? = null,
)

class CheckerViewModel(
    initialProductId: String?,
    private val catalog: CatalogRepository,
    private val compatibility: CompatibilityRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CheckerUiState())
    val state: StateFlow<CheckerUiState> = _state.asStateFlow()
    private var pickerJob: Job? = null

    init {
        if (!initialProductId.isNullOrBlank()) {
            viewModelScope.launch {
                _state.update { it.copy(initialLoading = true) }
                runCatching { catalog.product(initialProductId) }
                    .onSuccess { product ->
                        _state.update {
                            it.copy(
                                productA = ProductSummary(
                                    id = product.id,
                                    slug = product.slug,
                                    name = product.name,
                                    modelNumber = product.modelNumber,
                                    brandName = product.brandName,
                                    brandSlug = product.brandSlug,
                                    categoryName = product.categoryName,
                                    categorySlug = product.categorySlug,
                                    summary = product.summary,
                                    imageUrl = product.media.firstOrNull()?.url,
                                    ruleCount = product.rules.size,
                                ),
                                initialLoading = false,
                                result = null,
                            )
                        }
                    }
                    .onFailure { error ->
                        _state.update { it.copy(initialLoading = false, error = error.displayMessage()) }
                    }
            }
        }
    }

    fun openPicker(target: PickerTarget) {
        _state.update {
            it.copy(pickerTarget = target, pickerQuery = "", pickerResults = emptyList())
        }
        searchPicker("")
    }

    fun closePicker() {
        pickerJob?.cancel()
        _state.update { it.copy(pickerTarget = null, pickerQuery = "", pickerResults = emptyList()) }
    }

    fun onPickerQueryChange(query: String) {
        _state.update { it.copy(pickerQuery = query) }
        searchPicker(query)
    }

    private fun searchPicker(query: String) {
        pickerJob?.cancel()
        pickerJob = viewModelScope.launch {
            delay(250)
            _state.update { it.copy(pickerLoading = true) }
            runCatching { catalog.search(query, null, null, 20, 0) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(pickerResults = page.items, pickerLoading = false)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(pickerLoading = false, error = error.displayMessage())
                    }
                }
        }
    }

    fun choose(product: ProductSummary) {
        when (_state.value.pickerTarget) {
            PickerTarget.A -> _state.update {
                it.copy(productA = product, pickerTarget = null, result = null)
            }
            PickerTarget.B -> _state.update {
                it.copy(productB = product, pickerTarget = null, result = null)
            }
            null -> Unit
        }
    }

    fun clearA() = _state.update { it.copy(productA = null, result = null) }
    fun clearB() = _state.update { it.copy(productB = null, result = null) }

    fun check() {
        val a = _state.value.productA
        val b = _state.value.productB
        if (a == null || b == null || a.id == b.id) {
            _state.update { it.copy(error = "Choose two different products.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(checking = true, error = null, result = null) }
            runCatching { compatibility.check(a.id, b.id) }
                .onSuccess { result ->
                    _state.update { it.copy(checking = false, result = result) }
                }
                .onFailure { error ->
                    _state.update { it.copy(checking = false, error = error.displayMessage()) }
                }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}
