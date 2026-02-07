# SwingCoach - AI Tennis Coach with Voice Interaction

An always-on conversational AI tennis coach for Android. Talk to it naturally -- say "start recording" and it records your swing, say "stop" and it analyzes the video with Gemini AI, reads coaching feedback aloud, then keeps listening for follow-up questions with full conversation context. Hands-free coaching powered by on-device speech recognition and cloud video analysis.

**Built for the Snapdragon Multiverse Hackathon (Columbia, Feb 2026) -- Track 2: Conversational AI Companion**

## Quick Start

1. **Clone** the repo and open in Android Studio
2. **Get a Gemini API key** from [Google AI Studio](https://aistudio.google.com/apikey)
3. **Add to `local.properties`** (do NOT commit):
   ```
   GEMINI_API_KEY=your_key_here
   ```
4. **Connect** a physical Android device (API 31+ recommended for on-device speech)
5. **Build & run**: `./gradlew installDebug` or click Run in Android Studio
6. The app opens to the **Coach** tab -- grant camera + mic permissions and start talking

## Voice Commands

| Say this | What happens |
|----------|-------------|
| "Start recording" | Begins video recording of your swing |
| "Stop" | Stops recording, uploads to Gemini for analysis |
| Any question | Sends to Gemini as text with conversation context |

**Examples of follow-up questions:**
- "Tell me more about my follow-through"
- "How should I improve my grip?"
- "What drills can I do for that?"

## How It Works

```
LISTENING --> "start recording" --> RECORDING+LISTENING --> "stop" -->
UPLOADING (mic paused) --> SPEAKING (reads analysis) -->
LISTENING --> user asks question --> UPLOADING --> SPEAKING --> LISTENING
```

**Edge AI Pipeline:**
1. **Speech-to-Text**: On-device via Android SpeechRecognizer (Snapdragon NPU on API 31+)
2. **Video Analysis**: Cloud via Gemini 2.5 Flash (inline base64 MP4)
3. **Text-to-Speech**: On-device via Android TTS engine

2 of 3 AI pipelines run entirely on-device. Only video analysis uses the cloud.

**Key design:** Video records WITHOUT audio, keeping the microphone free for voice commands during recording.

## Features

### Coach Mode (Primary)
- Camera preview with voice-driven controls
- Continuous speech recognition with auto-restart
- Conversation transcript with chat bubbles
- Multi-turn context -- follow-up questions reference prior analysis
- Manual mic toggle as safety fallback
- Animated recording indicator

### Replay Mode
- Select sample videos for offline testing
- Toggle between fake analyzer and real Gemini API

### Record Mode
- Manual camera recording with start/stop buttons
- 10-second auto-stop timer

## Architecture

```
com.example.multiverse_hackathon/
├── MainActivity.kt              # Entry point, tab navigation, dependency wiring
├── voice/
│   └── VoiceCommandRecognizer.kt # On-device continuous speech recognition + command parsing
├── analysis/
│   ├── VideoAnalyzer.kt         # Interface + ConversationTurn + AnalysisResult
│   ├── FakeVideoAnalyzer.kt     # Offline fake (2s delay)
│   ├── GeminiVideoAnalyzer.kt   # Gemini REST API: video analysis + text Q&A
│   ├── FrameExtractor.kt        # MediaMetadataRetriever frame extraction
│   └── OkHttpExtensions.kt      # Suspending OkHttp call
├── camera/
│   ├── CameraPreview.kt         # CameraX PreviewView composable
│   ├── CameraManager.kt         # CameraX video-only recording (no audio)
│   └── RecordScreen.kt          # Manual record tab UI
├── tts/
│   └── AndroidSpeechPlayer.kt   # Android TextToSpeech wrapper
├── ui/
│   ├── SwingViewModel.kt        # MVVM state: conversation history, voice handling
│   ├── CoachScreen.kt           # Primary coaching UI (camera + conversation)
│   ├── ReplayScreen.kt          # Sample video replay UI
│   ├── StatusBanner.kt          # Animated status indicator
│   └── DebugPanel.kt            # Debug info display
└── util/
    └── AssetCopier.kt           # Asset to cache file copier
```

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug

# Run unit tests (43 tests)
./gradlew testDebugUnitTest

# Run instrumented tests (requires device)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean assembleDebug
```

## Testing

### Unit Tests (43 tests)
- `VoiceCommandParserTest` -- 12 tests: command parsing, case insensitivity, question detection
- `SwingViewModelTest` -- 18 tests: state machine, conversation history, voice input handling
- `GeminiVideoAnalyzerTest` -- 10 tests: API calls, retry logic, conversation requests
- `FakeSpeechPlayerTest` -- 3 tests: TTS fake behavior

### Instrumented Tests (requires device)
- `ReplayScreenTest` -- E2E Compose UI
- `GeminiApiRealTest` -- Live Gemini API integration
- `TtsRealWorkflowTest` -- Real TTS engine verification
- `DeviceReadinessTest` -- Device capabilities check

## Tech Stack
- Kotlin 2.0, Jetpack Compose, Material 3
- CameraX 1.3.4 (video-only recording)
- Android SpeechRecognizer (on-device STT)
- Android TextToSpeech (on-device TTS)
- OkHttp 4.11 (Gemini REST API)
- Coroutines + StateFlow (async + reactive state)
- Gemini 2.5 Flash (cloud video analysis + conversational Q&A)

## Troubleshooting

- **Speech recognition not working**: Ensure RECORD_AUDIO permission is granted. On-device recognition requires API 31+.
- **TTS not speaking**: Install Google TTS from Play Store if missing.
- **Build fails**: Ensure JDK 17+ and Android SDK 36 are installed.
- **Gemini API errors**: Check your API key, network connection, and rate limits.
- **Camera black on emulator**: Use a physical device for best results. For emulator, set AVD camera to "VirtualScene".
