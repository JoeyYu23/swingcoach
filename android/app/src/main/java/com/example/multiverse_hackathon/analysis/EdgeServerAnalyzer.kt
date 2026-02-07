package com.example.multiverse_hackathon.analysis

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * VideoAnalyzer that sends video to the PC backend for combined
 * MediaPipe pose estimation + Gemini vision analysis + TTS.
 *
 * Uses multipart/form-data (not base64 JSON) to match FastAPI's File(...) parameter.
 */
class EdgeServerAnalyzer(
    private val context: Context,
    private val serverUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)  // longer — server runs parallel analysis
        .build(),
) : VideoAnalyzer {

    override suspend fun analyze(videoUri: Uri): AnalysisResult {
        val bytes = withContext(Dispatchers.IO) { readUriBytes(videoUri) }
        return analyzeBytes(bytes)
    }

    /**
     * POST video bytes to the PC backend's /api/analyze-swing endpoint.
     * Parses the CompleteAnalysisResponse JSON and formats a user-friendly text.
     */
    internal suspend fun analyzeBytes(
        videoBytes: ByteArray,
        rotation: Int = 0
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "swing.mp4",
                videoBytes.toRequestBody("video/mp4".toMediaType())
            )
            .addFormDataPart("rotation", rotation.toString())
            .build()

        val request = Request.Builder()
            .url("$serverUrl/api/analyze-swing")
            .post(body)
            .build()

        val response = client.newCall(request).await()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No response body"
            throw IOException("Edge server error ${response.code}: $errorBody")
        }

        val json = JSONObject(response.body!!.string())
        AnalysisResult(formatResponse(json))
    }

    /**
     * For text-only follow-ups, fall back to Gemini directly via a simple
     * proxy endpoint (or handle locally). For now, returns a helpful message.
     */
    override suspend fun askQuestion(
        question: String,
        conversationHistory: List<ConversationTurn>
    ): AnalysisResult {
        // Text follow-ups aren't routed through the edge server pipeline.
        // The ViewModel's textAnalyzer (configured separately) handles these.
        throw UnsupportedOperationException(
            "Edge server handles video only. Configure a conversation LLM in Settings."
        )
    }

    private fun readUriBytes(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot read video from $uri")
    }

    /**
     * Format the CompleteAnalysisResponse JSON into a human-readable chat message.
     */
    internal fun formatResponse(json: JSONObject): String {
        val parts = mutableListOf<String>()

        // Gemini AI feedback (primary)
        val gemini = json.optJSONObject("gemini_analysis")
        if (gemini != null) {
            val feedback = gemini.optString("feedback_text", "")
            if (feedback.isNotBlank()) parts.add(feedback)

            val metricName = gemini.optString("metric_name", "")
            val metricValue = gemini.optString("metric_value", "")
            if (metricName.isNotBlank() && metricValue.isNotBlank()) {
                parts.add("$metricName: $metricValue")
            }
        }

        // Pose analysis summary
        val pose = json.optJSONObject("pose_analysis")
        if (pose != null) {
            val score = pose.optDouble("overall_score", -1.0)
            if (score >= 0) {
                parts.add("Swing Score: ${score.toInt()}/100")
            }
            val torso = pose.optDouble("max_torso_rotation", -1.0)
            if (torso >= 0) {
                parts.add("Max Torso Rotation: ${String.format("%.1f", torso)}°")
            }
        }

        // Processing time
        val info = json.optJSONObject("processing_info")
        if (info != null) {
            val total = info.optDouble("total_seconds", -1.0)
            if (total >= 0) {
                parts.add("(Processed in ${String.format("%.1f", total)}s)")
            }
        }

        return if (parts.isNotEmpty()) parts.joinToString("\n") else "Analysis complete."
    }

    /** Audio base64 string from the response, if TTS was generated. */
    internal fun extractAudio(json: JSONObject): String? {
        val audio = json.optString("audio_base64", "")
        return audio.ifEmpty { null }
    }
}
