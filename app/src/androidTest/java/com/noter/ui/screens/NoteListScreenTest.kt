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
                    onRecordClick = {},
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
                    onRecordClick = {},
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
                    onRecordClick = {},
                    onNoteClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("NOTES LIST").assertDoesNotExist()
    }
}
