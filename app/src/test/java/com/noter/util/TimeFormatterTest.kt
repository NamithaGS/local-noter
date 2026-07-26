package com.noter.util

import org.junit.Assert.*
import org.junit.Test

class TimeFormatterTest {

    @Test
    fun formatDurationZeroSeconds() {
        assertEquals("00:00", TimeFormatter.formatDuration(0))
    }

    @Test
    fun formatDurationUnderMinute() {
        assertEquals("00:45", TimeFormatter.formatDuration(45))
    }

    @Test
    fun formatDurationMultipleMinutes() {
        assertEquals("05:30", TimeFormatter.formatDuration(330))
    }

    @Test
    fun formatDurationOverHour() {
        assertEquals("65:15", TimeFormatter.formatDuration(3915))
    }

    @Test
    fun formatRelativeTimeHoursAgo() {
        val now = System.currentTimeMillis()
        val twoHoursAgo = now - (2 * 60 * 60 * 1000)
        assertEquals("2 hours ago", TimeFormatter.formatRelativeTime(twoHoursAgo, now))
    }

    @Test
    fun formatRelativeTimeYesterday() {
        val now = System.currentTimeMillis()
        val yesterday = now - (25 * 60 * 60 * 1000)
        assertEquals("Yesterday", TimeFormatter.formatRelativeTime(yesterday, now))
    }

    @Test
    fun formatRelativeTimeJustNow() {
        val now = System.currentTimeMillis()
        val fewSecondsAgo = now - (30 * 1000)
        assertEquals("Just now", TimeFormatter.formatRelativeTime(fewSecondsAgo, now))
    }

    @Test
    fun formatRelativeTimeMinutesAgo() {
        val now = System.currentTimeMillis()
        val fifteenMinutesAgo = now - (15 * 60 * 1000)
        assertEquals("15 minutes ago", TimeFormatter.formatRelativeTime(fifteenMinutesAgo, now))
    }

    @Test
    fun formatRelativeTimeOneMinuteAgo() {
        val now = System.currentTimeMillis()
        val oneMinuteAgo = now - (60 * 1000)
        assertEquals("1 minute ago", TimeFormatter.formatRelativeTime(oneMinuteAgo, now))
    }

    @Test
    fun formatRelativeTimeOneHourAgo() {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - (60 * 60 * 1000)
        assertEquals("1 hour ago", TimeFormatter.formatRelativeTime(oneHourAgo, now))
    }

    @Test
    fun formatRelativeDaysAgo() {
        val now = System.currentTimeMillis()
        val fiveDaysAgo = now - (5 * 24 * 60 * 60 * 1000)
        assertEquals("5 days ago", TimeFormatter.formatRelativeTime(fiveDaysAgo, now))
    }

    @Test
    fun formatRelativeTimeWeeksAgo() {
        val now = System.currentTimeMillis()
        val threeWeeksAgo = now - (21 * 24 * 60 * 60 * 1000)
        assertEquals("3 weeks ago", TimeFormatter.formatRelativeTime(threeWeeksAgo, now))
    }

    @Test
    fun formatRelativeTimeOneWeekAgo() {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000)
        assertEquals("1 week ago", TimeFormatter.formatRelativeTime(sevenDaysAgo, now))
    }

    @Test
    fun formatDurationHandlesEdgeCases() {
        assertEquals("00:00", TimeFormatter.formatDuration(0))
        assertEquals("00:01", TimeFormatter.formatDuration(1))
        assertEquals("00:59", TimeFormatter.formatDuration(59))
    }

    @Test
    fun formatDurationSingleSecond() {
        assertEquals("00:01", TimeFormatter.formatDuration(1))
    }

    @Test
    fun formatDurationOneMinute() {
        assertEquals("01:00", TimeFormatter.formatDuration(60))
    }

    @Test
    fun formatDurationMaxMinutesWithoutHour() {
        assertEquals("59:59", TimeFormatter.formatDuration(3599))
    }

    @Test
    fun formatDurationAtHourBoundary() {
        assertEquals("60:00", TimeFormatter.formatDuration(3600))
    }

    @Test
    fun formatDurationMultipleHours() {
        assertEquals("125:45", TimeFormatter.formatDuration(7545))
    }
}
