package com.noter.data.repository

import com.noter.data.db.NoteDao
import com.noter.data.db.NoteEntity
import com.noter.data.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class NoteRepositoryTest {
    @Mock
    private lateinit var noteDao: NoteDao

    private lateinit var repository: NoteRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = NoteRepository(noteDao)
    }

    @Test
    fun getAllNotesConvertsEntitiesToDomainModels() = runBlocking {
        val entity = NoteEntity(
            id = "1",
            title = "Test",
            transcriptPath = "/transcript.txt",
            audioPath = "/audio.m4a",
            summary = "Summary",
            createdAt = 1000L,
            duration = 60
        )
        `when`(noteDao.getAllNotes()).thenReturn(flowOf(listOf(entity)))

        val notes = repository.getAllNotes().first()

        assertEquals(1, notes.size)
        assertEquals("Test", notes[0].title)
        assertEquals("Summary", notes[0].summary)
    }
}
