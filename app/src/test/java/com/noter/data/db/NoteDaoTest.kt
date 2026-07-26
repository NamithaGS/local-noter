package com.noter.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var noteDao: NoteDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        noteDao = database.noteDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveNote() = runBlocking {
        val note = NoteEntity(
            id = "test-id",
            title = "Test Note",
            transcriptPath = "/path/to/transcript.txt",
            audioPath = "/path/to/audio.m4a",
            summary = null,
            createdAt = System.currentTimeMillis(),
            duration = 60
        )

        noteDao.insert(note)
        val retrieved = noteDao.getAllNotes().first()

        assertEquals(1, retrieved.size)
        assertEquals("Test Note", retrieved[0].title)
    }
}
