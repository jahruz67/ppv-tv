package com.example.ui.player

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
    var autoReconnectAttempts by remember(streamUrl, reloadNonce) { mutableIntStateOf(0) }

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
        onBuffering()
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = !userPaused
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

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> onBuffering()
                    Player.STATE_READY -> {
                        if (exoPlayer.isPlaying || exoPlayer.playWhenReady) {
                            onPlaying()
                        } else {
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
                if (isPlaying) {
                    onPlaying()
                } else if (exoPlayer.playbackState == Player.STATE_READY && !exoPlayer.playWhenReady) {
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
                if (autoReconnectAttempts < 2) {
                    autoReconnectAttempts += 1
                    onBuffering()
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                } else {
                    onError(
                        "Playback Error (${error.errorCodeName})",
                        error.localizedMessage ?: "Unable to play stream via Media3 ExoPlayer.",
                    )
                }
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (!userPaused) {
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
            playerRefCallback(null)
            exoPlayer.release()
        }
    }

    // Small safety check if buffering stalls on launch
    LaunchedEffect(autoReconnectAttempts) {
        if (autoReconnectAttempts > 0) {
            delay(1_200L)
        }
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
