package com.noter.domain.transcription

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/**
 * Decodes a compressed audio file into 16-bit signed PCM, mono, at [TARGET_SAMPLE_RATE].
 *
 * Vosk only accepts raw PCM at a known sample rate, but [com.noter.domain.RecordingManager]
 * writes AAC in an MP4 container, so recordings have to be decoded (and if necessary
 * downmixed and resampled) before they can be transcribed.
 */
object PcmAudioDecoder {

    /** Sample rate the Vosk models in this project are trained for. */
    const val TARGET_SAMPLE_RATE = 16_000

    private const val DEQUEUE_TIMEOUT_US = 10_000L

    /**
     * Decodes [file] and hands PCM to [onPcm] in chunks as they become available.
     *
     * Streaming rather than returning one array is deliberate: ten minutes of 16 kHz
     * mono audio is ~19 MB of shorts, and an unconverted 44.1 kHz stereo source is
     * several times that.
     *
     * [onPcm] is called with a buffer and the number of valid samples at its start.
     * The buffer is reused across calls, so consumers must read it, not retain it.
     *
     * @throws IOException if the file has no decodable audio track.
     */
    fun decode(file: File, onPcm: (ShortArray, Int) -> Unit) {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        try {
            val trackIndex = findAudioTrack(extractor)
                ?: throw IOException("No audio track found in ${file.name}")
            extractor.selectTrack(trackIndex)

            val trackFormat = extractor.getTrackFormat(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IOException("Audio track in ${file.name} declares no MIME type")

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(trackFormat, null, null, 0)
                codec.start()
                drain(codec, extractor, trackFormat, onPcm)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        }

    private fun drain(
        codec: MediaCodec,
        extractor: MediaExtractor,
        trackFormat: MediaFormat,
        onPcm: (ShortArray, Int) -> Unit
    ) {
        val bufferInfo = MediaCodec.BufferInfo()

        // The container format is a starting guess; the decoder reports the authoritative
        // values via INFO_OUTPUT_FORMAT_CHANGED before it emits any audio.
        var channelCount = trackFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
        var resampler = Resampler(
            inputRate = trackFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: TARGET_SAMPLE_RATE,
            outputRate = TARGET_SAMPLE_RATE
        )

        var monoBuffer = ShortArray(0)
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(
                            inputIndex, 0, sampleSize, extractor.sampleTime, 0
                        )
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outputFormat = codec.outputFormat
                    channelCount = outputFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT)
                        ?: channelCount
                    val sampleRate = outputFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE)
                        ?: TARGET_SAMPLE_RATE
                    resampler = Resampler(sampleRate, TARGET_SAMPLE_RATE)
                }

                // Covers INFO_TRY_AGAIN_LATER and the deprecated
                // INFO_OUTPUT_BUFFERS_CHANGED: nothing to read, keep pumping input.
                outputIndex < 0 -> Unit

                else -> {
                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val shorts = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val frameCount = shorts.remaining() / channelCount

                        if (monoBuffer.size < frameCount) {
                            monoBuffer = ShortArray(frameCount)
                        }
                        downmixToMono(shorts, frameCount, channelCount, monoBuffer)

                        if (resampler.isPassthrough) {
                            onPcm(monoBuffer, frameCount)
                        } else {
                            val produced = resampler.process(monoBuffer, frameCount)
                            if (produced > 0) onPcm(resampler.output, produced)
                        }
                    }

                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        }
    }

    /**
     * Averages interleaved channels into a single channel. Averaging rather than
     * dropping channels keeps quiet speech audible when it sits in only one channel.
     */
    private fun downmixToMono(
        source: java.nio.ShortBuffer,
        frameCount: Int,
        channelCount: Int,
        destination: ShortArray
    ) {
        if (channelCount == 1) {
            source.get(destination, 0, frameCount)
            return
        }
        for (frame in 0 until frameCount) {
            var sum = 0
            for (channel in 0 until channelCount) {
                sum += source.get().toInt()
            }
            destination[frame] = (sum / channelCount).toShort()
        }
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    /**
     * Linear-interpolating resampler that carries its fractional read position and the
     * trailing input sample across calls, so decoding in chunks produces the same
     * result as resampling the whole stream at once.
     */
    private class Resampler(private val inputRate: Int, private val outputRate: Int) {

        val isPassthrough = inputRate == outputRate

        /** Reused output buffer; only the first `process()` return value is valid. */
        var output = ShortArray(0)
            private set

        private val step = inputRate.toDouble() / outputRate

        /** Absolute read position in the input stream, in input samples. */
        private var position = 0.0

        /** Count of input samples handed over in previous calls. */
        private var consumed = 0L

        /** Last sample of the previous chunk, needed to interpolate across boundaries. */
        private var previous: Short = 0

        fun process(input: ShortArray, length: Int): Int {
            if (length <= 0) return 0

            val capacity = (length.toDouble() * outputRate / inputRate).toInt() + 2
            if (output.size < capacity) {
                output = ShortArray(capacity)
            }

            // An output sample at `position` interpolates between floor(position) and
            // floor(position) + 1, so stop once the upper neighbour would fall outside
            // this chunk and wait for the next one.
            val limit = consumed + length - 1
            var produced = 0
            while (position < limit && produced < output.size) {
                val lowerIndex = kotlin.math.floor(position).toLong()
                val fraction = position - lowerIndex

                val lower = sampleAt(lowerIndex, input)
                val upper = sampleAt(lowerIndex + 1, input)
                output[produced++] = (lower + (upper - lower) * fraction)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()

                position += step
            }

            previous = input[length - 1]
            consumed += length
            return produced
        }

        private fun sampleAt(absoluteIndex: Long, input: ShortArray): Double =
            when {
                absoluteIndex < consumed -> previous.toDouble()
                else -> input[(absoluteIndex - consumed).toInt()].toDouble()
            }
    }
}
