# Noter UI Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Jetpack Compose UI layer for Noter app - ViewModels, Screens, Navigation, and MainActivity.

**Architecture:** MVVM with Jetpack Compose. ViewModels manage state, Screens are stateless composables, Navigation handles routing.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose

**Prerequisites:** Foundation complete (Tasks 1-7 from main plan)

---

## Task 9: ViewModels - State Management

**Files:**
- Create: `app/src/main/java/com/noter/ui/viewmodels/NoteListViewModel.kt`
- Create: `app/src/main/java/com/noter/ui/viewmodels/RecordingViewModel.kt`
- Create: `app/src/main/java/com/noter/ui/viewmodels/NoteDetailViewModel.kt`

- [ ] **Step 1: Implement NoteListViewModel**

```kotlin
package com.noter.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noter.data.model.Note
import com.noter.data.repository.NoteRepository
import kotlinx.coroutines.flow.SFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteListViewModel(private val repository: NoteRepository) : ViewModel() {
    
    val notes: StateFlow<List<Note>> = repository.getAllNotes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }
}
```

- [ ] **Step 2: Implement RecordingViewModel**

```kotlin
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
    
    private var currentNoteId: String? = null
    
    fun startRecording() {
        val noteId = UUID.randomUUID().toString()
        currentNoteId = noteId
        
        val result = recordingManager.startRecording(noteId)
        if (result.isSuccess) {
            startTimer()
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
                        "noteId" to currentNoteId,
                        "audioPath" to audioPath
                    ))
                    .build()
                workManager.enqueue(workRequest)
            }
        }
    }
    
    fun cancelRecording() {
        recordingManager.cancelRecording()
    }
    
    private fun startTimer() {
        viewModelScope.launch {
            while (isActive && recordingState.value == RecordingManager.RecordingState.RECORDING) {
                recordingManager.updateElapsedTime()
                delay(1000)
            }
        }
    }
}
```

- [ ] **Step 3: Implement NoteDetailViewModel**

```kotlin
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
```

- [ ] **Step 4: Commit ViewModels**

```bash
git add app/src/main/java/com/noter/ui/viewmodels/
git commit -m "feat: add ViewModels for note list, recording, and detail screens"
```

---

## Task 10: UI Screens - Compose UI

**Files:**
- Create: `app/src/main/java/com/noter/ui/screens/NoteListScreen.kt`
- Create: `app/src/main/java/com/noter/ui/screens/RecordingScreen.kt`
- Create: `app/src/main/java/com/noter/ui/screens/NoteDetailScreen.kt`

- [ ] **Step 1: Implement NoteListScreen**

```kotlin
package com.noter.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noter.data.model.Note
import com.noter.ui.theme.CardBackground
import com.noter.ui.theme.RecordRed
import com.noter.ui.theme.TextSecondary
import com.noter.ui.viewmodels.NoteListViewModel
import com.noter.util.TimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NoteListViewModel,
    onRecordClick: () -> Unit,
    onNoteClick: (String) -> Unit
) {
    val notes by viewModel.notes.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Noter") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            RecordButton(onClick = onRecordClick)
            
            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No notes yet. Tap above to record.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                Text(
                    "NOTES LIST",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn {
                    items(notes) { note ->
                        NoteItem(note = note, onClick = { onNoteClick(note.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordButton(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = RecordRed
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("●", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Start Recording", style = MaterialTheme.typography.titleMedium)
                Text("Tap the button to begin", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun NoteItem(note: Note, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(note.title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            TimeFormatter.formatRelativeTime(note.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Divider(modifier = Modifier.padding(top = 12.dp))
    }
}
```

- [ ] **Step 2: Implement RecordingScreen (simplified version)**

```kotlin
package com.noter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noter.ui.theme.RecordRed
import com.noter.ui.viewmodels.RecordingViewModel
import com.noter.util.TimeFormatter

@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel,
    onStopClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val elapsedTime by viewModel.elapsedTime.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            TimeFormatter.formatDuration(elapsedTime),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = RecordRed
        ) {}
        
        Spacer(modifier = Modifier.height(64.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onCancelClick) {
                Text("Cancel")
            }
            Button(onClick = onStopClick) {
                Text("Stop")
            }
        }
    }
}
```

- [ ] **Step 3: Implement NoteDetailScreen (simplified version)**

