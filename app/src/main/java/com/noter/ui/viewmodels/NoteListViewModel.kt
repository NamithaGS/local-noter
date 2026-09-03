package com.noter.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noter.data.model.Note
import com.noter.data.repository.NoteRepository
import com.noter.domain.backup.DriveAuth
import com.noter.domain.backup.DriveService
import com.noter.domain.backup.NoteDigestFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NoteListViewModel(private val repository: NoteRepository) : ViewModel() {

    val notes: StateFlow<List<Note>> = repository.getAllNotes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedNoteIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNoteIds: StateFlow<Set<String>> = _selectedNoteIds.asStateFlow()

    // One-off UI feedback (snackbar text) for the manual upload action - a StateFlow
    // would replay the last message on every recomposition/config change, which is
    // wrong for something that should only ever be shown once.
    private val _uploadEvents = MutableSharedFlow<String>()
    val uploadEvents: SharedFlow<String> = _uploadEvents

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun toggleSelection(noteId: String) {
        _selectedNoteIds.value = _selectedNoteIds.value.let { current ->
            if (noteId in current) current - noteId else current + noteId
        }
    }

    fun clearSelection() {
        _selectedNoteIds.value = emptySet()
    }

    /**
     * Uploads the currently selected notes to Drive as a single digest file, then marks
     * them uploaded so the automatic daily job (or a future manual upload) never sends
     * the same note's content again.
     */
    fun uploadSelectedNotes(context: Context) {
        val noteIds = _selectedNoteIds.value.toList()
        if (noteIds.isEmpty()) return

        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val account = DriveAuth.getSignedInAccount(context)
                        ?: return@withContext "Connect Google Drive first (Backup button above)"

                    // Re-filter here even though the UI already hides uploaded notes
                    // from selection: the DB is the source of truth, and a note could
                    // have been swept up by the daily job in the moments between
                    // rendering the list and tapping Upload.
                    val notes = repository.getNotesByIds(noteIds).filterNot { it.uploadedToDrive }
                    if (notes.isEmpty()) {
                        return@withContext "Selected note(s) were already backed up"
                    }

                    val label = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.now())
                    val digest = NoteDigestFormatter.format("Manual upload $label", notes)

                    DriveService(context, account).uploadDigest("Noter Manual Upload $label.txt", digest)
                    repository.markUploaded(notes.map { it.id })

                    "Uploaded ${notes.size} note${if (notes.size == 1) "" else "s"} to Drive"
                } catch (e: Exception) {
                    "Upload failed: ${e.message ?: "unknown error"}"
                }
            }

            _uploadEvents.emit(message)
            clearSelection()
        }
    }
}
