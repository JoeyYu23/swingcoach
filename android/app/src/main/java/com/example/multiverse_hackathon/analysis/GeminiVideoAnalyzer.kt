package com.example.multiverse_hackathon.analysis

import android.content.Context
import android.net.Uri
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiVideoAnalyzer(
    private val apiKey: String,
    private val context: Context,
    private val model: String = DEFAULT_MODEL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://generativelanguage.googleapis.com"
) : VideoAnalyzer {

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        private const val MAX_INLINE_SIZE = 15 * 1024 * 1024 // 15MB
        private val PROMPT = """
            Analyze this tennis swing video. Provide short, actionable coaching feedback in 2-3 sentences.
            Focus on stance, racket path, and follow-through. Be specific and encouraging.
        """.trimIndent()
    }

    override suspend fun analyze(videoUri: Uri): AnalysisResult {
        val bytes = withContext(Dispatchers.IO) { readUriBytes(videoUri) }
        return analyzeBytes(bytes)
    }

    /**
     * Send a text-only question to Gemini with conversation history for context.
     * Used for follow-up questions after video analysis.
     */
    override suspend fun askQuestion(
        question: String,
        conversationHistory: List<ConversationTurn>
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val requestBody = buildConversationRequest(question, conversationHistory)
        val request = Request.Builder()
            .url("$baseUrl/v1beta/models/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = executeWithRetry(request)
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No response body"
            throw IOException("Gemini API error ${response.code}: $errorBody")
        }

        val json = JSONObject(response.body!!.string())
        val text = json
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        AnalysisResult(text)
    }

    internal suspend fun analyzeBytes(videoBytes: ByteArray): AnalysisResult =
        withContext(Dispatchers.IO) {
            val requestBody = buildInlineVideoRequest(videoBytes)
            val request = Request.Builder()
                .url("$baseUrl/v1beta/models/$model:generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = executeWithRetry(request)
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No response body"
                throw IOException("Gemini API error ${response.code}: $errorBody")
            }

            val json = JSONObject(response.body!!.string())
            val text = json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            AnalysisResult(text)
        }

    private fun readUriBytes(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot read video from $uri")
    }

    internal fun buildConversationRequest(
        question: String,
        history: List<ConversationTurn>
    ): String {
        val hasAnalysis = history.any { it.role == "model" }
        val systemInstruction = if (hasAnalysis) {
            "You are a friendly tennis coach. " +
                "Answer questions about tennis technique based on the previous swing analysis in the conversation. " +
                "Keep answers concise (2-3 sentences), actionable, and encouraging."
        } else {
            "You are a friendly tennis coach. " +
                "No swing video has been recorded or analyzed yet. " +
                "If the user asks about their swing, tell them you haven't seen it yet and suggest they record one first. " +
                "You can still answer general tennis questions. " +
                "Keep answers concise (2-3 sentences) and encouraging."
        }

        return JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemInstruction) })
                })
            })
            put("contents", JSONArray().apply {
                // Add conversation history
                for (turn in history) {
                    put(JSONObject().apply {
                        put("role", turn.role)
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", turn.text) })
                        })
                    })
                }
                // Add new user question
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", question) })
                    })
                })
            })
        }.toString()
    }

    private fun buildInlineVideoRequest(bytes: ByteArray): String {
        val base64 = Base64.getEncoder().encodeToString(bytes)
        val mimeType = if (bytes.size <= MAX_INLINE_SIZE) "video/mp4" else "image/jpeg"

        return JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", mimeType)
                                put("data", base64)
                            })
                        })
                        put(JSONObject().apply { put("text", PROMPT) })
                    })
                })
            })
        }.toString()
    }

    private suspend fun executeWithRetry(request: Request, maxRetries: Int = 2): okhttp3.Response {
        var lastException: IOException? = null
        repeat(maxRetries + 1) { attempt ->
            try {
                val response = client.newCall(request).await()
                if (response.isSuccessful || response.code !in listOf(429, 503) || attempt == maxRetries) {
                    return response
                }
                response.close()
                delay(1000L * (attempt + 1))
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries) delay(1000L * (attempt + 1))
            }
        }
        throw lastException ?: IOException("Request failed after retries")
    }
}
