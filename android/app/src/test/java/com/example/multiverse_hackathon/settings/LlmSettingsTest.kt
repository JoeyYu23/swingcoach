package com.example.multiverse_hackathon.settings

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LlmSettingsTest {

    // --- LlmSlotConfig tests ---

    @Test
    fun `isConfigured returns true when apiKey is non-blank`() {
        val config = LlmSlotConfig(apiKey = "sk-1234")
        assertTrue(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when apiKey is blank`() {
        val config = LlmSlotConfig(apiKey = "")
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when apiKey is whitespace only`() {
        val config = LlmSlotConfig(apiKey = "   ")
        assertFalse(config.isConfigured)
    }

    @Test
    fun `default provider is GEMINI`() {
        val config = LlmSlotConfig()
        assertEquals(LlmProvider.GEMINI, config.provider)
    }

    @Test
    fun `default model is empty`() {
        val config = LlmSlotConfig()
        assertEquals("", config.model)
    }

    @Test
    fun `default LlmSettings has unconfigured slots`() {
        val settings = LlmSettings()
        assertFalse(settings.videoAnalysis.isConfigured)
        assertFalse(settings.conversation.isConfigured)
    }

    @Test
    fun `both slots can have different providers`() {
        val settings = LlmSettings(
            videoAnalysis = LlmSlotConfig(provider = LlmProvider.GEMINI, apiKey = "key1"),
            conversation = LlmSlotConfig(provider = LlmProvider.OPENROUTER, apiKey = "key2")
        )
        assertEquals(LlmProvider.GEMINI, settings.videoAnalysis.provider)
        assertEquals(LlmProvider.OPENROUTER, settings.conversation.provider)
    }

    // --- LlmSettingsRepository tests ---

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private val storedValues = mutableMapOf<String, String?>()

    @Before
    fun setup() {
        storedValues.clear()
        mockEditor = mock {
            on { putString(any(), any()) }.thenAnswer { invocation ->
                storedValues[invocation.getArgument(0)] = invocation.getArgument(1)
                mockEditor
            }
            on { apply() }.then { /* no-op */ }
        }
        mockPrefs = mock {
            on { edit() }.thenReturn(mockEditor)
            on { getString(any(), any()) }.thenAnswer { invocation ->
                val key = invocation.getArgument<String>(0)
                val default = invocation.getArgument<String?>(1)
                storedValues[key] ?: default
            }
        }
    }

    private fun createRepository(): LlmSettingsRepository {
        val mockContext: Context = mock()
        whenever(mockContext.getSharedPreferences(eq(LlmSettingsRepository.PREFS_NAME), eq(Context.MODE_PRIVATE)))
            .thenReturn(mockPrefs)
        return LlmSettingsRepository(mockContext)
    }

    @Test
    fun `repository round-trips settings correctly`() {
        val repo = createRepository()
        val settings = LlmSettings(
            videoAnalysis = LlmSlotConfig(LlmProvider.OPENROUTER, "or-key", "gpt-4o"),
            conversation = LlmSlotConfig(LlmProvider.GEMINI, "gem-key", "gemini-2.5-flash")
        )
        repo.save(settings)
        val loaded = repo.load()

        assertEquals(settings.videoAnalysis.provider, loaded.videoAnalysis.provider)
        assertEquals(settings.videoAnalysis.apiKey, loaded.videoAnalysis.apiKey)
        assertEquals(settings.videoAnalysis.model, loaded.videoAnalysis.model)
        assertEquals(settings.conversation.provider, loaded.conversation.provider)
        assertEquals(settings.conversation.apiKey, loaded.conversation.apiKey)
        assertEquals(settings.conversation.model, loaded.conversation.model)
    }

    @Test
    fun `repository returns defaults when prefs are empty`() {
        val repo = createRepository()
        val loaded = repo.load()

        assertEquals(LlmProvider.GEMINI, loaded.videoAnalysis.provider)
        assertEquals("", loaded.videoAnalysis.apiKey)
        assertEquals("", loaded.videoAnalysis.model)
        assertEquals(LlmProvider.GEMINI, loaded.conversation.provider)
    }

    @Test
    fun `repository handles partial config`() {
        val repo = createRepository()
        // Only save video slot
        storedValues["video_provider"] = "OPENROUTER"
        storedValues["video_api_key"] = "test-key"
        storedValues["video_model"] = "claude-3"

        val loaded = repo.load()
        assertEquals(LlmProvider.OPENROUTER, loaded.videoAnalysis.provider)
        assertEquals("test-key", loaded.videoAnalysis.apiKey)
        // Conversation should be defaults
        assertEquals(LlmProvider.GEMINI, loaded.conversation.provider)
        assertEquals("", loaded.conversation.apiKey)
    }

    @Test
    fun `repository handles invalid provider name gracefully`() {
        val repo = createRepository()
        storedValues["video_provider"] = "UNKNOWN_PROVIDER"

        val loaded = repo.load()
        assertEquals(LlmProvider.GEMINI, loaded.videoAnalysis.provider)
    }

    // --- EDGE_SERVER tests ---

    @Test
    fun `isConfigured returns true for EDGE_SERVER even without apiKey`() {
        val config = LlmSlotConfig(provider = LlmProvider.EDGE_SERVER, apiKey = "")
        assertTrue(config.isConfigured)
    }

    @Test
    fun `edge server provider round-trips`() {
        val repo = createRepository()
        val settings = LlmSettings(
            videoAnalysis = LlmSlotConfig(LlmProvider.EDGE_SERVER, "", ""),
            conversation = LlmSlotConfig(LlmProvider.GEMINI, "key", "")
        )
        repo.save(settings)
        val loaded = repo.load()

        assertEquals(LlmProvider.EDGE_SERVER, loaded.videoAnalysis.provider)
    }

    @Test
    fun `edge server url persistence`() {
        val repo = createRepository()
        val settings = LlmSettings(edgeServerUrl = "http://192.168.1.100:8001")
        repo.save(settings)
        val loaded = repo.load()

        assertEquals("http://192.168.1.100:8001", loaded.edgeServerUrl)
    }

    @Test
    fun `default edgeServerUrl is empty`() {
        val settings = LlmSettings()
        assertEquals("", settings.edgeServerUrl)
    }
}
