package com.noter.data.repository

import com.noter.data.db.NoteDao
import com.noter.data.db.NoteEntity
import com.noter.data.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepository(private val noteDao: NoteDao) {

    fun getAllNotes(): Flow<List<Note>> {
        return noteDao.getAllNotes().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun getNoteById(id: String): Note? {
        return noteDao.getNoteById(id)?.toDomainModel()
    }

    suspend fun insertNote(note: Note) {
        noteDao.insert(note.toEntity())
    }

    suspend fun updateNote(note: Note) {
        noteDao.update(note.toEntity())
    }

    suspend fun deleteNote(note: Note) {
        noteDao.deleteById(note.id)
    }

    private fun NoteEntity.toDomainModel() = Note(
        id = id,
        title = title,
        transcriptPath = transcriptPath,
        audioPath = audioPath,
        summary = summary,
        createdAt = createdAt,
        duration = duration
    )

    private fun Note.toEntity() = NoteEntity(
        id = id,
        title = title,
        transcriptPath = transcriptPath,
        audioPath = audioPath,
        summary = summary,
        createdAt = createdAt,
        duration = duration
    )
}
