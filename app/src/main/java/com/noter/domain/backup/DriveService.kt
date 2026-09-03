package com.noter.domain.backup

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile

/**
 * Uploads the daily note digest into a "Noter Backups" folder in the signed-in user's
 * Google Drive, creating the folder on first use.
 *
 * Scoped to `drive.file` (see [DriveAuth]), so this can only see and manage files it
 * created itself - it has no visibility into the rest of the user's Drive.
 */
class DriveService(context: Context, account: GoogleSignInAccount) {

    private val drive: Drive = run {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        ).apply { selectedAccount = account.account }

        Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Noter")
            .build()
    }

    /**
     * Uploads [content] as a new file named [fileName] into the backup folder.
     *
     * Shared by both the automatic daily job and a manual on-demand upload - there's
     * nothing daily-specific here beyond the caller's choice of file name.
     */
    fun uploadDigest(fileName: String, content: String) {
        val folderId = findOrCreateBackupFolder()

        val metadata = DriveFile().apply {
            name = fileName
            parents = listOf(folderId)
        }
        val mediaContent = ByteArrayContent("text/plain", content.toByteArray(Charsets.UTF_8))

        drive.files().create(metadata, mediaContent).setFields("id").execute()
    }

    /**
     * Drive has no real folder-path API - "folders" are just files with a special
     * mimeType - so finding one means querying by name and mimeType, not by path.
     */
    private fun findOrCreateBackupFolder(): String {
        val query = "mimeType = 'application/vnd.google-apps.folder' " +
            "and name = '$BACKUP_FOLDER_NAME' and trashed = false"

        val existing = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
            .files

        if (existing.isNotEmpty()) return existing[0].id

        val folderMetadata = DriveFile().apply {
            name = BACKUP_FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
        }
        return drive.files().create(folderMetadata).setFields("id").execute().id
    }

    private companion object {
        const val BACKUP_FOLDER_NAME = "Noter Backups"
    }
}
