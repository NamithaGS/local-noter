package com.noter.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val transcriptPath: String,
    val audioPath: String,
    val summary: String?,
    val createdAt: Long,
    val duration: Int,
    // Archived into the AllNotes/<year>/<month>/<date> doc yet? (name kept from when
    // this was the only Drive step, before SummarizedNotes filing was added as a second pass)
    val uploadedToDrive: Boolean = false,
    // User-supplied topic - the only source of a note's SummarizedNotes/<tag> doc name;
    // a note with no tag simply doesn't get filed there. No more on-device classification
    // step guessing a topic - tags are a direct, unambiguous signal, and Gemini Nano's
    // reliability issues make it a poor thing to depend on for this.
    val manualTag: String? = null,
    // Whether the summarize-and-file pass (see DriveBackupWorker) has already run for
    // this note. Separate from uploadedToDrive so archiving and summarizing are two
    // independent passes, each idempotent on its own.
    val filedToSummary: Boolean = false
)
