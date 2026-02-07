# SwingCoach 🎾

AI-powered tennis swing analysis using pose estimation and machine learning.

![License](https://img.shields.io/badge/License-MIT-green.svg)
![Python](https://img.shields.io/badge/Python-3.10+-blue.svg)
![React](https://img.shields.io/badge/React-19-61DAFB.svg)

## Features

- **Real-time Pose Estimation**: 33 keypoints tracking using MediaPipe
- **Joint Angle Analysis**: Precise angle calculations for all major joints
- **Tennis-specific Metrics**: Torso rotation, follow-through, contact point analysis
- **AI Feedback**: Gemini-powered coaching feedback
- **Video Annotation**: Skeleton overlay and angle visualization

## Architecture

```
swingcoach/
├── frontend/          # React + TypeScript UI
│   ├── components/    # UI components
│   ├── services/      # API services
│   └── ...
│
├── backend/           # FastAPI server
│   ├── pose/          # Pose estimation (MediaPipe)
│   │   ├── mediapipe_backend.py
│   │   └── angle_calculator.py
│   └── analysis/      # Tennis analysis
│       └── tennis_analyzer.py
│
└── models/            # Pre-trained models
```

## Quick Start

### Backend

```bash
cd backend
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

API runs at `http://localhost:8000`

### Frontend

```bash
cd frontend
cp .env.example .env.local
# Edit .env.local and add your Gemini API key
npm install
npm run dev
```

App runs at `http://localhost:3000`

### Snapdragon X Elite (Windows ARM64)

One-click setup for Qualcomm Oryon CPU devices:

**PowerShell:**
```powershell
.\setup-snapdragon.ps1
```

**CMD:**
```cmd
setup-snapdragon.bat
```

**Manual Setup:**
```bash
# Install Python ARM64
winget install Python.Python.3.11 --architecture arm64

# Setup backend
cd backend
python -m venv venv
.\venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

> **Note**: MediaPipe 0.10+ supports Windows ARM64 natively. No code changes required.

### WSL (Windows Subsystem for Linux)

```bash
chmod +x setup-wsl.sh
./setup-wsl.sh
```

Or manually:
```bash
# Install dependencies
sudo apt-get update
sudo apt-get install -y python3 python3-pip python3-venv
sudo apt-get install -y libgl1-mesa-glx libglib2.0-0

# Setup backend
cd backend
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python app.py
```

> **Tip**: WSL2 backend can be accessed from Windows browser at `http://localhost:8001`

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/api/analyze-video` | POST | Analyze full swing video |
| `/api/analyze-frame` | POST | Analyze single frame |
| `/api/annotate-video` | POST | Get video with skeleton overlay |

### Example: Analyze Video

```bash
curl -X POST "http://localhost:8000/api/analyze-video" \
  -F "file=@swing.mp4"
```

Response:
```json
{
  "overall_score": 78,
  "max_torso_rotation": 65.3,
  "contact_elbow_angle": 158.2,
  "issues": ["Elbow too bent at contact"],
  "recommendations": ["Extend your arm more through contact"],
  "feedback_text": "Elbow too bent at contact. Extend your arm more through contact."
}
```

## Metrics Calculated

### Joint Angles
- Elbow (left/right)
- Shoulder (left/right)
- Hip (left/right)
- Knee (left/right)
- Wrist (left/right)
- Ankle (left/right)

### Posture Metrics
- Head tilt
- Neck angle
- Body lean
- Shoulder tilt
- Hip tilt
- Spine curve

### Tennis-specific
- Torso rotation (hip-shoulder separation)
- Racket arm extension
- Knee bend at contact
- Follow-through completion %

## Technology Stack

- **Pose Estimation**: Google MediaPipe (BlazePose)
- **Backend**: Python, FastAPI, OpenCV
- **Frontend**: React 19, TypeScript, Vite
- **AI**: Google Gemini API
- **Styling**: Tailwind CSS

## Supported Backends

| Backend | Keypoints | 3D | Platform |
|---------|-----------|-----|----------|
| MediaPipe | 33 | ✅ | All |
| HRNet (planned) | 17 | ❌ | Windows/Snapdragon |

## Roadmap

- [ ] Multi-person support
- [ ] Shot type detection (forehand/backhand/serve)
- [ ] Comparison with pro player swings
- [ ] Mobile app (iOS/Android)
- [ ] Real-time video streaming analysis

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License

MIT License - see [LICENSE](LICENSE) for details.

## Acknowledgments

- [MediaPipe](https://google.github.io/mediapipe/) by Google
- [pose-tracker](https://github.com/JoeyYu23/pose-tracker) for angle calculation algorithms
- [Qualcomm HRPoseNet](https://github.com/quic/Pose-Detection-with-HRPoseNet) for HRNet integration reference
