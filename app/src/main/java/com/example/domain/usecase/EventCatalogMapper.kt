package com.example.domain.usecase

import com.example.data.models.PpvCategoryDto
import com.example.data.models.PpvStreamDto
import com.example.data.models.PpvStreamsResponse
import com.example.data.models.PpvSubstreamDto
import com.example.domain.models.CatalogSnapshot
import com.example.domain.models.EventStatus
import com.example.domain.models.SportCategory
import com.example.domain.models.SportEvent
import com.example.domain.models.StreamSource
import com.example.domain.playback.PlaybackStrategyResolver
import java.util.Locale

object EventCatalogMapper {

    fun mapToCatalog(
        response: PpvStreamsResponse,
        nowEpochSec: Long = if (response.timestamp > 0L) response.timestamp else System.currentTimeMillis() / 1000L,
        fetchedAtEpochMs: Long = System.currentTimeMillis(),
        isFromCache: Boolean = false,
        isFixtureFallback: Boolean = false,
        warningMessage: String? = null,
    ): CatalogSnapshot {
        val mappedCategories = response.streams.map { catDto ->
            mapCategory(catDto, nowEpochSec)
        }

        // Deduplicate events across categories by event ID if the API repeats an item, keeping the richest copy.
        val allEvents = mappedCategories
            .flatMap { it.events }
            .distinctBy { it.id }
            .sortedWith(eventComparator)

        return CatalogSnapshot(
            categories = mappedCategories,
            allEvents = allEvents,
            serverTimestampSec = nowEpochSec,
            fetchedAtEpochMs = fetchedAtEpochMs,
            isFromCache = isFromCache,
            isFixtureFallback = isFixtureFallback,
            warningMessage = warningMessage,
        )
    }

    private fun mapCategory(categoryDto: PpvCategoryDto, nowEpochSec: Long): SportCategory {
        val categoryName = categoryDto.category.ifBlank { "General Sports" }
        val events = categoryDto.streams
            .map { streamDto -> mapEvent(streamDto, categoryDto.id, categoryName, categoryDto.alwaysLive, nowEpochSec) }
            .sortedWith(eventComparator)
        return SportCategory(
            id = categoryDto.id,
            name = categoryName,
            alwaysLive = categoryDto.alwaysLive,
            events = events,
        )
    }

    private fun mapEvent(
        dto: PpvStreamDto,
        categoryId: Int,
        fallbackCategoryName: String,
        categoryAlwaysLive: Boolean,
        nowEpochSec: Long,
    ): SportEvent {
        val resolvedCategory = dto.categoryName?.takeIf { it.isNotBlank() } ?: fallbackCategoryName
        val status = EventStatusCalculator.computeStatus(
            alwaysLive = dto.alwaysLive,
            categoryAlwaysLive = categoryAlwaysLive,
            rawStartsAt = dto.startsAt,
            rawEndsAt = dto.endsAt,
            nowEpochSec = nowEpochSec,
            tag = dto.tag,
        )
        val sources = buildSources(dto)

        return SportEvent(
            id = dto.id,
            title = dto.name.ifBlank { "Untitled Broadcast #${dto.id}" },
            leagueTag = dto.tag?.takeIf { it.isNotBlank() },
            categoryId = categoryId,
            categoryName = resolvedCategory,
            posterUrl = dto.poster?.takeIf { it.isNotBlank() },
            accentColors = dto.colors,
            uriName = dto.uriName?.takeIf { it.isNotBlank() },
            startsAtEpochSec = EventStatusCalculator.normalizeEpochSeconds(dto.startsAt),
            endsAtEpochSec = EventStatusCalculator.normalizeEpochSeconds(dto.endsAt),
            alwaysLive = dto.alwaysLive || categoryAlwaysLive || status == EventStatus.ALWAYS_LIVE,
            status = status,
            viewers = dto.viewers.coerceAtLeast(0),
            sources = sources,
        )
    }

