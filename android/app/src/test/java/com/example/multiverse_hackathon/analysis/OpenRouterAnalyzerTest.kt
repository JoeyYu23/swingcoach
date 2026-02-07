package com.example.multiverse_hackathon.analysis

import android.content.Context
import com.example.multiverse_hackathon.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OpenRouterAnalyzerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockServer: MockWebServer
    private lateinit var analyzer: OpenRouterAnalyzer

    private val successJson = """
        {"choices":[{"message":{"role":"assistant","content":"Great forehand! Try rotating your hips more."}}]}
    """.trimIndent()

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        val mockContext: Context = mock()
        analyzer = OpenRouterAnalyzer(
            apiKey = "test-or-key",
            context = mockContext,
            model = "google/gemini-2.5-flash",
            client = OkHttpClient(),
            baseUrl = mockServer.url("/api/v1").toString().trimEnd('/')
        )
    }

    @After
    fun teardown() {
        mockServer.shutdown()
    }

    // --- analyze() tests ---

    @Test
    fun `analyzeBytes parses successful response`() = runTest {
        mockServer.enqueue(MockResponse().setBody(successJson).setResponseCode(200))

        val result = analyzer.analyzeBytes(byteArrayOf(1, 2, 3))

        assertEquals("Great forehand! Try rotating your hips more.", result.text)
    }

    @Test
    fun `analyzeBytes sends Bearer auth header`() = runTest {
        mockServer.enqueue(MockResponse().setBody(successJson).setResponseCode(200))

        analyzer.analyzeBytes(byteArrayOf(1, 2, 3))

        val request = mockServer.takeRequest()
        assertEquals("Bearer test-or-key", request.getHeader("Authorization"))
    }

    @Test
    fun `analyzeBytes sends to chat completions endpoint`() = runTest {
        mockServer.enqueue(MockResponse().setBody(successJson).setResponseCode(200))

        analyzer.analyzeBytes(byteArrayOf(1, 2, 3))

        val request = mockServer.takeRequest()
        assertTrue(request.path!!.contains("/chat/completions"))
    }

    @Test(expected = IOException::class)
    fun `analyzeBytes throws on HTTP 401`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        analyzer.analyzeBytes(byteArrayOf(1, 2, 3))
    }

    @Test(expected = IOException::class)
    fun `analyzeBytes throws on malformed response`() = runTest {
        mockServer.enqueue(MockResponse().setBody("{\"invalid\":true}").setResponseCode(200))

        analyzer.analyzeBytes(byteArrayOf(1, 2, 3))
    }

    // --- askQuestion() tests ---

    @Test
    fun `askQuestion parses successful response`() = runTest {
        val responseJson = """
            {"choices":[{"message":{"role":"assistant","content":"Keep your wrist firm on the backswing."}}]}
        """.trimIndent()
        mockServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = analyzer.askQuestion("How do I improve my backhand?")

        assertEquals("Keep your wrist firm on the backswing.", result.text)
    }

    @Test
    fun `askQuestion sends conversation history`() = runTest {
        mockServer.enqueue(MockResponse().setBody(successJson).setResponseCode(200))

        val history = listOf(
            ConversationTurn("user", "Analyze my swing"),
            ConversationTurn("model", "Your follow-through is short.")
        )
        analyzer.askQuestion("Tell me more", history)

        val request = mockServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue("Should contain user history", body.contains("Analyze my swing"))
        assertTrue("Should contain model history", body.contains("Your follow-through is short."))
        assertTrue("Should contain new question", body.contains("Tell me more"))
    }

    @Test
    fun `askQuestion maps model role to assistant`() = runTest {
        mockServer.enqueue(MockResponse().setBody(successJson).setResponseCode(200))

        val history = listOf(
            ConversationTurn("model", "Previous analysis")
        )
        analyzer.askQuestion("Follow up", history)

        val request = mockServer.takeRequest()
        val body = request.body.readUtf8()
        val json = JSONObject(body)
        val messages = json.getJSONArray("messages")
        // messages: [system, model→assistant, user]
        // Index 1 should be "assistant" (mapped from "model")
        val historyMsg = messages.getJSONObject(1)
        assertEquals("assistant", historyMsg.getString("role"))
    }

    @Test(expected = IOException::class)
    fun `askQuestion throws on failure`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("Server error"))

        analyzer.askQuestion("test")
    }

    // --- Request building tests ---

    @Test
    fun `buildVideoRequest includes model and video data`() {
        val json = JSONObject(analyzer.buildVideoRequest(byteArrayOf(1, 2, 3)))

        assertEquals("google/gemini-2.5-flash", json.getString("model"))
        val messages = json.getJSONArray("messages")
        assertTrue("Should have system + user messages", messages.length() >= 2)

        // User message should contain video_url content
        val userMsg = messages.getJSONObject(1)
        assertEquals("user", userMsg.getString("role"))
        val content = userMsg.getJSONArray("content")
        val videoContent = content.getJSONObject(0)
        assertEquals("video_url", videoContent.getString("type"))
    }

    @Test
    fun `buildConversationRequest includes system instruction`() {
        val json = analyzer.buildConversationRequest("test", emptyList())
        assertTrue("Should contain tennis coach system prompt", json.contains("tennis coach"))
    }

    @Test
    fun `buildConversationRequest uses no-video prompt when history has no analysis`() {
        val json = analyzer.buildConversationRequest("How was my swing?", emptyList())
        assertTrue("Should mention no video analyzed", json.contains("No swing video has been recorded"))
    }

    @Test
    fun `buildConversationRequest uses analysis prompt when history has model response`() {
        val history = listOf(
            ConversationTurn("model", "Your swing looks good.")
        )
        val json = analyzer.buildConversationRequest("Tell me more", history)
        assertTrue("Should NOT mention no video", !json.contains("No swing video"))
    }

    @Test
    fun `buildConversationRequest preserves message ordering`() {
        val history = listOf(
            ConversationTurn("user", "first msg"),
            ConversationTurn("model", "first reply"),
            ConversationTurn("user", "second msg")
        )
        val json = analyzer.buildConversationRequest("third msg", history)

        val firstIdx = json.indexOf("first msg")
        val replyIdx = json.indexOf("first reply")
        val secondIdx = json.indexOf("second msg")
        val thirdIdx = json.indexOf("third msg")

        assertTrue("Messages should be ordered", firstIdx < replyIdx)
        assertTrue("Messages should be ordered", replyIdx < secondIdx)
        assertTrue("Messages should be ordered", secondIdx < thirdIdx)
    }
}
