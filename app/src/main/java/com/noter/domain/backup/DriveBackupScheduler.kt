package com.noter.domain.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules [DriveBackupWorker] to run once a day at [BACKUP_HOUR]:00 in [BACKUP_ZONE].
 *
 * WorkManager has no "run at this wall-clock time" primitive - only delays and periodic
 * intervals, and periodic intervals drift against wall-clock time whenever a run is
 * deferred by Doze/battery optimization. So instead of PeriodicWorkRequest, this
 * schedules a single one-time request each time, and [DriveBackupWorker] calls
 * [scheduleNext] again after it runs to queue up the following day - the pattern
 * WorkManager's own docs recommend for daily-at-a-fixed-time jobs. This trades exact
 * timing for simplicity: an idle overnight phone may run this anywhere from 6:00 AM to
 * a couple hours later, never earlier.
 */
object DriveBackupScheduler {

    const val UNIQUE_WORK_NAME = "drive_daily_backup"

    private val BACKUP_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")
    private const val BACKUP_HOUR = 6

    /** (Re)schedules the next run, replacing any pending one. Safe to call repeatedly. */
    fun scheduleNext(context: Context) {
        val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setInitialDelay(delayUntilNextRun().toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /** Time from now until the next occurrence of [BACKUP_HOUR]:00 in [BACKUP_ZONE]. */
    private fun delayUntilNextRun(): Duration {
        val now = ZonedDateTime.now(BACKUP_ZONE)
        var next = now.toLocalDate().atTime(LocalTime.of(BACKUP_HOUR, 0)).atZone(BACKUP_ZONE)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next)
    }
}
