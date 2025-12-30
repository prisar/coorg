# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Coorg is a voice recording Android app that calculates speaking rate in words per minute. It uses MediaRecorder for audio capture and SpeechRecognizer for real-time speech-to-text transcription.

## Technology Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM with ViewModel and StateFlow
- **Permissions**: Accompanist Permissions library
- **APIs**: Android MediaRecorder, SpeechRecognizer
- **Build**: Gradle with Kotlin DSL, version catalogs
- **Java Version**: Java 11

## Build Commands

### Development
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Clean build
./gradlew clean
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

### Code Quality
```bash
# Lint checks
./gradlew lint

# View lint report at:
# app/build/reports/lint-results.html
```

## Application Architecture

### Package Structure
```
com.prisar.coorg/
├── MainActivity.kt              # Entry point, renders RecordingScreen
├── RecordingScreen.kt           # Main UI with permission handling
├── RecordingViewModel.kt        # State management and recording logic
└── ui/theme/
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

### State Management

The app uses a single `RecordingState` data class managed by `RecordingViewModel`:

```kotlin
data class RecordingState(
    val isRecording: Boolean = false,
    val recognizedText: String = "",
    val wordCount: Int = 0,
    val recordingDurationSeconds: Int = 0,
    val wordsPerMinute: Int = 0,
    val error: String? = null
)
```

State flows from ViewModel to UI via StateFlow:
- `RecordingViewModel._state` (private MutableStateFlow)
- `RecordingViewModel.state` (public StateFlow)
- UI collects state with `collectAsState()`

### Audio Recording Implementation

**MediaRecorder Setup**:
- Audio source: `MediaRecorder.AudioSource.MIC`
- Output format: `MediaRecorder.OutputFormat.THREE_GPP`
- Audio encoder: `MediaRecorder.AudioEncoder.AMR_NB`
- Output file: Temporary file in cache directory (`*.3gp`)
- API level handling: Uses constructor with Context on Android 12+ (API 31+)

**SpeechRecognizer Integration**:
- Runs continuously during recording via auto-restart in `onEndOfSpeech` and `onError` callbacks
- Language model: `RecognizerIntent.LANGUAGE_MODEL_FREE_FORM`
- Appends new recognized text to existing text with space separator
- Word count calculated by splitting text on whitespace with regex `\\s+`
- WPM calculation: `(wordCount / durationSeconds) * 60`

**Recording Lifecycle**:
1. `startRecording()` - Creates MediaRecorder and SpeechRecognizer, starts both
2. SpeechRecognizer auto-restarts after each result to enable continuous recognition
3. `stopRecording()` - Stops MediaRecorder, destroys SpeechRecognizer, calculates final WPM
4. `onCleared()` - Cleanup in ViewModel when destroyed

### Permissions

Requires `RECORD_AUDIO` permission declared in AndroidManifest.xml.

Permission handling in RecordingScreen.kt:
- Uses Accompanist Permissions library (`@OptIn(ExperimentalPermissionsApi::class)`)
- `rememberPermissionState(Manifest.permission.RECORD_AUDIO)`
- Shows permission request card when not granted
- Recording controls only available when permission granted

### UI Components

**RecordingScreen.kt** displays:
- Permission request card (if needed)
- Speaking rate in large text (words per minute)
- Word count and duration in info card
- Recognized text in scrollable card
- Start/Stop recording button
- Error messages in error-styled card

All components use Material 3 styling and responsive layouts with Compose.

## Key Implementation Patterns

### ViewModel Pattern
```kotlin
class RecordingViewModel : ViewModel() {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    // Update state immutably
    _state.value = _state.value.copy(isRecording = true)
}
```

### Compose UI with ViewModel
```kotlin
@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    // Use state.isRecording, state.wordCount, etc.
}
```

### MediaRecorder Version Handling
```kotlin
mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    MediaRecorder(context)
} else {
    @Suppress("DEPRECATION")
    MediaRecorder()
}
```

## Dependencies

Dependencies are managed via `gradle/libs.versions.toml`:

Key versions:
- Android Gradle Plugin: 8.13.2
- Kotlin: 2.0.21
- Compose BOM: 2024.09.00
- Accompanist Permissions: 0.36.0 (direct dependency in app/build.gradle.kts)
- Lifecycle ViewModel Compose: 2.8.7 (direct dependency in app/build.gradle.kts)

API levels:
- minSdk: 24
- targetSdk: 36
- compileSdk: 36

## Development Notes

- App has single screen, no navigation library needed
- Audio files stored in cache directory as temporary files
- SpeechRecognizer availability checked before use with `SpeechRecognizer.isRecognitionAvailable()`
- Error handling via state.error field displayed in UI
- Resources cleaned up in ViewModel.onCleared()