```kotlin
package com.noter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noter.ui.viewmodels.NoteDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: String,
    viewModel: NoteDetailViewModel,
    onBackClick: () -> Unit
) {
    val note by viewModel.note.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    
    LaunchedEffect(noteId) {
        viewModel.loadNote(noteId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(note?.title ?: "Note") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("FULL TRANSCRIPT", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(transcript, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
```

- [ ] **Step 4: Commit Screens**

```bash
git add app/src/main/java/com/noter/ui/screens/
git commit -m "feat: add Compose UI screens for note list, recording, and detail"
```

---

## Task 11: Navigation and MainActivity

**Files:**
- Create: `app/src/main/java/com/noter/ui/navigation/NavGraph.kt`
- Create: `app/src/main/java/com/noter/MainActivity.kt`

- [ ] **Step 1: Implement NavGraph**

```kotlin
package com.noter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.noter.ui.screens.NoteDetailScreen
import com.noter.ui.screens.NoteListScreen
import com.noter.ui.screens.RecordingScreen
import com.noter.ui.viewmodels.NoteDetailViewModel
import com.noter.ui.viewmodels.NoteListViewModel
import com.noter.ui.viewmodels.RecordingViewModel

sealed class Screen(val route: String) {
    object NoteList : Screen("note_list")
    object Recording : Screen("recording")
    object NoteDetail : Screen("note_detail/{noteId}") {
        fun createRoute(noteId: String) = "note_detail/$noteId"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    noteListViewModel: NoteListViewModel,
    recordingViewModel: RecordingViewModel,
    noteDetailViewModel: NoteDetailViewModel
) {
    NavHost(navController = navController, startDestination = Screen.NoteList.route) {
        composable(Screen.NoteList.route) {
            NoteListScreen(
                viewModel = noteListViewModel,
                onRecordClick = { navController.navigate(Screen.Recording.route) },
                onNoteClick = { noteId -> navController.navigate(Screen.NoteDetail.createRoute(noteId)) }
            )
        }
        
        composable(Screen.Recording.route) {
            RecordingScreen(
                viewModel = recordingViewModel,
                onStopClick = {
                    recordingViewModel.stopRecording()
                    navController.popBackStack()
                },
                onCancelClick = {
                    recordingViewModel.cancelRecording()
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.NoteDetail.route) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
            NoteDetailScreen(
                noteId = noteId,
                viewModel = noteDetailViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
```

- [ ] **Step 2: Implement MainActivity**

```kotlin
package com.noter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkManager
import com.noter.data.db.AppDatabase
import com.noter.data.repository.NoteRepository
import com.noter.domain.RecordingManager
import com.noter.ui.navigation.NavGraph
import com.noter.ui.theme.NoterTheme
import com.noter.ui.viewmodels.NoteDetailViewModel
import com.noter.ui.viewmodels.NoteListViewModel
import com.noter.ui.viewmodels.RecordingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NoterTheme {
                NoterApp()
            }
        }
    }
}

@Composable
fun NoterApp() {
    val navController = rememberNavController()
    
    // Simple DI - in production use Hilt
    val database = AppDatabase.getDatabase(LocalContext.current)
    val repository = NoteRepository(database.noteDao())
    val recordingManager = RecordingManager(LocalContext.current)
    val workManager = WorkManager.getInstance(LocalContext.current)
    
    val noteListViewModel: NoteListViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return NoteListViewModel(repository) as T
            }
        }
    )
    
    val recordingViewModel: RecordingViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return RecordingViewModel(recordingManager, repository, workManager) as T
            }
        }
    )
    
    val noteDetailViewModel: NoteDetailViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return NoteDetailViewModel(repository) as T
            }
        }
    )
    
    NavGraph(
        navController = navController,
        noteListViewModel = noteListViewModel,
        recordingViewModel = recordingViewModel,
        noteDetailViewModel = noteDetailViewModel
    )
}
```

- [ ] **Step 3: Commit Navigation and MainActivity**

```bash
git add app/src/main/java/com/noter/ui/navigation/
git add app/src/main/java/com/noter/MainActivity.kt
git commit -m "feat: add navigation graph and MainActivity with manual DI"
```

---

## Task 12: Final Integration and Testing

- [ ] **Step 1: Update RecordingManager to expose currentFile**

Add public getter to RecordingManager:
```kotlin
var currentFile: File? = null
    private set
```

- [ ] **Step 2: Add missing imports to MainActivity**

```kotlin
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
```

- [ ] **Step 3: Build and test**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "chore: final integration fixes and build verification"
```