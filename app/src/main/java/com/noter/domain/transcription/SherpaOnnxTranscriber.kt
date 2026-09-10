package com.noter.domain.transcription

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Offline speech-to-text over recorded audio files, backed by sherpa-onnx running a
 * Zipformer transducer model - see `scripts/fetch-sherpa-model.sh` for exactly which
 * one and why (replaced Vosk: noticeably more accurate at a comparable model size).
 *
 * The model files ship in `assets/` and are copied to app-private filesystem storage on
 * first use - ONNX Runtime needs real file paths, not asset streams. They are not in
 * version control because they're ~125MB combined; run `scripts/fetch-sherpa-model.sh`
 * once after cloning.
 */
class SherpaOnnxTranscriber(private val context: Context) {

    /** Raised when the model assets are absent, i.e. the fetch script was never run. */
    class ModelNotInstalledException(message: String, cause: Throwable? = null) :
        IOException(message, cause)

    /**
     * Transcribes [audioFile] and returns the recognised text, or an empty string when
     * the recording contained no intelligible speech.
     *
     * @throws ModelNotInstalledException if the sherpa-onnx model is missing from assets.
     * @throws IOException on decode or recognition failure.
     */
    suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        require(audioFile.exists()) { "Audio file does not exist: ${audioFile.absolutePath}" }

        val modelDir = unpackModel()
        val recognizer = createRecognizer(modelDir)
        try {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(decodeToFloatSamples(audioFile), PcmAudioDecoder.TARGET_SAMPLE_RATE)
                recognizer.decode(stream)
                recognizer.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        } finally {
            recognizer.release()
        }
    }

    /**
     * Decodes [audioFile] into one normalized float32 PCM buffer, in the -1f..1f range
     * sherpa-onnx expects.
     *
     * Unlike Vosk's incremental streaming recogniser, sherpa-onnx's *offline* API wants
     * the entire utterance in a single `acceptWaveform()` call, so [PcmAudioDecoder]'s
     * chunked callback output is accumulated into one growable buffer here rather than
     * fed through directly.
     */
    private fun decodeToFloatSamples(audioFile: File): FloatArray {
        var buffer = FloatArray(INITIAL_SAMPLE_CAPACITY)
        var length = 0

        PcmAudioDecoder.decode(audioFile) { pcm, sampleCount ->
            if (length + sampleCount > buffer.size) {
                buffer = buffer.copyOf(maxOf(buffer.size * 2, length + sampleCount))
            }
            for (i in 0 until sampleCount) {
                buffer[length + i] = pcm[i] / 32768f
            }
            length += sampleCount
        }

        return if (length == buffer.size) buffer else buffer.copyOf(length)
    }

    private fun createRecognizer(modelDir: File): OfflineRecognizer {
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = PcmAudioDecoder.TARGET_SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = File(modelDir, "encoder.onnx").absolutePath,
                    decoder = File(modelDir, "decoder.onnx").absolutePath,
                    joiner = File(modelDir, "joiner.onnx").absolutePath
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                modelType = "transducer"
            )
        )
        return try {
            OfflineRecognizer(config = config)
        } catch (e: IllegalArgumentException) {
            // OfflineRecognizer's own init{} throws exactly this (via require()) when
            // the native side fails to load the model files - most commonly because
            // they're missing or corrupt, which unpackModel() already checked for
            // proactively, but a corrupt/partial file wouldn't be caught by that.
            throw ModelNotInstalledException("Failed to load sherpa-onnx model: ${e.message}", e)
        }
    }

    /**
     * Copies the model out of assets on first use and returns its on-disk directory.
     *
     * Re-checks only for `tokens.txt` (the smallest file) as the "already unpacked"
     * marker - cheap, and good enough since all four files are written together in one
     * pass below with no partial-success path.
     */
    private fun unpackModel(): File {
        val assetNames = runCatching { context.assets.list("")?.toList() }.getOrNull().orEmpty()
        if (MODEL_ASSET_DIR !in assetNames) {
            throw ModelNotInstalledException(
                "sherpa-onnx model assets missing: expected app/src/main/assets/$MODEL_ASSET_DIR. " +
                    "Run scripts/fetch-sherpa-model.sh and rebuild."
            )
        }

        val targetDir = File(context.filesDir, MODEL_ASSET_DIR)
        if (File(targetDir, "tokens.txt").exists()) return targetDir

        targetDir.mkdirs()
        try {
            for (fileName in MODEL_FILES) {
                context.assets.open("$MODEL_ASSET_DIR/$fileName").use { input ->
                    File(targetDir, fileName).outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (e: IOException) {
            targetDir.deleteRecursively()
            throw ModelNotInstalledException("Failed to unpack sherpa-onnx model: ${e.message}", e)
        }
        return targetDir
    }

    private companion object {
        /** Directory under `app/src/main/assets/` holding the model. */
        const val MODEL_ASSET_DIR = "sherpa-onnx-en"

        val MODEL_FILES = listOf("encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt")

        /** ~1 minute of 16kHz mono audio; decodeToFloatSamples grows this as needed. */
        const val INITIAL_SAMPLE_CAPACITY = 16_000 * 60
    }
}
