package com.example.domain.playback

import com.example.domain.models.PlaybackMode
import java.net.URI
import java.util.Locale

object PlaybackStrategyResolver {

    private val directMediaExtensions = setOf(
        ".m3u8",
        ".mpd",
        ".mp4",
        ".m4v",
        ".webm",
        ".mkv",
        ".ts",
        ".aac",
        ".mp3",
    )

    private val directStreamHints = listOf(
        "playlist.m3u8",
        "manifest.mpd",
        "master.m3u8",
        "/hls/",
        "/dash/",
        "format=m3u8",
        "format=mpd",
    )

    fun resolve(rawUrl: String?): PlaybackMode {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return PlaybackMode.UNSUPPORTED

        val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return PlaybackMode.UNSUPPORTED
        val scheme = parsed.scheme?.lowercase(Locale.US).orEmpty()
        if (scheme != "https" && scheme != "http") {
            return PlaybackMode.UNSUPPORTED
        }

        val host = parsed.host?.lowercase(Locale.US).orEmpty()
        if (host.isEmpty()) return PlaybackMode.UNSUPPORTED

        val path = parsed.path?.lowercase(Locale.US).orEmpty()
        val fullLower = trimmed.lowercase(Locale.US)

        if (directMediaExtensions.any { ext -> path.endsWith(ext) } ||
            directStreamHints.any { hint -> fullLower.contains(hint) }
        ) {
            return PlaybackMode.NATIVE_MEDIA3
        }

        return PlaybackMode.WEB_EMBED
    }
}
