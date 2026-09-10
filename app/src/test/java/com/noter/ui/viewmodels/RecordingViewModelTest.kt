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
// Explicit (non-star) import wins over Mockito.*'s any() for plain `any()` calls in this
// file: org.mockito.kotlin's any() returns a real fake value for reference types instead
// of null, avoiding a Kotlin null-check crash on a non-null parameter (e.g. insertNote's
// Note) that plain Mockito's any() triggers.
import org.mockito.kotlin.any
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
        `when`(recordingManager.amplitude).thenReturn(
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
        // anyString(), not any() - startRecording(noteId: String) takes a non-null
        // String, but plain Mockito any() matches by returning null, which trips
        // Kotlin's runtime null-check on that parameter ("any(...) must not be null")
        // before the stub is even reached.
        `when`(recordingManager.startRecording(anyString())).thenReturn(Result.success(mockFile))

        viewModel.startRecording()
        advanceUntilIdle()

        verify(recordingManager).startRecording(anyString())
    }

    @Test
    fun `stopRecording saves note to repository`() = runTest {
        // Same anyString() reasoning as above - also needed here since this test calls
        // startRecording() on the way to testing stopRecording(). An unstubbed call
        // (or one broken by any()'s null) throws before stopRecording() is even reached.
        `when`(recordingManager.startRecording(anyString())).thenReturn(Result.success(mockFile))
        `when`(recordingManager.stopRecording()).thenReturn(Result.success(60))
        `when`(recordingManager.currentFile).thenReturn(mockFile)
        `when`(mockFile.absolutePath).thenReturn("/audio.m4a")

        viewModel.startRecording()
        viewModel.stopRecording()
        advanceUntilIdle()

        verify(repository).insertNote(any())
    }
}
