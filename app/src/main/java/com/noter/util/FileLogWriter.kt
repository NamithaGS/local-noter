package com.noter.util

import java.io.File

/**
 * Appends lines to a plain-text log file, rotating it once it grows past [maxBytes].
 *
 * Kept free of Android APIs so it can be unit tested on the JVM; [DiagnosticLog] is the
 * thin Android-aware wrapper that decides where the file lives.
 *
 * Rotation keeps exactly one previous file (`name.1.ext`), so disk use is bounded at
 * roughly twice [maxBytes] no matter how long the app runs.
 */
class FileLogWriter(
    private val file: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES
) {
    private val lock = Any()

    private val rotatedFile: File
        get() = File(file.parentFile, "${file.nameWithoutExtension}.1.${file.extension}")

    /**
     * Appends [line] (a newline is added). Never throws: a log that can crash the app it
     * is meant to diagnose - storage unavailable, permission revoked, disk full - is worse
     * than a missing log line.
     */
    fun append(line: String) {
        synchronized(lock) {
            try {
                file.parentFile?.mkdirs()
                if (file.exists() && file.length() > maxBytes) {
                    rotatedFile.delete()
                    file.renameTo(rotatedFile)
                }
                file.appendText(line + "\n")
            } catch (_: Exception) {
                // Intentionally swallowed - see the doc comment above.
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 1_000_000L
    }
}
