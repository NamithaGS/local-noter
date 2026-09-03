package com.noter.domain.backup

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

/**
 * Wraps Google Sign-In for the daily Drive backup feature.
 *
 * Requests only the `drive.file` scope, not full Drive access: the app can see and
 * manage only the files it creates itself. That keeps this out of Google's "sensitive
 * scope" verification review, which full Drive access would require before real users
 * could sign in.
 */
object DriveAuth {

    private val DRIVE_FILE_SCOPE = Scope(DriveScopes.DRIVE_FILE)

    fun getSignInClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(DRIVE_FILE_SCOPE)
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    /**
     * Returns the signed-in account only if it still holds the Drive scope - a plain
     * "last signed in" check would also pass for a Google account signed in for
     * something unrelated to Drive.
     */
    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return if (GoogleSignIn.hasPermissions(account, DRIVE_FILE_SCOPE)) account else null
    }

    fun signOut(context: Context) {
        getSignInClient(context).signOut()
    }
}
