package com.example.multiverse_hackathon.analysis

import android.content.Context
import android.net.Uri
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenRouterAnalyzer(
    private val apiKey: String,
    private val context: Context,
    private val model: String = DEFAULT_MODEL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://openrouter.ai/api/v1"
) : VideoAnalyzer {

    companion object {
        const val DEFAULT_MODEL = "google/gemini-2.5-flash"
        private val SYSTEM_PROMPT = """
            You are a friendly tennis coach. Analyze tennis swing videos and answer
            questions about tennis technique. Keep answers concise (2-3 sentences),
            actionable, and encouraging.
        """.trimIndent()
        private const val MAX_TOKENS = 512
        private val VIDEO_PROMPT = """
            Analyze this tennis swing video. Provide short, actionable coaching feedback
            in 2-3 sentences. Focus on stance, racket path, and follow-through. Be specific
            and encouraging.
        """.trimIndent()
    }

    override suspend fun analyze(videoUri: Uri): AnalysisResult {
        val bytes = withContext(Dispatchers.IO) { readUriBytes(videoUri) }
        return analyzeBytes(bytes)
    }

    override suspend fun askQuestion(
        question: String,
        conversationHistory: List<ConversationTurn>
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val requestBody = buildConversationRequest(question, conversationHistory)
        val response = executeRequest(requestBody)
        parseResponse(response)
    }

    internal suspend fun analyzeBytes(videoBytes: ByteArray): AnalysisResult =
        withContext(Dispatchers.IO) {
            val requestBody = buildVideoRequest(videoBytes)
            val response = executeRequest(requestBody)
            parseResponse(response)
        }

    internal fun buildVideoRequest(videoBytes: ByteArray): String {
        val base64 = Base64.getEncoder().encodeToString(videoBytes)
        val dataUrl = "data:video/mp4;base64,$base64"

        return JSONObject().apply {
            put("model", model)
            put("max_tokens", MAX_TOKENS)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "video_url")
                            put("video_url", JSONObject().apply {
                                put("url", dataUrl)
                            })
                        })
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", VIDEO_PROMPT)
                        })
                    })
                })
            })
        }.toString()
    }

    internal fun buildConversationRequest(
        question: String,
        history: List<ConversationTurn>
    ): String {
        val hasAnalysis = history.any { it.role == "model" }
        val systemPrompt = if (hasAnalysis) {
            SYSTEM_PROMPT
        } else {
            """
                You are a friendly tennis coach. No swing video has been recorded or analyzed yet.
                If the user asks about their swing, tell them you haven't seen it yet and suggest
                they record one first. You can still answer general tennis questions.
                Keep answers concise (2-3 sentences) and encouraging.
            """.trimIndent()
        }

        return JSONObject().apply {
            put("model", model)
            put("max_tokens", MAX_TOKENS)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                for (turn in history) {
                    put(JSONObject().apply {
                        put("role", if (turn.role == "model") "assistant" else turn.role)
                        put("content", turn.text)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", question)
                })
            })
        }.toString()
    }

    private fun executeRequest(body: String): String {
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw IOException("OpenRouter API error ${response.code}: $responseBody")
        }

        return responseBody
    }

    private fun parseResponse(responseBody: String): AnalysisResult {
        try {
            val json = JSONObject(responseBody)
            val text = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            return AnalysisResult(text)
        } catch (e: Exception) {
            throw IOException("Failed to parse OpenRouter response: ${e.message}", e)
        }
    }

    private fun readUriBytes(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot read video from $uri")
    }
}
