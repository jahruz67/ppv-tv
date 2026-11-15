package com.example

import com.example.data.api.NetworkModule
import com.example.data.models.FixtureCatalog
import com.example.data.models.PpvStreamsResponse
import com.example.domain.models.EventStatus
import com.example.domain.models.PlaybackMode
import com.example.domain.playback.PlaybackStrategyResolver
import com.example.domain.usecase.EventCatalogMapper
import com.example.domain.usecase.EventStatusCalculator
import com.example.domain.usecase.SearchEventsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PpvDomainUnitTest {

    @Test
    fun `parses live ppv_st json schema with string viewers int always_live and substreams`() {
        val rawJson = """
            {
              "success": true,
              "timestamp": 1794721000,
              "READ_ME": "Interested in using our API?",
              "performance": 0.03,
              "streams": [
                {
                  "category": "Combat Sports",
                  "id": 33,
                  "always_live": false,
                  "streams": [
                    {
                      "id": 32218,
                      "name": "UFC 334: Gane vs. Hokit",
                      "tag": "MMA",
                      "source_tag": "HD | Paramount+",
                      "poster": "https://static.ppvservices.st/assets/thumb/ufc.jpg",
                      "starts_at": 1794693600,
                      "ends_at": 1794722400,
                      "always_live": 0,
                      "locale": "en",
                      "category_name": "Combat Sports",
                      "iframe": "https://taifood-blog.asia/embed/ufc-334",
                      "viewers": "412",
                      "substreams": [
                        {
                          "id": 1032218,
                          "name": "UFC 334: Gane vs. Hokit",
                          "tag": "MMA",
                          "uri_name": "ufc-334_540p",
                          "source_tag": "SD | Paramount+",
                          "locale": "en",
                          "iframe": "https://taifood-blog.asia/embed/ufc-334_540p"
                        },
                        {
                          "id": 32219,
                          "name": "UFC 334: Gane vs. Hokit",
                          "tag": "MMA",
                          "source_tag": "HD | Paramount+",
                          "locale": "es",
                          "iframe": "https://taifood-blog.asia/embed/ufc-334-es"
                        }
                      ]
                    }
                  ]
                },
                {
                  "category": "24/7 Streams",
                  "id": 14,
                  "always_live": true,
                  "streams": [
                    {
                      "id": 5112,
                      "name": "24/7 Sports Desk",
                      "tag": "24/7 channel",
                      "source_tag": "HD | 24/7 channel",
                      "starts_at": 0,
                      "ends_at": 0,
                      "always_live": 1,
                      "locale": "en",
                      "iframe": "https://taifood-blog.asia/embed/247-desk",
                      "viewers": 19
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val dto = NetworkModule.json.decodeFromString<PpvStreamsResponse>(rawJson)
        assertTrue(dto.success)
        assertEquals(2, dto.streams.size)

        val catalog = EventCatalogMapper.mapToCatalog(dto, nowEpochSec = 1794721000L)
        assertEquals(2, catalog.categories.size)
        assertEquals(2, catalog.allEvents.size)

        val ufc = catalog.allEvents.first { it.id == 32218 }
        assertEquals(EventStatus.LIVE, ufc.status)
        assertEquals(412, ufc.viewers)
        assertEquals(3, ufc.sources.size)
        assertEquals("HD | Paramount+", ufc.sources[0].label)
        assertEquals("SD (540p)", ufc.sources[1].quality)
        assertEquals("ES", ufc.sources[2].language)
        assertEquals(PlaybackMode.WEB_EMBED, ufc.sources[0].playbackMode)

        val channel = catalog.allEvents.first { it.id == 5112 }
        assertEquals(EventStatus.ALWAYS_LIVE, channel.status)
        assertEquals(19, channel.viewers)
    }

    @Test
    fun `event status calculation handles live upcoming ended 24_7 millis and missing schedules`() {
        val now = 1794721000L

        assertEquals(
            EventStatus.ALWAYS_LIVE,
            EventStatusCalculator.computeStatus(
                alwaysLive = true,
                categoryAlwaysLive = false,
                rawStartsAt = 0L,
                rawEndsAt = 0L,
                nowEpochSec = now,
            ),
        )

        assertEquals(
            EventStatus.LIVE,
            EventStatusCalculator.computeStatus(
                alwaysLive = false,
                categoryAlwaysLive = false,
                rawStartsAt = now - 600L,
                rawEndsAt = now + 3600L,
                nowEpochSec = now,
            ),
        )

        // Millisecond timestamp input normalized accurately
        assertEquals(
            EventStatus.UPCOMING,
            EventStatusCalculator.computeStatus(
                alwaysLive = false,
                categoryAlwaysLive = false,
                rawStartsAt = (now + 3600L) * 1000L,
                rawEndsAt = (now + 7200L) * 1000L,
                nowEpochSec = now,
            ),
        )

        assertEquals(
            EventStatus.ENDED,
            EventStatusCalculator.computeStatus(
                alwaysLive = false,
                categoryAlwaysLive = false,
                rawStartsAt = now - 7200L,
                rawEndsAt = now - 60L,
                nowEpochSec = now,
            ),
        )

        // Missing schedule must NEVER be marked LIVE blindly
        val missingScheduleStatus = EventStatusCalculator.computeStatus(
            alwaysLive = false,
            categoryAlwaysLive = false,
            rawStartsAt = 0L,
            rawEndsAt = 0L,
            nowEpochSec = now,
        )
        assertFalse(missingScheduleStatus == EventStatus.LIVE)
        assertNull(EventStatusCalculator.normalizeEpochSeconds(0L))
    }

    @Test
    fun `catalog sorting orders LIVE then UPCOMING by start time then 24_7`() {
        val now = 1794721000L
        val catalog = EventCatalogMapper.mapToCatalog(
            FixtureCatalog.createOfflineFallback(now),
            nowEpochSec = now,
        )
        val statuses = catalog.allEvents.map { it.status }
        assertEquals(
            listOf(
                EventStatus.LIVE,
                EventStatus.UPCOMING,
                EventStatus.UPCOMING,
                EventStatus.ALWAYS_LIVE,
            ),
            statuses,
        )
        val upcoming = catalog.allEvents.filter { it.status == EventStatus.UPCOMING }
        assertTrue((upcoming[0].startsAtEpochSec ?: 0L) <= (upcoming[1].startsAtEpochSec ?: 0L))
    }

    @Test
    fun `playback strategy distinguishes native media3 streams from html embeds and invalid urls`() {
        assertEquals(PlaybackMode.NATIVE_MEDIA3, PlaybackStrategyResolver.resolve("https://cdn.example.com/live/stream.m3u8"))
        assertEquals(PlaybackMode.NATIVE_MEDIA3, PlaybackStrategyResolver.resolve("https://cdn.example.com/dash/manifest.mpd?token=1"))
        assertEquals(PlaybackMode.NATIVE_MEDIA3, PlaybackStrategyResolver.resolve("https://cdn.example.com/clip.mp4"))
        assertEquals(PlaybackMode.WEB_EMBED, PlaybackStrategyResolver.resolve("https://taifood-blog.asia/embed/nfl-network"))
        assertEquals(PlaybackMode.UNSUPPORTED, PlaybackStrategyResolver.resolve(""))
        assertEquals(PlaybackMode.UNSUPPORTED, PlaybackStrategyResolver.resolve("javascript:alert(1)"))
    }

    @Test
    fun `search filters across title category tag and broadcaster`() {
        val now = 1794721000L
        val catalog = EventCatalogMapper.mapToCatalog(
            FixtureCatalog.createOfflineFallback(now),
            nowEpochSec = now,
        )

        assertEquals(1, SearchEventsUseCase.search(catalog.allEvents, "Chiefs").size)
        assertEquals(1, SearchEventsUseCase.search(catalog.allEvents, "NBA").size)
        assertEquals(1, SearchEventsUseCase.search(catalog.allEvents, "CBS").size)
        assertEquals(2, SearchEventsUseCase.search(catalog.allEvents, "American Football").size)
        assertEquals(0, SearchEventsUseCase.search(catalog.allEvents, "Snooker").size)
    }
}
