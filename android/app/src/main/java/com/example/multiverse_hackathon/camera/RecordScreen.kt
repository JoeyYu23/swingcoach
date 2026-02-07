package com.example.multiverse_hackathon.camera

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.multiverse_hackathon.ui.AppStatus
import com.example.multiverse_hackathon.ui.DebugPanel
import com.example.multiverse_hackathon.ui.ResultArea
import com.example.multiverse_hackathon.ui.SwingViewModel

@Composable
fun RecordScreen(viewModel: SwingViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermissions by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasPermissions = grants[Manifest.permission.CAMERA] == true
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
    }

    if (!hasPermissions) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission required")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    )
                }) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    val cameraManager = remember(lifecycleOwner) {
        CameraManager(context, lifecycleOwner)
    }

    DisposableEffect(cameraManager) {
        onDispose { cameraManager.release() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Camera preview area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onPreviewViewReady = { previewView ->
                    if (!cameraReady) {
                        cameraManager.bindCamera(
                            previewView = previewView,
                            onReady = { cameraReady = true }
                        )
                    }
                }
            )

            // Recording indicator
            if (uiState.status == AppStatus.RECORDING) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .background(
                            Color.Red.copy(alpha = 0.8f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("REC", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Controls
        Column(modifier = Modifier.padding(16.dp)) {
            val isRecording = uiState.status == AppStatus.RECORDING
            val isBusy = uiState.status in setOf(
                AppStatus.UPLOADING, AppStatus.WAITING, AppStatus.SPEAKING
            )

            Button(
                onClick = {
                    if (isRecording) {
                        cameraManager.stopRecording()
                        viewModel.cancelRecordingTimer()
                    } else {
                        viewModel.onRecordingStarted()
                        cameraManager.startRecording(
                            onFinalized = { uri -> viewModel.onRecordingFinalized(uri) },
                            onError = { msg ->
                                // Will be handled by ViewModel error state
                            }
                        )
                        viewModel.startRecordingTimer {
                            cameraManager.stopRecording()
                        }
                    }
                },
                enabled = cameraReady && !isBusy,
                colors = if (isRecording) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRecording) "Stop Recording" else "Start Recording (10s max)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            ResultArea(
                uiState = uiState,
                onStopSpeaking = { viewModel.stopSpeaking() },
                onRetry = { viewModel.retry() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            DebugPanel(uiState = uiState, modifier = Modifier.fillMaxWidth())
        }
    }
}
