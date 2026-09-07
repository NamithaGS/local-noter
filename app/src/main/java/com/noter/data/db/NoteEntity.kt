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
    // this was the only Drive step, before Work-doc filing was added as a second pass)
    val uploadedToDrive: Boolean = false,
    // User-supplied hint for classification - set directly, it's used as-is for both
    // "is this Work?" and the topic doc name, skipping on-device classification entirely.
    val manualTag: String? = null,
    // Whether the Work-classification pass (see DriveBackupWorker) has already run for
    // this note. Separate from uploadedToDrive so archiving and classification can be
    // two independent passes, each idempotent on its own.
    val filedToWorkDoc: Boolean = false
)
