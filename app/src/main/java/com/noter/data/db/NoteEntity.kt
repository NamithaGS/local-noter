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
    val duration: Int
)
