package com.example.multiverse_hackathon.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.multiverse_hackathon.analysis.ConversationTurn
import com.example.multiverse_hackathon.analysis.FakeVideoAnalyzer
import com.example.multiverse_hackathon.analysis.VideoAnalyzer
import com.example.multiverse_hackathon.voice.VoiceAction
import com.example.multiverse_hackathon.voice.VoiceInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppStatus {
    IDLE, LISTENING, RECORDING, UPLOADING, WAITING, SPEAKING, ERROR
}

data class SwingUiState(
    val status: AppStatus = AppStatus.IDLE,
    val analysisText: String? = null,
    val lastVideoUri: Uri? = null,
    val errorMessage: String? = null,
    val isRealGemini: Boolean = false,
    val elapsedMs: Long = 0,
    val isListening: Boolean = false,
    val lastTranscript: String = "",
    val conversationHistory: List<ConversationTurn> = emptyList()
)

class SwingViewModel(
    private var videoAnalyzer: VideoAnalyzer,
    private var textAnalyzer: VideoAnalyzer,
    private val ttsPlayer: SpeechPlayer,
    private val geminiAnalyzerFactory: (() -> VideoAnalyzer)? = null
) : ViewModel() {

    /**
     * Secondary constructor for backward compatibility — uses a single analyzer for both slots.
     * All existing tests and ReplayScreen use this constructor.
     */
    constructor(
        analyzer: VideoAnalyzer,
        ttsPlayer: SpeechPlayer,
        geminiAnalyzerFactory: (() -> VideoAnalyzer)? = null
    ) : this(
        videoAnalyzer = analyzer,
        textAnalyzer = analyzer,
        ttsPlayer = ttsPlayer,
        geminiAnalyzerFactory = geminiAnalyzerFactory
    )

    private val _uiState = MutableStateFlow(SwingUiState())
    val uiState: StateFlow<SwingUiState> = _uiState.asStateFlow()

    private var recordingTimerJob: Job? = null

    // --- Conversation mode ---

    /**
     * Handle parsed voice input from VoiceCommandRecognizer.
     * Commands trigger app actions; questions are sent to Gemini.
     */
    fun handleVoiceInput(
        input: VoiceInput,
        onStartRecording: () -> Unit,
        onStopRecording: () -> Unit
    ) {
        when (input) {
            is VoiceInput.Command -> when (input.action) {
                VoiceAction.START_RECORDING -> {
                    clearConversation()
                    onStartRecording()
                }
                VoiceAction.STOP -> {
                    onStopRecording()
                }
            }
            is VoiceInput.Question -> {
                _uiState.value = _uiState.value.copy(
                    lastTranscript = input.text
                )
                askFollowUp(input.text)
            }
        }
    }

    /**
     * Send a text-only follow-up question to Gemini with conversation history.
     */
    fun askFollowUp(question: String) {
        viewModelScope.launch {
            // Add user question to history
            val history = _uiState.value.conversationHistory.toMutableList()
            history.add(ConversationTurn("user", question))

            _uiState.value = _uiState.value.copy(
                status = AppStatus.UPLOADING,
                conversationHistory = history,
                errorMessage = null,
                elapsedMs = 0
            )
            val startTime = System.currentTimeMillis()
            try {
                val result = textAnalyzer.askQuestion(question, history.dropLast(1))
                val elapsed = System.currentTimeMillis() - startTime

                // Add model response to history
                history.add(ConversationTurn("model", result.text))

                _uiState.value = _uiState.value.copy(
                    status = AppStatus.SPEAKING,
                    analysisText = result.text,
                    conversationHistory = history,
                    elapsedMs = elapsed
                )
                speakThenListen(result.text)
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                _uiState.value = _uiState.value.copy(
                    status = AppStatus.ERROR,
                    errorMessage = e.message ?: "Unknown error",
                    elapsedMs = elapsed
                )
            }
        }
    }

    /**
     * Clear conversation history — called when starting a new recording.
     */
    fun clearConversation() {
        _uiState.value = _uiState.value.copy(conversationHistory = emptyList())
    }

    /**
     * Update the listening state indicator (driven by VoiceCommandRecognizer).
     */
    fun setListening(listening: Boolean) {
        _uiState.value = _uiState.value.copy(isListening = listening)
        if (listening && _uiState.value.status == AppStatus.IDLE) {
            _uiState.value = _uiState.value.copy(status = AppStatus.LISTENING)
        }
    }

    // --- Existing replay/record mode ---

    fun analyzeVideo(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                status = AppStatus.UPLOADING,
                lastVideoUri = uri,
                errorMessage = null,
                elapsedMs = 0
            )
            val startTime = System.currentTimeMillis()
            try {
                val result = videoAnalyzer.analyze(uri)
                val elapsed = System.currentTimeMillis() - startTime

                // Add analysis to conversation history for follow-up context
                val history = _uiState.value.conversationHistory.toMutableList()
                history.add(ConversationTurn("model", result.text))

                _uiState.value = _uiState.value.copy(
                    status = AppStatus.SPEAKING,
                    analysisText = result.text,
                    conversationHistory = history,
                    elapsedMs = elapsed
                )
                speakThenListen(result.text)
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                _uiState.value = _uiState.value.copy(
                    status = AppStatus.ERROR,
                    errorMessage = e.message ?: "Unknown error",
                    elapsedMs = elapsed
                )
            }
        }
    }

    fun retry() {
        val uri = _uiState.value.lastVideoUri ?: return
        analyzeVideo(uri)
    }

    fun toggleAnalyzer(useReal: Boolean) {
        if (useReal && geminiAnalyzerFactory != null) {
            try {
                val real = geminiAnalyzerFactory.invoke()
                videoAnalyzer = real
                textAnalyzer = real
                _uiState.value = _uiState.value.copy(isRealGemini = true, errorMessage = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Cannot enable Gemini: ${e.message}"
                )
            }
        } else {
            val fake = FakeVideoAnalyzer()
            videoAnalyzer = fake
            textAnalyzer = fake
            _uiState.value = _uiState.value.copy(isRealGemini = false)
        }
    }

    /**
     * Update one or both analyzer slots independently.
     * Pass null to keep the current analyzer for that slot.
     */
    fun updateAnalyzers(
        newVideoAnalyzer: VideoAnalyzer? = null,
        newTextAnalyzer: VideoAnalyzer? = null
    ) {
        if (newVideoAnalyzer != null) videoAnalyzer = newVideoAnalyzer
        if (newTextAnalyzer != null) textAnalyzer = newTextAnalyzer
    }

    fun onRecordingStarted() {
        _uiState.value = _uiState.value.copy(status = AppStatus.RECORDING, errorMessage = null)
    }

    fun onRecordingFinalized(uri: Uri) {
        analyzeVideo(uri)
    }

    fun startRecordingTimer(onTimeout: () -> Unit) {
        recordingTimerJob = viewModelScope.launch {
            delay(10_000)
            onTimeout()
        }
    }

    fun cancelRecordingTimer() {
        recordingTimerJob?.cancel()
    }

    /**
     * Speak text then return to LISTENING state (for coach mode).
     */
    private fun speakThenListen(text: String) {
        ttsPlayer.speak(text) {
            _uiState.value = _uiState.value.copy(status = AppStatus.LISTENING)
        }
    }

    /**
     * Speak text then return to IDLE (for replay mode, legacy).
     */
    private fun speak(text: String) {
        ttsPlayer.speak(text) {
            _uiState.value = _uiState.value.copy(status = AppStatus.IDLE)
        }
    }

    fun stopSpeaking() {
        ttsPlayer.stop()
        _uiState.value = _uiState.value.copy(status = AppStatus.IDLE)
    }
}

interface SpeechPlayer {
    fun speak(text: String, onFinished: () -> Unit)
    fun stop()
}

class FakeSpeechPlayer : SpeechPlayer {
    var lastSpokenText: String? = null
    override fun speak(text: String, onFinished: () -> Unit) {
        lastSpokenText = text
        onFinished()
    }
    override fun stop() {}
}
