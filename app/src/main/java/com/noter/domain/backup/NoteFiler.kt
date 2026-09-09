package com.noter.domain.backup

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.noter.data.model.Note
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

    // Resolved once per NoteFiler instance (one per backup run) and reused by both
    // archiveNotes and summarizeNotes, so AllNotes/ and SummarizedNotes/ always land
    // under the same root instead of each call re-resolving it independently.
    private val rootFolderId: String by lazy { driveService.findOrCreateFolder(ROOT_FOLDER) }

    /**
     * Backs up [notes] into `LocalNoter/AllNotes/<year>/<month>/<date>`, grouped by each
     * note's own creation date - a complete chronological record of every note (full
     * transcript, plus its summary if one exists yet), regardless of topic.
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
     * Files each of [notes] that has both a manual tag and an existing AI summary into
     * `LocalNoter/SummarizedNotes/<tag>` - just the summary, dated with the note's
     * creation time as a small heading, not the full transcript ([archiveNotes] already
     * covers that). The topic doc is created the first time a tag is seen, then reused
     * (found, not recreated) on every later note with that same tag.
     *
     * A note with no tag isn't filed anywhere here - tags are the only topic signal now,
     * no on-device classification guessing one. A note with no summary yet (the common
     * case while Gemini Nano isn't producing one - see [SummarizationConfig]) is simply
     * left out for this pass; there's no separate retry/backfill once one shows up later.
     */
    fun summarizeNotes(notes: List<Note>) {
        if (notes.isEmpty()) return

        var summarizedFolderId: String? = null
        notes.forEach { note ->
            val topic = note.manualTag?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val summary = note.summary ?: return@forEach

            val folderId = summarizedFolderId
                ?: driveService.findOrCreateFolder(SUMMARIZED_NOTES_FOLDER, rootFolderId)
                    .also { summarizedFolderId = it }
            val topicDocId = driveService.findOrCreateDoc(topic, folderId)
            val entry = NoteSectionFormatter.formatSummaryEntry(note, summary)
            driveService.appendToDocWithHeading(topicDocId, entry.heading, entry.body)
        }
    }

    private fun monthFolderName(date: LocalDate): String {
        val monthName = date.month.getDisplayName(TextStyle.FULL, Locale.US)
        return "%02d-%s".format(date.monthValue, monthName)
    }

    private companion object {
        const val ROOT_FOLDER = "LocalNoter"
        const val ALL_NOTES_FOLDER = "AllNotes"
        const val SUMMARIZED_NOTES_FOLDER = "SummarizedNotes"
    }
}
