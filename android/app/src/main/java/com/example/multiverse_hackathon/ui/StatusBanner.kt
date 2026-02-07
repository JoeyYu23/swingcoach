package com.example.multiverse_hackathon.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatusBanner(status: AppStatus, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        AppStatus.IDLE -> "" to MaterialTheme.colorScheme.surface
        AppStatus.LISTENING -> "Listening..." to MaterialTheme.colorScheme.primaryContainer
        AppStatus.RECORDING -> "Recording..." to MaterialTheme.colorScheme.errorContainer
        AppStatus.UPLOADING -> "Analyzing..." to MaterialTheme.colorScheme.tertiaryContainer
        AppStatus.WAITING -> "Waiting for response..." to MaterialTheme.colorScheme.tertiaryContainer
        AppStatus.SPEAKING -> "Speaking..." to MaterialTheme.colorScheme.primaryContainer
        AppStatus.ERROR -> "Error occurred" to MaterialTheme.colorScheme.errorContainer
    }

    AnimatedVisibility(
        visible = status != AppStatus.IDLE && status != AppStatus.LISTENING,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            color = color,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status in setOf(AppStatus.UPLOADING, AppStatus.WAITING, AppStatus.RECORDING)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
