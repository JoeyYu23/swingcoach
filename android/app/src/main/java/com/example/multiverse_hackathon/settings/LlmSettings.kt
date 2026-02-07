package com.example.multiverse_hackathon.settings

import android.content.Context
import android.content.SharedPreferences

enum class LlmProvider {
    GEMINI, OPENROUTER, EDGE_SERVER
}

data class LlmSlotConfig(
    val provider: LlmProvider = LlmProvider.GEMINI,
    val apiKey: String = "",
    val model: String = ""
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() || provider == LlmProvider.EDGE_SERVER
}

data class LlmSettings(
    val videoAnalysis: LlmSlotConfig = LlmSlotConfig(),
    val conversation: LlmSlotConfig = LlmSlotConfig(),
    val edgeServerUrl: String = ""
)

class LlmSettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): LlmSettings = LlmSettings(
        videoAnalysis = loadSlot(PREFIX_VIDEO),
        conversation = loadSlot(PREFIX_CONVERSATION),
        edgeServerUrl = prefs.getString(KEY_EDGE_SERVER_URL, "") ?: ""
    )

    fun save(settings: LlmSettings) {
        prefs.edit().apply {
            saveSlot(PREFIX_VIDEO, settings.videoAnalysis)
            saveSlot(PREFIX_CONVERSATION, settings.conversation)
            putString(KEY_EDGE_SERVER_URL, settings.edgeServerUrl)
            apply()
        }
    }

    private fun loadSlot(prefix: String): LlmSlotConfig {
        val providerName = prefs.getString("${prefix}_provider", LlmProvider.GEMINI.name)
            ?: LlmProvider.GEMINI.name
        return LlmSlotConfig(
            provider = try {
                LlmProvider.valueOf(providerName)
            } catch (_: IllegalArgumentException) {
                LlmProvider.GEMINI
            },
            apiKey = prefs.getString("${prefix}_api_key", "") ?: "",
            model = prefs.getString("${prefix}_model", "") ?: ""
        )
    }

    private fun SharedPreferences.Editor.saveSlot(prefix: String, config: LlmSlotConfig) {
        putString("${prefix}_provider", config.provider.name)
        putString("${prefix}_api_key", config.apiKey)
        putString("${prefix}_model", config.model)
    }

    companion object {
        internal const val PREFS_NAME = "llm_settings"
        private const val PREFIX_VIDEO = "video"
        private const val PREFIX_CONVERSATION = "conversation"
        private const val KEY_EDGE_SERVER_URL = "edge_server_url"
    }
}
