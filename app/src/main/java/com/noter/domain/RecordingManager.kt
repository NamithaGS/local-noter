package com.noter.domain

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.noter.domain.transcription.PcmAudioDecoder
import com.noter.util.FileHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class RecordingManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    var currentFile: File? = null
        private set
    private var startTime: Long = 0

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState

    private val _elapsedTime = MutableStateFlow(0)
    val elapsedTime: StateFlow<Int> = _elapsedTime

    fun startRecording(noteId: String): Result<File> {
        return try {
            val file = FileHelper.getAudioFile(context, noteId)
            currentFile = file

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                // Recorded to match what the Vosk models expect, so transcription can
                // decode straight to PCM without downmixing or resampling. Speech
                // recognition gains nothing from a higher rate or a second channel.
                setAudioChannels(1)
                setAudioSamplingRate(PcmAudioDecoder.TARGET_SAMPLE_RATE)
                setAudioEncodingBitRate(AUDIO_BIT_RATE)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            startTime = System.currentTimeMillis()
            _recordingState.value = RecordingState.RECORDING

            Result.success(file)
        } catch (e: Exception) {
            _recordingState.value = RecordingState.ERROR
            Result.failure(e)
        }
    }

    fun stopRecording(): Result<Int> {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            _recordingState.value = RecordingState.IDLE
            _elapsedTime.value = 0

            Result.success(duration)
        } catch (e: Exception) {
            _recordingState.value = RecordingState.ERROR
            Result.failure(e)
        }
    }

    fun updateElapsedTime() {
        if (_recordingState.value == RecordingState.RECORDING) {
            _elapsedTime.value = ((System.currentTimeMillis() - startTime) / 1000).toInt()
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            currentFile?.delete()
            currentFile = null

            _recordingState.value = RecordingState.IDLE
            _elapsedTime.value = 0
        } catch (e: Exception) {
            _recordingState.value = RecordingState.ERROR
        }
    }

    enum class RecordingState {
        IDLE, RECORDING, ERROR
    }

    private companion object {
        /** Ample for 16 kHz mono AAC speech; roughly 240 KB per minute. */
        const val AUDIO_BIT_RATE = 32_000
    }
}
