package com.example.compatchecker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.compatchecker.core.config.DataSourceType
import com.example.compatchecker.data.repository.RepairGuideRepository
import com.example.compatchecker.domain.model.RepairGuide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GuidesUiState(
    val guides: List<RepairGuide> = emptyList(),
    val selectedGuide: RepairGuide? = null,
    val dataSourceType: DataSourceType = DataSourceType.DEMONSTRATION,
    val isLoading: Boolean = true
)

class GuidesViewModel(
    private val guideRepository: RepairGuideRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuidesUiState())
    val uiState: StateFlow<GuidesUiState> = _uiState.asStateFlow()

    init {
        loadGuides()
    }

    private fun loadGuides() {
        viewModelScope.launch {
            guideRepository.getAllGuides().collect { envelope ->
                _uiState.update { current ->
                    current.copy(
                        guides = envelope.data,
                        dataSourceType = envelope.sourceType,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectGuide(guideId: String) {
        val guide = _uiState.value.guides.find { it.id == guideId }
        _uiState.update { it.copy(selectedGuide = guide) }
    }

    fun clearSelectedGuide() {
        _uiState.update { it.copy(selectedGuide = null) }
    }
}
