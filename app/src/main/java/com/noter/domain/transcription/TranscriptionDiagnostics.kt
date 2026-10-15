package com.noter.domain.transcription

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/** Level statistics for a run of normalized (-1f..1f) PCM samples. */
data class AudioLevels(val peak: Float, val rms: Float) {

    /** RMS in dBFS, floored at [SILENCE_FLOOR_DB] so digital silence isn't -infinity. */
    val rmsDb: Float
        get() = if (rms <= 0f) SILENCE_FLOOR_DB else maxOf(SILENCE_FLOOR_DB, 20f * log10(rms))

    companion object {
        const val SILENCE_FLOOR_DB = -120f
    }
}

/**
 * Cheap, content-free signals for working out *why* a transcript came out badly, without
 * writing what was said into a log. Both answer a question that otherwise needs the audio
 * itself: "was the input just very quiet?" and "did the model get stuck repeating itself?"
 */
object TranscriptionDiagnostics {

    /**
     * Peak and RMS over the first [length] entries of [samples].
     *
     * Speech at a healthy level sits roughly between -35 and -15 dBFS RMS; a recording
     * that reads far below that is a capture problem (distance, gain), not a model one.
     */
    fun audioLevels(samples: FloatArray, length: Int = samples.size): AudioLevels {
        val count = length.coerceIn(0, samples.size)
        if (count == 0) return AudioLevels(0f, 0f)

        var peak = 0f
        var sumSquares = 0.0
        for (i in 0 until count) {
            val sample = samples[i]
            val magnitude = abs(sample)
            if (magnitude > peak) peak = magnitude
            sumSquares += sample.toDouble() * sample
        }
        return AudioLevels(peak, sqrt(sumSquares / count).toFloat())
    }

    /**
     * Length of the longest run of the same word repeated back-to-back, ignoring case.
     *
     * Transducer models without voice-activity detection tend to emit a short phrase on
     * silence ("THE THE THE THE"); a large value here on a long transcript is the
     * signature of that, as opposed to genuinely poor recognition of real speech.
     */
    fun longestWordRun(text: String): Int {
        var longest = 0
        var current = 0
        var previous: String? = null

        for (word in text.split(Regex("\\s+"))) {
            if (word.isEmpty()) continue
            current = if (previous != null && word.equals(previous, ignoreCase = true)) current + 1 else 1
            previous = word
            if (current > longest) longest = current
        }
        return longest
    }
}
