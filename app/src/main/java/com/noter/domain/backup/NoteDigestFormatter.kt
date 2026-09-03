package com.noter.domain.backup

import com.noter.data.model.Note
import com.noter.util.TimeFormatter
import java.io.File

/**
 * Formats a batch of notes into a single plain-text digest for a Drive backup upload -
 * used for both the automatic daily job and a manual on-demand selection.
 */
object NoteDigestFormatter {

    fun format(title: String, notes: List<Note>): String = buildString {
        appendLine("Noter - $title")
        appendLine("${notes.size} note${if (notes.size == 1) "" else "s"}")
        appendLine()

        notes.forEach { note ->
            appendLine("## ${note.title}")
            appendLine("Duration: ${TimeFormatter.formatDuration(note.duration)}")

            note.summary?.let { summary ->
                appendLine()
                appendLine("Summary:")
                appendLine(summary)
            }

            appendLine()
            appendLine("Transcript:")
            appendLine(readTranscript(note))
            appendLine()
            appendLine("---")
            appendLine()
        }
    }

    private fun readTranscript(note: Note): String {
        if (note.transcriptPath.isBlank()) return "(no transcript)"
        val file = File(note.transcriptPath)
        return if (file.exists()) file.readText() else "(transcript file missing)"
    }
}
