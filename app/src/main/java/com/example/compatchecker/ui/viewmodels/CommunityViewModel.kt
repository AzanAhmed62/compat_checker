package com.example.compatchecker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.compatchecker.core.config.DataSourceType
import com.example.compatchecker.data.repository.CommunityRepository
import com.example.compatchecker.domain.model.CompatibilityStatus
import com.example.compatchecker.domain.model.Contribution
import com.example.compatchecker.domain.model.ProductCategory
import com.example.compatchecker.domain.model.ProductRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommunityUiState(
    val requests: List<ProductRequest> = emptyList(),
    val contributions: List<Contribution> = emptyList(),
    val dataSourceType: DataSourceType = DataSourceType.DEMONSTRATION,
    val isSubmitting: Boolean = false,
    val submitSuccessNotice: String? = null
)

class CommunityViewModel(
    private val communityRepository: CommunityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            communityRepository.getProductRequests().collect { envelope ->
                _uiState.update { current ->
                    current.copy(
                        requests = envelope.data,
                        dataSourceType = envelope.sourceType
                    )
                }
            }
        }

        viewModelScope.launch {
            communityRepository.getContributions().collect { envelope ->
                _uiState.update { current ->
                    current.copy(contributions = envelope.data)
                }
            }
        }
    }

    fun voteRequest(request: ProductRequest) {
        viewModelScope.launch {
            communityRepository.voteRequest(request.id, request.hasUserVoted)
        }
    }

    fun submitNewRequest(targetDevice: String, modelNumber: String, category: ProductCategory, notes: String) {
        if (targetDevice.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val newRequest = ProductRequest(
                id = "req_${System.currentTimeMillis()}",
                targetDeviceName = targetDevice.trim(),
                targetModelNumber = modelNumber.trim(),
                componentType = category,
                notes = notes.trim(),
                votesCount = 1,
                hasUserVoted = true
            )
            communityRepository.submitProductRequest(newRequest)
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    submitSuccessNotice = "Verification request logged for community lab testing!"
                )
            }
        }
    }

    fun submitNewContribution(
        title: String,
        sourceProduct: String,
        targetProduct: String,
        status: CompatibilityStatus,
        evidenceUrl: String,
        technicalNotes: String,
        submitterEmail: String
    ) {
        if (title.isBlank() || sourceProduct.isBlank() || targetProduct.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val contribution = Contribution(
                id = "contrib_${System.currentTimeMillis()}",
                title = title.trim(),
                sourceProductName = sourceProduct.trim(),
                targetProductName = targetProduct.trim(),
                proposedStatus = status,
                evidenceUrl = evidenceUrl.trim(),
                technicalNotes = technicalNotes.trim(),
                submitterEmail = submitterEmail.trim()
            )
            communityRepository.submitContribution(contribution)
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    submitSuccessNotice = "Contribution submitted for technical review!"
                )
            }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(submitSuccessNotice = null) }
    }
}
