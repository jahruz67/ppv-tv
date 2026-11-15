package com.example.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.models.PlaybackMode
import com.example.domain.models.SportEvent
import com.example.domain.models.StreamSource
import com.example.preferences.UserPreferencesDataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PlaybackStatus {
    data object Preparing : PlaybackStatus
    data object Buffering : PlaybackStatus
    data object Playing : PlaybackStatus
    data object Paused : PlaybackStatus
    data class Error(val title: String, val detail: String) : PlaybackStatus
    data class Unsupported(val reason: String) : PlaybackStatus
}

data class AudioTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean,
)

data class PlayerUiState(
    val event: SportEvent? = null,
    val selectedSourceIndex: Int = 0,
    val playbackStatus: PlaybackStatus = PlaybackStatus.Preparing,
    val controlsVisible: Boolean = true,
    val sourceSelectorVisible: Boolean = false,
    val audioSelectorVisible: Boolean = false,
    val audioTracks: List<AudioTrackOption> = emptyList(),
    val reloadNonce: Int = 0,
    val userPaused: Boolean = false,
) {
    val activeSource: StreamSource?
        get() = event?.sources?.getOrNull(selectedSourceIndex)
}

class PlayerViewModel(
    private val preferences: UserPreferencesDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var autoHideJob: Job? = null

    fun initialize(event: SportEvent?, initialSourceIndex: Int) {
        if (event == null) {
            _uiState.value = PlayerUiState(
                event = null,
                playbackStatus = PlaybackStatus.Unsupported("Event details are unavailable."),
            )
            return
        }
        val safeIndex = initialSourceIndex.coerceIn(0, (event.sources.size - 1).coerceAtLeast(0))
        val source = event.sources.getOrNull(safeIndex)
        val initialStatus = when {
            source == null -> PlaybackStatus.Unsupported("No stream sources are available for this event.")
            source.playbackMode == PlaybackMode.UNSUPPORTED ->
                PlaybackStatus.Unsupported("This source URL format is unsupported or requires unavailable authorization.")
            else -> PlaybackStatus.Preparing
        }
        _uiState.value = PlayerUiState(
            event = event,
            selectedSourceIndex = safeIndex,
            playbackStatus = initialStatus,
            controlsVisible = true,
        )
        scheduleAutoHide()
    }

    fun selectSource(index: Int) {
        val currentEvent = _uiState.value.event ?: return
        val source = currentEvent.sources.getOrNull(index) ?: return
        val status = if (source.playbackMode == PlaybackMode.UNSUPPORTED) {
            PlaybackStatus.Unsupported("Selected source is unsupported or access-restricted.")
        } else {
            PlaybackStatus.Preparing
        }
        _uiState.update { state ->
            state.copy(
                selectedSourceIndex = index,
                playbackStatus = status,
                sourceSelectorVisible = false,
                audioSelectorVisible = false,
                audioTracks = emptyList(),
                userPaused = false,
                reloadNonce = state.reloadNonce + 1,
                controlsVisible = true,
            )
        }
        scheduleAutoHide()
    }

    fun retry() {
        val source = _uiState.value.activeSource ?: return
        if (source.playbackMode == PlaybackMode.UNSUPPORTED) return
        _uiState.update { state ->
            state.copy(
                playbackStatus = PlaybackStatus.Preparing,
                reloadNonce = state.reloadNonce + 1,
                controlsVisible = true,
            )
        }
        scheduleAutoHide()
    }

    fun onBuffering() {
        _uiState.update { it.copy(playbackStatus = PlaybackStatus.Buffering) }
    }

    fun onPlaying() {
        val state = _uiState.value
        val eventId = state.event?.id
        val sourceId = state.activeSource?.id
        if (eventId != null && sourceId != null) {
            viewModelScope.launch {
                preferences.saveLastSelectedSource(eventId, sourceId)
            }
        }
        _uiState.update { it.copy(playbackStatus = PlaybackStatus.Playing, userPaused = false) }
        scheduleAutoHide()
    }

    fun onPaused() {
        autoHideJob?.cancel()
        _uiState.update { it.copy(playbackStatus = PlaybackStatus.Paused, userPaused = true, controlsVisible = true) }
    }

    fun onError(title: String, detail: String) {
        autoHideJob?.cancel()
        _uiState.update {
            it.copy(
                playbackStatus = PlaybackStatus.Error(title, detail),
                controlsVisible = true,
            )
        }
    }

    fun updateAudioTracks(tracks: List<AudioTrackOption>) {
        _uiState.update { it.copy(audioTracks = tracks) }
    }

    fun showControls() {
        _uiState.update { it.copy(controlsVisible = true) }
        scheduleAutoHide()
    }

    fun toggleControls() {
        val next = !_uiState.value.controlsVisible
        _uiState.update {
            it.copy(
                controlsVisible = next,
                sourceSelectorVisible = if (!next) false else it.sourceSelectorVisible,
                audioSelectorVisible = if (!next) false else it.audioSelectorVisible,
            )
        }
        if (next) scheduleAutoHide() else autoHideJob?.cancel()
    }

    fun setSourceSelectorVisible(visible: Boolean) {
        _uiState.update {
            it.copy(
                sourceSelectorVisible = visible,
                audioSelectorVisible = false,
                controlsVisible = true,
            )
        }
        if (visible) autoHideJob?.cancel() else scheduleAutoHide()
    }

    fun setAudioSelectorVisible(visible: Boolean) {
        _uiState.update {
            it.copy(
                audioSelectorVisible = visible,
                sourceSelectorVisible = false,
                controlsVisible = true,
            )
        }
        if (visible) autoHideJob?.cancel() else scheduleAutoHide()
    }

    private fun scheduleAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = viewModelScope.launch {
            delay(5_500L)
            val current = _uiState.value
            if (current.playbackStatus is PlaybackStatus.Playing &&
                !current.sourceSelectorVisible &&
                !current.audioSelectorVisible
            ) {
                _uiState.update { it.copy(controlsVisible = false) }
            }
        }
    }

    class Factory(
        private val preferences: UserPreferencesDataStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(preferences) as T
        }
    }
}
