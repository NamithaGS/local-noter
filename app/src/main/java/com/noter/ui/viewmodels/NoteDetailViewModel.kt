package com.noter.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noter.data.model.Note
import com.noter.data.repository.NoteRepository
import com.noter.util.FileHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class NoteDetailViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

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

    fun regenerateSummary() {
        // TODO: Implement Gemini Nano summarization
        // Placeholder for now
    }
}
