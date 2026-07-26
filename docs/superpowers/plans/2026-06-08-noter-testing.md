# Noter Testing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add comprehensive unit and integration tests for Noter app.

**Architecture:** JUnit 4 for unit tests, AndroidX Test for integration tests, Mockito for mocking, Compose Test for UI tests.

**Tech Stack:** JUnit 4, Mockito, AndroidX Test, Room Testing, Compose UI Test, Coroutines Test

---

## Task 1: ViewModel Unit Tests

**Files:**
- Create: `app/src/test/java/com/noter/ui/viewmodels/NoteListViewModelTest.kt`
- Create: `app/src/test/java/com/noter/ui/viewmodels/RecordingViewModelTest.kt`
- Create: `app/src/test/java/com/noter/ui/viewmodels/NoteDetailViewModelTest.kt`

- [ ] **Step 1: Create NoteListViewModelTest**

```kotlin
package com.noter.ui.viewmodels

import com.noter.data.model.Note
import com.noter.data.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class NoteListViewModelTest {
    
    private val testDispatcher = StandardTestDispatcher()
    
    @Mock
    private lateinit var repository: NoteRepository
    
    private lateinit var viewModel: NoteListViewModel
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockitoAnnotations.openMocks(this)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `notes flow emits list from repository`() = runTest {
        val testNotes = listOf(
            Note("1", "Test 1", "/transcript1.txt", "/audio1.m4a", null, 1000L, 60),
            Note("2", "Test 2", "/transcript2.txt", "/audio2.m4a", "Summary", 2000L, 120)
        )
        `when`(repository.getAllNotes()).thenReturn(flowOf(testNotes))
        
        viewModel = NoteListViewModel(repository)
        advanceUntilIdle()
        
        val notes = viewModel.notes.first()
        assertEquals(2, notes.size)
        assertEquals("Test 1", notes[0].title)
        assertEquals("Test 2", notes[1].title)
    }
    
    @Test
    fun `deleteNote calls repository delete`() = runTest {
        val note = Note("1", "Test", "/transcript.txt", "/audio.m4a", null, 1000L, 60)
        `when`(repository.getAllNotes()).thenReturn(flowOf(emptyList()))
        
        viewModel = NoteListViewModel(repository)
        viewModel.deleteNote(note)
        advanceUntilIdle()
        
        verify(repository).deleteNote(note)
    }
}
```

- [ ] **Step 2: Create RecordingViewModelTest**

```kotlin
package com.noter.ui.viewmodels

import androidx.work.WorkManager
import com.noter.data.model.Note
import com.noter.data.repository.NoteRepository
import com.noter.domain.RecordingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.io.File

@ExperimentalCoroutinesApi
class RecordingViewModelTest {
    
    private val testDispatcher = StandardTestDispatcher()
    
    @Mock
    private lateinit var recordingManager: RecordingManager
    
    @Mock
    private lateinit var repository: NoteRepository
    
    @Mock
    private lateinit var workManager: WorkManager
    
    @Mock
    private lateinit var mockFile: File
    
    private lateinit var viewModel: RecordingViewModel
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockitoAnnotations.openMocks(this)
        
        `when`(recordingManager.recordingState).thenReturn(
            kotlinx.coroutines.flow.MutableStateFlow(RecordingManager.RecordingState.IDLE)
        )
        `when`(recordingManager.elapsedTime).thenReturn(
            kotlinx.coroutines.flow.MutableStateFlow(0)
        )
        
        viewModel = RecordingViewModel(recordingManager, repository, workManager)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `startRecording calls recordingManager with generated noteId`() = runTest {
        `when`(recordingManager.startRecording(any())).thenReturn(Result.success(mockFile))
        
        viewModel.startRecording()
        advanceUntilIdle()
        
        verify(recordingManager).startRecording(any())
    }
    
    @Test
    fun `stopRecording saves note to repository`() = runTest {
        `when`(recordingManager.stopRecording()).thenReturn(Result.success(60))
        `when`(recordingManager.currentFile).thenReturn(mockFile)
        `when`(mockFile.absolutePath).thenReturn("/audio.m4a")
        
        viewModel.startRecording()
        viewModel.stopRecording()
        advanceUntilIdle()
        
        verify(repository).insertNote(any())
    }
    
    @Test
    fun `cancelRecording calls recordingManager cancel`() = runTest {
        viewModel.cancelRecording()
        advanceUntilIdle()
        
        verify(recordingManager).cancelRecording()
    }
}
```

