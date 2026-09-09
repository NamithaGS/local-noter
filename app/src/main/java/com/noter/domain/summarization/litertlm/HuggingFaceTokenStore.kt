package com.noter.domain.summarization.litertlm

import android.content.Context

/**
 * Stores the user's own Hugging Face access token, used to download the gated Gemma
 * model file [LiteRtLmSummarizationEngine] runs - the user generates this themselves
 * (after accepting Gemma's license on huggingface.co) and pastes it in; this app never
 * sees or handles their Hugging Face account password.
 *
 * Plain SharedPreferences, matching [com.noter.domain.backup.BackupStatusStore]'s
 * existing precedent in this codebase - reasonable for a single-user, local-only
 * personal app. A published multi-user app would warrant EncryptedSharedPreferences
 * instead.
 */
object HuggingFaceTokenStore {
    private const val PREFS_NAME = "litertlm_prefs"
    private const val KEY_TOKEN = "hf_token"

    fun getToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun setToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
