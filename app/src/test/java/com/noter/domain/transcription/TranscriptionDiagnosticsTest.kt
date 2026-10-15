package com.noter.domain.transcription

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class TranscriptionDiagnosticsTest {

    @Test
    fun silenceHasZeroLevelsAndFloorsAtMinusOneTwentyDb() {
        val levels = TranscriptionDiagnostics.audioLevels(FloatArray(1000))

        assertEquals(0f, levels.peak, 0f)
        assertEquals(0f, levels.rms, 0f)
        assertEquals(AudioLevels.SILENCE_FLOOR_DB, levels.rmsDb, 0f)
    }

    @Test
    fun emptyInputIsTreatedAsSilenceRatherThanDividingByZero() {
        val levels = TranscriptionDiagnostics.audioLevels(FloatArray(10), length = 0)

        assertEquals(0f, levels.rms, 0f)
    }

    @Test
    fun fullScaleSquareWaveIsZeroDbfs() {
        val samples = FloatArray(1000) { if (it % 2 == 0) 1f else -1f }

        val levels = TranscriptionDiagnostics.audioLevels(samples)

        assertEquals(1f, levels.peak, 1e-6f)
        assertEquals(0f, levels.rmsDb, 1e-3f)
    }

    @Test
    fun sineWaveRmsIsPeakOverRootTwo() {
        val samples = FloatArray(16_000) { (0.5 * sin(2 * PI * 440 * it / 16_000)).toFloat() }

        val levels = TranscriptionDiagnostics.audioLevels(samples)

        assertEquals(0.5f, levels.peak, 1e-3f)
        assertEquals(0.5f / 1.41421356f, levels.rms, 1e-3f)
    }

    @Test
    fun onlyTheRequestedPrefixIsMeasured() {
        // Chunk buffers are reused, so stale data past `length` must be ignored.
        val samples = FloatArray(100) { if (it < 10) 0.1f else 1f }

        val levels = TranscriptionDiagnostics.audioLevels(samples, length = 10)

        assertEquals(0.1f, levels.peak, 1e-6f)
    }

    @Test
    fun longestWordRunFindsConsecutiveRepeatsIgnoringCase() {
        assertEquals(4, TranscriptionDiagnostics.longestWordRun("THE THE the The and then"))
    }

    @Test
    fun longestWordRunIsOneForNormalTextAndZeroForEmpty() {
        assertEquals(1, TranscriptionDiagnostics.longestWordRun("we shipped the release today"))
        assertEquals(0, TranscriptionDiagnostics.longestWordRun("   "))
    }

    @Test
    fun nonConsecutiveRepeatsDoNotCountAsARun() {
        assertEquals(1, TranscriptionDiagnostics.longestWordRun("the cat and the dog and the bird"))
    }
}