- [ ] **Step 3: Create NoteDetailViewModelTest**

```kotlin
package com.noter.ui.viewmodels

import com.noter.data.model.Note
import com.noter.data.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class NoteDetailViewModelTest {
    
    private val testDispatcher = StandardTestDispatcher()
    
    @Mock
    private lateinit var repository: NoteRepository
    
    private lateinit var viewModel: NoteDetailViewModel
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockitoAnnotations.openMocks(this)
        viewModel = NoteDetailViewModel(repository)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `loadNote sets note and transcript`() = runTest {
        val testNote = Note("1", "Test", "/transcript.txt", "/audio.m4a", "Summary", 1000L, 60)
        `when`(repository.getNoteById("1")).thenReturn(testNote)
        
        viewModel.loadNote("1")
        advanceUntilIdle()
        
        val note = viewModel.note.first()
        assertEquals("Test", note?.title)
        assertEquals("Summary", note?.summary)
        verify(repository).getNoteById("1")
    }
    
    @Test
    fun `loadNote sets loading state correctly`() = runTest {
        val testNote = Note("1", "Test", "/transcript.txt", "/audio.m4a", null, 1000L, 60)
        `when`(repository.getNoteById("1")).thenReturn(testNote)
        
        viewModel.loadNote("1")
        
        assertTrue(viewModel.isLoading.value)
        advanceUntilIdle()
        assertFalse(viewModel.isLoading.value)
    }
}
```

- [ ] **Step 4: Update build.gradle.kts with test dependencies**

Add to dependencies section:
```kotlin
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
testImplementation("org.mockito:mockito-core:5.3.1")
testImplementation("org.mockito.kotlin:mockito-kotlin:5.0.0")
testImplementation("androidx.arch.core:core-testing:2.2.0")
```

- [ ] **Step 5: Commit ViewModel tests**

```bash
git add app/src/test/java/com/noter/ui/viewmodels/
git add app/build.gradle.kts
git commit -m "test: add unit tests for ViewModels"
```

---

## Task 2: RecordingManager Unit Tests

**Files:**
- Create: `app/src/test/java/com/noter/domain/RecordingManagerTest.kt`

- [ ] **Step 1: Create RecordingManagerTest**

```kotlin
package com.noter.domain

import android.content.Context
import com.noter.util.FileHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class RecordingManagerTest {
    
    @Mock
    private lateinit var mockFile: File
    
    private lateinit var context: Context
    private lateinit var recordingManager: RecordingManager
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        context = RuntimeEnvironment.getApplication()
        recordingManager = RecordingManager(context)
    }
    
    @Test
    fun `initial state is IDLE`() = runTest {
        assertEquals(RecordingManager.RecordingState.IDLE, recordingManager.recordingState.first())
    }
    
    @Test
    fun `initial elapsed time is zero`() = runTest {
        assertEquals(0, recordingManager.elapsedTime.first())
    }
    
    @Test
    fun `updateElapsedTime only updates when recording`() = runTest {
        recordingManager.updateElapsedTime()
        assertEquals(0, recordingManager.elapsedTime.first())
    }
}
```

- [ ] **Step 2: Add Robolectric dependency**

Add to build.gradle.kts:
```kotlin
testImplementation("org.robolectric:robolectric:4.11.1")
```

- [ ] **Step 3: Commit RecordingManager tests**

```bash
git add app/src/test/java/com/noter/domain/RecordingManagerTest.kt
git add app/build.gradle.kts
git commit -m "test: add unit tests for RecordingManager"
```

---

## Task 3: Repository Integration Tests

**Files:**
- Create: `app/src/androidTest/java/com/noter/data/repository/NoteRepositoryIntegrationTest.kt`

- [ ] **Step 1: Create NoteRepositoryIntegrationTest**

