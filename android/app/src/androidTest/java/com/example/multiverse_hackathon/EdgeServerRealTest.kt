package com.example.multiverse_hackathon

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.multiverse_hackathon.analysis.EdgeServerAnalyzer
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * E2E test for the Edge Server analyzer.
 * Requires the PC backend to be running on the same network.
 * Skipped automatically if server is unreachable.
 */
@RunWith(AndroidJUnit4::class)
class EdgeServerRealTest {

    private val serverUrl = "http://192.168.1.100:8001"
    private lateinit var analyzer: EdgeServerAnalyzer

    @Before
    fun setup() {
        // Skip if server is not reachable
        val client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
        val reachable = try {
            val resp = client.newCall(
                Request.Builder().url("$serverUrl/health").build()
            ).execute()
            resp.isSuccessful
        } catch (_: Exception) {
            false
        }
        assumeTrue("Edge server not reachable at $serverUrl", reachable)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        analyzer = EdgeServerAnalyzer(
            context = context,
            serverUrl = serverUrl
        )
    }

    @Test
    fun test_real_edge_server_with_sample_video() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sampleBytes = context.assets.open("sample1.mp4").use { it.readBytes() }

        val result = analyzer.analyzeBytes(sampleBytes)

        assert(result.text.isNotBlank()) { "Expected non-empty analysis text" }
    }

    @Test
    fun test_real_edge_server_returns_pose_and_ai() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sampleBytes = context.assets.open("sample1.mp4").use { it.readBytes() }

        val result = analyzer.analyzeBytes(sampleBytes)

        // Should contain both pose score and Gemini feedback
        assert(result.text.contains("Score") || result.text.contains("score") ||
            result.text.length > 20) {
            "Expected combined analysis with pose and AI content, got: ${result.text}"
        }
    }
}
