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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class EdgeServerAnalyzerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockServer: MockWebServer
    private lateinit var analyzer: EdgeServerAnalyzer

    private val completeResponseJson = """
        {
          "pose_analysis": {
            "total_frames": 30,
            "overall_score": 78.5,
            "max_torso_rotation": 65.3,
            "contact_elbow_angle": 158.2,
            "contact_knee_angle": 145.0,
            "follow_through_completion": 85.0,
            "issues": ["Elbow too bent at contact"],
            "recommendations": ["Extend your arm more"],
            "feedback_text": "Elbow too bent at contact.",
            "frames": []
          },
          "gemini_analysis": {
            "feedback_text": "Drop the racket head sooner.",
            "metric_name": "Racket Drop",
            "metric_value": "Late"
          },
          "audio_base64": "AAAA_base64_audio_data",
          "processing_info": {
            "total_seconds": 4.2,
            "parallel_seconds": 3.1,
            "pose_error": null,
            "gemini_error": null
          }
        }
    """.trimIndent()

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        val mockContext: Context = mock()
        analyzer = EdgeServerAnalyzer(
            context = mockContext,
            serverUrl = mockServer.url("/").toString().trimEnd('/'),
            client = OkHttpClient()
        )
    }

    @After
    fun teardown() {
        mockServer.shutdown()
    }

    @Test
    fun `analyzeBytes parses complete response`() = runTest {
        mockServer.enqueue(MockResponse().setBody(completeResponseJson).setResponseCode(200))

        val result = analyzer.analyzeBytes(byteArrayOf(1, 2, 3))

        assertTrue("Should contain Gemini feedback", result.text.contains("Drop the racket head sooner."))
        assertTrue("Should contain metric", result.text.contains("Racket Drop: Late"))
        assertTrue("Should contain score", result.text.contains("78"))
    }

    @Test
    fun `analyzeBytes sends multipart video`() = runTest {
        mockServer.enqueue(MockResponse().setBody(completeResponseJson).setResponseCode(200))

        analyzer.analyzeBytes(byteArrayOf(1, 2, 3))

        val request = mockServer.takeRequest()
        assertTrue(
            "Should be multipart",
            request.getHeader("Content-Type")!!.contains("multipart/form-data")
        )
        assertTrue("Body should contain video data", request.bodySize > 0)
    }

    @Test
    fun `analyzeBytes sends to correct endpoint`() = runTest {
        mockServer.enqueue(MockResponse().setBody(completeResponseJson).setResponseCode(200))

        analyzer.analyzeBytes(byteArrayOf(1, 2, 3))

        val request = mockServer.takeRequest()
        assertEquals("/api/analyze-swing", request.path)
    }

    @Test(expected = IOException::class)
    fun `analyzeBytes handles server error`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal error"))

        analyzer.analyzeBytes(byteArrayOf(1, 2, 3))
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `askQuestion throws unsupported`() = runTest {
        analyzer.askQuestion("How was my swing?")
    }

    @Test
    fun `formatResponse handles complete JSON`() {
        val json = JSONObject(completeResponseJson)
        val text = analyzer.formatResponse(json)

        assertTrue(text.contains("Drop the racket head sooner."))
        assertTrue(text.contains("Racket Drop: Late"))
        assertTrue(text.contains("78"))
        assertTrue(text.contains("4.2"))
    }

    @Test
    fun `formatResponse handles missing gemini analysis`() {
        val json = JSONObject().apply {
            put("pose_analysis", JSONObject().apply {
                put("overall_score", 72.0)
                put("max_torso_rotation", 55.0)
            })
            put("processing_info", JSONObject().apply {
                put("total_seconds", 3.0)
            })
        }
        val text = analyzer.formatResponse(json)

        assertTrue(text.contains("72"))
        assertTrue(text.contains("55.0"))
    }

    @Test
    fun `formatResponse handles missing pose analysis`() {
        val json = JSONObject().apply {
            put("gemini_analysis", JSONObject().apply {
                put("feedback_text", "Nice swing!")
                put("metric_name", "Follow Through")
                put("metric_value", "Good")
            })
        }
        val text = analyzer.formatResponse(json)

        assertTrue(text.contains("Nice swing!"))
        assertTrue(text.contains("Follow Through: Good"))
    }

    @Test
    fun `extractAudio returns base64 string`() {
        val json = JSONObject(completeResponseJson)
        val audio = analyzer.extractAudio(json)
        assertEquals("AAAA_base64_audio_data", audio)
    }

    @Test
    fun `extractAudio returns null when no audio`() {
        val json = JSONObject().apply {
            put("gemini_analysis", JSONObject().apply {
                put("feedback_text", "test")
            })
        }
        val audio = analyzer.extractAudio(json)
        assertNull(audio)
    }
}
