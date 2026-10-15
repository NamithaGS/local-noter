package com.noter.util

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FileLogWriterTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("filelogwriter").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun appendCreatesMissingParentDirectoriesAndWritesLines() {
        val file = File(dir, "nested/deeper/log.txt")
        val writer = FileLogWriter(file)

        writer.append("first")
        writer.append("second")

        assertEquals(listOf("first", "second"), file.readLines())
    }

    @Test
    fun rotatesOnceTheFileExceedsMaxBytesKeepingOnlyOnePreviousFile() {
        val file = File(dir, "log.txt")
        val writer = FileLogWriter(file, maxBytes = 20)

        writer.append("a".repeat(30))   // pushes the file past the limit
        writer.append("after-rotation") // triggers rotation, then lands in a fresh file

        assertEquals(listOf("after-rotation"), file.readLines())
        assertEquals(listOf("a".repeat(30)), File(dir, "log.1.txt").readLines())

        // A second rotation replaces the old rotated file rather than accumulating more:
        // "after-rotation" was still in the live file, so it moves into log.1.txt
        // together with the new line, and the original "aaa..." line is gone for good.
        writer.append("b".repeat(30))
        writer.append("again")
        assertEquals(listOf("again"), file.readLines())
        assertEquals(listOf("after-rotation", "b".repeat(30)), File(dir, "log.1.txt").readLines())
    }

    @Test
    fun appendNeverThrowsWhenTheDirectoryCannotBeCreated() {
        // A regular file where a directory is needed makes mkdirs()/appendText fail.
        val blocker = File(dir, "blocker").apply { writeText("not a directory") }
        val writer = FileLogWriter(File(blocker, "log.txt"))

        writer.append("this must not throw")
    }
}
