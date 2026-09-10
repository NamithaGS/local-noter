package com.noter.domain.summarization

import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Covers only the facade's own policy (the minimum-length skip) - not what a real
 * summarize() call does past that point, since that reaches whichever backend is
 * active (see [SummarizationConfig]) and its real dependencies (AICore, or a
 * downloaded model file), neither available in a plain JVM unit test.
 */
class NoteSummarizerTest {

    private val context: Context = mock(Context::class.java)

    @Test
    fun summarizeSkipsTranscriptBelowMinimumLength() = runTest {
        val result = NoteSummarizer(context).summarize("too short")

        assertTrue(result is SummarizationResult.Skipped)
        assertTrue((result as SummarizationResult.Skipped).reason.contains("40-char threshold"))
    }

    @Test
    fun summarizeSkipsEmptyTranscript() = runTest {
        val result = NoteSummarizer(context).summarize("")

        assertTrue(result is SummarizationResult.Skipped)
    }
}
