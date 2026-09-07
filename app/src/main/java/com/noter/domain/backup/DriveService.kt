package com.noter.domain.backup

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.docs.v1.Docs
import com.google.api.services.docs.v1.DocsScopes
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest
import com.google.api.services.docs.v1.model.EndOfSegmentLocation
import com.google.api.services.docs.v1.model.InsertTextRequest
import com.google.api.services.docs.v1.model.Request
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile

/**
 * Drive/Docs operations backing the note-filing pipeline: navigating (and creating, on
 * first use) the AllNotes/<year>/<month> archive hierarchy and the Work/<topic> doc
 * hierarchy, and appending dated sections to whichever doc a note belongs in.
 *
 * Scoped to `drive.file` + Docs (see [DriveAuth]): this can only see and manage files it
 * created itself, and can only edit Doc content through the Docs API for docs it created
 * via Drive - it has no visibility into the rest of the user's Drive.
 */
class DriveService(context: Context, account: GoogleSignInAccount) {

    private val credential = GoogleAccountCredential.usingOAuth2(
        context, listOf(DriveScopes.DRIVE_FILE, DocsScopes.DOCUMENTS)
    ).apply { selectedAccount = account.account }

    private val drive: Drive = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
        .setApplicationName("Local Noter")
        .build()

    private val docs: Docs = Docs.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
        .setApplicationName("Local Noter")
        .build()

    /**
     * Finds a folder named [name] directly under [parentId] (Drive's root "My Drive" if
     * null), creating it if it doesn't exist yet.
     *
     * Drive has no real folder-path API - "folders" are just files with a special
     * mimeType - so finding one means querying by name/mimeType/parent, not by path.
     */
    fun findOrCreateFolder(name: String, parentId: String? = null): String {
        findChild(name, MIME_FOLDER, parentId)?.let { return it }

        val metadata = DriveFile().apply {
            this.name = name
            mimeType = MIME_FOLDER
            if (parentId != null) parents = listOf(parentId)
        }
        return drive.files().create(metadata).setFields("id").execute().id
    }

    /**
     * Finds a Google Doc named [name] directly under [parentId], creating an empty one
     * if it doesn't exist yet.
     *
     * Created through the Drive API (not the Docs API's own `create`) so the parent
     * folder can be set in the same call - the Docs API has no notion of a parent folder.
     */
    fun findOrCreateDoc(name: String, parentId: String): String {
        findChild(name, MIME_DOCUMENT, parentId)?.let { return it }

        val metadata = DriveFile().apply {
            this.name = name
            mimeType = MIME_DOCUMENT
            parents = listOf(parentId)
        }
        return drive.files().create(metadata).setFields("id").execute().id
    }

    /**
     * Appends [text] to the end of the doc identified by [documentId].
     *
     * `endOfSegmentLocation` inserts at the end of the document body without needing to
     * fetch the doc first to compute an exact character index.
     */
    fun appendToDoc(documentId: String, text: String) {
        val request = Request().setInsertText(
            InsertTextRequest()
                .setText(text)
                .setEndOfSegmentLocation(EndOfSegmentLocation())
        )
        docs.documents()
            .batchUpdate(documentId, BatchUpdateDocumentRequest().setRequests(listOf(request)))
            .execute()
    }

    private fun findChild(name: String, mimeType: String, parentId: String?): String? {
        val escapedName = name.replace("\\", "\\\\").replace("'", "\\'")
        val parentClause = if (parentId != null) " and '$parentId' in parents" else ""
        val query = "mimeType = '$mimeType' and name = '$escapedName' " +
            "and trashed = false$parentClause"

        return drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
            .files
            .firstOrNull()
            ?.id
    }

    private companion object {
        const val MIME_FOLDER = "application/vnd.google-apps.folder"
        const val MIME_DOCUMENT = "application/vnd.google-apps.document"
    }
}
