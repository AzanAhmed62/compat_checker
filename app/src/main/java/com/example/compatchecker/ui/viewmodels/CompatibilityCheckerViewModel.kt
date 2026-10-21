package com.example.compatchecker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.compatchecker.core.config.DataSourceType
import com.example.compatchecker.data.repository.CompatibilityRepository
import com.example.compatchecker.data.repository.ProductRepository
import com.example.compatchecker.domain.model.CompatibilityRule
import com.example.compatchecker.domain.model.CompatibilityStatus
import com.example.compatchecker.domain.model.ConfidenceLevel
import com.example.compatchecker.domain.model.Product
import com.example.compatchecker.domain.model.RiskLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CheckerUiState(
    val allProducts: List<Product> = emptyList(),
    val selectedSourceProduct: Product? = null,
    val selectedTargetProduct: Product? = null,
    val evaluatedRule: CompatibilityRule? = null,
    val isEvaluated: Boolean = false,
    val dataSourceType: DataSourceType = DataSourceType.DEMONSTRATION
)

class CompatibilityCheckerViewModel(
    private val productRepository: ProductRepository,
    private val compatibilityRepository: CompatibilityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckerUiState())
    val uiState: StateFlow<CheckerUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            productRepository.getProducts().collect { envelope ->
                _uiState.update { current ->
                    current.copy(
                        allProducts = envelope.data,
                        dataSourceType = envelope.sourceType
                    )
                }
            }
        }
    }

    fun selectSource(product: Product) {
        _uiState.update { it.copy(selectedSourceProduct = product) }
        reevaluate()
    }

    fun selectTarget(product: Product) {
        _uiState.update { it.copy(selectedTargetProduct = product) }
        reevaluate()
    }

    fun swapSelections() {
        _uiState.update { current ->
            current.copy(
                selectedSourceProduct = current.selectedTargetProduct,
                selectedTargetProduct = current.selectedSourceProduct
            )
        }
        reevaluate()
    }

    fun reset() {
        _uiState.update {
            it.copy(
                selectedSourceProduct = null,
                selectedTargetProduct = null,
                evaluatedRule = null,
                isEvaluated = false
            )
        }
    }

    fun reevaluate() {
        val src = _uiState.value.selectedSourceProduct
        val tgt = _uiState.value.selectedTargetProduct
        if (src == null || tgt == null) {
            _uiState.update { it.copy(evaluatedRule = null, isEvaluated = false) }
            return
        }

        if (src.id == tgt.id) {
            _uiState.update {
                it.copy(
                    evaluatedRule = CompatibilityRule(
                        id = "same_product",
                        sourceProductId = src.id,
                        targetProductId = tgt.id,
                        sourceProductName = src.name,
                        targetProductName = tgt.name,
                        status = CompatibilityStatus.COMPATIBLE,
                        confidence = ConfidenceLevel.HIGH_VERIFIED,
                        summary = "Identical hardware model reference (${src.modelNumber}).",
                        technicalDetails = "Direct replacement for identical unit with 1:1 pinout and dimensions.",
                        riskLevel = RiskLevel.NONE
                    ),
                    isEvaluated = true
                )
            }
            return
        }

        viewModelScope.launch {
            compatibilityRepository.checkCompatibility(src.id, tgt.id).collect { envelope ->
                val existingRule = envelope.data
                if (existingRule != null) {
                    _uiState.update { it.copy(evaluatedRule = existingRule, isEvaluated = true) }
                } else {
                    // Generate explicit unverified compatibility assessment
                    val isSameBrand = src.brand.equals(tgt.brand, ignoreCase = true)
                    val summaryText = if (isSameBrand) {
                        "Brand family match (${src.brand}), but no verified teardown report or lab benchmark exists for this specific component pairing."
                    } else {
                        "Cross-manufacturer pairing (${src.brand} vs ${tgt.brand}). Extremely high probability of physical or electrical connector incompatibility."
                    }

                    val unverifiedRule = CompatibilityRule(
                        id = "unverified_${src.id}_${tgt.id}",
                        sourceProductId = src.id,
                        targetProductId = tgt.id,
                        sourceProductName = src.name,
                        targetProductName = tgt.name,
                        status = if (isSameBrand) CompatibilityStatus.UNVERIFIED else CompatibilityStatus.INCOMPATIBLE,
                        confidence = ConfidenceLevel.LOW_EXPERIMENTAL,
                        summary = summaryText,
                        technicalDetails = "Connector specifications (${src.connectorType} vs ${tgt.connectorType}) must be physically verified under microscope or tested with a break-out board before attempting installation.",
                        requiredModifications = "Request community lab validation or consult OEM service bulletins.",
                        riskLevel = if (isSameBrand) RiskLevel.MODERATE else RiskLevel.HIGH_SERIALIZED_LOCKOUT,
                        evidenceSources = emptyList()
                    )
                    _uiState.update { it.copy(evaluatedRule = unverifiedRule, isEvaluated = true) }
                }
            }
        }
    }
}
