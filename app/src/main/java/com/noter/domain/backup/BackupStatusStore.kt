package com.noter.domain.backup

import android.content.Context

/**
 * Persists "when did the backup pipeline last run successfully" - a single scalar the
 * UI reads to show "Backed up 5m ago", so it doesn't need its own Room column or
 * migration. Updated by both the automatic daily job and a manual "back up now" tap,
 * whichever ran most recently.
 */
object BackupStatusStore {

    private const val PREFS_NAME = "backup_status"
    private const val KEY_LAST_BACKUP_TIME = "last_backup_time"

    fun getLastBackupTime(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getLong(KEY_LAST_BACKUP_TIME, -1L)
        return if (value == -1L) null else value
    }

    fun setLastBackupTime(context: Context, timeMillis: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_BACKUP_TIME, timeMillis)
            .apply()
    }
}
