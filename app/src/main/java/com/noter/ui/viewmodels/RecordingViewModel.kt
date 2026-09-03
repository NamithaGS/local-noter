package com.noter.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.noter.data.model.Note
import com.noter.data.repository.NoteRepository
import com.noter.domain.RecordingManager
import com.noter.domain.TranscriptionWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class RecordingViewModel(
    private val recordingManager: RecordingManager,
    private val repository: NoteRepository,
    private val workManager: WorkManager
) : ViewModel() {

    val recordingState: StateFlow<RecordingManager.RecordingState> = recordingManager.recordingState
    val elapsedTime: StateFlow<Int> = recordingManager.elapsedTime
    val amplitude: StateFlow<Int> = recordingManager.amplitude

    private var currentNoteId: String? = null

    fun startRecording() {
        val noteId = UUID.randomUUID().toString()
        currentNoteId = noteId

        val result = recordingManager.startRecording(noteId)
        if (result.isSuccess) {
            startTimer()
            startAmplitudeMonitoring()
        }
    }

    fun stopRecording() {
        val result = recordingManager.stopRecording()
        if (result.isSuccess && currentNoteId != null) {
            val duration = result.getOrNull() ?: 0
            val audioPath = recordingManager.currentFile?.absolutePath ?: ""

            viewModelScope.launch {
                val note = Note(
                    id = currentNoteId!!,
                    title = "Recording...",
                    transcriptPath = "",
                    audioPath = audioPath,
                    summary = null,
                    createdAt = System.currentTimeMillis(),
                    duration = duration
                )
                repository.insertNote(note)

                val workRequest = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                    .setInputData(workDataOf(
                        TranscriptionWorker.KEY_NOTE_ID to currentNoteId,
                        TranscriptionWorker.KEY_AUDIO_PATH to audioPath
                    ))
                    .build()
                workManager.enqueue(workRequest)
            }
        }
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (isActive && recordingState.value == RecordingManager.RecordingState.RECORDING) {
                recordingManager.updateElapsedTime()
                delay(1000)
            }
        }
    }

    /**
     * Polls the mic input level on its own faster loop, separate from [startTimer]'s
     * 1s tick - a level graph sampled only once a second would look choppy and could
     * miss short bursts of speech entirely.
     */
    private fun startAmplitudeMonitoring() {
        viewModelScope.launch {
            while (isActive && recordingState.value == RecordingManager.RecordingState.RECORDING) {
                recordingManager.updateAmplitude()
                delay(AMPLITUDE_POLL_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val AMPLITUDE_POLL_INTERVAL_MS = 100L
    }
}
