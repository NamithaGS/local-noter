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
