# Architecture

Single-module MVVM, constructor injection, no DI framework.

## Source Map (`app/src/main/java/.../`)

| Package | Key Files | Purpose |
|---------|-----------|---------|
| `analysis/` | `VideoAnalyzer.kt`, `GeminiVideoAnalyzer.kt`, `OpenRouterAnalyzer.kt`, `FakeVideoAnalyzer.kt` | LLM API clients |
| `settings/` | `LlmSettings.kt`, `AnalyzerFactory.kt` | Settings data model, SharedPreferences, factory |
| `ui/` | `SwingViewModel.kt`, `CoachScreen.kt`, `SettingsScreen.kt`, `ReplayScreen.kt` | MVVM UI layer |
| `voice/` | `VoiceCommandRecognizer.kt` | On-device speech recognition + command parsing |
| `camera/` | `CameraManager.kt`, `RecordScreen.kt` | CameraX video-only recording |
| `tts/` | `AndroidSpeechPlayer.kt` | TextToSpeech wrapper |
| root | `MainActivity.kt` | 4 tabs, settings wiring, analyzer fallback |

## Key Patterns

- `VideoAnalyzer` interface: `analyze(Uri)` + `askQuestion(String, history)` — Gemini, OpenRouter, Fake implementations
- Dual analyzers on `SwingViewModel`: `videoAnalyzer` (multimodal) + `textAnalyzer` (conversation), independently swappable via `updateAnalyzers()`
- `AnalyzerFactory.create(config, context)` routes to provider based on `LlmSlotConfig.provider`
- Fallback chain: in-app API key -> `BuildConfig.GEMINI_API_KEY` -> `FakeVideoAnalyzer`
- `internal` methods (`analyzeBytes`, `buildVideoRequest`) for unit testing without Android Context
- Video records WITHOUT audio — keeps mic free for SpeechRecognizer

## State Machine

```
IDLE -> LISTENING -> RECORDING -> UPLOADING -> SPEAKING -> LISTENING
                  -> UPLOADING (voice question) -> SPEAKING -> LISTENING
Any -> ERROR
```

## Tests (77 unit + 5 E2E)

| File | # | What |
|------|---|------|
| `SwingViewModelTest.kt` | 24 | State machine, dual-analyzer, conversation, voice |
| `VoiceCommandParserTest.kt` | 12 | Command parsing |
| `OpenRouterAnalyzerTest.kt` | 12 | MockWebServer: auth, parsing, errors |
| `LlmSettingsTest.kt` | 11 | Data model + SharedPreferences round-trip |
| `GeminiVideoAnalyzerTest.kt` | 10 | MockWebServer: analyze, askQuestion, retry |
| `AnalyzerFactoryTest.kt` | 5 | Provider routing |
| `FakeSpeechPlayerTest.kt` | 3 | TTS fake |
| `OpenRouterApiRealTest.kt` (E2E) | 5 | Real OpenRouter API calls |
