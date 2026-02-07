package com.example.multiverse_hackathon.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Parsed voice input — either a recognized command or a free-form question.
 */
sealed class VoiceInput {
    data class Command(val action: VoiceAction) : VoiceInput()
    data class Question(val text: String) : VoiceInput()
}

enum class VoiceAction { START_RECORDING, STOP }

data class VoiceState(
    val isListening: Boolean = false,
    val lastTranscript: String = "",
    val error: String? = null
)

/**
 * On-device speech recognizer using Android's SpeechRecognizer API.
 * Uses createOnDeviceSpeechRecognizer() on API 31+ (runs on Snapdragon NPU).
 * Supports continuous listening via auto-restart after each result.
 *
 * Must be created and used on the main thread.
 */
class VoiceCommandRecognizer(
    private val context: Context,
    var onVoiceInput: (VoiceInput) -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var shouldBeListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = _state.value.copy(isListening = true, error = null)
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _state.value = _state.value.copy(isListening = false)
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null // Normal timeout, just restart
                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                else -> "Recognition error: $error"
            }
            _state.value = _state.value.copy(
                isListening = false,
                error = message
            )
            // Auto-restart for expected errors (no match, timeout)
            if (shouldBeListening && error in listOf(
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                )
            ) {
                restartWithDelay()
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val transcript = matches?.firstOrNull() ?: ""

            if (transcript.isNotBlank()) {
                _state.value = _state.value.copy(
                    lastTranscript = transcript,
                    isListening = false
                )
                val input = parseInput(transcript)
                onVoiceInput(input)
            }

            // Auto-restart for continuous listening
            if (shouldBeListening) {
                restartWithDelay()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: ""
            if (partial.isNotBlank()) {
                _state.value = _state.value.copy(lastTranscript = partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun initialize() {
        mainHandler.post {
            speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            speechRecognizer?.setRecognitionListener(listener)
        }
    }

    fun startListening() {
        shouldBeListening = true
        mainHandler.post {
            speechRecognizer?.startListening(recognizerIntent)
        }
    }

    fun stopListening() {
        shouldBeListening = false
        mainHandler.post {
            speechRecognizer?.stopListening()
        }
        _state.value = _state.value.copy(isListening = false)
    }

    fun destroy() {
        shouldBeListening = false
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    private fun restartWithDelay() {
        mainHandler.postDelayed({
            if (shouldBeListening) {
                speechRecognizer?.startListening(recognizerIntent)
            }
        }, 300)
    }

    companion object {
        /**
         * Parse transcript into a VoiceInput (Command or Question).
         * Public for unit testing.
         */
        fun parseInput(transcript: String): VoiceInput {
            val lower = transcript.lowercase().trim()
            return when {
                lower.contains("start recording") ||
                lower.contains("start record") ||
                lower == "record" -> VoiceInput.Command(VoiceAction.START_RECORDING)

                lower == "stop" ||
                lower.contains("stop recording") ||
                lower.contains("stop record") -> VoiceInput.Command(VoiceAction.STOP)

                else -> VoiceInput.Question(transcript)
            }
        }
    }
}
