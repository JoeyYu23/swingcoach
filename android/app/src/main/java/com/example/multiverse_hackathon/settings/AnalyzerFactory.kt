package com.example.multiverse_hackathon.settings

import android.content.Context
import com.example.multiverse_hackathon.analysis.EdgeServerAnalyzer
import com.example.multiverse_hackathon.analysis.GeminiVideoAnalyzer
import com.example.multiverse_hackathon.analysis.OpenRouterAnalyzer
import com.example.multiverse_hackathon.analysis.VideoAnalyzer

object AnalyzerFactory {

    fun create(config: LlmSlotConfig, context: Context, edgeServerUrl: String = ""): VideoAnalyzer {
        return when (config.provider) {
            LlmProvider.GEMINI -> GeminiVideoAnalyzer(
                apiKey = config.apiKey,
                context = context,
                model = config.model.ifBlank { GeminiVideoAnalyzer.DEFAULT_MODEL }
            )
            LlmProvider.OPENROUTER -> OpenRouterAnalyzer(
                apiKey = config.apiKey,
                context = context,
                model = config.model.ifBlank { OpenRouterAnalyzer.DEFAULT_MODEL }
            )
            LlmProvider.EDGE_SERVER -> EdgeServerAnalyzer(
                context = context,
                serverUrl = edgeServerUrl
            )
        }
    }
}
