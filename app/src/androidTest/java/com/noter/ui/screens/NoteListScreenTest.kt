package com.noter.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.noter.data.model.Note
import com.noter.data.repository.NoteRepository
import com.noter.domain.RecordingManager
import com.noter.ui.theme.NoterTheme
import com.noter.ui.viewmodels.NoteListViewModel
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
class NoteListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Mock
    private lateinit var repository: NoteRepository

    @Mock
    private lateinit var recordingManager: RecordingManager

    @Mock
    private lateinit var workManager: WorkManager

    private lateinit var viewModel: NoteListViewModel
    private lateinit var recordingViewModel: RecordingViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(repository.getAllNotes()).thenReturn(MutableStateFlow(emptyList()))
        viewModel = NoteListViewModel(repository)

        `when`(recordingManager.recordingState).thenReturn(
            MutableStateFlow(RecordingManager.RecordingState.IDLE)
        )
        `when`(recordingManager.elapsedTime).thenReturn(MutableStateFlow(0))
        `when`(recordingManager.amplitude).thenReturn(MutableStateFlow(0))
        recordingViewModel = RecordingViewModel(recordingManager, repository, workManager)
    }

    @Test
    fun emptyStateDisplaysMessage() {
        composeTestRule.setContent {
            NoterTheme {
                NoteListScreen(
                    viewModel = viewModel,
                    recordingViewModel = recordingViewModel,
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
                    recordingViewModel = recordingViewModel,
                    onNoteClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Start Recording")
            .assertIsDisplayed()
    }

    @Test
    fun recordingStateShowsStopLabelAndLevelGraph() {
        `when`(recordingManager.recordingState).thenReturn(
            MutableStateFlow(RecordingManager.RecordingState.RECORDING)
        )
        `when`(recordingManager.elapsedTime).thenReturn(MutableStateFlow(65))
        recordingViewModel = RecordingViewModel(recordingManager, repository, workManager)

        composeTestRule.setContent {
            NoterTheme {
                NoteListScreen(
                    viewModel = viewModel,
                    recordingViewModel = recordingViewModel,
                    onNoteClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Recording 01:05").assertIsDisplayed()
        composeTestRule.onNodeWithText("No sound detected - try speaking closer to the mic")
            .assertIsDisplayed()
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
                    recordingViewModel = recordingViewModel,
                    onNoteClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Note 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Note 2").assertIsDisplayed()
    }

    @Test
    fun notesListShowsHeader() {
        val testNotes = listOf(
            Note("1", "Note 1", "/t1.txt", "/a1.m4a", null, System.currentTimeMillis(), 60)
        )
        `when`(repository.getAllNotes()).thenReturn(MutableStateFlow(testNotes))
        viewModel = NoteListViewModel(repository)

        composeTestRule.setContent {
            NoterTheme {
                NoteListScreen(
                    viewModel = viewModel,
                    recordingViewModel = recordingViewModel,
                    onNoteClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("NOTES LIST").assertIsDisplayed()
    }

    @Test
    fun noteClickTriggersCallback() {
        var clickedNoteId: String? = null
        val testNotes = listOf(
            Note("note-123", "Test Note", "/t1.txt", "/a1.m4a", null, System.currentTimeMillis(), 60)
        )
        `when`(repository.getAllNotes()).thenReturn(MutableStateFlow(testNotes))
        viewModel = NoteListViewModel(repository)

        composeTestRule.setContent {
            NoterTheme {
                NoteListScreen(
                    viewModel = viewModel,
                    recordingViewModel = recordingViewModel,
                    onNoteClick = { noteId -> clickedNoteId = noteId }
                )
            }
        }

        composeTestRule.onNodeWithText("Test Note").performClick()
        assert(clickedNoteId == "note-123")
    }

    @Test
    fun emptyStateHidesNotesList() {
        composeTestRule.setContent {
            NoterTheme {
                NoteListScreen(
                    viewModel = viewModel,
                    recordingViewModel = recordingViewModel,
                    onNoteClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("NOTES LIST").assertDoesNotExist()
    }
}
