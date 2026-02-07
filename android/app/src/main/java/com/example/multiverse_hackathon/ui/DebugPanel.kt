package com.example.multiverse_hackathon.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DebugPanel(uiState: SwingUiState, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("DEBUG", style = MaterialTheme.typography.labelSmall)
            Text("Status: ${uiState.status}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Analyzer: ${if (uiState.isRealGemini) "Gemini" else "Fake"}",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Elapsed: ${uiState.elapsedMs}ms", style = MaterialTheme.typography.bodySmall)
            uiState.errorMessage?.let {
                Text(
                    "Error: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
