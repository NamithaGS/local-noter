package com.noter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkManager
import com.noter.data.db.AppDatabase
import com.noter.data.repository.NoteRepository
import com.noter.domain.RecordingManager
import com.noter.ui.navigation.NavGraph
import com.noter.ui.theme.NoterTheme
import com.noter.ui.viewmodels.NoteDetailViewModel
import com.noter.ui.viewmodels.NoteListViewModel
import com.noter.ui.viewmodels.RecordingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NoterTheme {
                NoterApp()
            }
        }
    }
}

@Composable
fun NoterApp() {
    val navController = rememberNavController()

    // Simple DI - in production use Hilt
    val database = AppDatabase.getDatabase(LocalContext.current)
    val repository = NoteRepository(database.noteDao())
    val recordingManager = RecordingManager(LocalContext.current)
    val workManager = WorkManager.getInstance(LocalContext.current)

    val noteListViewModel: NoteListViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return NoteListViewModel(repository) as T
            }
        }
    )

    val recordingViewModel: RecordingViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return RecordingViewModel(recordingManager, repository, workManager) as T
            }
        }
    )

    val noteDetailViewModel: NoteDetailViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return NoteDetailViewModel(repository) as T
            }
        }
    )

    NavGraph(
        navController = navController,
        noteListViewModel = noteListViewModel,
        recordingViewModel = recordingViewModel,
        noteDetailViewModel = noteDetailViewModel
    )
}
