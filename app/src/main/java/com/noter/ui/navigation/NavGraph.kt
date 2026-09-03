package com.noter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.noter.ui.screens.NoteDetailScreen
import com.noter.ui.screens.NoteListScreen
import com.noter.ui.viewmodels.NoteDetailViewModel
import com.noter.ui.viewmodels.NoteListViewModel
import com.noter.ui.viewmodels.RecordingViewModel

sealed class Screen(val route: String) {
    object NoteList : Screen("note_list")
    object NoteDetail : Screen("note_detail/{noteId}") {
        fun createRoute(noteId: String) = "note_detail/$noteId"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    noteListViewModel: NoteListViewModel,
    recordingViewModel: RecordingViewModel,
    noteDetailViewModel: NoteDetailViewModel
) {
    NavHost(navController = navController, startDestination = Screen.NoteList.route) {
        composable(Screen.NoteList.route) {
            NoteListScreen(
                viewModel = noteListViewModel,
                recordingViewModel = recordingViewModel,
                onNoteClick = { noteId -> navController.navigate(Screen.NoteDetail.createRoute(noteId)) }
            )
        }

        composable(Screen.NoteDetail.route) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
            NoteDetailScreen(
                noteId = noteId,
                viewModel = noteDetailViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
