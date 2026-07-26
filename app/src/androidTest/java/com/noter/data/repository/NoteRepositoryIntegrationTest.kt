package com.noter.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.noter.data.db.AppDatabase
import com.noter.data.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteRepositoryIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: NoteRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        repository = NoteRepository(database.noteDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveNote() = runBlocking {
        val note = Note(
            id = "test-1",
            title = "Test Note",
            transcriptPath = "/transcript.txt",
            audioPath = "/audio.m4a",
            summary = null,
            createdAt = System.currentTimeMillis(),
            duration = 60
        )

        repository.insertNote(note)
        val retrieved = repository.getNoteById("test-1")

        assertNotNull(retrieved)
        assertEquals("Test Note", retrieved?.title)
        assertEquals(60, retrieved?.duration)
    }

    @Test
    fun getAllNotesReturnsOrderedByCreatedAt() = runBlocking {
        val note1 = Note("1", "First", "/t1.txt", "/a1.m4a", null, 1000L, 30)
        val note2 = Note("2", "Second", "/t2.txt", "/a2.m4a", null, 2000L, 45)
        val note3 = Note("3", "Third", "/t3.txt", "/a3.m4a", null, 3000L, 60)

        repository.insertNote(note1)
        repository.insertNote(note2)
        repository.insertNote(note3)

        val notes = repository.getAllNotes().first()

        assertEquals(3, notes.size)
        assertEquals("Third", notes[0].title)
        assertEquals("Second", notes[1].title)
        assertEquals("First", notes[2].title)
    }

    @Test
    fun updateNoteModifiesExisting() = runBlocking {
        val note = Note("1", "Original", "/t.txt", "/a.m4a", null, 1000L, 60)
        repository.insertNote(note)

        val updated = note.copy(title = "Updated", summary = "New Summary")
        repository.updateNote(updated)

        val retrieved = repository.getNoteById("1")
        assertEquals("Updated", retrieved?.title)
        assertEquals("New Summary", retrieved?.summary)
    }

    @Test
    fun deleteNoteRemovesFromDatabase() = runBlocking {
        val note = Note("1", "Test", "/t.txt", "/a.m4a", null, 1000L, 60)
        repository.insertNote(note)

        repository.deleteNote(note)

        val retrieved = repository.getNoteById("1")
        assertNull(retrieved)
    }
}
