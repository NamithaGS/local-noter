package com.noter.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.common.GenAiException
import com.noter.data.model.Note
import com.noter.data.repository.NoteRepository
import com.noter.domain.summarization.NoteSummarizer
import com.noter.domain.summarization.SummarizationResult
import com.noter.domain.summarization.litertlm.GemmaModelDownloader
import com.noter.domain.summarization.litertlm.HuggingFaceTokenStore
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

    // True when the active summarization backend needs one-time setup (currently:
    // LiteRT-LM's model hasn't been downloaded) before it can run at all - drives whether
    // the UI shows the "set up on-device AI" prompt.
    private val _needsModelSetup = MutableStateFlow(false)
    val needsModelSetup: StateFlow<Boolean> = _needsModelSetup

    // Non-null while the Gemma model download is in progress, 0f..1f. Null the rest of
    // the time, including on completion/failure, so the UI can use "is this non-null" as
    // its "show the progress bar" signal.
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

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
     * device didn't have the active backend ready yet) or to regenerate an existing one.
     */
    fun summarize(context: Context) {
        val currentNote = _note.value ?: return
        val currentTranscript = _transcript.value
        if (currentTranscript.isBlank()) {
            viewModelScope.launch { _summaryEvents.emit("No transcript to summarize yet") }
            return
        }
        viewModelScope.launch { runSummarize(context, currentNote, currentTranscript) }
    }

    /** Dismisses the "set up on-device AI" prompt without starting a download. */
    fun dismissModelSetup() {
        _needsModelSetup.value = false
    }

    /**
     * Saves [token], downloads the LiteRT-LM backend's model, and - once that succeeds -
     * runs summarization immediately, so setup and the summary the user actually asked
     * for happen as one action instead of requiring a second tap on Summarize.
     */
    fun downloadModelAndSummarize(context: Context, token: String) {
        if (token.isBlank()) {
            viewModelScope.launch { _summaryEvents.emit("Enter a Hugging Face token first") }
            return
        }
        val currentNote = _note.value ?: return
        val currentTranscript = _transcript.value
        _needsModelSetup.value = false

        viewModelScope.launch {
            HuggingFaceTokenStore.setToken(context, token)
            _downloadProgress.value = 0f
            try {
                withContext(Dispatchers.IO) {
                    GemmaModelDownloader.download(context, token) { progress ->
                        _downloadProgress.value = progress
                    }
                }
                _downloadProgress.value = null
                runSummarize(context, currentNote, currentTranscript)
            } catch (e: Exception) {
                _downloadProgress.value = null
                _summaryEvents.emit("Model download failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun runSummarize(context: Context, currentNote: Note, currentTranscript: String) {
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
                // The error code (e.g. 15 = RESPONSE_GENERATION_ERROR) is Gemini Nano's
                // own generic bucket for "couldn't produce a response" - it covers more
                // than just safety-classifier rejections, so surfacing the number lets a
                // recurring failure be told apart from a one-off without logcat access.
                // Only meaningful for the Gemini Nano backend; absent for LiteRT-LM.
                val suffix = (cause as? GenAiException)?.errorCode?.let { " (code $it)" } ?: ""
                _summaryEvents.emit("Summarization failed: $description$suffix")
            }
            SummarizationResult.NeedsSetup -> _needsModelSetup.value = true
        }
        _isSummarizing.value = false
    }
}
