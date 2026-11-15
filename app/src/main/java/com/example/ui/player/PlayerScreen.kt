package com.example.ui.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.R
import com.example.domain.models.PlaybackMode
import com.example.domain.models.StreamSource
import com.example.ui.components.StatusBadge
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorderSubtle
import com.example.ui.theme.TvFocusRing
import com.example.ui.theme.TvPrimary
import com.example.ui.theme.TvSecondary
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceElevated
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBackToEvent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var exoPlayerRef by remember { mutableStateOf<ExoPlayer?>(null) }
    var selectedAudioTrack by remember { mutableStateOf<AudioTrackOption?>(null) }

    val retryFocusRequester = remember { FocusRequester() }
    val menuPillFocusRequester = remember { FocusRequester() }
    val primaryOverlayFocus = remember { FocusRequester() }

    BackHandler {
        when {
            state.sourceSelectorVisible -> viewModel.setSourceSelectorVisible(false)
            state.audioSelectorVisible -> viewModel.setAudioSelectorVisible(false)
            state.controlsVisible && state.playbackStatus is PlaybackStatus.Playing -> viewModel.toggleControls()
            else -> onBackToEvent()
        }
    }

    LaunchedEffect(state.playbackStatus) {
        if (state.playbackStatus is PlaybackStatus.Error || state.playbackStatus is PlaybackStatus.Unsupported) {
            runCatching { retryFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(state.controlsVisible) {
        if (state.controlsVisible) {
            runCatching { primaryOverlayFocus.requestFocus() }
        } else {
            runCatching { menuPillFocusRequester.requestFocus() }
        }
    }

    val activeSource = state.activeSource
    val event = state.event

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen")
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                        viewModel.toggleControls()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        val player = exoPlayerRef
                        if (player != null) {
                            if (player.isPlaying) {
                                player.pause()
                                viewModel.onPaused()
                            } else {
                                player.play()
                                viewModel.onPlaying()
                            }
                            true
                        } else {
                            viewModel.toggleControls()
                            true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (!state.controlsVisible) {
                            viewModel.showControls()
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (!state.controlsVisible) {
                            viewModel.showControls()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
    ) {
        if (activeSource != null && state.playbackStatus !is PlaybackStatus.Error && state.playbackStatus !is PlaybackStatus.Unsupported) {
            when (activeSource.playbackMode) {
                PlaybackMode.NATIVE_MEDIA3 -> {
                    NativeMediaPlayer(
                        streamUrl = activeSource.url,
                        reloadNonce = state.reloadNonce,
                        userPaused = state.userPaused,
                        selectedAudioTrack = selectedAudioTrack,
                        playerRefCallback = { exoPlayerRef = it },
                        onBuffering = viewModel::onBuffering,
                        onPlaying = viewModel::onPlaying,
                        onPaused = viewModel::onPaused,
                        onError = viewModel::onError,
                        onAudioTracksUpdated = viewModel::updateAudioTracks,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                PlaybackMode.WEB_EMBED -> {
                    EmbeddedWebPlayer(
                        embedUrl = activeSource.url,
                        reloadNonce = state.reloadNonce,
                        onBuffering = viewModel::onBuffering,
                        onPlaying = viewModel::onPlaying,
                        onError = viewModel::onError,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                PlaybackMode.UNSUPPORTED -> Unit
            }
        }

        // Always-reachable discreet Menu pill when controls are hidden to guarantee no focus trap
        if (!state.controlsVisible && state.playbackStatus is PlaybackStatus.Playing) {
            Surface(
                onClick = { viewModel.showControls() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .focusRequester(menuPillFocusRequester)
                    .testTag("player_collapsed_menu_pill"),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    focusedContainerColor = TvPrimary,
                    contentColor = TvTextPrimary,
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(1.dp, TvBorderSubtle)),
                    focusedBorder = Border(BorderStroke(2.dp, TvFocusRing)),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = "Controls", modifier = Modifier.size(16.dp))
                    Text("Menu / Sources", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Buffering indicator
        if (state.playbackStatus is PlaybackStatus.Preparing || state.playbackStatus is PlaybackStatus.Buffering) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TvBackground.copy(alpha = 0.85f))
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(color = TvPrimary, modifier = Modifier.size(26.dp))
                    Text(
                        text = if (activeSource?.playbackMode == PlaybackMode.NATIVE_MEDIA3) {
                            "Connecting Media3 stream..."
                        } else {
                            "Loading broadcast embed..."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = TvTextPrimary,
                    )
                }
            }
        }

        // Error or Unsupported recovery state
        val errorState = state.playbackStatus as? PlaybackStatus.Error
        val unsupportedState = state.playbackStatus as? PlaybackStatus.Unsupported
        if (errorState != null || unsupportedState != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TvBackground.copy(alpha = 0.94f))
                    .testTag("player_error_overlay"),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TvSurface)
                        .padding(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = "Playback Error",
                        tint = TvPrimary,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = errorState?.title ?: "Source Unavailable",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TvTextPrimary,
                    )
                    Text(
                        text = errorState?.detail ?: unsupportedState?.reason.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvTextSecondary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (errorState != null) {
                            Button(
                                onClick = { viewModel.retry() },
                                modifier = Modifier
                                    .focusRequester(retryFocusRequester)
                                    .testTag("player_retry_button"),
                                colors = ButtonDefaults.colors(
                                    containerColor = TvPrimary,
                                    focusedContainerColor = TvFocusRing,
                                    contentColor = Color.White,
                                    focusedContentColor = TvBackground,
                                ),
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_retry))
                            }
                        }

                        if ((event?.sources?.size ?: 0) > 1) {
                            OutlinedButton(
                                onClick = { viewModel.setSourceSelectorVisible(true) },
                                modifier = Modifier
                                    .let { if (errorState == null) it.focusRequester(retryFocusRequester) else it }
                                    .testTag("player_change_source_button"),
                            ) {
                                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_change_source))
                            }
                        }

                        OutlinedButton(
                            onClick = onBackToEvent,
                            modifier = Modifier.testTag("player_back_to_event_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_back_to_event))
                        }
                    }
                }
            }
        }

        // Native TV Controls Overlay
        AnimatedVisibility(
            visible = state.controlsVisible && errorState == null && unsupportedState == null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top info bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(TvBackground.copy(alpha = 0.92f), Color.Transparent),
                            ),
                        )
                        .padding(horizontal = 36.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            event?.let { StatusBadge(status = it.status) }
                            Text(
                                text = event?.title ?: "Live Stream",
                                style = MaterialTheme.typography.titleLarge,
                                color = TvTextPrimary,
                            )
                        }
                        if (activeSource != null) {
                            val modeLabel = if (activeSource.playbackMode == PlaybackMode.NATIVE_MEDIA3) {
                                stringResource(R.string.player_mode_native)
                            } else {
                                stringResource(R.string.player_mode_embed)
                            }
                            Text(
                                text = "${activeSource.label} • $modeLabel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TvSecondary,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onBackToEvent,
                        modifier = Modifier.testTag("player_overlay_back_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exit Player")
                    }
                }

                // Bottom action bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, TvBackground.copy(alpha = 0.94f)),
                            ),
                        )
                        .padding(horizontal = 36.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (activeSource?.playbackMode == PlaybackMode.NATIVE_MEDIA3 && exoPlayerRef != null) {
                        Button(
                            onClick = {
                                val player = exoPlayerRef ?: return@Button
                                if (player.isPlaying) {
                                    player.pause()
                                    viewModel.onPaused()
                                } else {
                                    player.play()
                                    viewModel.onPlaying()
                                }
                            },
                            modifier = Modifier
                                .focusRequester(primaryOverlayFocus)
                                .testTag("player_play_pause_button"),
                        ) {
                            val isPaused = state.playbackStatus is PlaybackStatus.Paused
                            Icon(
                                imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = if (isPaused) "Play" else "Pause",
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPaused) "Resume" else "Pause")
                        }
                    }

                    Button(
                        onClick = { viewModel.retry() },
                        modifier = Modifier
                            .let {
                                if (activeSource?.playbackMode != PlaybackMode.NATIVE_MEDIA3) {
                                    it.focusRequester(primaryOverlayFocus)
                                } else {
                                    it
                                }
                            }
                            .testTag("player_reload_button"),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reload")
                    }

                    if ((event?.sources?.size ?: 0) > 1) {
                        OutlinedButton(
                            onClick = { viewModel.setSourceSelectorVisible(!state.sourceSelectorVisible) },
                            modifier = Modifier.testTag("player_sources_button"),
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Switch Source (${event?.sources?.size ?: 0})")
                        }
                    }

                    if (state.audioTracks.size > 1) {
                        OutlinedButton(
                            onClick = { viewModel.setAudioSelectorVisible(!state.audioSelectorVisible) },
                            modifier = Modifier.testTag("player_audio_tracks_button"),
                        ) {
                            Icon(Icons.Filled.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Audio (${state.audioTracks.size})")
                        }
                    }
                }
            }
        }

        // Source Switcher Drawer Overlay
        if (state.sourceSelectorVisible && event != null) {
            SourceSwitcherOverlay(
                sources = event.sources,
                selectedIndex = state.selectedSourceIndex,
                onSelectSource = { index -> viewModel.selectSource(index) },
                onClose = { viewModel.setSourceSelectorVisible(false) },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        // Audio Track Switcher Overlay
        if (state.audioSelectorVisible && state.audioTracks.isNotEmpty()) {
            AudioTrackOverlay(
                tracks = state.audioTracks,
                onSelectTrack = { track ->
                    selectedAudioTrack = track
                    viewModel.setAudioSelectorVisible(false)
                },
                onClose = { viewModel.setAudioSelectorVisible(false) },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceSwitcherOverlay(
    sources: List<StreamSource>,
    selectedIndex: Int,
    onSelectSource: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstSourceFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstSourceFocus.requestFocus() }
    }

    Column(
        modifier = modifier
            .width(420.dp)
            .fillMaxSize()
            .background(TvBackground.copy(alpha = 0.96f))
            .padding(28.dp)
            .testTag("source_switcher_overlay"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Broadcast Sources",
                style = MaterialTheme.typography.headlineMedium,
                color = TvTextPrimary,
            )
            OutlinedButton(onClick = onClose) {
                Text("Close")
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(sources, key = { _, src -> src.id }) { index, source ->
                val isSelected = index == selectedIndex
                Surface(
                    onClick = { onSelectSource(index) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (index == 0) it.focusRequester(firstSourceFocus) else it }
                        .testTag("overlay_source_$index"),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) TvSurfaceElevated else TvSurface,
                        focusedContainerColor = TvPrimary,
                        contentColor = TvTextPrimary,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, if (isSelected) TvSecondary else TvBorderSubtle)),
                        focusedBorder = Border(BorderStroke(2.dp, TvFocusRing)),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(source.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = listOfNotNull(
                                source.broadcaster,
                                source.language,
                                if (isSelected) "Active" else null,
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TvTextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AudioTrackOverlay(
    tracks: List<AudioTrackOption>,
    onSelectTrack: (AudioTrackOption) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(360.dp)
            .fillMaxSize()
            .background(TvBackground.copy(alpha = 0.96f))
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Audio Tracks",
                style = MaterialTheme.typography.headlineMedium,
                color = TvTextPrimary,
            )
            OutlinedButton(onClick = onClose) {
                Text("Close")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(tracks) { index, track ->
                Surface(
                    onClick = { onSelectTrack(track) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (track.isSelected) TvSurfaceElevated else TvSurface,
                        focusedContainerColor = TvPrimary,
                        contentColor = TvTextPrimary,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, if (track.isSelected) TvSecondary else TvBorderSubtle)),
                        focusedBorder = Border(BorderStroke(2.dp, TvFocusRing)),
                    ),
                ) {
                    Text(
                        text = if (track.isSelected) "${track.label} (Active)" else track.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }
    }
}
