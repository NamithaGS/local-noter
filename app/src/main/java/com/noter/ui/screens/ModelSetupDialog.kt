package com.noter.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One-time setup prompt for the LiteRT-LM summarization backend's Gemma model - triggered
 * from the "Setup" item in [com.noter.ui.screens.NoteListScreen]'s overflow menu, not
 * automatically from the Summarize button, so downloading a ~560MB file only ever happens
 * when the user deliberately asks for it.
 *
 * The token itself never lives in any longer-lived state than this composable - it's
 * handed straight to the caller's [onSetUp], which persists it via
 * [com.noter.domain.summarization.litertlm.HuggingFaceTokenStore] and starts the download.
 */
@Composable
fun ModelSetupDialog(onDismiss: () -> Unit, onSetUp: (token: String) -> Unit) {
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
            TextButton(onClick = { onSetUp(token) }) {
                Text("Set Up")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
