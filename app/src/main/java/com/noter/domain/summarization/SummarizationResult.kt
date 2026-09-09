package com.noter.domain.summarization

/**
 * Outcome of a summarization attempt, shared by every [SummarizationEngine] so callers
 * don't need to know which backend actually ran.
 */
sealed interface SummarizationResult {
    data class Success(val summary: String) : SummarizationResult

    /** The engine can't produce a summary for a reason that isn't a failure - e.g. the
     * feature is unavailable on this device, or the transcript was too short to bother. */
    data class Skipped(val reason: String) : SummarizationResult

    data class Failed(val cause: Throwable) : SummarizationResult

    /**
     * The engine needs one-time setup before it can run at all (e.g. the LiteRT-LM
     * backend's model file hasn't been downloaded yet). Kept distinct from [Skipped] so
     * the UI can offer a concrete "set this up" action instead of just showing an error.
     */
    object NeedsSetup : SummarizationResult
}
