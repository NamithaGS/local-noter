package com.noter.domain.summarization

import android.content.Context
import com.noter.domain.summarization.litertlm.LiteRtLmSummarizationEngine

/** Picks the concrete [SummarizationEngine] for [SummarizationConfig]'s active backend. */
object SummarizationEngineProvider {
    fun create(context: Context): SummarizationEngine = when (SummarizationConfig.ACTIVE_BACKEND) {
        SummarizationBackend.GEMINI_NANO -> GeminiNanoSummarizationEngine(context)
        SummarizationBackend.LITERT_LM -> LiteRtLmSummarizationEngine(context)
    }
}
