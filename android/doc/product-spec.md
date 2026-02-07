# SwingCoach — Product Spec

## What

AI tennis coaching app for the Snapdragon Multiverse Hackathon. Records swings on-device, analyzes with AI, provides real-time voice feedback.

## User Flow

1. Open app -> Coach tab starts listening for voice commands
2. Say "start recording" -> camera records tennis swing (no audio)
3. Say "stop" or auto-stop -> video sent to AI for analysis
4. AI coaching feedback spoken aloud via TTS
5. Ask follow-up questions by voice -> conversational coaching
6. Settings tab: configure separate LLM providers for video analysis vs conversation

## Tabs

| Tab | Purpose |
|-----|---------|
| Coach | Primary: camera preview (60%) + conversation (40%) + always-on voice |
| Record | Manual video recording |
| Replay | Video replay with analysis, analyzer toggle |
| Settings | LLM provider config (Gemini/OpenRouter) for video + conversation slots |

## LLM Providers

| Provider | Video Analysis | Conversation | Auth |
|----------|---------------|--------------|------|
| Gemini (direct) | Yes (inline base64) | Yes | `x-goog-api-key` header |
| OpenRouter | Yes (`video_url`, model-dependent) | Yes | `Bearer` token |

## Voice Commands

| Command | Action |
|---------|--------|
| "start recording" / "record" | Begin video capture |
| "stop" / "stop recording" | End recording, trigger analysis |
| Any other speech | Treated as follow-up question |

## Non-Goals (hackathon scope)

- No user accounts or cloud storage
- No offline AI (requires network)
- No multi-sport support (tennis only)
