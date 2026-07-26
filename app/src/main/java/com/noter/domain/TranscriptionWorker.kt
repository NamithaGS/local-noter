package com.noter.domain

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noter.data.db.AppDatabase
import com.noter.domain.summarization.NoteSummarizer
import com.noter.domain.transcription.VoskTranscriber
import com.noter.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Background worker that turns a finished recording into a titled, transcribed and
 * (where the hardware allows) summarised note.
 *
 * Triggered by [com.noter.ui.viewmodels.RecordingViewModel] once recording stops. The
 * note row already exists at that point with a placeholder title; this worker fills in
 * the transcript path, title and summary.
 *
 * Input parameters:
 * - [KEY_NOTE_ID]: the id of the note to update
 * - [KEY_AUDIO_PATH]: the path of the audio file to transcribe
 *
 * Usage example:
 * ```kotlin
 * val workRequest = OneTimeWorkRequestBuilder<TranscriptionWorker>()
 *     .setInputData(
 *         workDataOf(
 *             TranscriptionWorker.KEY_NOTE_ID to noteId,
 *             TranscriptionWorker.KEY_AUDIO_PATH to audioPath
 *         )
 *     )
 *     .build()
 * WorkManager.getInstance(context).enqueue(workRequest)
 * ```
 *
 * Transcription is on-device via Vosk ([VoskTranscriber]) and summarisation is on-device
 * via Gemini Nano ([NoteSummarizer]); nothing leaves the phone.
 */
class TranscriptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_NOTE_ID = "note_id"
        const val KEY_AUDIO_PATH = "audio_path"

        private const val TAG = "TranscriptionWorker"
        private const val MAX_TITLE_LENGTH = 30
        private const val MAX_ATTEMPTS = 3
        private const val NO_SPEECH_TITLE = "No speech detected"
    }

    private val transcriber by lazy { VoskTranscriber(applicationContext) }
    private val summarizer by lazy { NoteSummarizer(applicationContext) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val noteId = inputData.getString(KEY_NOTE_ID) ?: return@withContext Result.failure()
        val audioPath = inputData.getString(KEY_AUDIO_PATH) ?: return@withContext Result.failure()

        val audioFile = File(audioPath)
        if (!audioFile.exists()) {
            Log.e(TAG, "Audio file missing, giving up on note $noteId: $audioPath")
            return@withContext Result.failure()
        }

        try {
            val transcript = transcriber.transcribe(audioFile)

            if (transcript.isEmpty()) {
                // Retrying will not conjure speech out of silence, so record the outcome
                // in the title and treat the job as done.
                Log.i(TAG, "No speech recognised in note $noteId")
                updateNote(noteId, title = NO_SPEECH_TITLE)
                return@withContext Result.success()
            }

            val transcriptFile = FileHelper.getTranscriptFile(applicationContext, noteId)
            FileHelper.writeTranscript(transcriptFile, transcript)

            val summary = when (val result = summarizer.summarize(transcript)) {
                is NoteSummarizer.Result.Success -> result.summary
                is NoteSummarizer.Result.Skipped -> {
                    Log.i(TAG, "Summary skipped for note $noteId: ${result.reason}")
                    null
                }
                is NoteSummarizer.Result.Failed -> null
            }

            updateNote(
                noteId = noteId,
                title = generateTitle(transcript),
                transcriptPath = transcriptFile.absolutePath,
                summary = summary
            )

            Result.success()
        } catch (e: VoskTranscriber.ModelNotInstalledException) {
            // A missing model is a build/setup problem; retrying cannot fix it.
            Log.e(TAG, "Vosk model not installed", e)
            Result.failure()
        } catch (e: IOException) {
            Log.w(TAG, "Transcription of note $noteId failed (attempt $runAttemptCount)", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Unrecoverable failure transcribing note $noteId", e)
            Result.failure()
        }
    }

    /**
     * Generates a title from the transcript by taking the first [MAX_TITLE_LENGTH]
     * characters, cut at a word boundary where one is reasonably placed.
     */
    private fun generateTitle(transcript: String): String {
        val trimmed = transcript.trim()
        return if (trimmed.length <= MAX_TITLE_LENGTH) {
            trimmed
        } else {
            val truncated = trimmed.substring(0, MAX_TITLE_LENGTH).trim()
            val lastSpace = truncated.lastIndexOf(' ')
            if (lastSpace > MAX_TITLE_LENGTH / 2) {
                truncated.substring(0, lastSpace) + "..."
            } else {
                truncated + "..."
            }
        }
    }

    /**
     * Applies transcription results to the stored note, leaving fields untouched when
     * the corresponding argument is null.
     *
     * The DAO is resolved directly from [AppDatabase] rather than injected, matching the
     * "simple DI" approach in [com.noter.MainActivity]. A DI framework would let
     * WorkManager construct this worker with its dependencies instead.
     */
    private suspend fun updateNote(
        noteId: String,
        title: String? = null,
        transcriptPath: String? = null,
        summary: String? = null
    ) {
        val noteDao = AppDatabase.getDatabase(applicationContext).noteDao()
        val note = noteDao.getNoteById(noteId)
        if (note == null) {
            Log.w(TAG, "Note $noteId no longer exists; discarding transcription result")
            return
        }

        noteDao.update(
            note.copy(
                title = title ?: note.title,
                transcriptPath = transcriptPath ?: note.transcriptPath,
                summary = summary ?: note.summary
            )
        )
    }
}
