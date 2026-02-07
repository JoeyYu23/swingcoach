package com.example.multiverse_hackathon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.multiverse_hackathon.util.AssetCopier

@Composable
fun ReplayScreen(viewModel: SwingViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val samples = listOf("sample1.mp4", "sample2.mp4")
    var selectedSample by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Select a sample video:", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(samples) { sample ->
                val isSelected = selectedSample == sample
                Card(
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { selectedSample = sample }
                ) {
                    ListItem(
                        headlineContent = { Text(sample) }
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text("Use Gemini API", modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.isRealGemini,
                onCheckedChange = { viewModel.toggleAnalyzer(it) },
                enabled = uiState.status in setOf(AppStatus.IDLE, AppStatus.LISTENING)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                selectedSample?.let { sample ->
                    val uri = AssetCopier.copyAssetToCache(context, sample)
                    viewModel.analyzeVideo(uri)
                }
            },
            enabled = selectedSample != null && uiState.status in setOf(AppStatus.IDLE, AppStatus.LISTENING),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Analyze")
        }

        Spacer(modifier = Modifier.height(16.dp))

        ResultArea(
            uiState = uiState,
            onStopSpeaking = { viewModel.stopSpeaking() },
            onRetry = { viewModel.retry() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        DebugPanel(uiState = uiState, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ResultArea(
    uiState: SwingUiState,
    onStopSpeaking: () -> Unit,
    onRetry: () -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Status: ${uiState.status}", style = MaterialTheme.typography.labelLarge)

            uiState.analysisText?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Analysis Result:", style = MaterialTheme.typography.titleMedium)
                Text(it)
            }

            if (uiState.status == AppStatus.SPEAKING) {
                Button(onClick = onStopSpeaking, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Stop Speaking")
                }
            }

            if (uiState.status == AppStatus.ERROR) {
                uiState.errorMessage?.let {
                    Text("Error: $it", color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Retry")
                }
            }
        }
    }
}
