package com.compatchecker.app.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.compatchecker.app.domain.model.CollectionDetail
import com.compatchecker.app.domain.model.CollectionSummary
import com.compatchecker.app.domain.model.LeaderboardEntry
import com.compatchecker.app.domain.model.PublicProfile
import com.compatchecker.app.domain.repository.SocialRepository
import com.compatchecker.app.ui.displayMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionsUiState(
    val items: List<CollectionSummary> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val creating: Boolean = false,
    val createdId: String? = null,
    val message: String? = null,
)

class CollectionsViewModel(private val social: SocialRepository) : ViewModel() {
    private val _state = MutableStateFlow(CollectionsUiState())
    val state: StateFlow<CollectionsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { social.collections() }
                .onSuccess { items -> _state.update { it.copy(items = items, loading = false) } }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.displayMessage()) }
                }
        }
    }

    fun create(title: String, description: String?, isPublic: Boolean) {
        if (title.isBlank() || _state.value.creating) return
        viewModelScope.launch {
            _state.update { it.copy(creating = true, message = null) }
            runCatching { social.createCollection(title, description, isPublic) }
                .onSuccess { id ->
                    _state.update { it.copy(creating = false, createdId = id) }
                    load()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(creating = false, message = error.displayMessage())
                    }
                }
        }
    }

    fun consumeCreated() = _state.update { it.copy(createdId = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}

data class CollectionDetailUiState(
    val collection: CollectionDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val actionBusy: Boolean = false,
    val deleted: Boolean = false,
    val message: String? = null,
)

class CollectionDetailViewModel(
    private val collectionId: String,
    private val social: SocialRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CollectionDetailUiState())
    val state: StateFlow<CollectionDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { social.collection(collectionId) }
                .onSuccess { collection ->
                    _state.update { it.copy(collection = collection, loading = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.displayMessage()) }
                }
        }
    }

    fun removeItem(productId: String, removedMessage: String) {
        val collection = _state.value.collection ?: return
        if (_state.value.actionBusy) return
        viewModelScope.launch {
            _state.update { it.copy(actionBusy = true, message = null) }
            runCatching { social.removeFromCollection(collection.id, productId) }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            actionBusy = false,
                            collection = current.collection?.copy(
                                items = current.collection.items.filterNot { it.productId == productId },
                            ),
                            message = removedMessage,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(actionBusy = false, message = error.displayMessage())
                    }
                }
        }
    }

    fun setPublic(isPublic: Boolean) {
        val collection = _state.value.collection ?: return
        if (_state.value.actionBusy) return
        viewModelScope.launch {
            _state.update { it.copy(actionBusy = true, message = null) }
            runCatching { social.updateCollection(collection.id, null, null, isPublic) }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            actionBusy = false,
                            collection = current.collection?.copy(isPublic = isPublic),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(actionBusy = false, message = error.displayMessage())
                    }
                }
        }
    }

    fun delete() {
        val collection = _state.value.collection ?: return
        if (_state.value.actionBusy) return
        viewModelScope.launch {
            _state.update { it.copy(actionBusy = true, message = null) }
            runCatching { social.deleteCollection(collection.id) }
                .onSuccess { _state.update { it.copy(actionBusy = false, deleted = true) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(actionBusy = false, message = error.displayMessage())
                    }
                }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

data class PublicProfileUiState(
    val profile: PublicProfile? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val followBusy: Boolean = false,
    val message: String? = null,
)

class PublicProfileViewModel(
    private val userId: String,
    private val social: SocialRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PublicProfileUiState())
    val state: StateFlow<PublicProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { social.publicProfile(userId) }
                .onSuccess { profile ->
                    _state.update { it.copy(profile = profile, loading = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.displayMessage()) }
                }
        }
    }

    fun toggleFollow() {
        val profile = _state.value.profile ?: return
        if (profile.isSelf || _state.value.followBusy) return
        viewModelScope.launch {
            _state.update { it.copy(followBusy = true, message = null) }
            runCatching { social.toggleFollow(profile.id) }
                .onSuccess { result ->
                    _state.update { current ->
                        current.copy(
                            followBusy = false,
                            profile = current.profile?.copy(
                                isFollowing = result.following,
                                followers = result.followers,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(followBusy = false, message = error.displayMessage())
                    }
                }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

data class LeaderboardUiState(
    val entries: List<LeaderboardEntry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class LeaderboardViewModel(private val social: SocialRepository) : ViewModel() {
    private val _state = MutableStateFlow(LeaderboardUiState())
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { social.leaderboard() }
                .onSuccess { entries -> _state.update { it.copy(entries = entries, loading = false) } }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.displayMessage()) }
                }
        }
    }
}
