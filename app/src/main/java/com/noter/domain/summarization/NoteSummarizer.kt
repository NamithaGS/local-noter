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
 * Summarises a transcript on-device with Gemini Nano, via ML Kit's GenAI Summarization API.
 *
 * This runs through AICore, which only exists on a subset of devices (Pixel 8 and newer,
 * Galaxy S24 and newer at time of writing). Everywhere else the feature reports itself
 * unavailable, so summarisation is treated as a best-effort enrichment: a note is still
 * perfectly usable with a transcript and no summary.
 */
class NoteSummarizer(private val context: Context) {

    /** Outcome of a summarisation attempt. Absence of a summary is not an error. */
    sealed interface Result {
        data class Success(val summary: String) : Result

        /** The device cannot run Gemini Nano, or the transcript was too short to bother. */
        data class Skipped(val reason: String) : Result

        data class Failed(val cause: Throwable) : Result
    }

    /**
     * Returns a bulleted summary of [transcript].
     *
     * Never throws: every failure path is reported as [Result.Failed] or [Result.Skipped]
     * so callers can persist the transcript regardless.
     */
    suspend fun summarize(transcript: String): Result = withContext(Dispatchers.IO) {
        if (transcript.length < MIN_TRANSCRIPT_LENGTH) {
            return@withContext Result.Skipped(
                "Transcript is ${transcript.length} chars, below the " +
                    "$MIN_TRANSCRIPT_LENGTH-char threshold"
            )
        }

        val options = SummarizerOptions.builder(context)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
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
                    return@withContext Result.Skipped("Gemini Nano is unavailable on this device")

                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING ->
                    summarizer.awaitFeatureDownload()

                FeatureStatus.AVAILABLE -> Unit

                else -> Log.w(TAG, "Unrecognised feature status: $status; attempting inference")
            }

            summarizer.prepareInferenceEngine().await()

            val request = SummarizationRequest.builder(transcript).build()
            val summary = summarizer.runInference(request).await().summary.trim()

            if (summary.isEmpty()) {
                Result.Skipped("Model returned an empty summary")
            } else {
                Result.Success(summary)
            }
        } catch (e: GenAiException) {
            Log.w(TAG, "Summarisation failed with error code ${e.errorCode}", e)
            Result.Failed(e)
        } catch (e: Exception) {
            Log.w(TAG, "Summarisation failed", e)
            Result.Failed(e)
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
        const val TAG = "NoteSummarizer"

        /** Below this length a "summary" would be as long as the transcript. */
        const val MIN_TRANSCRIPT_LENGTH = 200
    }
}
