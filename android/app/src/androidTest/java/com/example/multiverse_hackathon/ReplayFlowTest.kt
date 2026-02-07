package com.example.multiverse_hackathon

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.multiverse_hackathon.ui.theme.MultiverseHackathonTheme
import org.junit.Rule
import org.junit.Test

class ReplayFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testReplayFlow() {
        // App starts on Replay screen by default in my current MainActivity implementation
        // If not, we would click the Replay nav item
        composeTestRule.onNodeWithText("Replay").performClick()

        // Select a sample
        composeTestRule.onNodeWithText("sample1.mp4").performClick()

        // Click Analyze
        composeTestRule.onNodeWithText("Analyze").performClick()

        // Wait for analysis result (FakeVideoAnalyzer has 2s delay)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Analysis Result:").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify result text is displayed
        composeTestRule.onNodeWithText("Great tennis swing! Your follow-through is excellent, but try to keep your eyes on the ball a bit longer.").assertIsDisplayed()
        
        // Verify Stop Speaking button appears
        composeTestRule.onNodeWithText("Stop Speaking").assertIsDisplayed()
    }
}
