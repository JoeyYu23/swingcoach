# SwingCoach

**AI tennis coach powered by on-device vision inference and multi-device orchestration.**

Record your swing on a Galaxy S25, get instant spoken coaching feedback analyzed by a vision language model running locally on a Copilot+ PC -- no cloud required.

Built for the **Qualcomm Snapdragon Multiverse Hackathon** at Columbia University, February 2026.

**Track 2: Conversational AI Companion** -- voice-driven wellness coaching with real-time contextually aware responses.

## Team

| Name | Email |
|------|-------|
| James Zhang | tz2642@columbia.edu  |
| Chenyang Yu | cy2758@columbia.edu  |
| Chihao Yu   | chy007@ucsd.edu      |
| Yang Zhou   | nbzy1995@gmail.com   |

## Demo

```
  Galaxy S25                              Copilot+ PC
  (Snapdragon 8 Elite)                   (Snapdragon X Elite)
  ========================                ========================

  1. Record swing          ---- WiFi ---> 2. Receive video
                                          3. Extract frames (ffmpeg)
                                          4. Vision LLM analyzes swing
                                             (Qwen2.5-VL via LM Studio)
  6. Android TTS speaks    <--- WiFi ---- 5. Return coaching JSON
     coaching feedback
  7. Listen for follow-up
     questions by voice
```

**Round-trip latency: ~2 seconds from recording stop to spoken feedback.**

## Architecture

```
swingcoach/
├── android/                  # Mobile app (Kotlin, Jetpack Compose)
│   ├── app/src/main/
│   │   ├── analysis/         #   EdgeServerAnalyzer, GeminiVideoAnalyzer, OpenRouterAnalyzer
│   │   ├── camera/           #   CameraX video-only recording (mic stays free)
│   │   ├── voice/            #   On-device SpeechRecognizer + command parsing
│   │   ├── tts/              #   Android TextToSpeech wrapper
│   │   ├── settings/         #   LLM provider config (Edge/Gemini/OpenRouter)
│   │   └── ui/               #   SwingViewModel, CoachScreen, SettingsScreen
│   └── app/src/test/         #   77 unit tests
│
├── backend/                  # Edge server (Python, FastAPI)
│   ├── app.py                #   REST API -- /api/analyze-swing
│   ├── lmstudio_service.py   #   Local VLM inference via LM Studio
│   ├── vision_provider.py    #   Routes to local (LM Studio) or cloud (Gemini)
│   ├── gemini_service.py     #   Gemini Vision + TTS (cloud fallback)
│   ├── pose.py               #   MediaPipe pose estimation (optional)
│   ├── analysis.py           #   Tennis swing scoring engine
│   ├── network_info.py       #   LAN IP discovery for phone pairing
│   └── tests/                #   28 pytest tests (unit + E2E)
│
└── frontend/                 # Web dashboard (React, TypeScript) -- optional
```

### Multi-Device Pipeline

The app uses a **phone + PC split architecture** designed for the Snapdragon ecosystem:

| Component | Device | Processor | Runs On-Device? |
|-----------|--------|-----------|-----------------|
| Video capture | Galaxy S25 | Snapdragon 8 Elite | Yes |
| Speech-to-text | Galaxy S25 | Snapdragon 8 Elite | Yes |
| Text-to-speech | Galaxy S25 | Snapdragon 8 Elite | Yes |
| Vision LLM (Qwen2.5-VL) | Copilot+ PC | Snapdragon X Elite | Yes |
| Frame extraction (ffmpeg) | Copilot+ PC | Snapdragon X Elite | Yes |
| Pose estimation (MediaPipe) | Copilot+ PC | Snapdragon X Elite | Yes (optional) |

**All AI inference runs on-device across the two Snapdragon processors. Zero cloud dependency.**

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check with provider and pose status |
| `/api/analyze-swing` | POST | Combined: vision + pose + TTS (main endpoint) |
| `/api/analyze-video` | POST | Pose estimation only |
| `/api/analyze-frame` | POST | Single frame joint angles |
| `/api/annotate-video` | POST | Video with skeleton overlay |
| `/api/network-info` | GET | LAN IP and connection URLs for phone pairing |

## Setup from Scratch

### Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Python | 3.10+ | Pre-installed on Copilot+ PC |
| LM Studio | Latest | [lmstudio.ai](https://lmstudio.ai) |
| ffmpeg | Latest | `winget install Gyan.FFmpeg` |
| Android Studio | Latest | [developer.android.com](https://developer.android.com/studio) |
| JDK | 17+ | Bundled with Android Studio |

### Step 1: Backend (Copilot+ PC)

```powershell
# Clone the repo
git clone https://github.com/nbzy1995/MultiverseHackathon.git
cd MultiverseHackathon

# Create Python virtual environment
cd backend
python -m venv .venv
.venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Configure environment
copy .env.example .env
# Edit .env -- set VISION_PROVIDER=local (default)
```

### Step 2: Load the Vision Model

1. Open **LM Studio**
2. Search for and download **Qwen2.5-VL-3B-Instruct** (or 7B for better quality)
3. Load the model in LM Studio
4. Verify it's running: `curl http://localhost:1234/v1/models`

### Step 3: Start the Backend

```powershell
cd backend
.venv\Scripts\activate
python -m uvicorn app:app --host 0.0.0.0 --port 8001
```

The server prints connection info on startup:

```
SwingCoach API ready!
  Vision provider: local
  Pose estimation: unavailable
  LAN IP:  10.206.81.71
  Phone connect URL: http://10.206.81.71:8001
```

Note the **Phone connect URL** -- you'll enter this in the Android app.

### Step 4: Android App (Galaxy S25)

**Option A: Install pre-built APK**

```powershell
adb install app-debug.apk
```

**Option B: Build from source**

```powershell
cd android

# Set JAVA_HOME (Windows)
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr

# Build and install
gradlew.bat installDebug
```

### Step 5: Connect Phone to PC

1. Ensure both devices are on the **same WiFi network**
2. Open the SwingCoach app on the Galaxy S25
3. Go to **Settings** (gear icon)
4. Set **Video Analysis** provider to **Edge Server**
5. Enter the PC's URL: `http://<PC_IP>:8001`
6. Tap **Save**
7. Return to the **Coach** tab

### Step 6: Use It

- Say **"start recording"** and swing your racket
- Say **"stop"** -- the video uploads to the PC
- Within ~2 seconds, the phone speaks coaching feedback
- Ask follow-up questions by voice: *"Tell me more about my grip"*

## Configuration

### Environment Variables (backend/.env)

```ini
# Vision provider: "local" (LM Studio) or "gemini" (Google Cloud)
VISION_PROVIDER=local

# LM Studio settings (used when VISION_PROVIDER=local)
LMSTUDIO_URL=http://localhost:1234
LMSTUDIO_MODEL=qwen2.5-vl-3b-instruct
NUM_FRAMES=6
MAX_FRAME_DIM=512

# Gemini API key (used when VISION_PROVIDER=gemini)
GEMINI_API_KEY=your_key_here
```

### Android Settings

The app supports three LLM providers, configurable per-slot (video analysis vs conversation):

| Provider | Use Case | Requires |
|----------|----------|----------|
| **Edge Server** | Video analysis via PC backend | PC on same network |
| **Gemini** | Direct cloud video analysis | API key |
| **OpenRouter** | Alternative cloud models | API key + balance |

## Testing

### Backend Tests (28 tests)

```powershell
cd backend
.venv\Scripts\activate

# Unit tests (no external dependencies)
python -m pytest tests/ -v --ignore=tests/test_gemini_service.py -k "not e2e"

# E2E tests (requires LM Studio running)
python -m pytest tests/test_e2e_local_llm.py -v

# All tests
python -m pytest tests/ -v --ignore=tests/test_gemini_service.py
```

### Android Unit Tests (77 tests)

```powershell
cd android
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat testDebugUnitTest
```

| Test Suite | Count | Coverage |
|------------|-------|----------|
| SwingViewModelTest | 24 | State machine, dual-analyzer, conversation, voice |
| VoiceCommandParserTest | 12 | Command parsing, case insensitivity |
| OpenRouterAnalyzerTest | 12 | MockWebServer: auth, parsing, errors |
| LlmSettingsTest | 11 | SharedPreferences round-trip |
| GeminiVideoAnalyzerTest | 10 | MockWebServer: analyze, retry, conversation |
| AnalyzerFactoryTest | 5 | Provider routing |
| FakeSpeechPlayerTest | 3 | TTS fake behavior |

### Android Connected Tests (27 tests, requires device + backend)

```powershell
cd android
gradlew.bat connectedDebugAndroidTest
```

| Test Suite | Count | What It Tests |
|------------|-------|---------------|
| DeviceReadinessTest | 10 | Camera, network, TTS, assets, API keys |
| EdgeServerRealTest | 2 | Full phone-to-PC-to-phone pipeline |
| TtsRealWorkflowTest | 5 | Android TTS speaks and callbacks work |
| GeminiApiRealTest | 4 | Direct Gemini vision analysis |
| OpenRouterApiRealTest | 5 | OpenRouter API (skipped without key) |
| ExampleInstrumentedTest | 1 | Basic app context |

### Verify the Full Pipeline

```powershell
# 1. Start LM Studio with Qwen2.5-VL loaded
# 2. Start the backend
cd backend && python -m uvicorn app:app --host 0.0.0.0 --port 8001

# 3. Test from command line
curl -X POST http://localhost:8001/api/analyze-swing ^
  -F "file=@tests/fixtures/sample_swing.mp4" ^
  -F "rotation=0"

# Expected: JSON with gemini_analysis.feedback_text containing coaching feedback
```

## Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Mobile app | Kotlin 2.0, Jetpack Compose, CameraX | Video capture, voice UI |
| Speech-to-text | Android SpeechRecognizer | On-device voice commands |
| Text-to-speech | Android TextToSpeech | On-device spoken feedback |
| Edge server | Python 3.12, FastAPI, uvicorn | Video processing API |
| Vision LLM | Qwen2.5-VL via LM Studio | On-device swing analysis |
| Frame extraction | ffmpeg + Pillow | Video to JPEG frames |
| Pose estimation | MediaPipe BlazePose (optional) | 33 keypoints, joint angles |
| HTTP client | OkHttp 4.11 | Multipart video upload |
| Build | Gradle 9.1, AGP 9.0 | Android build system |

## Key Design Decisions

1. **ffmpeg over OpenCV**: OpenCV has no ARM64 Windows wheels. ffmpeg subprocess + Pillow works natively on Snapdragon X Elite without WSL.

2. **Lazy imports**: `cv2`, `numpy`, `google.genai` are imported at function level, not module level. This allows the backend to start on ARM64 Windows even when optional dependencies are unavailable.

3. **Video-only recording**: The camera records WITHOUT audio, keeping the microphone free for the SpeechRecognizer to listen for "stop" commands during recording.

4. **Dual-analyzer architecture**: The Android app has separate analyzers for video (multimodal VLM) and conversation (text LLM), independently configurable via Settings.

5. **LM Studio over direct ONNX**: LM Studio provides an OpenAI-compatible API with automatic GPU/NPU acceleration on Snapdragon, avoiding manual model loading code.

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Backend won't start | Check `python --version` (3.10+) and `pip install -r requirements.txt` |
| LM Studio 404 | Ensure a model is loaded in LM Studio, not just downloaded |
| Phone can't reach PC | Both devices must be on the same WiFi. Check firewall for port 8001 |
| App crashes on HTTP | Verify `usesCleartextTraffic="true"` in AndroidManifest.xml |
| Pose estimation unavailable | Expected on ARM64 Windows (mediapipe has no ARM64 wheels). Vision-only mode still works. |
| TTS not speaking | Install Google TTS from Play Store if missing |
| ffmpeg not found | Run `winget install Gyan.FFmpeg` and restart terminal |

## References

- [LM Studio](https://lmstudio.ai) -- local LLM inference
- [Qwen2.5-VL](https://github.com/QwenLM/Qwen2.5-VL) -- vision language model
- [MediaPipe](https://google.github.io/mediapipe/) -- pose estimation
- [Qualcomm AI Hub](https://aihub.qualcomm.com) -- Snapdragon model optimization
- [FastAPI](https://fastapi.tiangolo.com) -- Python async web framework

## License

MIT License -- see [LICENSE](LICENSE) for details.