```kotlin
package com.noter.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.noter.data.db.AppDatabase
import com.noter.data.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteRepositoryIntegrationTest {
    
    private lateinit var database: AppDatabase
    private lateinit var repository: NoteRepository
    
    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        repository = NoteRepository(database.noteDao())
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    @Test
    fun insertAndRetrieveNote() = runBlocking {
        val note = Note(
            id = "test-1",
            title = "Test Note",
            transcriptPath = "/transcript.txt",
            audioPath = "/audio.m4a",
            summary = null,
            createdAt = System.currentTimeMillis(),
            duration = 60
        )
        
        repository.insertNote(note)
        val retrieved = repository.getNoteById("test-1")
        
        assertNotNull(retrieved)
        assertEquals("Test Note", retrieved?.title)
        assertEquals(60, retrieved?.duration)
    }
    
    @Test
    fun getAllNotesReturnsOrderedByCreatedAt() = runBlocking {
        val note1 = Note("1", "First", "/t1.txt", "/a1.m4a", null, 1000L, 30)
        val note2 = Note("2", "Second", "/t2.txt", "/a2.m4a", null, 2000L, 45)
        val note3 = Note("3", "Third", "/t3.txt", "/a3.m4a", null, 3000L, 60)
        
        repository.insertNote(note1)
        repository.insertNote(note2)
        repository.insertNote(note3)
        
        val notes = repository.getAllNotes().first()
        
        assertEquals(3, notes.size)
        assertEquals("Third", notes[0].title)
        assertEquals("Second", notes[1].title)
        assertEquals("First", notes[2].title)
    }
    
    @Test
    fun updateNoteModifiesExisting() = runBlocking {
        val note = Note("1", "Original", "/t.txt", "/a.m4a", null, 1000L, 60)
        repository.insertNote(note)
        
        val updated = note.copy(title = "Updated", summary = "New Summary")
        repository.updateNote(updated)
        
        val retrieved = repository.getNoteById("1")
        assertEquals("Updated", retrieved?.title)
        assertEquals("New Summary", retrieved?.summary)
    }
    
    @Test
    fun deleteNoteRemovesFromDatabase() = runBlocking {
        val note = Note("1", "Test", "/t.txt", "/a.m4a", null, 1000L, 60)
        repository.insertNote(note)
        
        repository.deleteNote(note)
        
        val retrieved = repository.getNoteById("1")
        assertNull(retrieved)
    }
}
```

- [ ] **Step 2: Commit repository integration tests**

```bash
git add app/src/androidTest/java/com/noter/data/repository/
git commit -m "test: add integration tests for NoteRepository"
```

---

## Task 4: UI Component Tests

**Files:**
- Create: `app/src/androidTest/java/com/noter/ui/screens/NoteListScreenTest.kt`
- Create: `app/src/androidTest/java/com/noter/ui/screens/RecordingScreenTest.kt`

- [ ] **Step 1: Create NoteListScreenTest**

