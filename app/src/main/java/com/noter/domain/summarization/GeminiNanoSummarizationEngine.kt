package com.noter.domain.summarization

import android.content.Context
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Summarizes a transcript on-device with Gemini Nano, via ML Kit's GenAI Summarization API.
 *
 * This runs through AICore, which only exists on a subset of devices (Pixel 8 and newer,
 * Galaxy S24 and newer at time of writing) - see [SummarizationConfig] for the current
 * status of this backend, including why it's not the active one right now.
 */
class GeminiNanoSummarizationEngine(private val context: Context) : SummarizationEngine {

    override suspend fun summarize(transcript: String): SummarizationResult = withContext(Dispatchers.IO) {
        val options = SummarizerOptions.builder(context)
            // CONVERSATION, not ARTICLE: these are spoken voice-note transcripts, not
            // formal written text. ARTICLE also turned out to enforce its own internal
            // minimum length (400 chars), which rejected completely normal short notes.
            .setInputType(SummarizerOptions.InputType.CONVERSATION)
            .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            // Recordings can easily exceed the model's context window; truncating beats
            // failing outright for a feature that is already best-effort.
            .setLongInputAutoTruncationEnabled(true)
            .build()

        val summarizer = Summarization.getClient(options)
        try {
            when (val status = summarizer.checkFeatureStatus().await()) {
                FeatureStatus.UNAVAILABLE ->
                    return@withContext SummarizationResult.Skipped("Gemini Nano is unavailable on this device")

                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING ->
                    summarizer.awaitFeatureDownload()

                FeatureStatus.AVAILABLE -> Unit

                else -> Log.w(TAG, "Unrecognised feature status: $status; attempting inference")
            }

            summarizer.prepareInferenceEngine().await()

            val request = SummarizationRequest.builder(transcript).build()
            val summary = summarizer.runInference(request).await().summary.trim()

            if (summary.isEmpty()) {
                SummarizationResult.Skipped("Model returned an empty summary")
            } else {
                SummarizationResult.Success(summary)
            }
        } catch (e: GenAiException) {
            Log.w(TAG, "Summarisation failed with error code ${e.errorCode}", e)
            SummarizationResult.Failed(e)
        } catch (e: Exception) {
            Log.w(TAG, "Summarisation failed", e)
            SummarizationResult.Failed(e)
        } finally {
            summarizer.close()
        }
    }

    /** Waits for AICore to finish fetching the model, surfacing failures as exceptions. */
    private suspend fun Summarizer.awaitFeatureDownload() {
        val callback = object : DownloadCallback {
            override fun onDownloadStarted(bytesToDownload: Long) {
                Log.i(TAG, "Gemini Nano download started: $bytesToDownload bytes")
            }

            override fun onDownloadProgress(totalBytesDownloaded: Long) = Unit

            override fun onDownloadCompleted() {
                Log.i(TAG, "Gemini Nano download completed")
            }

            override fun onDownloadFailed(e: GenAiException) {
                Log.w(TAG, "Gemini Nano download failed", e)
            }
        }
        downloadFeature(callback).await()
    }

    /**
     * Bridges Guava's [ListenableFuture] - which the GenAI APIs return - into a
     * cancellable suspending call, so no thread sits blocked in `get()`.
     */
    private suspend fun <T> ListenableFuture<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            // The listener only runs once the future is done, so get() cannot block here.
            addListener(
                {
                    try {
                        continuation.resume(get())
                    } catch (e: ExecutionException) {
                        continuation.resumeWithException(e.cause ?: e)
                    } catch (e: CancellationException) {
                        continuation.cancel()
                    } catch (e: Throwable) {
                        continuation.resumeWithException(e)
                    }
                },
                Executor { it.run() }
            )
            continuation.invokeOnCancellation { cancel(false) }
        }

    private companion object {
        const val TAG = "GeminiNanoSummarizer"
    }
}
