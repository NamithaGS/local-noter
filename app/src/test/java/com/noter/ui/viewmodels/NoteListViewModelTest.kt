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
