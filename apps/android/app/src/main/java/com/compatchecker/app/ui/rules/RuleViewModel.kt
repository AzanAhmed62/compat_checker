package com.compatchecker.app.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.compatchecker.app.domain.model.RuleDetail
import com.compatchecker.app.domain.repository.AuthRepository
import com.compatchecker.app.domain.repository.CompatibilityRepository
import com.compatchecker.app.domain.repository.ProfileRepository
import com.compatchecker.app.ui.displayMessage
import com.compatchecker.app.ui.profile.ContributionKind
import com.compatchecker.app.ui.profile.EvidenceDraft
import com.compatchecker.app.ui.profile.buildEvidencePayload
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
    val evidenceBusy: Boolean = false,
    val evidenceSubmitted: Boolean = false,
    val evidenceError: String? = null,
)

class RuleViewModel(
    private val ruleId: String,
    private val compatibility: CompatibilityRepository,
    private val profiles: ProfileRepository,
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

    fun submitEvidence(evidence: EvidenceDraft) {
        if (_state.value.evidenceBusy) return
        viewModelScope.launch {
            if (auth.currentSession() == null) {
                _state.update { it.copy(authPrompt = true) }
                return@launch
            }
            _state.update { it.copy(evidenceBusy = true, evidenceError = null) }
            runCatching {
                val payload = buildEvidencePayload(ruleId, evidence)
                profiles.submitContribution(ContributionKind.EVIDENCE.wire, payload)
            }
                .onSuccess {
                    _state.update {
                        it.copy(
                            evidenceBusy = false,
                            evidenceSubmitted = true,
                            evidenceError = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(evidenceBusy = false, evidenceError = error.displayMessage())
                    }
                }
        }
    }

    fun consumeEvidenceSubmitted() = _state.update { it.copy(evidenceSubmitted = false) }
    fun clearEvidenceError() = _state.update { it.copy(evidenceError = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeAuthPrompt() = _state.update { it.copy(authPrompt = false) }
}
