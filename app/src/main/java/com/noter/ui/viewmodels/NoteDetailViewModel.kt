package com.noter.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noter.data.model.Note
import com.noter.data.repository.NoteRepository
import com.noter.domain.summarization.NoteSummarizer
import com.noter.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class NoteDetailViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing

    // One-off UI feedback (snackbar text) for a failed/skipped summarization attempt -
    // a StateFlow would replay the last message on every recomposition, which is wrong
    // for something that should only ever be shown once.
    private val _summaryEvents = MutableSharedFlow<String>()
    val summaryEvents: SharedFlow<String> = _summaryEvents

    fun loadNote(noteId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val loadedNote = repository.getNoteById(noteId)
            _note.value = loadedNote

            loadedNote?.let {
                val transcriptFile = File(it.transcriptPath)
                if (transcriptFile.exists()) {
                    _transcript.value = FileHelper.readTranscript(transcriptFile)
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * Sets (or clears, if [tag] is blank) the manual classification hint for this note -
     * see [com.noter.domain.backup.WorkClassifier]. A tag always wins over on-device AI
     * classification, so this is the reliable way to route a note to a specific Work
     * topic doc instead of leaving it to the model's best guess.
     */
    fun updateTag(noteId: String, tag: String) {
        viewModelScope.launch {
            val normalized = tag.trim().ifEmpty { null }
            repository.updateTag(noteId, normalized)
            _note.value = _note.value?.copy(manualTag = normalized)
        }
    }

    /**
     * Runs (or re-runs) on-device summarization for the loaded note via the same
     * [NoteSummarizer] the background transcription pipeline uses - this is the manual
     * "Summarize" button's action, for a note that never got one automatically (e.g. the
     * device didn't have AICore ready yet) or to regenerate an existing one.
     */
    fun summarize(context: Context) {
        val currentNote = _note.value ?: return
        val currentTranscript = _transcript.value
        if (currentTranscript.isBlank()) {
            viewModelScope.launch { _summaryEvents.emit("No transcript to summarize yet") }
            return
        }

        viewModelScope.launch {
            _isSummarizing.value = true
            when (val result = withContext(Dispatchers.IO) {
                NoteSummarizer(context).summarize(currentTranscript)
            }) {
                is NoteSummarizer.Result.Success -> {
                    val updated = currentNote.copy(summary = result.summary)
                    repository.updateNote(updated)
                    _note.value = updated
                }
                is NoteSummarizer.Result.Skipped ->
                    _summaryEvents.emit("Couldn't summarize: ${result.reason}")
                is NoteSummarizer.Result.Failed ->
                    _summaryEvents.emit("Summarization failed: ${result.cause.message ?: "unknown error"}")
            }
            _isSummarizing.value = false
        }
    }
}
