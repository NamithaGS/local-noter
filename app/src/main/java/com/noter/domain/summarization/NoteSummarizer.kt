package com.noter.domain.summarization

import android.content.Context

/**
 * Facade every caller uses for on-device summarization, regardless of which backend is
 * currently active (see [SummarizationConfig]). Owns the one policy that applies no
 * matter which engine runs underneath: don't bother summarizing a transcript too short
 * to meaningfully condense.
 */
class NoteSummarizer(private val context: Context) {

    suspend fun summarize(transcript: String): SummarizationResult {
        if (transcript.length < MIN_TRANSCRIPT_LENGTH) {
            return SummarizationResult.Skipped(
                "Transcript is ${transcript.length} chars, below the " +
                    "$MIN_TRANSCRIPT_LENGTH-char threshold"
            )
        }
        return SummarizationEngineProvider.create(context).summarize(transcript)
    }

    private companion object {
        /**
         * Below this length a "summary" would be as long as the transcript. 200 was too
         * aggressive for real voice notes - a 154-char transcript is a completely normal
         * short note, not an edge case to skip.
         */
        const val MIN_TRANSCRIPT_LENGTH = 40
    }
}
