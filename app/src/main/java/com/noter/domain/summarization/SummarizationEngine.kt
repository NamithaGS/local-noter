package com.noter.domain.summarization

/**
 * One pluggable on-device summarization backend.
 *
 * See [SummarizationConfig] for the single switch point between implementations, and
 * [NoteSummarizer] for the facade every caller actually uses.
 */
interface SummarizationEngine {
    suspend fun summarize(transcript: String): SummarizationResult
}
