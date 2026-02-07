package com.example.multiverse_hackathon.settings

import android.content.Context
import com.example.multiverse_hackathon.analysis.EdgeServerAnalyzer
import com.example.multiverse_hackathon.analysis.GeminiVideoAnalyzer
import com.example.multiverse_hackathon.analysis.OpenRouterAnalyzer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class AnalyzerFactoryTest {

    private val mockContext: Context = mock()

    @Test
    fun `creates GeminiVideoAnalyzer for GEMINI provider`() {
        val config = LlmSlotConfig(
            provider = LlmProvider.GEMINI,
            apiKey = "test-key",
            model = "gemini-2.5-flash"
        )
        val analyzer = AnalyzerFactory.create(config, mockContext)
        assertTrue(analyzer is GeminiVideoAnalyzer)
    }

    @Test
    fun `creates OpenRouterAnalyzer for OPENROUTER provider`() {
        val config = LlmSlotConfig(
            provider = LlmProvider.OPENROUTER,
            apiKey = "test-key",
            model = "google/gemini-2.5-flash"
        )
        val analyzer = AnalyzerFactory.create(config, mockContext)
        assertTrue(analyzer is OpenRouterAnalyzer)
    }

    @Test
    fun `creates GeminiVideoAnalyzer with blank model uses default`() {
        val config = LlmSlotConfig(
            provider = LlmProvider.GEMINI,
            apiKey = "test-key",
            model = ""
        )
        val analyzer = AnalyzerFactory.create(config, mockContext)
        assertTrue(analyzer is GeminiVideoAnalyzer)
    }

    @Test
    fun `creates OpenRouterAnalyzer with blank model uses default`() {
        val config = LlmSlotConfig(
            provider = LlmProvider.OPENROUTER,
            apiKey = "test-key",
            model = ""
        )
        val analyzer = AnalyzerFactory.create(config, mockContext)
        assertTrue(analyzer is OpenRouterAnalyzer)
    }

    @Test
    fun `creates analyzer with custom model`() {
        val config = LlmSlotConfig(
            provider = LlmProvider.OPENROUTER,
            apiKey = "test-key",
            model = "anthropic/claude-3.5-sonnet"
        )
        val analyzer = AnalyzerFactory.create(config, mockContext)
        assertTrue(analyzer is OpenRouterAnalyzer)
    }

    @Test
    fun `creates EdgeServerAnalyzer for EDGE_SERVER provider`() {
        val config = LlmSlotConfig(
            provider = LlmProvider.EDGE_SERVER,
            apiKey = "",
            model = ""
        )
        val analyzer = AnalyzerFactory.create(config, mockContext, "http://192.168.1.100:8001")
        assertTrue(analyzer is EdgeServerAnalyzer)
    }
}
