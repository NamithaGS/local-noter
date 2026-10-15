package com.noter.util

import android.os.Environment
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * App-wide diagnostic log: every line goes to logcat *and* to a rotating file at
 * `Documents/Noter/logs/noter-log.txt`.
 *
 * The file is the point. Logcat needs a USB connection and its buffer is small and
 * volatile, so a failure that happens in a background job during a half-hour meeting is
 * gone long before anyone can look. The file survives, sits next to the recordings and
 * transcripts, and can be grabbed with any file manager.
 *
 * Deliberately records *metrics, never transcript text or audio* - timings, sizes, audio
 * levels, counts - so the log is safe to share. The transcripts themselves already live
 * in `Documents/Noter/transcripts`.
 *
 * Every call is wrapped so logging can never throw into the code being diagnosed.
 */
object DiagnosticLog {

    private val writer: FileLogWriter by lazy {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "Noter/logs"
        )
        FileLogWriter(File(dir, "noter-log.txt"))
    }

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun i(tag: String, message: String) = log('I', tag, message, null) { Log.i(tag, message) }

    fun w(tag: String, message: String, error: Throwable? = null) =
        log('W', tag, message, error) { Log.w(tag, message, error) }

    fun e(tag: String, message: String, error: Throwable? = null) =
        log('E', tag, message, error) { Log.e(tag, message, error) }

    private inline fun log(
        level: Char,
        tag: String,
        message: String,
        error: Throwable?,
        toLogcat: () -> Unit
    ) {
        runCatching { toLogcat() }
        runCatching {
            val timestamp = LocalDateTime.now().format(timestampFormat)
            val line = buildString {
                append(timestamp).append(' ').append(level).append('/').append(tag)
                append(": ").append(message)
                if (error != null) append(" | ").append(error.javaClass.name)
                    .append(": ").append(error.message)
            }
            writer.append(line)
        }
    }
}
