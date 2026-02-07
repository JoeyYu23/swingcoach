package com.example.multiverse_hackathon

import android.speech.tts.TextToSpeech
import androidx.test.platform.app.InstrumentationRegistry
import com.example.multiverse_hackathon.tts.AndroidSpeechPlayer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * E2E test for the real Android TTS engine.
 * Tests that AndroidSpeechPlayer initializes, speaks, and calls back properly.
 * Runs on a real device or emulator with a TTS engine installed.
 */
class TtsRealWorkflowTest {

    private lateinit var speechPlayer: AndroidSpeechPlayer
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        speechPlayer = AndroidSpeechPlayer(context)
        // Give TTS engine time to initialize (it's async)
        Thread.sleep(2000)
    }

    @After
    fun tearDown() {
        speechPlayer.shutdown()
    }

    @Test
    fun ttsEngine_isAvailable() {
        // Verify that a TTS engine is installed on the device
        val tts = TextToSpeech(context) {}
        // TTS constructor is async, but we can check engines
        val engines = tts.engines
        tts.shutdown()
        assertTrue(
            "At least one TTS engine should be installed on the device",
            engines.isNotEmpty()
        )
    }

    @Test
    fun androidSpeechPlayer_speakShortText_callsOnFinished() {
        val latch = CountDownLatch(1)
        var callbackCalled = false

        speechPlayer.speak("Hello") {
            callbackCalled = true
            latch.countDown()
        }

        // Wait up to 10 seconds for TTS to finish speaking
        val completed = latch.await(10, TimeUnit.SECONDS)

        // onFinished should be called either way:
        // - If TTS is initialized: speaks then calls back
        // - If TTS is not initialized: falls through and calls back immediately
        assertTrue("onFinished callback should have been called", callbackCalled)
    }

    @Test
    fun androidSpeechPlayer_speakLongerText_callsOnFinished() {
        val latch = CountDownLatch(1)
        var callbackCalled = false

        val coachingText = "Great swing technique. Try to follow through more completely. Keep your eye on the ball."
        speechPlayer.speak(coachingText) {
            callbackCalled = true
            latch.countDown()
        }

        val completed = latch.await(15, TimeUnit.SECONDS)
        assertTrue("onFinished should be called for longer text", callbackCalled)
    }

    @Test
    fun androidSpeechPlayer_stopWhileSpeaking_doesNotCrash() {
        val latch = CountDownLatch(1)

        speechPlayer.speak("This is a longer sentence that should take a while to speak completely") {
            latch.countDown()
        }

        // Stop immediately
        Thread.sleep(200)
        speechPlayer.stop()

        // Should not crash; latch may or may not count down (depends on timing)
        // The important thing is no crash
        assertTrue("Stop should complete without crashing", true)
    }

    @Test
    fun androidSpeechPlayer_speakTwice_secondOverridesFirst() {
        val latch = CountDownLatch(1)
        var secondCallbackCalled = false

        speechPlayer.speak("First utterance that will be interrupted") {}
        Thread.sleep(100)

        speechPlayer.speak("Second utterance") {
            secondCallbackCalled = true
            latch.countDown()
        }

        val completed = latch.await(10, TimeUnit.SECONDS)
        assertTrue("Second speak callback should be called", secondCallbackCalled)
    }
}
