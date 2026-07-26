package com.noter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noter.ui.theme.RecordRed
import com.noter.ui.viewmodels.RecordingViewModel
import com.noter.util.TimeFormatter

@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel,
    onStopClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val elapsedTime by viewModel.elapsedTime.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            TimeFormatter.formatDuration(elapsedTime),
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = RecordRed
        ) {}

        Spacer(modifier = Modifier.height(64.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onCancelClick) {
                Text("Cancel")
            }
            Button(onClick = onStopClick) {
                Text("Stop")
            }
        }
    }
}
