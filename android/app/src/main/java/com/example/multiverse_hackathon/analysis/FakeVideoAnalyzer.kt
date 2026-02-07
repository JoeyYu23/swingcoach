package com.example.multiverse_hackathon.analysis

import android.net.Uri
import kotlinx.coroutines.delay

class FakeVideoAnalyzer : VideoAnalyzer {
    override suspend fun analyze(videoUri: Uri): AnalysisResult {
        delay(2000) // Simulate network delay
        return AnalysisResult("Great tennis swing! Your follow-through is excellent, but try to keep your eyes on the ball a bit longer.")
    }
}
