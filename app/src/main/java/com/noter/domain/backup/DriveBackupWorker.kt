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

/**
 * Two-pass daily job, run once via [DriveBackupScheduler]:
 *
 * Pass 1 (archive): every note from yesterday gets appended to a dated doc under
 * `AllNotes/<year>/<month>`, regardless of content - a complete chronological record.
 *
 * Pass 2 (classify): runs only after pass 1 finishes, per "once the day's notes are
 * archived, do one more pass of classification" - each of those same notes gets
 * classified (manual tag, or on-device AI - see [WorkClassifier]) and, if Work,
 * appended to its topic doc under `Work/<topic>`.
 *
 * Both passes are independently idempotent (guarded by `uploadedToDrive` /
 * `filedToWorkDoc`), so a retry after a partial failure never double-appends a note, and
 * reschedules itself for the next day regardless of outcome so one bad morning doesn't
 * break the whole chain.
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

            val yesterday = LocalDate.now(DriveBackupScheduler.BACKUP_ZONE).minusDays(1)
            val startMillis = yesterday.atStartOfDay(DriveBackupScheduler.BACKUP_ZONE).toInstant().toEpochMilli()
            val endMillis = yesterday.plusDays(1)
                .atStartOfDay(DriveBackupScheduler.BACKUP_ZONE).toInstant().toEpochMilli()

            val repository = NoteRepository(AppDatabase.getDatabase(applicationContext).noteDao())
            val filer = NoteFiler(applicationContext, account)

            val toArchive = repository.getUnuploadedNotesBetween(startMillis, endMillis)
            if (toArchive.isNotEmpty()) {
                filer.archiveNotes(toArchive)
                repository.markUploaded(toArchive.map { it.id })
                Log.i(TAG, "Archived ${toArchive.size} note(s) for $yesterday")
            } else {
                Log.i(TAG, "No new notes to archive for $yesterday")
            }

            val toClassify = repository.getUnfiledWorkNotesBetween(startMillis, endMillis)
            if (toClassify.isNotEmpty()) {
                filer.classifyNotes(toClassify)
                repository.markFiledToWorkDoc(toClassify.map { it.id })
                Log.i(TAG, "Ran Work classification on ${toClassify.size} note(s) for $yesterday")
            }

            // Recorded even when there was nothing new to archive - "last backed up"
            // means "the pipeline last ran successfully", not "last time it found work".
            BackupStatusStore.setLastBackupTime(applicationContext, System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Daily Drive backup failed", e)
            Result.failure()
        } finally {
            DriveBackupScheduler.scheduleNext(applicationContext)
        }
    }

    private companion object {
        const val TAG = "DriveBackupWorker"
    }
}
