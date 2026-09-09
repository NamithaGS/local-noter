package com.noter.domain.summarization.litertlm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.google.ai.edge.litertlm.Message
import com.noter.domain.summarization.SummarizationEngine
import com.noter.domain.summarization.SummarizationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Summarizes a transcript on-device by prompting a small Gemma model directly via
 * Google's LiteRT-LM API, bypassing ML Kit's GenAI Summarization/AICore path entirely -
 * see [com.noter.domain.summarization.SummarizationConfig] for why.
 *
 * The model ([GemmaModelDownloader]) must be downloaded once before this can run; until
 * then [summarize] returns [SummarizationResult.NeedsSetup] so the UI can prompt for
 * setup instead of just showing an opaque failure.
 *
 * The loaded [Engine] is cached in the companion object rather than recreated per call -
 * loading a ~560MB model into memory takes several seconds, so paying that cost once per
 * process lifetime instead of on every [summarize] call is the difference between a
 * usable feature and an unusably slow one.
 */
class LiteRtLmSummarizationEngine(private val context: Context) : SummarizationEngine {

    override suspend fun summarize(transcript: String): SummarizationResult {
        val modelFile = GemmaModelDownloader.modelFile(context)
        if (!modelFile.exists()) return SummarizationResult.NeedsSetup

        return try {
            val engine = getOrCreateEngine(modelFile)
            val response = withContext(Dispatchers.IO) {
                engine.createConversation().use { conversation ->
                    conversation.sendMessage(buildPrompt(transcript.take(MAX_TRANSCRIPT_CHARS)))
                }
            }
            val summary = extractText(response).trim()
            if (summary.isEmpty()) {
                SummarizationResult.Skipped("Model returned an empty summary")
            } else {
                SummarizationResult.Success(summary)
            }
        } catch (e: LiteRtLmJniException) {
            Log.w(TAG, "LiteRT-LM inference failed", e)
            SummarizationResult.Failed(e)
        } catch (e: Exception) {
            Log.w(TAG, "LiteRT-LM inference failed", e)
            SummarizationResult.Failed(e)
        }
    }

    private suspend fun getOrCreateEngine(modelFile: File): Engine {
        cachedEngine?.let { return it }
        return engineMutex.withLock {
            // Re-check inside the lock: another call may have finished initializing
            // while this one was waiting on the mutex.
            cachedEngine ?: withContext(Dispatchers.IO) {
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
                Engine(config).apply { initialize() }
            }.also { cachedEngine = it }
        }
    }

    /** A [Message]'s text lives in a list of [Content] parts; text-only prompts always
     * produce a single [Content.Text] part in response, but concatenating all of them is
     * cheap and correct even if that ever changes. */
    private fun extractText(message: Message): String =
        message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    private fun buildPrompt(transcript: String): String = buildString {
        appendLine(
            "Summarize the following voice-note transcript in exactly three concise " +
                "bullet points. Output ONLY the three bullets, one per line, each " +
                "starting with \"- \". No preamble, no other commentary."
        )
        appendLine()
        appendLine("Transcript:")
        append(transcript)
    }

    private companion object {
        const val TAG = "LiteRtLmSummarizer"

        // Gemma3-1B-IT's KV cache for this build is 4096 tokens, shared between prompt
        // and response. ~4 chars/token is a rough but standard estimate for English
        // text; this caps the transcript well under that so there's still room left for
        // the instruction text and the model's own three-bullet response.
        const val MAX_TRANSCRIPT_CHARS = 10_000

        @Volatile
        private var cachedEngine: Engine? = null
        private val engineMutex = Mutex()
    }
}
