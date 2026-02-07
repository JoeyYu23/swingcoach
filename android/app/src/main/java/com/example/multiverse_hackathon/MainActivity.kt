package com.example.multiverse_hackathon

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.multiverse_hackathon.analysis.FakeVideoAnalyzer
import com.example.multiverse_hackathon.analysis.GeminiVideoAnalyzer
import com.example.multiverse_hackathon.camera.RecordScreen
import com.example.multiverse_hackathon.settings.AnalyzerFactory
import com.example.multiverse_hackathon.settings.LlmSettings
import com.example.multiverse_hackathon.settings.LlmSettingsRepository
import com.example.multiverse_hackathon.tts.AndroidSpeechPlayer
import com.example.multiverse_hackathon.ui.CoachScreen
import com.example.multiverse_hackathon.ui.ReplayScreen
import com.example.multiverse_hackathon.ui.SettingsScreen
import com.example.multiverse_hackathon.ui.SwingViewModel
import com.example.multiverse_hackathon.ui.theme.MultiverseHackathonTheme
import com.example.multiverse_hackathon.voice.VoiceCommandRecognizer

class MainActivity : ComponentActivity() {
    private val speechPlayer by lazy { AndroidSpeechPlayer(this) }
    private var voiceRecognizer: VoiceCommandRecognizer? = null
    private lateinit var settingsRepository: LlmSettingsRepository
    private var currentSettings = LlmSettings()

    private val viewModel by lazy {
        SwingViewModel(
            analyzer = FakeVideoAnalyzer(),
            ttsPlayer = speechPlayer,
            geminiAnalyzerFactory = {
                val key = BuildConfig.GEMINI_API_KEY
                if (key.isBlank()) {
                    throw IllegalStateException("Set GEMINI_API_KEY in local.properties")
                }
                GeminiVideoAnalyzer(apiKey = key, context = applicationContext)
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved LLM settings
        settingsRepository = LlmSettingsRepository(this)
        currentSettings = settingsRepository.load()
        applySettings(currentSettings)

        // Create voice recognizer
        voiceRecognizer = VoiceCommandRecognizer(
            context = this,
            onVoiceInput = { input ->
                // Voice input handling is done in CoachScreen via handleVoiceInput
            }
        )

        enableEdgeToEdge()
        setContent {
            MultiverseHackathonTheme {
                SwingCoachApp(
                    viewModel = viewModel,
                    voiceRecognizer = voiceRecognizer!!,
                    currentSettings = currentSettings,
                    onSaveSettings = { settings ->
                        settingsRepository.save(settings)
                        currentSettings = settings
                        applySettings(settings)
                        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    /**
     * Apply LLM settings by creating the appropriate analyzers.
     * Falls back to BuildConfig.GEMINI_API_KEY when no in-app key is set.
     */
    private fun applySettings(settings: LlmSettings) {
        val buildConfigKey = BuildConfig.GEMINI_API_KEY

        // Resolve video analyzer
        val videoConfig = settings.videoAnalysis
        val videoAnalyzer = if (videoConfig.isConfigured) {
            AnalyzerFactory.create(videoConfig, applicationContext, settings.edgeServerUrl)
        } else if (buildConfigKey.isNotBlank()) {
            // Fallback to BuildConfig key with Gemini
            GeminiVideoAnalyzer(apiKey = buildConfigKey, context = applicationContext)
        } else {
            null
        }

        // Resolve conversation analyzer
        val conversationConfig = settings.conversation
        val textAnalyzer = if (conversationConfig.isConfigured) {
            AnalyzerFactory.create(conversationConfig, applicationContext, settings.edgeServerUrl)
        } else if (buildConfigKey.isNotBlank()) {
            GeminiVideoAnalyzer(apiKey = buildConfigKey, context = applicationContext)
        } else {
            null
        }

        // Update ViewModel with resolved analyzers
        viewModel.updateAnalyzers(
            newVideoAnalyzer = videoAnalyzer,
            newTextAnalyzer = textAnalyzer
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceRecognizer?.destroy()
        speechPlayer.shutdown()
    }
}

@Composable
fun SwingCoachApp(
    viewModel: SwingViewModel,
    voiceRecognizer: VoiceCommandRecognizer,
    currentSettings: LlmSettings,
    onSaveSettings: (LlmSettings) -> Unit
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.COACH) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        val uiState by viewModel.uiState.collectAsState()
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                when (currentDestination) {
                    AppDestinations.COACH -> CoachScreen(viewModel, voiceRecognizer)
                    AppDestinations.RECORD -> RecordScreen(viewModel)
                    AppDestinations.REPLAY -> ReplayScreen(viewModel)
                    AppDestinations.SETTINGS -> SettingsScreen(
                        currentSettings = currentSettings,
                        onSave = onSaveSettings
                    )
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    COACH("Coach", Icons.Default.Mic),
    RECORD("Record", Icons.Default.Videocam),
    REPLAY("Replay", Icons.Default.PlayArrow),
    SETTINGS("Settings", Icons.Default.Settings),
}
