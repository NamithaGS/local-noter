package com.noter.ui.viewmodels

import com.noter.data.model.Note
import com.noter.data.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.stub
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class NoteDetailViewModelTest {

    // Unconfined, not Standard: loadNote() launches a coroutine and returns immediately,
    // without suspending itself - a StandardTestDispatcher would leave that coroutine
    // merely queued (not yet run) at that point, so isLoading would still read its
    // initial false. Unconfined runs it eagerly, synchronously up to its first real
    // suspension point, which is what lets "loading state sets correctly" below observe
    // isLoading == true in the window before that point.
    private val testDispatcher = UnconfinedTestDispatcher()

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
        // A plain thenReturn() completes instantly, with no real suspension between
        // isLoading = true and isLoading = false - the whole coroutine body would run as
        // one indivisible step no matter which dispatcher drives it, leaving no window to
        // observe the true state at all. This fake delay is a genuine suspension point,
        // which is what actually creates that window.
        repository.stub {
            onBlocking { getNoteById("1") } doSuspendableAnswer {
                delay(1)
                testNote
            }
        }

        viewModel.loadNote("1")

        assertTrue(viewModel.isLoading.value)
        advanceUntilIdle()
        assertFalse(viewModel.isLoading.value)
    }
}
