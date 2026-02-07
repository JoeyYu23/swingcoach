package com.example.multiverse_hackathon.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.multiverse_hackathon.ui.SpeechPlayer
import java.util.Locale
import java.util.UUID

class AndroidSpeechPlayer(context: Context) : SpeechPlayer {
    private var tts: TextToSpeech? = null
    private var onFinishedCallback: (() -> Unit)? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            isInitialized = (status == TextToSpeech.SUCCESS)
            if (isInitialized) {
                tts?.language = Locale.US
            }
        }
    }

    override fun speak(text: String, onFinished: () -> Unit) {
        onFinishedCallback = onFinished
        if (!isInitialized) {
            onFinished()
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onFinishedCallback?.invoke()
            }
            @Deprecated("Deprecated in API")
            override fun onError(utteranceId: String?) {
                onFinishedCallback?.invoke()
            }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
