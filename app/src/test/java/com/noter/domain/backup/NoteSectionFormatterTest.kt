package com.noter.domain.backup

import com.noter.data.model.Note
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.io.path.createTempFile
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class NoteSectionFormatterTest {

    private var tempTranscriptFile: File? = null

    @After
    fun tearDown() {
        tempTranscriptFile?.delete()
    }

    private fun epochMillisOf(dateTime: LocalDateTime): Long =
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun noteWithTranscript(text: String, summary: String? = null): Note {
        val file = createTempFile(prefix = "transcript", suffix = ".txt").toFile()
        file.writeText(text)
        tempTranscriptFile = file
        return Note(
            id = "note-1",
            title = "Roadmap discussion",
            transcriptPath = file.absolutePath,
            audioPath = "/audio.m4a",
            summary = summary,
            createdAt = epochMillisOf(LocalDateTime.of(2026, 3, 10, 14, 30)),
            duration = 90
        )
    }

    @Test
    fun formatIncludesTitleTimestampDurationAndTranscript() {
        val note = noteWithTranscript("We discussed the roadmap.")

        val section = NoteSectionFormatter.format(note)

        assertTrue(section.contains("Roadmap discussion"))
        assertTrue(section.contains("Tuesday, March 10, 2026 at 2:30 PM"))
        assertTrue(section.contains("Duration: 01:30"))
        assertTrue(section.contains("We discussed the roadmap."))
        assertTrue(section.trim().endsWith("---"))
    }

    @Test
    fun formatOmitsSummaryLineWhenNoSummary() {
        val note = noteWithTranscript("No summary yet.", summary = null)

        val section = NoteSectionFormatter.format(note)

        assertFalse(section.contains("Summary:"))
    }

    @Test
    fun formatIncludesSummaryLineWhenPresent() {
        val note = noteWithTranscript("Full transcript text.", summary = "Short summary.")

        val section = NoteSectionFormatter.format(note)

        assertTrue(section.contains("Summary: Short summary."))
    }

    @Test
    fun formatReportsMissingTranscriptFile() {
        val note = Note(
            id = "note-2",
            title = "No file",
            transcriptPath = "/does/not/exist.txt",
            audioPath = "/audio.m4a",
            summary = null,
            createdAt = epochMillisOf(LocalDateTime.of(2026, 3, 10, 14, 30)),
            duration = 10
        )

        val section = NoteSectionFormatter.format(note)

        assertTrue(section.contains("(transcript file missing)"))
    }

    @Test
    fun formatReportsBlankTranscriptPath() {
        val note = Note(
            id = "note-3",
            title = "No path",
            transcriptPath = "",
            audioPath = "/audio.m4a",
            summary = null,
            createdAt = epochMillisOf(LocalDateTime.of(2026, 3, 10, 14, 30)),
            duration = 10
        )

        val section = NoteSectionFormatter.format(note)

        assertTrue(section.contains("(no transcript)"))
    }

    @Test
    fun formatSummaryEntrySeparatesHeadingFromBody() {
        val note = noteWithTranscript("Full transcript, left out of the summary entry.")

        val entry = NoteSectionFormatter.formatSummaryEntry(note, "Three bullet summary.")

        assertEquals("Tuesday, March 10, 2026 at 2:30 PM", entry.heading)
        assertTrue(entry.body.contains("Roadmap discussion"))
        assertTrue(entry.body.contains("Three bullet summary."))
        assertFalse(entry.body.contains("Full transcript"))
        assertTrue(entry.body.trim().endsWith("---"))
    }
}
