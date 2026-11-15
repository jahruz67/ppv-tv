package com.example.domain.models

enum class EventStatus {
    LIVE,
    UPCOMING,
    ENDED,
    ALWAYS_LIVE,
}

enum class PlaybackMode {
    NATIVE_MEDIA3,
    WEB_EMBED,
    UNSUPPORTED,
}

data class StreamSource(
    val id: String,
    val label: String,
    val broadcaster: String,
    val quality: String?,
    val language: String?,
    val tag: String?,
    val url: String,
    val playbackMode: PlaybackMode,
    val isPrimary: Boolean,
)

data class SportEvent(
    val id: Int,
    val title: String,
    val leagueTag: String?,
    val categoryId: Int,
    val categoryName: String,
    val posterUrl: String?,
    val accentColors: List<String>,
    val uriName: String?,
    val startsAtEpochSec: Long?,
    val endsAtEpochSec: Long?,
    val alwaysLive: Boolean,
    val status: EventStatus,
    val viewers: Int,
    val sources: List<StreamSource>,
) {
    val primarySource: StreamSource?
        get() = sources.firstOrNull()
}

data class SportCategory(
    val id: Int,
    val name: String,
    val alwaysLive: Boolean,
    val events: List<SportEvent>,
) {
    val liveCount: Int
        get() = events.count { it.status == EventStatus.LIVE || it.status == EventStatus.ALWAYS_LIVE }
}

data class CatalogSnapshot(
    val categories: List<SportCategory>,
    val allEvents: List<SportEvent>,
    val serverTimestampSec: Long,
    val fetchedAtEpochMs: Long,
    val isFromCache: Boolean = false,
    val isFixtureFallback: Boolean = false,
    val warningMessage: String? = null,
)
