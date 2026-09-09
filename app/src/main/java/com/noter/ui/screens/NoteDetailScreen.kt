package com.noter.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noter.ui.theme.SummaryBackground
import com.noter.ui.theme.SummaryBorder
import com.noter.ui.theme.TextSecondary
import com.noter.ui.viewmodels.NoteDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: String,
    viewModel: NoteDetailViewModel,
    onBackClick: () -> Unit
) {
    val note by viewModel.note.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSummarizing by viewModel.isSummarizing.collectAsState()
    val needsModelSetup by viewModel.needsModelSetup.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(noteId) {
        viewModel.loadNote(noteId)
    }

    LaunchedEffect(Unit) {
        viewModel.summaryEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (needsModelSetup) {
        ModelSetupDialog(
            onDismiss = { viewModel.dismissModelSetup() },
            onDownload = { token -> viewModel.downloadModelAndSummarize(context, token) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(note?.title ?: "Note") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // A manual tag always wins over on-device classification (see
                // WorkClassifier) - it's the reliable way to route this note to a
                // specific Work topic doc instead of leaving it to the model's guess.
                var tagInput by remember(note?.id) { mutableStateOf(note?.manualTag.orEmpty()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text("Work topic tag (optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { note?.let { viewModel.updateTag(it.id, tagInput) } }) {
                        Text("Save")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // On-device summarization is best-effort (see NoteSummarizer) - a note
                // can be fully valid with summary == null, e.g. before the active
                // backend is set up, or for a transcript too short to bother
                // summarizing. The button below lets the user retry on-demand instead of
                // only ever getting one automatically.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SUMMARY",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSummarizing || downloadProgress != null) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        TextButton(onClick = { viewModel.summarize(context) }) {
                            Text(if (note?.summary != null) "Regenerate" else "Summarize")
                        }
                    }
                }
                downloadProgress?.let { progress ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Downloading on-device model: ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                note?.summary?.let { summary ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = SummaryBackground,
                        border = BorderStroke(1.dp, SummaryBorder)
                    ) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "FULL TRANSCRIPT",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = transcript.ifEmpty { "No transcript available" },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

/**
 * One-time setup prompt shown when the active summarization backend needs a Hugging Face
 * access token to download its model (currently: LiteRT-LM's Gemma model). The token
 * itself never touches this app's UI state beyond this composable - it's handed straight
 * to [NoteDetailViewModel.downloadModelAndSummarize], which persists it via
 * [com.noter.domain.summarization.litertlm.HuggingFaceTokenStore] and starts the download.
 */
@Composable
private fun ModelSetupDialog(onDismiss: () -> Unit, onDownload: (token: String) -> Unit) {
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set up on-device AI") },
        text = {
            Column {
                Text(
                    "Summarizing needs a one-time ~560MB model download from Hugging " +
                        "Face. Accept the Gemma license at huggingface.co/litert-community/" +
                        "Gemma3-1B-IT, then paste an access token from your account here."
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Hugging Face access token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDownload(token) }) {
                Text("Download & Summarize")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
