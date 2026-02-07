package com.example.multiverse_hackathon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.multiverse_hackathon.settings.LlmProvider
import com.example.multiverse_hackathon.settings.LlmSettings
import com.example.multiverse_hackathon.settings.LlmSlotConfig

@Composable
fun SettingsScreen(
    currentSettings: LlmSettings,
    onSave: (LlmSettings) -> Unit
) {
    var videoConfig by remember(currentSettings) {
        mutableStateOf(currentSettings.videoAnalysis)
    }
    var conversationConfig by remember(currentSettings) {
        mutableStateOf(currentSettings.conversation)
    }
    var edgeServerUrl by remember(currentSettings) {
        mutableStateOf(currentSettings.edgeServerUrl)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "LLM Settings",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Configure providers for video analysis and conversation independently.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Edge Server URL (shown when either slot uses EDGE_SERVER)
        EdgeServerCard(
            serverUrl = edgeServerUrl,
            onUrlChange = { edgeServerUrl = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SlotConfigCard(
            title = "Video Analysis LLM",
            subtitle = "Analyzes tennis swing videos (needs multimodal support)",
            config = videoConfig,
            onConfigChange = { videoConfig = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SlotConfigCard(
            title = "Conversation LLM",
            subtitle = "Handles follow-up text questions",
            config = conversationConfig,
            onConfigChange = { conversationConfig = it }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                onSave(LlmSettings(videoConfig, conversationConfig, edgeServerUrl))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Settings")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Settings are saved on this device. API keys from local.properties are used as fallback when no key is set here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EdgeServerCard(
    serverUrl: String,
    onUrlChange: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Edge Server (PC)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Set the URL of the PC running the SwingCoach backend. " +
                    "Select 'Edge server' as provider above to route video analysis through it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = onUrlChange,
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.1.100:8001") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SlotConfigCard(
    title: String,
    subtitle: String,
    config: LlmSlotConfig,
    onConfigChange: (LlmSlotConfig) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Provider selector
            Text("Provider", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LlmProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = config.provider == provider,
                        onClick = {
                            onConfigChange(config.copy(provider = provider))
                        },
                        label = {
                            Text(
                                when (provider) {
                                    LlmProvider.EDGE_SERVER -> "Edge server"
                                    else -> provider.name.lowercase()
                                        .replaceFirstChar { it.uppercase() }
                                }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // API Key
            var showKey by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = config.apiKey,
                onValueChange = { onConfigChange(config.copy(apiKey = it)) },
                label = { Text("API Key") },
                placeholder = {
                    Text(
                        if (config.provider == LlmProvider.GEMINI) "AIza..."
                        else "sk-or-..."
                    )
                },
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    androidx.compose.material3.IconButton(onClick = { showKey = !showKey }) {
                        Text(
                            if (showKey) "Hide" else "Show",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Model
            OutlinedTextField(
                value = config.model,
                onValueChange = { onConfigChange(config.copy(model = it)) },
                label = { Text("Model") },
                placeholder = {
                    Text(
                        if (config.provider == LlmProvider.GEMINI) "gemini-2.5-flash"
                        else "google/gemini-2.5-flash"
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
