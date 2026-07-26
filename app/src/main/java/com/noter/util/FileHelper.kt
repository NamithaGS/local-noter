package com.noter.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.IOException

object FileHelper {

    private const val AUDIO_DIR = "Noter/audio"
    private const val TRANSCRIPT_DIR = "Noter/transcripts"

    fun getAudioFile(context: Context, noteId: String): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            AUDIO_DIR
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "note_$noteId.m4a")
    }

    fun getTranscriptFile(context: Context, noteId: String): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            TRANSCRIPT_DIR
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "note_$noteId.txt")
    }

    fun writeTranscript(file: File, text: String) {
        try {
            file.writeText(text)
        } catch (e: IOException) {
            throw IOException("Failed to write transcript: ${e.message}")
        }
    }

    fun readTranscript(file: File): String {
        return if (file.exists()) {
            file.readText()
        } else {
            throw IOException("Transcript file not found")
        }
    }

    fun checkStorageSpace(): Boolean {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val usableSpace = dir.usableSpace
        val minRequired = 100 * 1024 * 1024L
        return usableSpace >= minRequired
    }
}
