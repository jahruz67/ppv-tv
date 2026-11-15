package com.example.ui.components

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeFormatters {

    fun formatLocalEventTime(epochSeconds: Long?, timeZone: TimeZone = TimeZone.getDefault()): String {
        if (epochSeconds == null || epochSeconds <= 0L) return "Time TBD"
        val date = Date(epochSeconds * 1000L)
        val nowCal = Calendar.getInstance(timeZone)
        val eventCal = Calendar.getInstance(timeZone).apply { time = date }

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        val timeText = timeFormat.format(date)

        return when {
            isSameDay(nowCal, eventCal) -> "Today • $timeText"
            isTomorrow(nowCal, eventCal) -> "Tomorrow • $timeText"
            else -> {
                val dayFormat = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()).apply {
                    this.timeZone = timeZone
                }
                dayFormat.format(date)
            }
        }
    }

    fun formatLocalTimeRange(
        startsAtSec: Long?,
        endsAtSec: Long?,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        if (startsAtSec == null || startsAtSec <= 0L) return "Schedule TBD"
        val startText = formatLocalEventTime(startsAtSec, timeZone)
        if (endsAtSec == null || endsAtSec <= startsAtSec) return startText
        val endFormat = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        return "$startText – ${endFormat.format(Date(endsAtSec * 1000L))}"
    }

    fun formatCurrentClock(epochMs: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        return fmt.format(Date(epochMs))
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isTomorrow(now: Calendar, target: Calendar): Boolean {
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        return isSameDay(tomorrow, target)
    }
}
