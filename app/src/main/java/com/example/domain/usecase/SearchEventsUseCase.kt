package com.example.domain.usecase

import com.example.domain.models.SportEvent
import java.util.Locale

object SearchEventsUseCase {

    fun search(events: List<SportEvent>, query: String): List<SportEvent> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return events

        val tokens = trimmed.lowercase(Locale.US)
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

        return events
            .filter { event ->
                val searchableCorpus = buildString {
                    append(event.title).append(' ')
                    append(event.categoryName).append(' ')
                    event.leagueTag?.let { append(it).append(' ') }
                    event.sources.forEach { source ->
                        append(source.label).append(' ')
                        append(source.broadcaster).append(' ')
                        source.tag?.let { append(it).append(' ') }
                        source.language?.let { append(it).append(' ') }
                    }
                }.lowercase(Locale.US)

                tokens.all { token -> searchableCorpus.contains(token) }
            }
            .sortedWith(EventCatalogMapper.eventComparator)
    }
}
