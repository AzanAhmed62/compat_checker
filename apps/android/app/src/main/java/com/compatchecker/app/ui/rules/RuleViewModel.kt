package com.compatchecker.app.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.compatchecker.app.domain.model.RuleDetail
import com.compatchecker.app.domain.repository.AuthRepository
import com.compatchecker.app.domain.repository.CompatibilityRepository
import com.compatchecker.app.ui.displayMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RuleUiState(
    val rule: RuleDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val voting: Boolean = false,
    val message: String? = null,
    val authPrompt: Boolean = false,
)

class RuleViewModel(
    private val ruleId: String,
    private val compatibility: CompatibilityRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RuleUiState())
    val state: StateFlow<RuleUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { compatibility.rule(ruleId) }
                .onSuccess { rule -> _state.update { it.copy(rule = rule, loading = false) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.displayMessage()) } }
        }
    }

    fun vote(vote: String) {
        if (_state.value.voting) return
        viewModelScope.launch {
            if (auth.currentSession() == null) {
                _state.update { it.copy(authPrompt = true) }
                return@launch
            }
            _state.update { it.copy(voting = true) }
            runCatching { compatibility.castVote(ruleId, vote) }
                .onSuccess { rule ->
                    _state.update { it.copy(rule = rule, voting = false, message = "Vote recorded") }
                }
                .onFailure { error ->
                    _state.update { it.copy(voting = false, message = error.displayMessage()) }
                }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeAuthPrompt() = _state.update { it.copy(authPrompt = false) }
}
