package com.example.ui.player

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun NativeMediaPlayer(
    streamUrl: String,
    reloadNonce: Int,
    userPaused: Boolean,
    selectedAudioTrack: AudioTrackOption?,
    playerRefCallback: (ExoPlayer?) -> Unit,
    onBuffering: () -> Unit,
    onPlaying: () -> Unit,
    onPaused: () -> Unit,
    onError: (title: String, detail: String) -> Unit,
    onAudioTracksUpdated: (List<AudioTrackOption>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestUserPaused by rememberUpdatedState(userPaused)
    var autoReconnectAttempts by remember { mutableIntStateOf(0) }
    var pendingError by remember { mutableStateOf<PlaybackException?>(null) }
    var playbackActive by remember { mutableStateOf(false) }
    var isForeground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    val trackSelector = remember {
        DefaultTrackSelector(context)
    }

    val exoPlayer = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Android TV; PPVstTV/1.0)")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30_000, 60_000, 1_500, 5_000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    LaunchedEffect(exoPlayer) {
        playerRefCallback(exoPlayer)
    }

    LaunchedEffect(streamUrl, reloadNonce) {
        autoReconnectAttempts = 0
        pendingError = null
        trackSelector.parameters = trackSelector.buildUponParameters()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .build()
        onAudioTracksUpdated(emptyList())
        onBuffering()
        exoPlayer.setMediaItem(
            MediaItem.Builder()
                .setUri(Uri.parse(streamUrl))
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(12_000L)
                        .build(),
                )
                .build(),
        )
        exoPlayer.prepare()
        exoPlayer.playWhenReady = !latestUserPaused && isForeground
    }

    LaunchedEffect(selectedAudioTrack) {
        val track = selectedAudioTrack ?: return@LaunchedEffect
        val audioGroup = exoPlayer.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .getOrNull(track.groupIndex) ?: return@LaunchedEffect

        trackSelector.parameters = trackSelector.buildUponParameters()
            .setOverrideForType(TrackSelectionOverride(audioGroup.mediaTrackGroup, track.trackIndex))
            .build()
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> onBuffering()
                    Player.STATE_READY -> {
                        if (exoPlayer.isPlaying) {
                            onPlaying()
                        } else if (latestUserPaused) {
                            onPaused()
                        }
                    }
                    Player.STATE_ENDED -> onError(
                        "Stream Ended",
                        "The broadcast feed ended. Retry or select another source.",
                    )
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playbackActive = isPlaying
                if (isPlaying) {
                    onPlaying()
                } else if (exoPlayer.playbackState == Player.STATE_READY && latestUserPaused) {
                    onPaused()
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val options = mutableListOf<AudioTrackOption>()
                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                audioGroups.forEachIndexed { groupIdx, group ->
                    for (trackIdx in 0 until group.length) {
                        if (group.isTrackSupported(trackIdx)) {
                            val format = group.getTrackFormat(trackIdx)
                            val lang = format.language?.uppercase() ?: "Track ${options.size + 1}"
                            val label = listOfNotNull(
                                format.label?.takeIf { it.isNotBlank() },
                                lang,
                            ).distinct().joinToString(" • ")
                            options += AudioTrackOption(
                                groupIndex = groupIdx,
                                trackIndex = trackIdx,
                                label = label,
                                isSelected = group.isTrackSelected(trackIdx),
                            )
                        }
                    }
                }
                onAudioTracksUpdated(options)
            }

            override fun onPlayerError(error: PlaybackException) {
                pendingError = error
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    isForeground = false
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    isForeground = true
                    if (!latestUserPaused && pendingError == null) {
                        exoPlayer.play()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            playerRefCallback(null)
            exoPlayer.release()
        }
    }

    // Buffering is left entirely to ExoPlayer. Only explicit errors can retry.
    LaunchedEffect(pendingError, streamUrl, reloadNonce, isForeground, userPaused) {
        val error = pendingError ?: return@LaunchedEffect
        if (!isForeground || userPaused) return@LaunchedEffect
        if (!isRecoverablePlaybackError(error) || autoReconnectAttempts >= 3) {
            onError(
                "Playback Error (${error.errorCodeName})",
                error.localizedMessage ?: "Unable to play this stream. Retry or select another source.",
            )
            return@LaunchedEffect
        }
        onBuffering()
        delay(1_000L shl autoReconnectAttempts)
        autoReconnectAttempts += 1
        pendingError = null
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            exoPlayer.seekToDefaultPosition()
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = !latestUserPaused && isForeground
    }

    LaunchedEffect(playbackActive, streamUrl, reloadNonce) {
        if (!playbackActive) return@LaunchedEffect
        delay(30_000L)
        autoReconnectAttempts = 0
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
                isFocusable = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view ->
            if (view.player !== exoPlayer) {
                view.player = exoPlayer
            }
        },
    )
}

@OptIn(UnstableApi::class)
private fun isRecoverablePlaybackError(error: PlaybackException): Boolean = when (error.errorCode) {
    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> true
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
        val status = (error.cause as? DefaultHttpDataSource.InvalidResponseCodeException)?.responseCode
        status == 408 || status == 429 || (status != null && status >= 500)
    }
    else -> false
}
