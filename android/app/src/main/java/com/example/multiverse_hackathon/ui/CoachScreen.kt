package com.example.multiverse_hackathon.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.multiverse_hackathon.analysis.ConversationTurn
import com.example.multiverse_hackathon.camera.CameraManager
import com.example.multiverse_hackathon.camera.CameraPreview
import com.example.multiverse_hackathon.voice.VoiceCommandRecognizer

@Composable
fun CoachScreen(
    viewModel: SwingViewModel,
    voiceRecognizer: VoiceCommandRecognizer
) {
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by voiceRecognizer.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermissions by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasPermissions = grants[Manifest.permission.CAMERA] == true &&
                grants[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
    }

    // Start/stop voice recognizer based on app status
    LaunchedEffect(uiState.status) {
        when (uiState.status) {
            AppStatus.LISTENING, AppStatus.IDLE, AppStatus.RECORDING -> {
                voiceRecognizer.startListening()
                viewModel.setListening(true)
            }
            AppStatus.UPLOADING, AppStatus.SPEAKING -> {
                voiceRecognizer.stopListening()
                viewModel.setListening(false)
            }
            AppStatus.ERROR -> {
                // Resume listening after error so user can retry via voice
                voiceRecognizer.startListening()
                viewModel.setListening(true)
            }
            AppStatus.WAITING -> {
                voiceRecognizer.stopListening()
                viewModel.setListening(false)
            }
        }
    }

    if (!hasPermissions) {
        PermissionRequest(onGrant = {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        })
        return
    }

    // Create camera manager once we have permissions
    if (cameraManager == null) {
        cameraManager = remember(lifecycleOwner) {
            CameraManager(context, lifecycleOwner)
        }
    }

    DisposableEffect(cameraManager) {
        onDispose { cameraManager?.release() }
    }

    // Wire voice input callback to ViewModel + CameraManager
    LaunchedEffect(cameraManager) {
        val cm = cameraManager
        voiceRecognizer.onVoiceInput = { input ->
            viewModel.handleVoiceInput(
                input = input,
                onStartRecording = {
                    viewModel.onRecordingStarted()
                    cm?.startRecording(
                        onFinalized = { uri -> viewModel.onRecordingFinalized(uri) },
                        onError = { /* handled by ViewModel error state */ }
                    )
                    viewModel.startRecordingTimer {
                        cm?.stopRecording()
                    }
                },
                onStopRecording = {
                    cm?.stopRecording()
                    viewModel.cancelRecordingTimer()
                }
            )
        }
        voiceRecognizer.initialize()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Camera preview (60% of screen)
        Box(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxWidth()
        ) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onPreviewViewReady = { previewView ->
                    if (!cameraReady) {
                        cameraManager?.bindCamera(
                            previewView = previewView,
                            onReady = { cameraReady = true }
                        )
                    }
                }
            )

            // Recording overlay
            if (uiState.status == AppStatus.RECORDING) {
                RecordingOverlay(modifier = Modifier.align(Alignment.TopCenter))
            }
        }

        // Status bar
        CoachStatusBar(
            status = uiState.status,
            isListening = voiceState.isListening,
            lastTranscript = voiceState.lastTranscript,
            onMicToggle = {
                if (voiceState.isListening) {
                    voiceRecognizer.stopListening()
                    viewModel.setListening(false)
                } else {
                    voiceRecognizer.startListening()
                    viewModel.setListening(true)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Conversation transcript (40% of screen)
        ConversationArea(
            history = uiState.conversationHistory,
            status = uiState.status,
            errorMessage = uiState.errorMessage,
            modifier = Modifier
                .weight(0.4f)
                .fillMaxWidth()
        )
    }

}

@Composable
private fun PermissionRequest(onGrant: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Camera & Microphone permission required",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "SwingCoach needs the camera to record your swing\nand the microphone for voice commands.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onGrant) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
private fun RecordingOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_alpha"
    )

    Box(
        modifier = modifier
            .padding(16.dp)
            .alpha(alpha)
            .background(
                Color.Red.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("REC", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CoachStatusBar(
    status: AppStatus,
    isListening: Boolean,
    lastTranscript: String,
    onMicToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = when (status) {
            AppStatus.LISTENING, AppStatus.IDLE -> {
                if (isListening) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            }
            AppStatus.RECORDING -> MaterialTheme.colorScheme.errorContainer
            AppStatus.UPLOADING, AppStatus.WAITING -> MaterialTheme.colorScheme.tertiaryContainer
            AppStatus.SPEAKING -> MaterialTheme.colorScheme.secondaryContainer
            AppStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        },
        label = "status_bg"
    )

    val statusText = when (status) {
        AppStatus.LISTENING, AppStatus.IDLE -> {
            if (isListening) "Listening..." else "Mic paused"
        }
        AppStatus.RECORDING -> "Recording... say \"stop\" when done"
        AppStatus.UPLOADING, AppStatus.WAITING -> "Analyzing..."
        AppStatus.SPEAKING -> "Coach speaking..."
        AppStatus.ERROR -> "Error - say something to try again"
    }

    Row(
        modifier = modifier
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (status in setOf(AppStatus.UPLOADING, AppStatus.WAITING)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column {
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelLarge
                )
                if (lastTranscript.isNotBlank() && isListening) {
                    Text(
                        "\"$lastTranscript\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Manual mic toggle button (safety fallback)
        IconButton(
            onClick = onMicToggle,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isListening)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = if (isListening) "Mute" else "Unmute",
                tint = if (isListening)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ConversationArea(
    history: List<ConversationTurn>,
    status: AppStatus,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem(history.size - 1)
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        if (history.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Say \"start recording\" to begin",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Or ask any tennis question",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(history) { turn ->
                    ConversationBubble(turn)
                }

                // Show loading indicator when uploading
                if (status == AppStatus.UPLOADING || status == AppStatus.WAITING) {
                    item {
                        Row(
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Coach is thinking...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Error overlay
        if (status == AppStatus.ERROR && errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Error: $errorMessage",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ConversationBubble(turn: ConversationTurn) {
    val isUser = turn.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "You" else "Coach",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = turn.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
