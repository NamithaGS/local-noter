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
 *
 * Everything nests under one `LocalNoter` root folder that this class creates itself.
 * That's deliberate, not incidental: under the `drive.file` OAuth scope, the app can
 * only see and write files/folders it created (or that were explicitly picked via
 * Drive's file picker, which this app doesn't use) - so creating one root folder here
 * is what makes "the app can only touch this one folder tree" a real, Google-enforced
 * guarantee rather than just a promise in this code. A folder the user creates manually
 * in Drive is invisible to the app regardless of OAuth consent, by design.
 */
class NoteFiler(context: Context, account: GoogleSignInAccount) {

    private val driveService = DriveService(context, account)
    private val classifier = WorkClassifier(context)

    // Resolved once per NoteFiler instance (one per backup run) and reused by both
    // archiveNotes and classifyNotes, so AllNotes/ and Work/ always land under the same
    // root instead of each call re-resolving it independently.
    private val rootFolderId: String by lazy { driveService.findOrCreateFolder(ROOT_FOLDER) }

    /**
     * Archives [notes] into `LocalNoter/AllNotes/<year>/<month>/<date>`, grouped by each
     * note's own creation date - a complete chronological record regardless of Work
     * classification.
     */
    fun archiveNotes(notes: List<Note>) {
        if (notes.isEmpty()) return

        val allNotesId = driveService.findOrCreateFolder(ALL_NOTES_FOLDER, rootFolderId)
        notes.groupBy { note ->
            LocalDate.ofInstant(Instant.ofEpochMilli(note.createdAt), DriveBackupScheduler.BACKUP_ZONE)
        }.forEach { (date, notesForDate) ->
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
     * `LocalNoter/Work/<topic>`. Notes classified as not-work are left out of Work
     * entirely, per "there has to be a Work folder only" - nothing else gets filed there.
     */
    suspend fun classifyNotes(notes: List<Note>) {
        if (notes.isEmpty()) return

        var workFolderId: String? = null
        notes.forEach { note ->
            val transcript = readTranscript(note)
            when (val result = classifier.classify(transcript, note.manualTag)) {
                is WorkClassification.Work -> {
                    val folderId = workFolderId
                        ?: driveService.findOrCreateFolder(WORK_FOLDER, rootFolderId).also { workFolderId = it }
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
        const val ROOT_FOLDER = "LocalNoter"
        const val ALL_NOTES_FOLDER = "AllNotes"
        const val WORK_FOLDER = "Work"
    }
}
