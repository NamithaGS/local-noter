package com.noter.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noter.data.model.Note
import com.noter.domain.RecordingManager
import com.noter.ui.theme.CardBackground
import com.noter.ui.theme.RecordRed
import com.noter.ui.theme.TextSecondary
import com.noter.ui.viewmodels.NoteListViewModel
import com.noter.ui.viewmodels.RecordingViewModel
import com.noter.util.PermissionHelper
import com.noter.util.TimeFormatter
import kotlinx.coroutines.launch

// MediaRecorder.getMaxAmplitude() is documented as returning 0..32767.
private const val MAX_RAW_AMPLITUDE = 32767f
// How many recent samples the level graph shows; at the ViewModel's 100ms poll
// interval this is a ~4 second rolling window.
private const val LEVEL_HISTORY_SIZE = 40
// Below this normalized level we treat the input as room noise rather than voice, so
// the "no sound detected" hint doesn't flicker on ambient hiss.
private const val SILENCE_THRESHOLD = 0.05f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NoteListViewModel,
    recordingViewModel: RecordingViewModel,
    onNoteClick: (String) -> Unit
) {
    val notes by viewModel.notes.collectAsState()
    val recordingState by recordingViewModel.recordingState.collectAsState()
    val elapsedTime by recordingViewModel.elapsedTime.collectAsState()
    val amplitude by recordingViewModel.amplitude.collectAsState()
    val isRecording = recordingState == RecordingManager.RecordingState.RECORDING

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // RECORD_AUDIO is a dangerous permission, so it must be requested at runtime even
    // though it's declared in the manifest. Without this, MediaRecorder.start() throws a
    // SecurityException the first time the record button is tapped, RecordingManager
    // swallows it into an ERROR state, and recording looks like it simply never started.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            recordingViewModel.startRecording()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required to record notes")
            }
        }
    }

    // Presentation-only rolling window derived from the amplitude stream, driving the
    // level graph below. Cleared whenever a recording isn't in progress so starting a
    // new one begins with a blank graph instead of the previous one's tail.
    val levelHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(isRecording) {
        if (!isRecording) levelHistory.clear()
    }
    LaunchedEffect(amplitude) {
        if (isRecording) {
            levelHistory.add((amplitude / MAX_RAW_AMPLITUDE).coerceIn(0f, 1f))
            if (levelHistory.size > LEVEL_HISTORY_SIZE) {
                levelHistory.removeAt(0)
            }
        }
    }
    val hasDetectedVoice = levelHistory.any { it > SILENCE_THRESHOLD }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Noter") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            RecordButton(
                isRecording = isRecording,
                elapsedTime = elapsedTime,
                onClick = {
                    when {
                        isRecording -> recordingViewModel.stopRecording()
                        PermissionHelper.hasRecordAudioPermission(context) -> recordingViewModel.startRecording()
                        else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )

            if (isRecording) {
                VoiceLevelGraph(
                    levels = levelHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(60.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (hasDetectedVoice) "Voice detected" else "No sound detected - try speaking closer to the mic",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasDetectedVoice) TextSecondary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Recording can fail silently at the MediaRecorder layer (permission revoked
            // mid-session, mic already in use, etc). Without this, tapping the button
            // again just looks like nothing happened.
            if (recordingState == RecordingManager.RecordingState.ERROR) {
                Text(
                    "Couldn't record. Check microphone permission and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No notes yet. Tap above to record.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                Text(
                    "NOTES LIST",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn {
                    items(notes) { note ->
                        NoteItem(note = note, onClick = { onNoteClick(note.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, elapsedTime: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = RecordRed
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (isRecording) "■" else "●",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    if (isRecording) {
                        "Recording ${TimeFormatter.formatDuration(elapsedTime)}"
                    } else {
                        "Start Recording"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (isRecording) "Tap to stop" else "Tap the button to begin",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * Scrolling bar graph of recent microphone levels - oldest sample on the left, newest
 * on the right - so the user can see at a glance whether the mic is picking up sound.
 *
 * @param levels normalized input levels in `0f..1f`, oldest first.
 */
@Composable
private fun VoiceLevelGraph(levels: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (levels.isEmpty()) return@Canvas

        val slotWidth = size.width / LEVEL_HISTORY_SIZE
        val barWidth = slotWidth * 0.7f
        levels.forEachIndexed { index, level ->
            // Always draw a thin sliver even at zero level, so silence reads as a
            // visible flat line rather than an empty gap that looks like a bug.
            val barHeight = (size.height * level).coerceAtLeast(2f)
            drawRect(
                color = RecordRed,
                topLeft = Offset(x = index * slotWidth, y = size.height - barHeight),
                size = Size(width = barWidth, height = barHeight)
            )
        }
    }
}

@Composable
private fun NoteItem(note: Note, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(note.title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            TimeFormatter.formatRelativeTime(note.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    }
}
