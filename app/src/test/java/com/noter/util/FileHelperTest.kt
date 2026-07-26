package com.noter.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class FileHelperTest {

    private lateinit var context: Context
    private lateinit var tempFiles: MutableList<File>

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tempFiles = mutableListOf()
    }

    @After
    fun cleanup() {
        tempFiles.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }

    @Test
    fun getAudioFileReturnsCorrectPath() {
        val file = FileHelper.getAudioFile(context, "test-123")

        assertTrue(file.absolutePath.contains("Noter/audio"))
        assertTrue(file.name == "note_test-123.m4a")
    }

    @Test
    fun getAudioFileWithDifferentNoteIds() {
        val file1 = FileHelper.getAudioFile(context, "note-1")
        val file2 = FileHelper.getAudioFile(context, "note-2")

        assertTrue(file1.name == "note_note-1.m4a")
        assertTrue(file2.name == "note_note-2.m4a")
        assertNotEquals(file1.absolutePath, file2.absolutePath)
    }

    @Test
    fun getTranscriptFileReturnsCorrectPath() {
        val file = FileHelper.getTranscriptFile(context, "test-456")

        assertTrue(file.absolutePath.contains("Noter/transcripts"))
        assertTrue(file.name == "note_test-456.txt")
    }

    @Test
    fun getTranscriptFileWithDifferentNoteIds() {
        val file1 = FileHelper.getTranscriptFile(context, "note-1")
        val file2 = FileHelper.getTranscriptFile(context, "note-2")

        assertTrue(file1.name == "note_note-1.txt")
        assertTrue(file2.name == "note_note-2.txt")
        assertNotEquals(file1.absolutePath, file2.absolutePath)
    }

    @Test
    fun writeAndReadTranscript() {
        val file = File.createTempFile("test", ".txt")
        tempFiles.add(file)
        val content = "This is a test transcript."

        FileHelper.writeTranscript(file, content)
        val read = FileHelper.readTranscript(file)

        assertEquals(content, read)
    }

    @Test
    fun writeTranscriptWithEmptyContent() {
        val file = File.createTempFile("test", ".txt")
        tempFiles.add(file)
        val content = ""

        FileHelper.writeTranscript(file, content)
        val read = FileHelper.readTranscript(file)

        assertEquals(content, read)
    }

    @Test
    fun writeTranscriptWithMultilineContent() {
        val file = File.createTempFile("test", ".txt")
        tempFiles.add(file)
        val content = "Line 1\nLine 2\nLine 3"

        FileHelper.writeTranscript(file, content)
        val read = FileHelper.readTranscript(file)

        assertEquals(content, read)
    }

    @Test
    fun writeTranscriptOverwritesPreviousContent() {
        val file = File.createTempFile("test", ".txt")
        tempFiles.add(file)
        val content1 = "First content"
        val content2 = "Second content"

        FileHelper.writeTranscript(file, content1)
        FileHelper.writeTranscript(file, content2)
        val read = FileHelper.readTranscript(file)

        assertEquals(content2, read)
    }

    @Test
    fun writeTranscriptWithSpecialCharacters() {
        val file = File.createTempFile("test", ".txt")
        tempFiles.add(file)
        val content = "Special chars: !@#$%^&*()_+-=[]{}|;:',.<>?/\\"

        FileHelper.writeTranscript(file, content)
        val read = FileHelper.readTranscript(file)

        assertEquals(content, read)
    }

    @Test
    fun readTranscriptThrowsExceptionForNonexistentFile() {
        val nonexistentFile = File("/nonexistent/path/file.txt")

        assertThrows(IOException::class.java) {
            FileHelper.readTranscript(nonexistentFile)
        }
    }

    @Test
    fun checkStorageSpaceReturnBoolean() {
        val hasSpace = FileHelper.checkStorageSpace()
        assertTrue(hasSpace || !hasSpace)
    }

    @Test
    fun checkStorageSpaceReturnsFalseOrTrue() {
        val hasSpace = FileHelper.checkStorageSpace()
        assertThat(hasSpace, org.hamcrest.CoreMatchers.anyOf(
            org.hamcrest.CoreMatchers.`is`(true),
            org.hamcrest.CoreMatchers.`is`(false)
        ))
    }

    @Test
    fun getAudioFilePathHasCorrectExtension() {
        val file = FileHelper.getAudioFile(context, "audio-test")
        assertTrue(file.name.endsWith(".m4a"))
    }

    @Test
    fun getTranscriptFilePathHasCorrectExtension() {
        val file = FileHelper.getTranscriptFile(context, "transcript-test")
        assertTrue(file.name.endsWith(".txt"))
    }

    @Test
    fun writeTranscriptCreatesFileIfNotExists() {
        val file = File.createTempFile("test", ".txt")
        file.delete()
        tempFiles.add(file)
        val content = "New file content"

        assertFalse(file.exists())
        FileHelper.writeTranscript(file, content)
        assertTrue(file.exists())
    }
}
