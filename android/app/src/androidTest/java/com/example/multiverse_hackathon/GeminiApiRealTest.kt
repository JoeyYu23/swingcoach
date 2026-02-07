package com.example.multiverse_hackathon

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.multiverse_hackathon.analysis.GeminiVideoAnalyzer
import com.example.multiverse_hackathon.util.AssetCopier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * E2E test that calls the real Gemini API with a sample video.
 * Requires: GEMINI_API_KEY set in local.properties AND network connectivity.
 * Skips gracefully if no API key is configured.
 */
class GeminiApiRealTest {

    private lateinit var analyzer: GeminiVideoAnalyzer
    private lateinit var sampleUri: Uri
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        assumeTrue(
            "Skipping real Gemini test: no API key configured",
            apiKey.isNotBlank()
        )
        analyzer = GeminiVideoAnalyzer(apiKey = apiKey, context = context)

        // Copy sample video from assets to cache so we have a real Uri
        sampleUri = AssetCopier.copyAssetToCache(context, "sample1.mp4")
    }

    @Test
    fun realGeminiApi_analyzeSampleVideo_returnsNonEmptyText() = runBlocking {
        val result = analyzer.analyze(sampleUri)

        assertNotNull("Analysis result should not be null", result)
        assertTrue(
            "Analysis text should not be blank, got: '${result.text}'",
            result.text.isNotBlank()
        )
        assertTrue(
            "Analysis text should be at least 10 chars (got ${result.text.length})",
            result.text.length >= 10
        )
    }

    @Test
    fun realGeminiApi_analyzeBytes_returnsCoachingFeedback() = runBlocking {
        // Read the sample video bytes directly
        val bytes = context.assets.open("sample1.mp4").use { it.readBytes() }

        val result = analyzer.analyzeBytes(bytes)

        assertNotNull("Analysis result should not be null", result)
        assertTrue(
            "Response should contain actual text, got: '${result.text}'",
            result.text.isNotBlank()
        )
    }

    @Test
    fun realGeminiApi_analyzeSample2_returnsResult() = runBlocking {
        val uri2 = AssetCopier.copyAssetToCache(context, "sample2.mp4")
        val result = analyzer.analyze(uri2)

        assertNotNull("Analysis result for sample2 should not be null", result)
        assertTrue(
            "Analysis text for sample2 should not be blank",
            result.text.isNotBlank()
        )
    }

    @Test
    fun realGeminiApi_apiKeyInBuildConfig_isNotBlank() {
        val key = BuildConfig.GEMINI_API_KEY
        assertTrue("GEMINI_API_KEY should be set in BuildConfig", key.isNotBlank())
        assertTrue("GEMINI_API_KEY should start with 'AIza'", key.startsWith("AIza"))
    }
}
