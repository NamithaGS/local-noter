package com.noter.domain.backup

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.noter.data.model.Note
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Files notes into Drive - shared by [DriveBackupWorker] (automatic, date-range based)
 * and the manual on-demand upload (arbitrary selection), so both paths append through
 * the exact same Drive/Docs calls and doc-naming rules instead of two implementations
 * that could quietly drift apart.
 */
class NoteFiler(context: Context, account: GoogleSignInAccount) {

    private val driveService = DriveService(context, account)
    private val classifier = WorkClassifier(context)

    /**
     * Archives [notes] into `AllNotes/<year>/<month>/<date>`, grouped by each note's own
     * creation date - a complete chronological record regardless of Work classification.
     */
    fun archiveNotes(notes: List<Note>) {
        if (notes.isEmpty()) return

        notes.groupBy { note ->
            LocalDate.ofInstant(Instant.ofEpochMilli(note.createdAt), DriveBackupScheduler.BACKUP_ZONE)
        }.forEach { (date, notesForDate) ->
            val allNotesId = driveService.findOrCreateFolder(ALL_NOTES_FOLDER)
            val yearId = driveService.findOrCreateFolder(date.year.toString(), allNotesId)
            val monthId = driveService.findOrCreateFolder(monthFolderName(date), yearId)
            val dayDocId = driveService.findOrCreateDoc(date.toString(), monthId)

            notesForDate.forEach { note ->
                driveService.appendToDoc(dayDocId, NoteSectionFormatter.format(note))
            }
        }
    }

    /**
     * Classifies each of [notes] (manual tag first, on-device AI otherwise - see
     * [WorkClassifier]) and appends Work-classified ones to their topic doc under
     * `Work/<topic>`. Notes classified as not-work are left out of Work entirely, per
     * "there has to be a Work folder only" - nothing else gets filed there.
     */
    suspend fun classifyNotes(notes: List<Note>) {
        if (notes.isEmpty()) return

        var workFolderId: String? = null
        notes.forEach { note ->
            val transcript = readTranscript(note)
            when (val result = classifier.classify(transcript, note.manualTag)) {
                is WorkClassification.Work -> {
                    val folderId = workFolderId
                        ?: driveService.findOrCreateFolder(WORK_FOLDER).also { workFolderId = it }
                    val topicDocId = driveService.findOrCreateDoc(result.topic, folderId)
                    driveService.appendToDoc(topicDocId, NoteSectionFormatter.format(note))
                }
                WorkClassification.NotWork -> Unit
            }
        }
    }

    private fun readTranscript(note: Note): String {
        if (note.transcriptPath.isBlank()) return ""
        val file = File(note.transcriptPath)
        return if (file.exists()) file.readText() else ""
    }

    private fun monthFolderName(date: LocalDate): String {
        val monthName = date.month.getDisplayName(TextStyle.FULL, Locale.US)
        return "%02d-%s".format(date.monthValue, monthName)
    }

    private companion object {
        const val ALL_NOTES_FOLDER = "AllNotes"
        const val WORK_FOLDER = "Work"
    }
}
