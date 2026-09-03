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

    private val _amplitude = MutableStateFlow(0)
    /** Raw microphone input level, 0..32767 (see [MediaRecorder.getMaxAmplitude]); 0 when idle. */
    val amplitude: StateFlow<Int> = _amplitude

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
            // Setup failed (e.g. RECORD_AUDIO permission not granted, or the mic is
            // already in use), so no MediaRecorder is actually running. Clear currentFile
            // too - otherwise stopRecording()/the caller can be fooled into thinking a
            // real recording exists at this path when it never started.
            mediaRecorder = null
            currentFile = null
            _recordingState.value = RecordingState.ERROR
            Result.failure(e)
        }
    }

    /**
     * Stops the in-progress recording.
     *
     * @return `Result.success` with the recording duration in seconds, or
     *   `Result.failure` if there was no active recording to stop (e.g. [startRecording]
     *   never succeeded) or the underlying [MediaRecorder] failed while stopping.
     */
    fun stopRecording(): Result<Int> {
        val recorder = mediaRecorder
        // Without this guard, a null recorder (startRecording failed, or stopRecording
        // was already called) would fall through to a no-op `?.apply` below and still
        // report Result.success - which previously caused the app to save a "recording"
        // that was never actually captured.
        if (recorder == null || _recordingState.value != RecordingState.RECORDING) {
            _recordingState.value = RecordingState.ERROR
            return Result.failure(IllegalStateException("No active recording to stop"))
        }

        return try {
            recorder.stop()
            recorder.release()
            mediaRecorder = null

            val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            _recordingState.value = RecordingState.IDLE
            _elapsedTime.value = 0
            _amplitude.value = 0

            Result.success(duration)
        } catch (e: Exception) {
            mediaRecorder = null
            _recordingState.value = RecordingState.ERROR
            Result.failure(e)
        }
    }

    fun updateElapsedTime() {
        if (_recordingState.value == RecordingState.RECORDING) {
            _elapsedTime.value = ((System.currentTimeMillis() - startTime) / 1000).toInt()
        }
    }

    /**
     * Samples the current microphone input level into [amplitude].
     *
     * [MediaRecorder.getMaxAmplitude] reports the loudest sample seen since the *last*
     * call and resets internally afterward - it's not a live "current level" read. So
     * this must be polled at a steady interval (see RecordingViewModel's amplitude
     * monitoring loop) rather than called once, or the reported level will be wrong.
     */
    fun updateAmplitude() {
        if (_recordingState.value != RecordingState.RECORDING) return
        _amplitude.value = try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            // Can throw if stop() raced this call and tore down the recorder mid-sample;
            // treat that as "no signal" rather than crashing the polling loop.
            0
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
