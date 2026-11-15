package com.example.domain.usecase

import com.example.domain.models.EventStatus

object EventStatusCalculator {

    private const val MILLIS_THRESHOLD = 10_000_000_000L
    private const val FALLBACK_LIVE_WINDOW_SEC = 4 * 60 * 60L

    fun normalizeEpochSeconds(rawTimestamp: Long?): Long? {
        if (rawTimestamp == null || rawTimestamp <= 0L) return null
        return if (rawTimestamp > MILLIS_THRESHOLD) rawTimestamp / 1000L else rawTimestamp
    }

    fun computeStatus(
        alwaysLive: Boolean,
        categoryAlwaysLive: Boolean,
        rawStartsAt: Long?,
        rawEndsAt: Long?,
        nowEpochSec: Long,
        tag: String? = null,
    ): EventStatus {
        val is247Tag = tag?.contains("24/7", ignoreCase = true) == true
        if (alwaysLive || categoryAlwaysLive || is247Tag) {
            return EventStatus.ALWAYS_LIVE
        }

        val startsAt = normalizeEpochSeconds(rawStartsAt)
        val endsAt = normalizeEpochSeconds(rawEndsAt)

        if (startsAt == null) {
            // Missing start time: never falsely mark LIVE just for appearing in a category.
            return if (endsAt != null && endsAt <= nowEpochSec) {
                EventStatus.ENDED
            } else {
                EventStatus.UPCOMING
            }
        }

        if (nowEpochSec < startsAt) {
            return EventStatus.UPCOMING
        }

        if (endsAt != null && endsAt >= startsAt) {
            return if (nowEpochSec < endsAt) {
                EventStatus.LIVE
            } else {
                EventStatus.ENDED
            }
        }

        // Inconsistent or missing end time when start <= now: treat as LIVE within a bounded window, otherwise ENDED.
        return if (nowEpochSec - startsAt <= FALLBACK_LIVE_WINDOW_SEC) {
            EventStatus.LIVE
        } else {
            EventStatus.ENDED
        }
    }
}
