package com.compatchecker.app.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.compatchecker.app.domain.model.Category
import com.compatchecker.app.domain.model.CommunityRequest
import com.compatchecker.app.domain.repository.AuthRepository
import com.compatchecker.app.domain.repository.CatalogRepository
import com.compatchecker.app.domain.repository.CommunityRepository
import com.compatchecker.app.ui.displayMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RequestsUiState(
    val requests: List<CommunityRequest> = emptyList(),
    val categories: List<Category> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val creating: Boolean = false,
    val showCreate: Boolean = false,
    val message: String? = null,
    val authPrompt: Boolean = false,
    val votingId: String? = null,
)

class RequestsViewModel(
    private val community: CommunityRepository,
    private val catalog: CatalogRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RequestsUiState())
    val state: StateFlow<RequestsUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            val categories = catalog.categories()
            _state.update { it.copy(categories = categories) }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { community.requests() }
                .onSuccess { requests ->
                    _state.update { it.copy(requests = requests, loading = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.displayMessage()) }
                }
        }
    }

    fun showCreate() = _state.update { it.copy(showCreate = true) }
    fun hideCreate() = _state.update { it.copy(showCreate = false) }

    fun create(title: String, description: String, categoryId: String?) {
        if (title.trim().length < 5 || description.trim().length < 10) {
            _state.update { it.copy(message = "Title needs 5+ characters and description 10+.") }
            return
        }
        viewModelScope.launch {
            if (auth.currentSession() == null) {
                _state.update { it.copy(authPrompt = true, showCreate = false) }
                return@launch
            }
            _state.update { it.copy(creating = true) }
            runCatching { community.createRequest(title, description, categoryId) }
                .onSuccess {
                    _state.update { it.copy(creating = false, showCreate = false, message = "Request submitted") }
                    load()
                }
                .onFailure { error ->
                    _state.update { it.copy(creating = false, message = error.displayMessage()) }
                }
        }
    }

    fun vote(request: CommunityRequest) {
        if (_state.value.votingId != null) return
        viewModelScope.launch {
            if (auth.currentSession() == null) {
                _state.update { it.copy(authPrompt = true) }
                return@launch
            }
            _state.update { it.copy(votingId = request.id) }
            runCatching { community.toggleRequestVote(request.id) }
                .onSuccess { votes ->
                    _state.update { state ->
                        state.copy(
                            votingId = null,
                            requests = state.requests.map {
                                if (it.id == request.id) it.copy(voteCount = votes) else it
                            }.sortedByDescending { it.voteCount },
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(votingId = null, message = error.displayMessage()) }
                }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeAuthPrompt() = _state.update { it.copy(authPrompt = false) }
}
