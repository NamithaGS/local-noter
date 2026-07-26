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
