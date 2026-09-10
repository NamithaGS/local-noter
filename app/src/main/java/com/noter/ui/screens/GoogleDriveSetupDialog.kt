package com.noter.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One-time setup instructions for Google Drive backup - shown before the actual sign-in
 * flow starts, from the note list's "Setup Google Drive" menu item.
 *
 * Backup needs a Google Cloud project with the Drive and Docs APIs enabled and an OAuth
 * client registered for this exact app build; none of that can happen automatically from
 * inside the app, so this spells out the concrete steps rather than just failing
 * opaquely (as it did earlier: a disabled API surfaces as a bare "403 Forbidden", a
 * missing/wrong OAuth client as "could not connect", neither self-explanatory).
 *
 * The SHA-1 shown is safe to hardcode: it's pinned to a fixed, committed debug keystore
 * (see app/build.gradle.kts) specifically so it never changes between builds or machines.
 */
@Composable
fun GoogleDriveSetupDialog(onDismiss: () -> Unit, onConnect: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set up Google Drive") },
        text = {
            Column {
                Text(
                    "Backup writes to your Drive as real Google Docs, which needs a " +
                        "Google Cloud project configured for this app. If you haven't " +
                        "done this yet, at console.cloud.google.com:"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "1. Select (or create) a project.\n" +
                        "2. APIs & Services -> Library: enable \"Google Drive API\" and " +
                        "\"Google Docs API\".\n" +
                        "3. APIs & Services -> Credentials: create an OAuth Client ID, " +
                        "type Android, package name com.noter, SHA-1 certificate:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    SHA1_FINGERPRINT,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "4. APIs & Services -> OAuth consent screen: add your Google " +
                        "account as a test user, if the app is in Testing mode.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Already done this once? Just tap Connect.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConnect) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private const val SHA1_FINGERPRINT = "74:DD:91:48:A6:F3:F5:AE:60:7B:71:95:5D:E9:4C:78:72:EC:1B:9A"
