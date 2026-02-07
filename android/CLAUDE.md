# SwingCoach — AI Tennis Coaching App

See `doc/product-spec.md` for product details, `doc/architecture.md` for file map and test inventory.

## Build

```bash
# Must set JAVA_HOME for CLI builds
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew assembleDebug          # Build
./gradlew installDebug           # Build + install to device
./gradlew testDebugUnitTest      # 77 unit tests
./gradlew connectedAndroidTest   # 5 E2E tests (needs device + network)
```

Device: Galaxy S25 Ultra (SM-S938U1), Android 15, ID `R3CXC08040M`

## DevOps Workflow

When planning a feature:
1. **Clarify design** — understand requirements, ask questions, decide approach
2. **Write E2E tests first** — real API calls (with `assumeTrue` skip for missing keys/unsupported features) that mirror the main user-facing behaviors
3. **Write unit tests alongside implementation** — MockWebServer for API clients, mock SharedPreferences for repositories, `MainDispatcherRule` for ViewModel coroutines
4. **Every API client must be testable** — inject `baseUrl` + `client` via constructor, expose `internal` methods like `analyzeBytes()` and `buildRequest()` for unit testing without Android Context
5. **Backward compatibility** — use secondary constructors or default params when extending classes that existing tests depend on
6. **Build + test before commit** — `assembleDebug` + `testDebugUnitTest` must both pass

## API Details

### Gemini (direct)
- Endpoint: `/v1beta/models/{model}:generateContent`
- Auth: `x-goog-api-key` header
- JSON: camelCase keys (`inlineData`, `mimeType`)
- Default model: `gemini-2.5-flash`, configurable via constructor

### OpenRouter (OpenAI-compatible)
- Endpoint: `{baseUrl}/chat/completions`
- Auth: `Authorization: Bearer {key}`
- Video: `video_url` content type — not all models support base64 video
- **Must set `max_tokens`** — default 65535 burns credits, we use 512
- Role mapping: Gemini uses `"model"`, OpenRouter uses `"assistant"`
- Free models: `z-ai/glm-4.5-air:free` (text), `nvidia/nemotron-nano-12b-v2-vl:free` (vision)
- Free vision models don't support base64 video — need paid model or $1+ balance

### Keys
- Stored in `local.properties` (gitignored): `GEMINI_API_KEY`, `OPENROUTER_API_KEY`
- Exposed via `BuildConfig` fields

## Testing Gotchas

- `android.net.Uri.parse()` returns null in JVM — use `mock<Uri>()`
- `android.util.Base64` is stubbed — use `java.util.Base64` (minSdk 26+)
- `org.json.JSONObject` is an Android stub — add `testImplementation("org.json:json:20231013")`
- `returnDefaultValues = true` in `testOptions.unitTests` prevents "not mocked" crashes
- CameraX `surfaceProvider` property doesn't exist — use `setSurfaceProvider()` method
- SharedPreferences in unit tests: mock with `mutableMapOf` backing store, no Robolectric needed
- MockWebServer: inject `baseUrl` + `client` via constructor

## Build Gotchas

- HEREDOC in git commit fails in sandbox — use inline multiline string
- Gradle wrapper needs `dangerouslyDisableSandbox: true` for CLI builds
- AGP 9.0.0 removed `kotlinOptions {}` — use `compileOptions` with Java 17
- Gradle 9.1.0, Kotlin 2.0.21, compileSdk 36, minSdk 26, targetSdk 34

## Conventions

- Kotlin 2.0 with Compose compiler plugin
- Stateless composables with state hoisting
- Coroutines for async, no callbacks
- Constructor injection for all dependencies
- `internal` visibility for testable helper methods on API clients
