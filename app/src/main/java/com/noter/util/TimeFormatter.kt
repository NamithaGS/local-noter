package com.noter.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

object TimeFormatter {

    // Not thread-safe (SimpleDateFormat never is); fine here since this is only ever
    // called from Compose's main-thread recomposition, one call at a time.
    private val TIME_OF_DAY_FORMAT = SimpleDateFormat("h:mm a", Locale.US)
    private val MONTH_DAY_FORMAT = DateTimeFormatter.ofPattern("MMMM d", Locale.US)
    private val MONTH_DAY_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }

    fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes minute${if (minutes == 1L) "" else "s"} ago"
            hours < 24 -> "$hours hour${if (hours == 1L) "" else "s"} ago"
            days < 2 -> "Yesterday"
            days < 7 -> "$days days ago"
            else -> "${days / 7} week${if (days < 14) "" else "s"} ago"
        }
    }

    /** Time of day only, e.g. "2:45 PM" - the note list shows this per-item since the date is already in the group header. */
    fun formatTime(timestamp: Long): String = TIME_OF_DAY_FORMAT.format(Date(timestamp))

    /**
     * Groups notes for the list by day: "Today", "Yesterday", then "September 5" (or
     * "September 5, 2025" once it's no longer this year) - the label used as each
     * section's header, and as the grouping key itself since two notes on the same day
     * always produce the same label.
     */
    fun formatDateHeader(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val zone = ZoneId.systemDefault()
        val noteDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

        return when {
            noteDate == today -> "Today"
            noteDate == today.minusDays(1) -> "Yesterday"
            noteDate.year == today.year -> noteDate.format(MONTH_DAY_FORMAT)
            else -> noteDate.format(MONTH_DAY_YEAR_FORMAT)
        }
    }
}
