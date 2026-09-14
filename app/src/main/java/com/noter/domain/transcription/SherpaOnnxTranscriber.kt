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
     * Feeds sherpa-onnx's *offline* (non-streaming) recogniser fixed-size chunks (see
     * [CHUNK_DURATION_SECONDS]) rather than the whole recording in one `acceptWaveform()`
     * call - that worked fine for short notes, but a ~30 minute meeting is ~28 million
     * samples as one buffer, which is not what this API is built to process as a single
     * utterance and risks an OutOfMemoryError or a native call slow enough to get the
     * background job killed. Chunking bounds memory and per-call compute to a fixed size
     * regardless of recording length. The trade-off is a word occasionally getting cut at
     * a chunk boundary and mis-recognised right at the seam - a small accuracy cost next
     * to "doesn't transcribe at all."
     *
     * @throws ModelNotInstalledException if the sherpa-onnx model is missing from assets.
     * @throws IOException on decode or recognition failure.
     */
    suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        require(audioFile.exists()) { "Audio file does not exist: ${audioFile.absolutePath}" }

        val modelDir = unpackModel()
        val recognizer = createRecognizer(modelDir)
        try {
            val transcript = StringBuilder()
            val chunk = FloatArray(CHUNK_SAMPLE_CAPACITY)
            var chunkLength = 0

            fun flushChunk() {
                if (chunkLength == 0) return
                val text = recognize(recognizer, chunk, chunkLength)
                if (text.isNotEmpty()) {
                    if (transcript.isNotEmpty()) transcript.append(' ')
                    transcript.append(text)
                }
                chunkLength = 0
            }

            PcmAudioDecoder.decode(audioFile) { pcm, sampleCount ->
                var offset = 0
                while (offset < sampleCount) {
                    val spaceLeft = chunk.size - chunkLength
                    val take = minOf(spaceLeft, sampleCount - offset)
                    for (i in 0 until take) {
                        chunk[chunkLength + i] = pcm[offset + i] / 32768f
                    }
                    chunkLength += take
                    offset += take
                    if (chunkLength == chunk.size) flushChunk()
                }
            }
            flushChunk() // the final, shorter-than-a-full-chunk remainder

            transcript.toString().trim()
        } finally {
            recognizer.release()
        }
    }

    /** Runs one chunk through a fresh stream and returns its recognised text. */
    private fun recognize(recognizer: OfflineRecognizer, samples: FloatArray, length: Int): String {
        val stream = recognizer.createStream()
        try {
            val chunkSamples = if (length == samples.size) samples else samples.copyOf(length)
            stream.acceptWaveform(chunkSamples, PcmAudioDecoder.TARGET_SAMPLE_RATE)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
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

        /**
         * Long enough that per-chunk overhead (re-running the encoder on a short clip)
         * stays negligible, short enough to keep memory and per-call compute bounded
         * regardless of how long the recording is. Not tuned beyond "reasonable" - revisit
         * if word-boundary mis-recognition at chunk seams turns out to matter in practice.
         */
        const val CHUNK_DURATION_SECONDS = 30
        const val CHUNK_SAMPLE_CAPACITY = PcmAudioDecoder.TARGET_SAMPLE_RATE * CHUNK_DURATION_SECONDS
    }
}
