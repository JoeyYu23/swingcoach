package com.example.multiverse_hackathon.ui

import android.net.Uri
import com.example.multiverse_hackathon.analysis.AnalysisResult
import com.example.multiverse_hackathon.analysis.ConversationTurn
import com.example.multiverse_hackathon.analysis.FakeVideoAnalyzer
import com.example.multiverse_hackathon.analysis.VideoAnalyzer
import com.example.multiverse_hackathon.util.MainDispatcherRule
import com.example.multiverse_hackathon.voice.VoiceAction
import com.example.multiverse_hackathon.voice.VoiceInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SwingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testUri: Uri = mock()

    private fun createViewModel(
        analyzer: VideoAnalyzer = FakeVideoAnalyzer(),
        ttsPlayer: SpeechPlayer = FakeSpeechPlayer()
    ) = SwingViewModel(analyzer, ttsPlayer)

    // --- Existing tests ---

    @Test
    fun `initial state is IDLE with no analysis text`() {
        val vm = createViewModel()
        val state = vm.uiState.value
        assertEquals(AppStatus.IDLE, state.status)
        assertNull(state.analysisText)
        assertNull(state.lastVideoUri)
        assertNull(state.errorMessage)
    }

    @Test
    fun `analyzeVideo transitions through UPLOADING to SPEAKING to LISTENING`() = runTest {
        val fakeTts = FakeSpeechPlayer()
        val vm = createViewModel(ttsPlayer = fakeTts)

        vm.analyzeVideo(testUri)
        advanceUntilIdle()

        // FakeSpeechPlayer calls onFinished synchronously, so we end at LISTENING
        assertEquals(AppStatus.LISTENING, vm.uiState.value.status)
        assertTrue(vm.uiState.value.analysisText!!.isNotEmpty())
        assertEquals(vm.uiState.value.analysisText, fakeTts.lastSpokenText)
    }

    @Test
    fun `analyzeVideo sets ERROR status when analyzer throws`() = runTest {
        val failingAnalyzer = object : VideoAnalyzer {
            override suspend fun analyze(videoUri: Uri): AnalysisResult {
                throw IOException("Network error")
            }
        }
        val vm = createViewModel(analyzer = failingAnalyzer)

        vm.analyzeVideo(testUri)
        advanceUntilIdle()

        assertEquals(AppStatus.ERROR, vm.uiState.value.status)
        assertEquals("Network error", vm.uiState.value.errorMessage)
    }

    @Test
    fun `stopSpeaking resets status to IDLE`() {
        val vm = createViewModel()
        vm.stopSpeaking()
        assertEquals(AppStatus.IDLE, vm.uiState.value.status)
    }

    @Test
    fun `toggleAnalyzer switches isRealGemini flag`() {
        val fakeFactory = { FakeVideoAnalyzer() as VideoAnalyzer }
        val vm = SwingViewModel(FakeVideoAnalyzer(), FakeSpeechPlayer(), fakeFactory)
        assertFalse(vm.uiState.value.isRealGemini)
        vm.toggleAnalyzer(true)
        assertTrue(vm.uiState.value.isRealGemini)
        vm.toggleAnalyzer(false)
        assertFalse(vm.uiState.value.isRealGemini)
    }

    @Test
    fun `retry re-runs analysis with last video uri`() = runTest {
        val failThenSucceed = object : VideoAnalyzer {
            var callCount = 0
            override suspend fun analyze(videoUri: Uri): AnalysisResult {
                callCount++
                if (callCount == 1) throw IOException("fail")
                return AnalysisResult("Success on retry")
            }
        }
        val vm = SwingViewModel(failThenSucceed, FakeSpeechPlayer())
        vm.analyzeVideo(testUri)
        advanceUntilIdle()
        assertEquals(AppStatus.ERROR, vm.uiState.value.status)

        vm.retry()
        advanceUntilIdle()
        assertEquals(AppStatus.LISTENING, vm.uiState.value.status)
        assertEquals("Success on retry", vm.uiState.value.analysisText)
    }

    @Test
    fun `onRecordingStarted sets RECORDING status`() {
        val vm = createViewModel()
        vm.onRecordingStarted()
        assertEquals(AppStatus.RECORDING, vm.uiState.value.status)
    }

    @Test
    fun `onRecordingFinalized triggers analysis`() = runTest {
        val vm = createViewModel()
        vm.onRecordingFinalized(testUri)
        advanceUntilIdle()
        assertEquals(AppStatus.LISTENING, vm.uiState.value.status)
        assertTrue(vm.uiState.value.analysisText!!.isNotEmpty())
    }

    // --- Conversation mode tests ---

    @Test
    fun `askFollowUp adds user turn to conversation history`() = runTest {
        val mockAnalyzer = object : VideoAnalyzer {
            override suspend fun analyze(videoUri: Uri) = AnalysisResult("analysis")
            override suspend fun askQuestion(
                question: String,
                conversationHistory: List<ConversationTurn>
            ) = AnalysisResult("follow-up answer")
        }
        val vm = createViewModel(analyzer = mockAnalyzer)

        vm.askFollowUp("How do I improve?")
        advanceUntilIdle()

        val history = vm.uiState.value.conversationHistory
        assertEquals(2, history.size)
        assertEquals("user", history[0].role)
        assertEquals("How do I improve?", history[0].text)
        assertEquals("model", history[1].role)
        assertEquals("follow-up answer", history[1].text)
    }

    @Test
    fun `askFollowUp transitions UPLOADING to SPEAKING to LISTENING`() = runTest {
        val mockAnalyzer = object : VideoAnalyzer {
            override suspend fun analyze(videoUri: Uri) = AnalysisResult("analysis")
            override suspend fun askQuestion(
                question: String,
                conversationHistory: List<ConversationTurn>
            ) = AnalysisResult("answer")
        }
        val vm = createViewModel(analyzer = mockAnalyzer)

        vm.askFollowUp("test question")
        advanceUntilIdle()

        // FakeSpeechPlayer calls onFinished synchronously → LISTENING
        assertEquals(AppStatus.LISTENING, vm.uiState.value.status)
        assertEquals("answer", vm.uiState.value.analysisText)
    }

    @Test
    fun `askFollowUp sets ERROR when analyzer throws`() = runTest {
        val failingAnalyzer = object : VideoAnalyzer {
            override suspend fun analyze(videoUri: Uri) = AnalysisResult("analysis")
            override suspend fun askQuestion(
                question: String,
                conversationHistory: List<ConversationTurn>
            ): AnalysisResult = throw IOException("Gemini down")
        }
        val vm = createViewModel(analyzer = failingAnalyzer)

        vm.askFollowUp("test")
        advanceUntilIdle()

        assertEquals(AppStatus.ERROR, vm.uiState.value.status)
        assertEquals("Gemini down", vm.uiState.value.errorMessage)
    }

    @Test
    fun `clearConversation resets history`() {
        val vm = createViewModel()
        // Manually set some history
        vm.askFollowUp("test")
        vm.clearConversation()
        assertTrue(vm.uiState.value.conversationHistory.isEmpty())
    }

    @Test
    fun `setListening updates isListening and status`() {
        val vm = createViewModel()
        vm.setListening(true)
        assertTrue(vm.uiState.value.isListening)
        assertEquals(AppStatus.LISTENING, vm.uiState.value.status)
    }

    @Test
    fun `analyzeVideo adds analysis to conversation history`() = runTest {
        val vm = createViewModel()
        vm.analyzeVideo(testUri)
        advanceUntilIdle()

        val history = vm.uiState.value.conversationHistory
        assertEquals(1, history.size)
        assertEquals("model", history[0].role)
        assertTrue(history[0].text.isNotEmpty())
    }

    @Test
    fun `handleVoiceInput START_RECORDING calls onStartRecording`() {
        val vm = createViewModel()
        var startCalled = false
        vm.handleVoiceInput(
            VoiceInput.Command(VoiceAction.START_RECORDING),
            onStartRecording = { startCalled = true },
            onStopRecording = {}
        )
        assertTrue(startCalled)
    }

    @Test
    fun `handleVoiceInput STOP calls onStopRecording`() {
        val vm = createViewModel()
        var stopCalled = false
        vm.handleVoiceInput(
            VoiceInput.Command(VoiceAction.STOP),
            onStartRecording = {},
            onStopRecording = { stopCalled = true }
        )
        assertTrue(stopCalled)
    }

    @Test
    fun `handleVoiceInput Question triggers askFollowUp`() = runTest {
        val mockAnalyzer = object : VideoAnalyzer {
            override suspend fun analyze(videoUri: Uri) = AnalysisResult("analysis")
            override suspend fun askQuestion(
                question: String,
                conversationHistory: List<ConversationTurn>
            ) = AnalysisResult("question answer")
        }
        val vm = createViewModel(analyzer = mockAnalyzer)

        vm.handleVoiceInput(
            VoiceInput.Question("How's my grip?"),
            onStartRecording = {},
            onStopRecording = {}
        )
        advanceUntilIdle()

        assertEquals("How's my grip?", vm.uiState.value.lastTranscript)
        assertEquals("question answer", vm.uiState.value.analysisText)
    }

    // --- Dual-analyzer tests ---

    @Test
    fun `analyzeVideo uses videoAnalyzer not textAnalyzer`() = runTest {
        var videoAnalyzeCalled = false
        var textAnalyzeCalled = false
        val videoAnalyzer = object : VideoAnalyzer {
            override suspend fun analyze(videoUri: Uri): AnalysisResult {
                videoAnalyzeCalled = true
                return AnalysisResult("video result")
            }
        }
        val textAnalyzer = object : VideoAnalyzer {
            override suspend fun analyze(videoUri: Uri): AnalysisResult {
                textAnalyzeCalled = true
                return AnalysisResult("text result")
            }
        }
        val vm = SwingViewModel(videoAnalyzer, textAnalyzer, FakeSpeechPlayer())
        vm.analyzeVideo(testUri)
        advanceUntilIdle()
        assertTrue(videoAnalyzeCalled)
        assertFalse(textAnalyzeCalled)
    }

    @Test
    fun `askFollowUp uses textAnalyzer not videoAnalyzer`() = runTest {
        var videoQuestionCalled = false
        var textQuestionCalled = false
        val videoAnalyzer = object : VideoAnalyzer {
            override suspend fun analyze(videoUri: Uri) = AnalysisResult("video")
            override suspend fun askQuestion(question: String, conversationHistory: List<ConversationTurn>): AnalysisResult {
                videoQuestionCalled = true
                return AnalysisResult("video answer")
            }
        }
        val textAnalyzer = object : VideoAnalyzer {
            override suspend fun analyze(videoUri: Uri) = AnalysisResult("text")
            override suspend fun askQuestion(question: String, conversationHistory: List<ConversationTurn>): AnalysisResult {
                textQuestionCalled = true
                return AnalysisResult("text answer")
            }
        }
        val vm = SwingViewModel(videoAnalyzer, textAnalyzer, FakeSpeechPlayer())
        vm.askFollowUp("test")
        advanceUntilIdle()
        assertFalse(videoQuestionCalled)
        assertTrue(textQuestionCalled)
    }

    @Test
    fun `updateAnalyzers swaps video analyzer`() = runTest {
        val original = FakeVideoAnalyzer()
        var newAnalyzeCalled = false
        val newVideoAnalyzer = object : VideoAnalyzer {
            override suspend fun analyze(videoUri: Uri): AnalysisResult {
                newAnalyzeCalled = true
                return AnalysisResult("new video result")
            }
        }
        val vm = createViewModel(analyzer = original)
        vm.updateAnalyzers(newVideoAnalyzer = newVideoAnalyzer)
        vm.analyzeVideo(testUri)
        advanceUntilIdle()
        assertTrue(newAnalyzeCalled)
    }

    @Test
    fun `updateAnalyzers swaps text analyzer`() = runTest {
        val original = FakeVideoAnalyzer()
        var newQuestionCalled = false
        val newTextAnalyzer = object : VideoAnalyzer {
            override suspend fun analyze(videoUri: Uri) = AnalysisResult("original")
            override suspend fun askQuestion(question: String, conversationHistory: List<ConversationTurn>): AnalysisResult {
                newQuestionCalled = true
                return AnalysisResult("new text answer")
            }
        }
        val vm = createViewModel(analyzer = original)
        vm.updateAnalyzers(newTextAnalyzer = newTextAnalyzer)
        vm.askFollowUp("test")
        advanceUntilIdle()
        assertTrue(newQuestionCalled)
    }

    @Test
    fun `updateAnalyzers null keeps current analyzer`() = runTest {
        var originalCalled = false
        val original = object : VideoAnalyzer {
            override suspend fun analyze(videoUri: Uri): AnalysisResult {
                originalCalled = true
                return AnalysisResult("original")
            }
        }
        val vm = SwingViewModel(original, FakeSpeechPlayer())
        vm.updateAnalyzers(newVideoAnalyzer = null, newTextAnalyzer = null)
        vm.analyzeVideo(testUri)
        advanceUntilIdle()
        assertTrue(originalCalled)
    }

    @Test
    fun `toggleAnalyzer sets both analyzers to same instance`() = runTest {
        var factoryCallCount = 0
        val fakeFactory = {
            factoryCallCount++
            FakeVideoAnalyzer() as VideoAnalyzer
        }
        val vm = SwingViewModel(FakeVideoAnalyzer(), FakeSpeechPlayer(), fakeFactory)
        vm.toggleAnalyzer(true)
        assertEquals(1, factoryCallCount)
        assertTrue(vm.uiState.value.isRealGemini)
    }

    @Test
    fun `handleVoiceInput START_RECORDING clears conversation`() = runTest {
        val mockAnalyzer = object : VideoAnalyzer {
            override suspend fun analyze(videoUri: Uri) = AnalysisResult("analysis")
            override suspend fun askQuestion(
                question: String,
                conversationHistory: List<ConversationTurn>
            ) = AnalysisResult("answer")
        }
        val vm = createViewModel(analyzer = mockAnalyzer)

        // Build up some history
        vm.askFollowUp("question 1")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.conversationHistory.isNotEmpty())

        // Start recording should clear it
        vm.handleVoiceInput(
            VoiceInput.Command(VoiceAction.START_RECORDING),
            onStartRecording = {},
            onStopRecording = {}
        )
        assertTrue(vm.uiState.value.conversationHistory.isEmpty())
    }
}
