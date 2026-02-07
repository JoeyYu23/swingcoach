package com.example.multiverse_hackathon.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceCommandParserTest {

    @Test
    fun parseInput_startRecording_returnsStartCommand() {
        val result = VoiceCommandRecognizer.parseInput("start recording")
        assertTrue(result is VoiceInput.Command)
        assertEquals(VoiceAction.START_RECORDING, (result as VoiceInput.Command).action)
    }

    @Test
    fun parseInput_startRecord_returnsStartCommand() {
        val result = VoiceCommandRecognizer.parseInput("start record")
        assertTrue(result is VoiceInput.Command)
        assertEquals(VoiceAction.START_RECORDING, (result as VoiceInput.Command).action)
    }

    @Test
    fun parseInput_record_returnsStartCommand() {
        val result = VoiceCommandRecognizer.parseInput("record")
        assertTrue(result is VoiceInput.Command)
        assertEquals(VoiceAction.START_RECORDING, (result as VoiceInput.Command).action)
    }

    @Test
    fun parseInput_stop_returnsStopCommand() {
        val result = VoiceCommandRecognizer.parseInput("stop")
        assertTrue(result is VoiceInput.Command)
        assertEquals(VoiceAction.STOP, (result as VoiceInput.Command).action)
    }

    @Test
    fun parseInput_stopRecording_returnsStopCommand() {
        val result = VoiceCommandRecognizer.parseInput("stop recording")
        assertTrue(result is VoiceInput.Command)
        assertEquals(VoiceAction.STOP, (result as VoiceInput.Command).action)
    }

    @Test
    fun parseInput_caseInsensitive_startRecording() {
        val result = VoiceCommandRecognizer.parseInput("Start Recording")
        assertTrue(result is VoiceInput.Command)
        assertEquals(VoiceAction.START_RECORDING, (result as VoiceInput.Command).action)
    }

    @Test
    fun parseInput_caseInsensitive_stop() {
        val result = VoiceCommandRecognizer.parseInput("STOP")
        assertTrue(result is VoiceInput.Command)
        assertEquals(VoiceAction.STOP, (result as VoiceInput.Command).action)
    }

    @Test
    fun parseInput_question_returnsQuestion() {
        val result = VoiceCommandRecognizer.parseInput("how do I improve my backhand?")
        assertTrue(result is VoiceInput.Question)
        assertEquals("how do I improve my backhand?", (result as VoiceInput.Question).text)
    }

    @Test
    fun parseInput_tellMeMore_returnsQuestion() {
        val result = VoiceCommandRecognizer.parseInput("tell me more about my follow-through")
        assertTrue(result is VoiceInput.Question)
        assertEquals("tell me more about my follow-through", (result as VoiceInput.Question).text)
    }

    @Test
    fun parseInput_preservesOriginalCase_inQuestion() {
        val result = VoiceCommandRecognizer.parseInput("What Should I Do Better?")
        assertTrue(result is VoiceInput.Question)
        assertEquals("What Should I Do Better?", (result as VoiceInput.Question).text)
    }

    @Test
    fun parseInput_withLeadingTrailingSpaces_trimmed() {
        val result = VoiceCommandRecognizer.parseInput("  stop  ")
        assertTrue(result is VoiceInput.Command)
        assertEquals(VoiceAction.STOP, (result as VoiceInput.Command).action)
    }

    @Test
    fun parseInput_embeddedStartRecording_returnsCommand() {
        val result = VoiceCommandRecognizer.parseInput("please start recording my swing")
        assertTrue(result is VoiceInput.Command)
        assertEquals(VoiceAction.START_RECORDING, (result as VoiceInput.Command).action)
    }
}
