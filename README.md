# SwingCoach 🎾

AI-powered tennis swing analysis using multi-device orchestration, edge compute, and cloud AI.

Built for the **Qualcomm Snapdragon Multiverse Hackathon** (Columbia, Feb 2026).

![License](https://img.shields.io/badge/License-MIT-green.svg)
![Python](https://img.shields.io/badge/Python-3.10+-blue.svg)
![React](https://img.shields.io/badge/React-19-61DAFB.svg)
![Android](https://img.shields.io/badge/Android-Kotlin-green.svg)

## How It Works

Phone captures video → PC runs edge CV (MediaPipe on Snapdragon) + cloud AI (Gemini) in parallel → returns combined analysis back to phone.

```
Phone POST /api/analyze-swing (video)
  │
  ├─ PARALLEL ─┬─ MediaPipe Pose (edge, ~3s)
  │             └─ Gemini Vision (cloud, ~4s)
  │
  ├─ SEQUENTIAL ── Gemini TTS (cloud, ~1s)
  │
  └─ Response: { pose_analysis, gemini_analysis, audio_base64, processing_info }
```

## Features

- **Multi-Device Pipeline**: Phone → PC → Phone orchestration
- **Edge Pose Estimation**: 33 keypoints via MediaPipe on Snapdragon
- **AI Coaching**: Gemini Vision identifies the single biggest flaw
- **Voice TTS**: Gemini TTS speaks coaching feedback to the player
- **Real-time Voice Control**: On-device speech recognition (Snapdragon NPU)
- **Joint Angle Analysis**: Precise angle calculations for all major joints
- **Tennis-specific Metrics**: Torso rotation, follow-through, contact point analysis
- **Video Annotation**: Skeleton overlay and angle visualization
- **Web Dashboard**: React frontend for detailed analysis review

## Monorepo Structure

```
swingcoach/
├── android/               # Android app (Kotlin, Jetpack Compose)
│   ├── app/src/main/      #   Voice coach, camera, edge server client
│   └── app/src/test/      #   77+ unit tests
├── backend/               # PC backend (Python, FastAPI)
│   ├── app.py             #   REST API with /api/analyze-swing
│   ├── gemini_service.py  #   Gemini Vision + TTS
│   ├── pose/              #   MediaPipe pose estimation
│   ├── analysis/          #   Tennis swing analyzer
│   └── tests/             #   pytest suite
├── frontend/              # Web dashboard (React, TypeScript, Vite)
└── server.py              # IMU sensor server
```

## Quick Start

### 1. PC Backend (WSL recommended for ARM64)

```bash
cd backend
cp .env.example .env
# Edit .env and add your Gemini API key

python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

The server prints your LAN IP on startup:
```
SwingCoach API ready!
  LAN IP:  192.168.1.100
  Phone connect URL: http://192.168.1.100:8001
```

### 2. Android App

```bash
cd android
# Set API keys in local.properties:
#   GEMINI_API_KEY=your_key
#   OPENROUTER_API_KEY=your_key (optional)
./gradlew installDebug
```

On the phone: **Settings** → select **Edge server** → enter your PC's URL → **Save**.

### 3. Web Dashboard (optional)

```bash
cd frontend
cp .env.example .env.local
# Edit .env.local with your Gemini API key
npm install
npm run dev
```

### Snapdragon X Elite (Windows ARM64)

```powershell
.\setup-snapdragon.ps1
```

### WSL (Windows Subsystem for Linux)

```bash
chmod +x setup-wsl.sh
./setup-wsl.sh
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/api/analyze-video` | POST | Pose estimation only |
| `/api/analyze-swing` | POST | Combined: pose + Gemini + TTS |
| `/api/analyze-frame` | POST | Analyze single frame |
| `/api/annotate-video` | POST | Video with skeleton overlay |
| `/api/network-info` | GET | LAN IP and connection URLs |

### Example: Combined Analysis

```bash
curl -X POST "http://localhost:8001/api/analyze-swing" \
  -F "file=@swing.mp4" \
  -F "rotation=0"
```

Response:
```json
{
  "pose_analysis": {
    "overall_score": 78,
    "max_torso_rotation": 65.3,
    "contact_elbow_angle": 158.2,
    "issues": ["Elbow too bent at contact"],
    "recommendations": ["Extend your arm more"],
    "frames": [...]
  },
  "gemini_analysis": {
    "feedback_text": "Drop the racket head sooner.",
    "metric_name": "Racket Drop",
    "metric_value": "Late"
  },
  "audio_base64": "...",
  "processing_info": {
    "total_seconds": 4.2,
    "parallel_seconds": 3.1
  }
}
```

## Testing

```bash
# PC backend unit tests
cd backend && python -m pytest tests/ -v -k "not e2e"

# PC backend E2E tests (needs API key)
cd backend && python -m pytest tests/test_e2e_real_api.py -v

# Android unit tests
cd android && ./gradlew testDebugUnitTest

# Android instrumented tests (needs device + PC backend running)
cd android && ./gradlew connectedAndroidTest
```

## Technology Stack

- **Pose Estimation**: Google MediaPipe (BlazePose, 33 keypoints, 3D)
- **Backend**: Python, FastAPI, OpenCV, NumPy
- **AI**: Google Gemini 3 Flash (Vision) + Gemini 2.5 Flash (TTS)
- **Android**: Kotlin, Jetpack Compose, CameraX, OkHttp
- **Frontend**: React 19, TypeScript, Vite, Tailwind CSS
- **Platform**: Qualcomm Snapdragon X Elite (ARM64)

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License

MIT License - see [LICENSE](LICENSE) for details.

## Acknowledgments

- [MediaPipe](https://google.github.io/mediapipe/) by Google
- [pose-tracker](https://github.com/JoeyYu23/pose-tracker) for angle calculation algorithms
- [Qualcomm HRPoseNet](https://github.com/quic/Pose-Detection-with-HRPoseNet) for HRNet integration reference
