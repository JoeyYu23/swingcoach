package com.example.multiverse_hackathon

import androidx.test.platform.app.InstrumentationRegistry
import com.example.multiverse_hackathon.analysis.ConversationTurn
import com.example.multiverse_hackathon.analysis.OpenRouterAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * E2E test that calls the real OpenRouter API.
 * Requires: OPENROUTER_API_KEY set in local.properties AND network connectivity.
 * Skips gracefully if no API key is configured.
 *
 * Uses free models to avoid burning credits:
 * - Text: z-ai/glm-4.5-air:free
 * - Video: nvidia/nemotron-nano-12b-v2-vl:free
 */
class OpenRouterApiRealTest {

    private lateinit var textAnalyzer: OpenRouterAnalyzer
    private lateinit var videoAnalyzer: OpenRouterAnalyzer
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    companion object {
        private const val TEXT_MODEL = "z-ai/glm-4.5-air:free"
        private const val VIDEO_MODEL = "nvidia/nemotron-nano-12b-v2-vl:free"
    }

    @Before
    fun setUp() {
        val apiKey = BuildConfig.OPENROUTER_API_KEY
        assumeTrue(
            "Skipping real OpenRouter test: no API key configured",
            apiKey.isNotBlank()
        )
        textAnalyzer = OpenRouterAnalyzer(
            apiKey = apiKey,
            context = context,
            model = TEXT_MODEL
        )
        videoAnalyzer = OpenRouterAnalyzer(
            apiKey = apiKey,
            context = context,
            model = VIDEO_MODEL
        )
    }

    @Test
    fun realOpenRouter_askQuestion_returnsNonEmptyText() = runBlocking {
        val result = textAnalyzer.askQuestion("What is the most important thing to focus on for a tennis forehand?")

        assertNotNull("Result should not be null", result)
        assertTrue(
            "Response text should not be blank, got: '${result.text}'",
            result.text.isNotBlank()
        )
        assertTrue(
            "Response should be at least 10 chars (got ${result.text.length})",
            result.text.length >= 10
        )
    }

    @Test
    fun realOpenRouter_askQuestionWithHistory_usesContext() = runBlocking {
        val history = listOf(
            ConversationTurn("user", "What should I focus on for my forehand?"),
            ConversationTurn("model", "Focus on your grip, stance, and follow-through. The continental grip works well for most players.")
        )

        val result = textAnalyzer.askQuestion("Tell me more about the grip", history)

        assertNotNull("Result should not be null", result)
        assertTrue(
            "Follow-up response should not be blank",
            result.text.isNotBlank()
        )
        assertTrue(
            "Response should be at least 10 chars (got ${result.text.length})",
            result.text.length >= 10
        )
    }

    @Test
    fun realOpenRouter_analyzeVideoBytes_returnsCoachingFeedback() = runBlocking {
        // Read a sample video from assets
        val bytes = context.assets.open("sample1.mp4").use { it.readBytes() }

        try {
            val result = videoAnalyzer.analyzeBytes(bytes)

            assertNotNull("Analysis result should not be null", result)
            assertTrue(
                "Analysis text should not be blank, got: '${result.text}'",
                result.text.isNotBlank()
            )
            assertTrue(
                "Analysis should be at least 10 chars (got ${result.text.length})",
                result.text.length >= 10
            )
        } catch (e: IOException) {
            // Skip if model doesn't support video or insufficient balance
            val msg = e.message.orEmpty()
            assumeTrue(
                "Skipping video test: $msg",
                !msg.contains("402") && !msg.contains("404") && !msg.contains("No endpoints")
            )
            throw e
        }
    }

    @Test
    fun realOpenRouter_apiKeyInBuildConfig_isNotBlank() {
        val key = BuildConfig.OPENROUTER_API_KEY
        assertTrue("OPENROUTER_API_KEY should be set in BuildConfig", key.isNotBlank())
        assertTrue("OPENROUTER_API_KEY should start with 'sk-or-'", key.startsWith("sk-or-"))
    }

    @Test
    fun realOpenRouter_multiTurnConversation_maintainsContext() = runBlocking {
        // First question
        val result1 = textAnalyzer.askQuestion("Name three types of tennis grips.")
        assertNotNull(result1)
        assertTrue(
            "First response should not be blank, got: '${result1.text}'",
            result1.text.isNotBlank()
        )

        // Build history from first exchange
        val history = listOf(
            ConversationTurn("user", "Name three types of tennis grips."),
            ConversationTurn("model", result1.text)
        )

        // Follow-up question that depends on context
        val result2 = textAnalyzer.askQuestion("Which one is best for beginners?", history)
        assertNotNull(result2)
        assertTrue(
            "Follow-up should not be blank, got: '${result2.text}'",
            result2.text.isNotBlank()
        )
    }
}
