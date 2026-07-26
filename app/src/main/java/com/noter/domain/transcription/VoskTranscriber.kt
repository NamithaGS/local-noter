package com.noter.domain.transcription

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File
import java.io.IOException

/**
 * Offline speech-to-text over recorded audio files, backed by Vosk.
 *
 * Vosk needs a model directory on the filesystem. The model ships in `assets/` and
 * [StorageService.sync] copies it to external app storage on first use, keyed on a
 * `uuid` file so a rebuilt model replaces the unpacked copy.
 *
 * The model is not in version control because it is tens of megabytes; run
 * `scripts/fetch-vosk-model.sh` once after cloning.
 */
class VoskTranscriber(private val context: Context) {

    /** Raised when the model assets are absent, i.e. the fetch script was never run. */
    class ModelNotInstalledException(message: String, cause: Throwable? = null) :
        IOException(message, cause)

    /**
     * Transcribes [audioFile] and returns the recognised text, or an empty string when
     * the recording contained no intelligible speech.
     *
     * @throws ModelNotInstalledException if the Vosk model is missing from assets.
     * @throws IOException on decode or recognition failure.
     */
    suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        require(audioFile.exists()) { "Audio file does not exist: ${audioFile.absolutePath}" }

        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val modelPath = unpackModel()

        Model(modelPath).use { model ->
            Recognizer(model, PcmAudioDecoder.TARGET_SAMPLE_RATE.toFloat()).use { recognizer ->
                val transcript = StringBuilder()

                PcmAudioDecoder.decode(audioFile) { pcm, sampleCount ->
                    // acceptWaveForm returns true once the recogniser has settled on a
                    // final result for an utterance; partial results are ignored because
                    // getFinalResult() below covers whatever is still buffered.
                    if (recognizer.acceptWaveForm(pcm, sampleCount)) {
                        transcript.appendSegment(recognizer.result)
                    }
                }
                transcript.appendSegment(recognizer.finalResult)

                transcript.toString().trim()
            }
        }
    }

    /**
     * Copies the model out of assets if needed and returns its on-disk path.
     *
     * [StorageService.sync] throws a bare [IOException] whether the assets are missing
     * or the copy failed, so the assets are probed first to produce an error that says
     * what to actually do about it.
     */
    private fun unpackModel(): String {
        val assetNames = runCatching { context.assets.list("")?.toList() }.getOrNull().orEmpty()
        if (MODEL_ASSET_DIR !in assetNames) {
            throw ModelNotInstalledException(
                "Vosk model assets missing: expected app/src/main/assets/$MODEL_ASSET_DIR. " +
                    "Run scripts/fetch-vosk-model.sh and rebuild."
            )
        }

        return try {
            StorageService.sync(context, MODEL_ASSET_DIR, MODEL_TARGET_DIR)
        } catch (e: IOException) {
            throw ModelNotInstalledException("Failed to unpack Vosk model: ${e.message}", e)
        }
    }

    /** Extracts the `text` field Vosk returns as JSON and appends it if non-empty. */
    private fun StringBuilder.appendSegment(resultJson: String) {
        val text = runCatching { JSONObject(resultJson).optString("text").trim() }
            .onFailure { Log.w(TAG, "Unparseable Vosk result: $resultJson", it) }
            .getOrDefault("")

        if (text.isNotEmpty()) {
            if (isNotEmpty()) append(' ')
            append(text)
        }
    }

    private companion object {
        const val TAG = "VoskTranscriber"

        /** Directory under `app/src/main/assets/` holding the model. */
        const val MODEL_ASSET_DIR = "vosk-model-en-us"

        /** Directory under external app storage the model is unpacked into. */
        const val MODEL_TARGET_DIR = "vosk-model-en-us"
    }
}