    private fun buildSources(dto: PpvStreamDto): List<StreamSource> {
        val collected = mutableListOf<StreamSource>()
        val seenUrls = mutableSetOf<String>()

        val primaryUrl = dto.iframe?.trim().orEmpty()
        if (primaryUrl.isNotEmpty()) {
            seenUrls.add(primaryUrl)
            val parsedTag = parseSourceTag(dto.sourceTag, dto.uriName)
            collected += StreamSource(
                id = "${dto.id}_primary",
                label = dto.sourceTag?.takeIf { it.isNotBlank() }
                    ?: dto.tag?.takeIf { it.isNotBlank() }
                    ?: "Primary Stream",
                broadcaster = parsedTag.broadcaster ?: "Primary Feed",
                quality = parsedTag.quality,
                language = dto.locale?.takeIf { it.isNotBlank() }?.uppercase(Locale.US),
                tag = dto.tag?.takeIf { it.isNotBlank() },
                url = primaryUrl,
                playbackMode = PlaybackStrategyResolver.resolve(primaryUrl),
                isPrimary = true,
            )
        }

        dto.substreams.forEachIndexed { index, sub ->
            val subUrl = sub.iframe?.trim().orEmpty()
            if (subUrl.isNotEmpty() && seenUrls.add(subUrl)) {
                collected += mapSubstream(dto.id, index, sub, subUrl)
            }
        }

        return collected
    }

    private fun mapSubstream(
        parentId: Int,
        index: Int,
        sub: PpvSubstreamDto,
        subUrl: String,
    ): StreamSource {
        val parsed = parseSourceTag(sub.sourceTag, sub.uriName)
        val quality = sub.quality?.takeIf { it.isNotBlank() } ?: parsed.quality
        val language = (sub.language ?: sub.locale)?.takeIf { it.isNotBlank() }?.uppercase(Locale.US)
        val label = sub.sourceTag?.takeIf { it.isNotBlank() }
            ?: sub.name?.takeIf { it.isNotBlank() }
            ?: sub.tag?.takeIf { it.isNotBlank() }
            ?: "Source ${index + 2}"
        return StreamSource(
            id = "${parentId}_sub_${sub.id}_$index",
            label = label,
            broadcaster = parsed.broadcaster ?: sub.name ?: "Alternate Feed",
            quality = quality,
            language = language,
            tag = sub.tag?.takeIf { it.isNotBlank() },
            url = subUrl,
            playbackMode = PlaybackStrategyResolver.resolve(subUrl),
            isPrimary = false,
        )
    }

    private data class ParsedSourceInfo(val quality: String?, val broadcaster: String?)

    private fun parseSourceTag(sourceTag: String?, uriName: String?): ParsedSourceInfo {
        val parts = sourceTag?.split("|")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        var quality: String? = null
        var broadcaster: String? = null

        if (parts.size >= 2) {
            quality = parts[0]
            broadcaster = parts.drop(1).joinToString(" | ")
        } else if (parts.size == 1) {
            val single = parts[0]
            if (single.equals("HD", true) || single.equals("SD", true) || single.endsWith("p", true)) {
                quality = single
            } else {
                broadcaster = single
            }
        }

        if (quality == null && uriName != null) {
            when {
                uriName.contains("1080", true) -> quality = "1080p"
                uriName.contains("720", true) -> quality = "720p"
                uriName.contains("540", true) -> quality = "540p"
                uriName.contains("480", true) -> quality = "480p"
            }
        } else if (quality != null && uriName != null && uriName.contains("540p", true) && !quality.contains("540")) {
            quality = "$quality (540p)"
        }

        return ParsedSourceInfo(quality = quality, broadcaster = broadcaster)
    }

    val eventComparator: Comparator<SportEvent> = Comparator { a, b ->
        val priorityA = statusPriority(a.status)
        val priorityB = statusPriority(b.status)
        if (priorityA != priorityB) return@Comparator priorityA.compareTo(priorityB)

        when (a.status) {
            EventStatus.LIVE -> {
                val viewerCmp = b.viewers.compareTo(a.viewers)
                if (viewerCmp != 0) viewerCmp else (a.startsAtEpochSec ?: Long.MAX_VALUE).compareTo(b.startsAtEpochSec ?: Long.MAX_VALUE)
            }
            EventStatus.UPCOMING -> {
                (a.startsAtEpochSec ?: Long.MAX_VALUE).compareTo(b.startsAtEpochSec ?: Long.MAX_VALUE)
            }
            else -> a.title.compareTo(b.title, ignoreCase = true)
        }
    }

    private fun statusPriority(status: EventStatus): Int = when (status) {
        EventStatus.LIVE -> 0
        EventStatus.UPCOMING -> 1
        EventStatus.ALWAYS_LIVE -> 2
        EventStatus.ENDED -> 3
    }
}
