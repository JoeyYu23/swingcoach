package com.example.multiverse_hackathon.analysis

import android.content.Context
import com.example.multiverse_hackathon.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiVideoAnalyzerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockServer: MockWebServer
    private lateinit var analyzer: GeminiVideoAnalyzer

    private val successJson = """
        {"candidates":[{"content":{"parts":[{"text":"Good swing! Keep your elbow higher on the backswing."}]}}]}
    """.trimIndent()

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        val mockContext: Context = mock()
        analyzer = GeminiVideoAnalyzer(
            apiKey = "test-api-key",
            context = mockContext,
            client = OkHttpClient(),
            baseUrl = mockServer.url("/").toString().trimEnd('/')
        )
    }

    @After
    fun teardown() {
        mockServer.shutdown()
    }

    @Test
    fun `analyzeBytes parses successful response`() = runTest {
        mockServer.enqueue(MockResponse().setBody(successJson).setResponseCode(200))

        val result = analyzer.analyzeBytes(byteArrayOf(1, 2, 3))

        assertEquals("Good swing! Keep your elbow higher on the backswing.", result.text)
    }

    @Test
    fun `analyzeBytes sends correct request headers and path`() = runTest {
        mockServer.enqueue(MockResponse().setBody(successJson).setResponseCode(200))

        analyzer.analyzeBytes(byteArrayOf(1, 2, 3))

        val request = mockServer.takeRequest()
        assertEquals("test-api-key", request.getHeader("x-goog-api-key"))
        assertTrue(request.path!!.contains("gemini-2.5-flash:generateContent"))
        assertTrue(request.body.size > 0)
    }

    @Test
    fun `analyzeBytes retries on 429 then succeeds`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(429).setBody("Rate limited"))
        mockServer.enqueue(MockResponse().setBody(successJson).setResponseCode(200))

        val result = analyzer.analyzeBytes(byteArrayOf(1, 2, 3))

        assertEquals("Good swing! Keep your elbow higher on the backswing.", result.text)
        assertEquals(2, mockServer.requestCount)
    }

    @Test(expected = IOException::class)
    fun `analyzeBytes throws on non-retryable error`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(400).setBody("Bad request"))

        analyzer.analyzeBytes(byteArrayOf(1, 2, 3))
    }

    // --- askQuestion() tests ---

    @Test
    fun `askQuestion parses successful response`() = runTest {
        val responseJson = """
            {"candidates":[{"content":{"parts":[{"text":"Try keeping your wrist firm during the backswing."}]}}]}
        """.trimIndent()
        mockServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = analyzer.askQuestion("How do I improve my backhand?")

        assertEquals("Try keeping your wrist firm during the backswing.", result.text)
    }

    @Test
    fun `askQuestion sends conversation history in request`() = runTest {
        mockServer.enqueue(MockResponse().setBody(successJson).setResponseCode(200))

        val history = listOf(
            ConversationTurn("user", "Analyze my swing"),
            ConversationTurn("model", "Your swing looks good but follow-through is short.")
        )
        analyzer.askQuestion("Tell me more about follow-through", history)

        val request = mockServer.takeRequest()
        val body = request.body.readUtf8()
        // Verify history turns are included
        assertTrue("Should contain user history", body.contains("Analyze my swing"))
        assertTrue("Should contain model history", body.contains("follow-through is short"))
        // Verify new question is included
        assertTrue("Should contain new question", body.contains("Tell me more about follow-through"))
    }

    @Test
    fun `askQuestion sends correct endpoint and API key`() = runTest {
        mockServer.enqueue(MockResponse().setBody(successJson).setResponseCode(200))

        analyzer.askQuestion("What about my grip?")

        val request = mockServer.takeRequest()
        assertEquals("test-api-key", request.getHeader("x-goog-api-key"))
        assertTrue(request.path!!.contains("gemini-2.5-flash:generateContent"))
    }

    @Test
    fun `askQuestion works with empty history`() = runTest {
        mockServer.enqueue(MockResponse().setBody(successJson).setResponseCode(200))

        val result = analyzer.askQuestion("General tennis tips?", emptyList())

        assertEquals("Good swing! Keep your elbow higher on the backswing.", result.text)
    }

    @Test
    fun `buildConversationRequest includes system instruction`() {
        val json = analyzer.buildConversationRequest("test question", emptyList())
        assertTrue("Should contain system instruction", json.contains("tennis coach"))
    }

    @Test
    fun `buildConversationRequest uses no-video prompt when history has no analysis`() {
        val json = analyzer.buildConversationRequest("How was my swing?", emptyList())
        assertTrue("Should mention no video analyzed", json.contains("No swing video has been recorded"))
    }

    @Test
    fun `buildConversationRequest uses analysis prompt when history has model response`() {
        val history = listOf(
            ConversationTurn("model", "Your swing looks good, nice follow-through.")
        )
        val json = analyzer.buildConversationRequest("Tell me more", history)
        assertTrue("Should reference previous analysis", json.contains("previous swing analysis"))
        assertTrue("Should NOT mention no video", !json.contains("No swing video"))
    }

    @Test
    fun `buildConversationRequest preserves role ordering`() {
        val history = listOf(
            ConversationTurn("user", "first message"),
            ConversationTurn("model", "first reply"),
            ConversationTurn("user", "second message"),
            ConversationTurn("model", "second reply")
        )
        val json = analyzer.buildConversationRequest("third message", history)

        // All messages should be present
        assertTrue(json.contains("first message"))
        assertTrue(json.contains("first reply"))
        assertTrue(json.contains("second message"))
        assertTrue(json.contains("second reply"))
        assertTrue(json.contains("third message"))

        // Verify ordering: each message appears after the previous one
        val firstIdx = json.indexOf("first message")
        val firstReplyIdx = json.indexOf("first reply")
        val secondIdx = json.indexOf("second message")
        val thirdIdx = json.indexOf("third message")
        assertTrue("Messages should be ordered", firstIdx < firstReplyIdx)
        assertTrue("Messages should be ordered", firstReplyIdx < secondIdx)
        assertTrue("Messages should be ordered", secondIdx < thirdIdx)
    }
}
