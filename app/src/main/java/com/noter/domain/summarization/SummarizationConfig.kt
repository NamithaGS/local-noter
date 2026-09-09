package com.noter.domain.summarization

import com.noter.domain.summarization.litertlm.LiteRtLmSummarizationEngine

enum class SummarizationBackend { GEMINI_NANO, LITERT_LM }

/**
 * Single switch point between on-device summarization backends - flip [ACTIVE_BACKEND]
 * and every caller (manual "Summarize" button, background transcription pipeline) picks
 * up the change automatically, with no other code changes needed.
 *
 * GEMINI_NANO uses ML Kit's GenAI Summarization API (Gemini Nano via AICore) - the
 * original implementation. As of writing it rejects every summarization attempt with a
 * "policy check failure" even on fully-supported hardware (Pixel 10 Pro), which looks
 * like a beta-API/AICore version-skew issue rather than anything content-related - see
 * [GeminiNanoSummarizationEngine]. Not something this app can fix.
 *
 * LITERT_LM runs a small Gemma model directly via Google's LiteRT-LM API instead,
 * bypassing AICore and its safety-classifier layer entirely. Requires a one-time model
 * download - see [LiteRtLmSummarizationEngine].
 *
 * Flip this back to GEMINI_NANO once Google ships a fix for the summarization beta.
 */
object SummarizationConfig {
    val ACTIVE_BACKEND = SummarizationBackend.LITERT_LM
}
