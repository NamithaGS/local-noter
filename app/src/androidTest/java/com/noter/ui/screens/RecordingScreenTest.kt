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

    @Test
    fun bothButtonsAreDisplayed() {
        composeTestRule.setContent {
            NoterTheme {
                RecordingScreen(
                    viewModel = viewModel,
                    onStopClick = {},
                    onCancelClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Stop").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun timerFormatsZeroMinutes() {
        `when`(recordingManager.elapsedTime).thenReturn(MutableStateFlow(0))
        viewModel = RecordingViewModel(recordingManager, repository, workManager)

        composeTestRule.setContent {
            NoterTheme {
                RecordingScreen(
                    viewModel = viewModel,
                    onStopClick = {},
                    onCancelClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("00:00").assertIsDisplayed()
    }

    @Test
    fun timerFormatsTwoDigitMinutes() {
        `when`(recordingManager.elapsedTime).thenReturn(MutableStateFlow(720))
        viewModel = RecordingViewModel(recordingManager, repository, workManager)

        composeTestRule.setContent {
            NoterTheme {
                RecordingScreen(
                    viewModel = viewModel,
                    onStopClick = {},
                    onCancelClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("12:00").assertIsDisplayed()
    }
}
