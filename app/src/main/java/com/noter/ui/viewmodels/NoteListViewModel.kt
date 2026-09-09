package com.noter.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.noter.data.model.Note
import com.noter.data.repository.NoteRepository
import com.noter.domain.backup.BackupStatusStore
import com.noter.domain.backup.DriveAuth
import com.noter.domain.backup.NoteFiler
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

class NoteListViewModel(private val repository: NoteRepository) : ViewModel() {

    val notes: StateFlow<List<Note>> = repository.getAllNotes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedNoteIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNoteIds: StateFlow<Set<String>> = _selectedNoteIds.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()

    private val _lastBackupTime = MutableStateFlow<Long?>(null)
    val lastBackupTime: StateFlow<Long?> = _lastBackupTime.asStateFlow()

    // One-off UI feedback (snackbar text) for a manual upload/backup action - a
    // StateFlow would replay the last message on every recomposition/config change,
    // which is wrong for something that should only ever be shown once.
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
     * Picks up the last-backup timestamp from disk - called once when the screen
     * appears, so a background daily run that completed while the app was closed still
     * shows up without needing a live cross-process observer for a WorkManager job.
     */
    fun refreshLastBackupTime(context: Context) {
        _lastBackupTime.value = BackupStatusStore.getLastBackupTime(context)
    }

    /**
     * Backs up every not-yet-uploaded note right now, instead of waiting for the next
     * 6AM run - the "tap to back up now" action on the Backup and summarize button.
     */
    fun backupNow(context: Context) {
        if (_isBackingUp.value) return

        viewModelScope.launch {
            _isBackingUp.value = true
            val message = withContext(Dispatchers.IO) {
                try {
                    val account = DriveAuth.getSignedInAccount(context)
                        ?: return@withContext "Connect Google Drive first"

                    val pending = notes.value.filterNot { it.uploadedToDrive }
                    if (pending.isNotEmpty()) {
                        fileAndClassify(context, account, pending)
                    }

                    val now = System.currentTimeMillis()
                    BackupStatusStore.setLastBackupTime(context, now)
                    _lastBackupTime.value = now

                    if (pending.isEmpty()) {
                        "Already backed up"
                    } else {
                        "Backed up ${pending.size} note${if (pending.size == 1) "" else "s"}"
                    }
                } catch (e: Exception) {
                    "Backup failed: ${describeError(e)}"
                }
            }
            _isBackingUp.value = false
            _uploadEvents.emit(message)
        }
    }

    /**
     * Files the currently selected notes into Drive right away - archive into
     * AllNotes/<year>/<month>/<date> plus a Work-classification pass, via the same
     * [NoteFiler] the automatic daily job uses - instead of waiting for the next 6AM run.
     */
    fun uploadSelectedNotes(context: Context) {
        val noteIds = _selectedNoteIds.value.toList()
        if (noteIds.isEmpty()) return

        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val account = DriveAuth.getSignedInAccount(context)
                        ?: return@withContext "Connect Google Drive first"

                    // Re-filter here even though the UI already hides uploaded notes
                    // from selection: the DB is the source of truth, and a note could
                    // have been swept up by the daily job in the moments between
                    // rendering the list and tapping Upload.
                    val notes = repository.getNotesByIds(noteIds).filterNot { it.uploadedToDrive }
                    if (notes.isEmpty()) {
                        return@withContext "Selected note(s) were already backed up"
                    }

                    fileAndClassify(context, account, notes)

                    "Filed ${notes.size} note${if (notes.size == 1) "" else "s"} to Drive"
                } catch (e: Exception) {
                    "Upload failed: ${describeError(e)}"
                }
            }

            _uploadEvents.emit(message)
            clearSelection()
        }
    }

    /** Shared by [backupNow] and [uploadSelectedNotes] so both run the exact same filing logic. */
    private suspend fun fileAndClassify(context: Context, account: GoogleSignInAccount, notes: List<Note>) {
        val filer = NoteFiler(context, account)
        filer.archiveNotes(notes)
        repository.markUploaded(notes.map { it.id })

        // Both callers run classification immediately rather than leaving it for the
        // next daily run - the user asked for this now, not tomorrow morning.
        filer.classifyNotes(notes)
        repository.markFiledToWorkDoc(notes.map { it.id })
    }

    /**
     * Turns a caught [Exception] into user-facing text for the snackbar.
     *
     * Many exceptions thrown by the Drive/Docs SDKs (e.g. an IOException from a network
     * failure) carry a null or unhelpful `message`, which is what previously surfaced as
     * a bare "unknown error" - useless for the user and for us when they report it. The
     * full exception (with stack trace) is always logged to logcat first so it can be
     * pulled from a bug report; the class name is used as a fallback label instead of a
     * generic string so at least the *kind* of failure (timeout, auth, I/O, ...) is visible.
     *
     * @param e the exception caught around a backup/upload attempt
     * @return a short human-readable description of [e], suitable for a snackbar
     */
    private fun describeError(e: Exception): String {
        Log.e(TAG, "Drive backup/upload failed", e)
        return e.message ?: e.javaClass.simpleName
    }

    private companion object {
        const val TAG = "NoteListViewModel"
    }
}
