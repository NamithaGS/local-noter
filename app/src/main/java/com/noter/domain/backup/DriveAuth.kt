package com.noter.domain.backup

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.docs.v1.DocsScopes
import com.google.api.services.drive.DriveScopes

/**
 * Wraps Google Sign-In for the Drive/Docs backup feature.
 *
 * Requests `drive.file` (not full Drive access - the app can only see/manage files it
 * creates itself, keeping this out of Google's "sensitive scope" review) plus the Docs
 * scope needed to create and append to the per-topic Google Docs. A user who connected
 * before the Docs scope was added will fail [getSignedInAccount]'s check below and need
 * to reconnect via the Backup button, which re-runs consent for the full scope set.
 */
object DriveAuth {

    private val DRIVE_FILE_SCOPE = Scope(DriveScopes.DRIVE_FILE)
    private val DOCS_SCOPE = Scope(DocsScopes.DOCUMENTS)

    fun getSignInClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(DRIVE_FILE_SCOPE, DOCS_SCOPE)
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    /**
     * Returns the signed-in account only if it still holds both required scopes - a
     * plain "last signed in" check would also pass for an account that never granted
     * Drive/Docs access, or granted it before the Docs scope was added.
     */
    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return if (GoogleSignIn.hasPermissions(account, DRIVE_FILE_SCOPE, DOCS_SCOPE)) account else null
    }

    fun signOut(context: Context) {
        getSignInClient(context).signOut()
    }
}
