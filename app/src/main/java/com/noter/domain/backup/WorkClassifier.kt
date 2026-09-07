package com.noter.domain.backup

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel

/** Result of classifying one note's transcript for the Work-doc filing pass. */
sealed interface WorkClassification {
    /** [topic] becomes both the routing decision and the topic doc's title. */
    data class Work(val topic: String) : WorkClassification
    object NotWork : WorkClassification
}

/**
 * Decides whether a note belongs in a Work topic doc, and if so, which one.
 *
 * A user-supplied [manualTag] always wins over AI classification - it's a direct,
 * unambiguous signal, whereas asking Gemini Nano fresh each time risks inventing
 * slightly different topic names for what should be the same doc (e.g. "Project Alpha"
 * vs "Alpha Project Notes"), fragmenting a topic across multiple docs.
 *
 * Falls back to [WorkClassification.NotWork] on any failure (model unavailable, device
 * without AICore, download failure) - classification is a best-effort enrichment, never
 * a reason to fail the backup pass.
 */
class WorkClassifier(private val context: Context) {

    suspend fun classify(transcript: String, manualTag: String?): WorkClassification {
        if (!manualTag.isNullOrBlank()) {
            return WorkClassification.Work(manualTag.trim())
        }
        return try {
            classifyWithGeminiNano(transcript)
        } catch (e: Exception) {
            Log.w(TAG, "On-device classification failed, treating as not-work", e)
            WorkClassification.NotWork
        }
    }

    private suspend fun classifyWithGeminiNano(transcript: String): WorkClassification {
        val model: GenerativeModel = Generation.getClient()

        when (model.checkStatus()) {
            FeatureStatus.UNAVAILABLE -> return WorkClassification.NotWork
            FeatureStatus.DOWNLOADABLE -> awaitDownload(model)
            else -> Unit
        }

        val prompt = buildString {
            appendLine(
                "You are sorting personal voice-note transcripts into topics. Read the " +
                    "transcript below."
            )
            appendLine(
                "If it is about work (meetings, projects, tasks, professional " +
                    "discussions), respond with ONLY a short 2-4 word topic name suitable " +
                    "as a document title, e.g. \"Project Alpha\" or \"Team Standups\"."
            )
            appendLine("If it is NOT about work, respond with exactly one word: NONE")
            appendLine()
            appendLine("Transcript:")
            append(transcript)
        }

        val responseText = model.generateContent(prompt).candidates.firstOrNull()?.text
            ?.trim().orEmpty()
        return if (responseText.equals("NONE", ignoreCase = true) || responseText.isEmpty()) {
            WorkClassification.NotWork
        } else {
            // Doc titles should stay short and single-line regardless of what the model
            // actually returned.
            WorkClassification.Work(responseText.lines().first().take(MAX_TOPIC_LENGTH).trim())
        }
    }

    private suspend fun awaitDownload(model: GenerativeModel) {
        model.download().collect { status ->
            if (status is DownloadStatus.DownloadFailed) {
                throw status.e
            }
        }
    }

    private companion object {
        const val TAG = "WorkClassifier"
        const val MAX_TOPIC_LENGTH = 60
    }
}
