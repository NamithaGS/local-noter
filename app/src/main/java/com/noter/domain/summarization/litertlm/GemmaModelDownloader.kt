package com.noter.domain.summarization.litertlm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the Gemma model [LiteRtLmSummarizationEngine] runs, from its gated Hugging
 * Face repo, using a token the user supplies themselves (see [HuggingFaceTokenStore]) -
 * Google's Gemma license requires accepting terms on huggingface.co before the file can
 * be fetched, which isn't something this app can do on the user's behalf.
 */
object GemmaModelDownloader {

    // "multi-prefill-seq" + no chip suffix is the general CPU/GPU-portable build, as
    // opposed to variants pinned to a specific Mediatek/Snapdragon NPU. q4 keeps the
    // download to ~560MB instead of the ~2GB fp32 file; ekv4096 is the largest context
    // window offered at this size, giving the most headroom for long transcripts.
    private const val MODEL_FILENAME = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"
    private const val DOWNLOAD_URL =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/$MODEL_FILENAME"
    private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024

    fun modelFile(context: Context): File = File(modelDir(context), MODEL_FILENAME)

    private fun modelDir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    /**
     * Downloads the model to its final location, reporting 0f..1f progress via
     * [onProgress]. Streams into a temp file first and renames it into place only once
     * the download completes fully, so a crash or cancellation mid-download never leaves
     * a corrupt file that [LiteRtLmSummarizationEngine] would otherwise treat as ready.
     *
     * @throws IOException if the HTTP request fails (including an invalid/expired token,
     *   or the Gemma license not yet accepted on the account that issued [token])
     */
    suspend fun download(context: Context, token: String, onProgress: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            val temp = File(modelDir(context), "$MODEL_FILENAME.download")
            val connection = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.instanceFollowRedirects = true
            try {
                connection.connect()
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException(
                        "Model download failed: HTTP ${connection.responseCode} " +
                            "${connection.responseMessage} - check that your Hugging Face " +
                            "token is valid and you've accepted the Gemma license at " +
                            "huggingface.co/litert-community/Gemma3-1B-IT"
                    )
                }

                val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
                var bytesRead = 0L
                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read
                            totalBytes?.let { onProgress((bytesRead.toFloat() / it).coerceIn(0f, 1f)) }
                        }
                    }
                }

                if (!temp.renameTo(modelFile(context))) {
                    throw IOException("Couldn't finalize the downloaded model file")
                }
            } catch (e: Exception) {
                temp.delete()
                throw e
            } finally {
                connection.disconnect()
            }
        }
    }
}
