package com.example.multiverse_hackathon.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.multiverse_hackathon.analysis.FakeVideoAnalyzer
import com.example.multiverse_hackathon.ui.theme.MultiverseHackathonTheme
import org.junit.Rule
import org.junit.Test

class ReplayScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun launchReplayScreen(
        viewModel: SwingViewModel = SwingViewModel(FakeVideoAnalyzer(), FakeSpeechPlayer())
    ) {
        composeTestRule.setContent {
            MultiverseHackathonTheme {
                ReplayScreen(viewModel)
            }
        }
    }

    @Test
    fun replayFlow_selectSample_analyze_showsResult() {
        val fakeTts = FakeSpeechPlayer()
        val viewModel = SwingViewModel(FakeVideoAnalyzer(), fakeTts)
        launchReplayScreen(viewModel)

        // Select first sample
        composeTestRule.onNodeWithText("sample1.mp4").performClick()

        // Click Analyze
        composeTestRule.onNodeWithText("Analyze").performClick()

        // Wait for fake delay (2s) + processing
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("Analysis Result:", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Verify result text appears
        composeTestRule
            .onNodeWithText("Great tennis swing!", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun analyzeButton_disabledWhenNoSelection() {
        launchReplayScreen()
        composeTestRule.onNodeWithText("Analyze").assertIsNotEnabled()
    }

    @Test
    fun analyzeButton_enabledAfterSelection() {
        launchReplayScreen()
        composeTestRule.onNodeWithText("sample1.mp4").performClick()
        composeTestRule.onNodeWithText("Analyze").assertIsEnabled()
    }
}
