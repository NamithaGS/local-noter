package com.noter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.noter.ui.screens.NoteDetailScreen
import com.noter.ui.screens.NoteListScreen
import com.noter.ui.screens.RecordingScreen
import com.noter.ui.viewmodels.NoteDetailViewModel
import com.noter.ui.viewmodels.NoteListViewModel
import com.noter.ui.viewmodels.RecordingViewModel

sealed class Screen(val route: String) {
    object NoteList : Screen("note_list")
    object Recording : Screen("recording")
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
                onRecordClick = { navController.navigate(Screen.Recording.route) },
                onNoteClick = { noteId -> navController.navigate(Screen.NoteDetail.createRoute(noteId)) }
            )
        }

        composable(Screen.Recording.route) {
            // Start recording when screen is shown
            androidx.compose.runtime.LaunchedEffect(Unit) {
                recordingViewModel.startRecording()
            }
            RecordingScreen(
                viewModel = recordingViewModel,
                onStopClick = {
                    recordingViewModel.stopRecording()
                    navController.popBackStack()
                },
                onCancelClick = {
                    recordingViewModel.cancelRecording()
                    navController.popBackStack()
                }
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
