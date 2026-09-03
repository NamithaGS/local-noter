package com.noter.domain.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noter.data.db.AppDatabase
import com.noter.data.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Collects yesterday's notes (transcript + summary, no audio) into a single text digest
 * and uploads it to the signed-in user's Google Drive.
 *
 * Runs once daily via [DriveBackupScheduler], which this reschedules on every run
 * regardless of outcome - see that class for why a self-rescheduling one-time worker is
 * used instead of PeriodicWorkRequest. Yesterday's notes (not today's) are collected
 * because this runs first thing in the morning, when "today" has barely started.
 */
class DriveBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val account = DriveAuth.getSignedInAccount(applicationContext)
            if (account == null) {
                Log.i(TAG, "No Google account connected, skipping backup")
                return@withContext Result.success()
            }

            val zone = ZoneId.of("America/Los_Angeles")
            val yesterday = LocalDate.now(zone).minusDays(1)
            val startMillis = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = yesterday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val repository = NoteRepository(AppDatabase.getDatabase(applicationContext).noteDao())
            // Excludes notes a manual upload (or a previous run of this job) already
            // sent, so the same note's content never lands in Drive twice.
            val notes = repository.getUnuploadedNotesBetween(startMillis, endMillis)

            if (notes.isEmpty()) {
                Log.i(TAG, "No new notes for $yesterday, skipping upload")
            } else {
                val digest = NoteDigestFormatter.format(yesterday.toString(), notes)
                val fileName = "Noter Backup $yesterday.txt"
                DriveService(applicationContext, account).uploadDigest(fileName, digest)
                repository.markUploaded(notes.map { it.id })
                Log.i(TAG, "Uploaded ${notes.size} note(s) for $yesterday to Drive")
            }

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Daily Drive backup failed", e)
            Result.failure()
        } finally {
            // Keep the daily chain alive even after a transient failure (no network, an
            // expired token) - tomorrow gets a fresh attempt rather than the whole
            // feature silently dying after one bad morning.
            DriveBackupScheduler.scheduleNext(applicationContext)
        }
    }

    private companion object {
        const val TAG = "DriveBackupWorker"
    }
}
