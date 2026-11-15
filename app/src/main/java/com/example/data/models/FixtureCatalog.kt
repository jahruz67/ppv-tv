package com.example.data.models

/**
 * Offline verification fixtures modeled on the verified PPV.st API schema.
 * Used ONLY when the live API and local disk cache are both unreachable.
 */
object FixtureCatalog {

    fun createOfflineFallback(nowSec: Long = System.currentTimeMillis() / 1000L): PpvStreamsResponse {
        return PpvStreamsResponse(
            success = true,
            timestamp = nowSec,
            readMe = "Offline verification fixture dataset",
            performance = 0.0,
            streams = listOf(
                PpvCategoryDto(
                    id = 26,
                    category = "American Football",
                    alwaysLive = false,
                    streams = listOf(
                        PpvStreamDto(
                            id = 90001,
                            name = "Offline Sample: Red Raiders vs. Cowboys",
                            tag = "College Football",
                            sourceTag = "HD | ESPN",
                            startsAt = nowSec - 1800L,
                            endsAt = nowSec + 5400L,
                            alwaysLive = false,
                            locale = "en",
                            categoryName = "American Football",
                            iframe = "https://taifood-blog.asia/embed/cfb/sample",
                            viewers = 402,
                            substreams = listOf(
                                PpvSubstreamDto(
                                    id = 91001,
                                    name = "Offline Sample: Red Raiders vs. Cowboys",
                                    tag = "College Football",
                                    sourceTag = "SD | ESPN",
                                    locale = "en",
                                    iframe = "https://taifood-blog.asia/embed/cfb/sample_540p",
                                )
                            ),
                        ),
                        PpvStreamDto(
                            id = 90002,
                            name = "Offline Sample: Chiefs vs. Falcons",
                            tag = "NFL",
                            sourceTag = "HD | CBS",
                            startsAt = nowSec + 7200L,
                            endsAt = nowSec + 18000L,
                            alwaysLive = false,
                            locale = "en",
                            categoryName = "American Football",
                            iframe = "https://taifood-blog.asia/embed/nfl/sample",
                            viewers = 0,
                        ),
                    ),
                ),
                PpvCategoryDto(
                    id = 37,
                    category = "Basketball",
                    alwaysLive = false,
                    streams = listOf(
                        PpvStreamDto(
                            id = 90003,
                            name = "Offline Sample: Pacers vs. 76ers",
                            tag = "NBA",
                            sourceTag = "HD | Home",
                            startsAt = nowSec + 10800L,
                            endsAt = nowSec + 21600L,
                            alwaysLive = false,
                            locale = "en",
                            categoryName = "Basketball",
                            iframe = "https://taifood-blog.asia/embed/nba/sample",
                            viewers = 0,
                            substreams = listOf(
                                PpvSubstreamDto(
                                    id = 91003,
                                    name = "Offline Sample: Pacers vs. 76ers",
                                    tag = "NBA",
                                    sourceTag = "HD | Away",
                                    locale = "en",
                                    iframe = "https://taifood-blog.asia/embed/nba/sample/away",
                                )
                            ),
                        )
                    ),
                ),
                PpvCategoryDto(
                    id = 14,
                    category = "24/7 Streams",
                    alwaysLive = true,
                    streams = listOf(
                        PpvStreamDto(
                            id = 90004,
                            name = "Offline Sample: 24/7 Sports News",
                            tag = "24/7 channel",
                            sourceTag = "HD | Sports Network",
                            startsAt = 0L,
                            endsAt = 0L,
                            alwaysLive = true,
                            locale = "en",
                            categoryName = "24/7 Streams",
                            iframe = "https://taifood-blog.asia/embed/247-sample",
                            viewers = 12,
                        )
                    ),
                ),
            ),
        )
    }
}
