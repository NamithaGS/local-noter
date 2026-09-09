package com.noter.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.common.GenAiException
import com.noter.data.model.Note
import com.noter.data.repository.NoteRepository
import com.noter.domain.summarization.NoteSummarizer
import com.noter.domain.summarization.SummarizationResult
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

    // One-off UI feedback (snackbar text) for a tag save, or a failed/skipped
    // summarization attempt - a StateFlow would replay the last message on every
    // recomposition, which is wrong for something that should only ever be shown once.
    private val _summaryEvents = MutableSharedFlow<String>()
    val summaryEvents: SharedFlow<String> = _summaryEvents

    fun loadNote(noteId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val loadedNote = repository.getNoteById(noteId)
            _note.value = loadedNote

            // Always assign, even to "" - previously this only assigned inside the
            // `exists()` branch, so a note with no transcript file (e.g. its title is
            // still the "No speech detected" placeholder) kept showing whatever the
            // *previously viewed* note's transcript was instead of clearing it.
            _transcript.value = loadedNote?.let {
                val transcriptFile = File(it.transcriptPath)
                if (transcriptFile.exists()) FileHelper.readTranscript(transcriptFile) else ""
            } ?: ""

            _isLoading.value = false
        }
    }

    /**
     * Sets (or clears, if [tag] is blank) this note's topic - see
     * [com.noter.domain.backup.NoteFiler.summarizeNotes]. This is the only signal used to
     * route a note's summary to a `SummarizedNotes/<tag>` doc; a note with no tag isn't
     * filed there at all.
     */
    fun updateTag(noteId: String, tag: String) {
        viewModelScope.launch {
            val normalized = tag.trim().ifEmpty { null }
            repository.updateTag(noteId, normalized)
            _note.value = _note.value?.copy(manualTag = normalized)
            // The save was already working - it just gave no feedback, so tapping it
            // looked like nothing happened even though the tag persisted correctly.
            _summaryEvents.emit(if (normalized != null) "Tag saved" else "Tag cleared")
        }
    }

    /**
     * Runs (or re-runs) on-device summarization for the loaded note via the same
     * [NoteSummarizer] the background transcription pipeline uses - this is the manual
     * "Summarize" button's action, for a note that never got one automatically (e.g. the
     * device didn't have the active backend ready yet) or to regenerate an existing one.
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
                is SummarizationResult.Success -> {
                    val updated = currentNote.copy(summary = result.summary)
                    repository.updateNote(updated)
                    _note.value = updated
                }
                is SummarizationResult.Skipped ->
                    _summaryEvents.emit("Couldn't summarize: ${result.reason}")
                is SummarizationResult.Failed -> {
                    val cause = result.cause
                    val description = cause.message ?: cause.javaClass.simpleName
                    // The error code (e.g. 15 = RESPONSE_GENERATION_ERROR) is Gemini
                    // Nano's own generic bucket for "couldn't produce a response" - it
                    // covers more than just safety-classifier rejections, so surfacing
                    // the number lets a recurring failure be told apart from a one-off
                    // without logcat access. Only meaningful for the Gemini Nano
                    // backend; absent for LiteRT-LM.
                    val suffix = (cause as? GenAiException)?.errorCode?.let { " (code $it)" } ?: ""
                    _summaryEvents.emit("Summarization failed: $description$suffix")
                }
                // Setup now lives behind the note list's overflow menu (Setup item)
                // instead of popping a dialog here - downloading a ~560MB file should
                // only ever happen when deliberately asked for, not as a side effect of
                // tapping Summarize on some arbitrary note.
                SummarizationResult.NeedsSetup ->
                    _summaryEvents.emit("On-device AI isn't set up yet - use Setup in the ⋮ menu on the home screen")
            }
            _isSummarizing.value = false
        }
    }
}