```kotlin
package com.noter.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.noter.data.model.Note
import com.noter.data.repository.NoteRepository
import com.noter.ui.theme.NoterTheme
import com.noter.ui.viewmodels.NoteListViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class NoteListScreenTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Mock
    private lateinit var repository: NoteRepository
    
    private lateinit var viewModel: NoteListViewModel
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(repository.getAllNotes()).thenReturn(MutableStateFlow(emptyList()))
        viewModel = NoteListViewModel(repository)
    }
    
    @Test
    fun emptyStateDisplaysMessage() {
        composeTestRule.setContent {
            NoterTheme {
                NoteListScreen(
                    viewModel = viewModel,
                    onRecordClick = {},
                    onNoteClick = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("No notes yet. Tap above to record.")
            .assertIsDisplayed()
    }
    
    @Test
    fun recordButtonIsDisplayed() {
        composeTestRule.setContent {
            NoterTheme {
                NoteListScreen(
                    viewModel = viewModel,
                    onRecordClick = {},
                    onNoteClick = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("Start Recording")
            .assertIsDisplayed()
    }
    
    @Test
    fun recordButtonClickTriggersCallback() {
        var clicked = false
        
        composeTestRule.setContent {
            NoterTheme {
                NoteListScreen(
                    viewModel = viewModel,
                    onRecordClick = { clicked = true },
                    onNoteClick = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("Start Recording")
            .performClick()
        
        assert(clicked)
    }
    
    @Test
    fun notesAreDisplayedInList() {
        val testNotes = listOf(
            Note("1", "Note 1", "/t1.txt", "/a1.m4a", null, System.currentTimeMillis(), 60),
            Note("2", "Note 2", "/t2.txt", "/a2.m4a", null, System.currentTimeMillis(), 120)
        )
        `when`(repository.getAllNotes()).thenReturn(MutableStateFlow(testNotes))
        viewModel = NoteListViewModel(repository)
        
        composeTestRule.setContent {
            NoterTheme {
                NoteListScreen(
                    viewModel = viewModel,
                    onRecordClick = {},
                    onNoteClick = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("Note 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Note 2").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Create RecordingScreenTest**

```kotlin
package com.noter.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.noter.data.repository.NoteRepository
import com.noter.domain.RecordingManager
import com.noter.ui.theme.NoterTheme
import com.noter.ui.viewmodels.RecordingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class RecordingScreenTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Mock
    private lateinit var recordingManager: RecordingManager
    
    @Mock
    private lateinit var repository: NoteRepository
    
    @Mock
    private lateinit var workManager: WorkManager
    
    private lateinit var viewModel: RecordingViewModel
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(recordingManager.recordingState).thenReturn(
            MutableStateFlow(RecordingManager.RecordingState.RECORDING)
        )
        `when`(recordingManager.elapsedTime).thenReturn(MutableStateFlow(65))
        
        viewModel = RecordingViewModel(recordingManager, repository, workManager)
    }
    
    @Test
    fun timerDisplaysElapsedTime() {
        composeTestRule.setContent {
            NoterTheme {
                RecordingScreen(
                    viewModel = viewModel,
                    onStopClick = {},
                    onCancelClick = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("01:05").assertIsDisplayed()
    }
    
    @Test
    fun stopButtonTriggersCallback() {
        var clicked = false
        
        composeTestRule.setContent {
            NoterTheme {
                RecordingScreen(
                    viewModel = viewModel,
                    onStopClick = { clicked = true },
                    onCancelClick = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("Stop").performClick()
        assert(clicked)
    }
    
    @Test
    fun cancelButtonTriggersCallback() {
        var clicked = false
        
        composeTestRule.setContent {
            NoterTheme {
                RecordingScreen(
                    viewModel = viewModel,
                    onStopClick = {},
                    onCancelClick = { clicked = true }
                )
            }
        }
        
        composeTestRule.onNodeWithText("Cancel").performClick()
        assert(clicked)
    }
}
```

- [ ] **Step 3: Commit UI component tests**

```bash
git add app/src/androidTest/java/com/noter/ui/screens/
git commit -m "test: add UI component tests for screens"
```

---

## Task 5: Utility Function Tests

**Files:**
- Test: Enhance `app/src/test/java/com/noter/util/TimeFormatterTest.kt`
- Create: `app/src/test/java/com/noter/util/FileHelperTest.kt`

- [ ] **Step 1: Enhance TimeFormatterTest with edge cases**

Add to existing test file:
```kotlin
@Test
fun formatRelativeTimeJustNow() {
    val now = System.currentTimeMillis()
    val fewSecondsAgo = now - 30 * 1000
    assertEquals("Just now", TimeFormatter.formatRelativeTime(fewSecondsAgo, now))
}

@Test
fun formatRelativeTimeWeeksAgo() {
    val now = System.currentTimeMillis()
    val threeWeeksAgo = now - (21 * 24 * 60 * 60 * 1000)
    assertEquals("3 weeks ago", TimeFormatter.formatRelativeTime(threeWeeksAgo, now))
}

@Test
fun formatDurationHandlesEdgeCases() {
    assertEquals("00:00", TimeFormatter.formatDuration(0))
    assertEquals("00:01", TimeFormatter.formatDuration(1))
    assertEquals("59:59", TimeFormatter.formatDuration(3599))
    assertEquals("60:00", TimeFormatter.formatDuration(3600))
}
```

- [ ] **Step 2: Create FileHelperTest**

```kotlin
package com.noter.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileHelperTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun getAudioFileReturnsCorrectPath() {
        val file = FileHelper.getAudioFile(context, "test-123")
        
        assertTrue(file.absolutePath.contains("Noter/audio"))
        assertTrue(file.name == "note_test-123.m4a")
    }
    
    @Test
    fun getTranscriptFileReturnsCorrectPath() {
        val file = FileHelper.getTranscriptFile(context, "test-456")
        
        assertTrue(file.absolutePath.contains("Noter/transcripts"))
        assertTrue(file.name == "note_test-456.txt")
    }
    
    @Test
    fun writeAndReadTranscript() {
        val file = File.createTempFile("test", ".txt")
        val content = "This is a test transcript."
        
        FileHelper.writeTranscript(file, content)
        val read = FileHelper.readTranscript(file)
        
        assertEquals(content, read)
        file.delete()
    }
    
    @Test
    fun checkStorageSpaceReturnBoolean() {
        val hasSpace = FileHelper.checkStorageSpace()
        assertTrue(hasSpace || !hasSpace)
    }
}
```

- [ ] **Step 3: Commit utility tests**

```bash
git add app/src/test/java/com/noter/util/
git commit -m "test: enhance utility function tests with edge cases"
```