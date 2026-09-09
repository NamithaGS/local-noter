package com.noter.domain.backup

import com.noter.data.model.Note
import com.noter.util.TimeFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats a single note as a dated section appended to an AllNotes archive doc - the same
 * note gets formatted identically regardless of which dated doc it lands in.
 */
object NoteSectionFormatter {

    // Not thread-safe (SimpleDateFormat never is), but NoteFiler only ever calls this
    // from one coroutine at a time per invocation, so a shared instance is fine here.
    private val TIMESTAMP_FORMAT = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.US)

    fun format(note: Note): String = buildString {
        appendLine()
        appendLine(TIMESTAMP_FORMAT.format(Date(note.createdAt)))
        appendLine(note.title)
        appendLine("Duration: ${TimeFormatter.formatDuration(note.duration)}")

        note.summary?.let { summary ->
            appendLine()
            appendLine("Summary: $summary")
        }

        appendLine()
        appendLine(readTranscript(note))
        appendLine()
        appendLine("---")
    }

    /** One entry for a `SummarizedNotes/<tag>` doc: [heading] is styled as a small
     * heading by [DriveService.appendToDocWithHeading]; [body] is plain text below it. */
    data class SummaryEntry(val heading: String, val body: String)

    /**
     * Formats one note's AI summary as a dated entry for a `SummarizedNotes/<tag>` doc -
     * the date/time as the heading (per-entry, since a topic doc holds many notes over
     * time), followed by the note's title and its summary. Deliberately leaves out the
     * full transcript that [format] includes, since the point of this doc is a condensed
     * per-topic view.
     */
    fun formatSummaryEntry(note: Note, summary: String): SummaryEntry {
        val heading = TIMESTAMP_FORMAT.format(Date(note.createdAt))
        val body = buildString {
            appendLine(note.title)
            appendLine()
            appendLine(summary)
            appendLine()
            appendLine("---")
        }
        return SummaryEntry(heading, body)
    }

    private fun readTranscript(note: Note): String {
        if (note.transcriptPath.isBlank()) return "(no transcript)"
        val file = File(note.transcriptPath)
        return if (file.exists()) file.readText() else "(transcript file missing)"
    }
}
