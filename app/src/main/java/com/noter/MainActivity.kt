package com.noter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkManager
import com.noter.data.db.AppDatabase
import com.noter.data.repository.NoteRepository
import com.noter.domain.RecordingManager
import com.noter.domain.backup.DriveAuth
import com.noter.domain.backup.DriveBackupScheduler
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
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val repository = NoteRepository(database.noteDao())
    val recordingManager = RecordingManager(context)
    val workManager = WorkManager.getInstance(context)

    // WorkManager's queue can be lost in ways that don't involve the user ever touching
    // the backup toggle (e.g. app data cleared then restored), so re-arm the daily chain
    // on launch whenever a Drive account is still connected instead of relying solely on
    // the toggle in NoteListScreen.
    LaunchedEffect(Unit) {
        if (DriveAuth.getSignedInAccount(context) != null) {
            DriveBackupScheduler.scheduleNext(context)
        }
    }

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
