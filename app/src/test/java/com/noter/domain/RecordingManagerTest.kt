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
