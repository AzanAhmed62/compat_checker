package com.compatchecker.app.ui.guides

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.compatchecker.app.domain.model.GuideDetail
import com.compatchecker.app.domain.model.GuideSummary
import com.compatchecker.app.domain.repository.GuideRepository
import com.compatchecker.app.ui.displayMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GuidesUiState(
    val guides: List<GuideSummary> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class GuidesViewModel(private val guidesRepository: GuideRepository) : ViewModel() {
    private val _state = MutableStateFlow(GuidesUiState())
    val state: StateFlow<GuidesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { guidesRepository.guides() }
                .onSuccess { guides -> _state.update { it.copy(guides = guides, loading = false) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.displayMessage()) } }
        }
    }
}

data class GuideDetailUiState(
    val guide: GuideDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class GuideDetailViewModel(
    private val guideId: String,
    private val guidesRepository: GuideRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GuideDetailUiState())
    val state: StateFlow<GuideDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { guidesRepository.guide(guideId) }
                .onSuccess { guide -> _state.update { it.copy(guide = guide, loading = false) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.displayMessage()) } }
        }
    }
}
