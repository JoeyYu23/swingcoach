package com.example.multiverse_hackathon.tts

import com.example.multiverse_hackathon.ui.FakeSpeechPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSpeechPlayerTest {

    @Test
    fun `speak records text and calls onFinished`() {
        val player = FakeSpeechPlayer()
        var finished = false
        player.speak("Hello world") { finished = true }
        assertTrue(finished)
        assertEquals("Hello world", player.lastSpokenText)
    }

    @Test
    fun `lastSpokenText is null initially`() {
        val player = FakeSpeechPlayer()
        assertNull(player.lastSpokenText)
    }

    @Test
    fun `stop does not crash`() {
        val player = FakeSpeechPlayer()
        player.stop() // should not throw
    }
}
