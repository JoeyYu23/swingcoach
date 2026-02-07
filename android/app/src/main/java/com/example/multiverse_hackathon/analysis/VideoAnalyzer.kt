package com.example.multiverse_hackathon.analysis

import android.net.Uri

data class AnalysisResult(val text: String)

data class ConversationTurn(val role: String, val text: String)

interface VideoAnalyzer {
    suspend fun analyze(videoUri: Uri): AnalysisResult

    /**
     * Ask a text-only follow-up question with conversation context.
     * Default implementation throws — override in real analyzers.
     */
    suspend fun askQuestion(
        question: String,
        conversationHistory: List<ConversationTurn> = emptyList()
    ): AnalysisResult = throw UnsupportedOperationException("Text queries not supported")
}
