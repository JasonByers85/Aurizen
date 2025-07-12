# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android wellness application called "AuriZen" that demonstrates MediaPipe LLM Inference capabilities. The app provides AI-powered wellness features including:

- Quick chat with AI
- Guided meditation sessions (both predefined and AI-generated)
- Mood tracking and analysis
- Dream interpretation
- Breathing exercises
- Health data integration (Google Fit, Health Connect)

## Development Commands

### Building and Running
```bash
# Build the project
./gradlew build

# Install debug APK to connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumentation tests
./gradlew connectedAndroidTest

# Clean build artifacts
./gradlew clean
```

### Configuration
- Set `HF_ACCESS_TOKEN` in `local.properties` for Hugging Face model downloads
- Requires Android Studio Hedgehog or later
- Minimum Android SDK 26 (Android 7.0)
- Target SDK 34
- Must run on physical device with GPU support

## Architecture

### Core Components

**MainActivity.kt**: Navigation host using Jetpack Compose Navigation with screen constants and route definitions

**InferenceModel.kt**: Singleton wrapper for MediaPipe LLM Inference with session management and model loading

**Model.kt**: Enum defining available LLM models (currently GEMMA3N with specific download URLs and parameters)

### Key Features Architecture

**Wellness Screens**: Each wellness feature (meditation, mood tracking, dream interpretation) has its own:
- Screen composable (UI)
- ViewModel (state management)
- Storage class (data persistence)

**Authentication**: OAuth integration with Hugging Face for model downloads via `OAuthCallbackActivity` and `LoginActivity`

**Model Management**: 
- `ModelDownloader.kt`: Handles downloading models from Hugging Face
- Models stored in app's internal files directory
- License acknowledgment flow for restricted models

**Audio Integration**: 
- `MeditationAudioManager.kt`: Background audio playback for meditation sessions
- TTS integration for AI-generated meditation guidance

### Data Storage

- **Secure Storage**: Uses Android EncryptedSharedPreferences for sensitive data
- **Model Storage**: LLM models stored in app's internal files directory
- **User Data**: Wellness data (moods, dreams, meditation history) stored locally

### Theme System

- Material Design 3 with custom theming
- Pastel color scheme defined in `ui/theme/PastelTheme.kt`
- Theme mode support (light/dark)

## Important Notes

- Model downloads require authentication with Hugging Face
- App requires INTERNET permission for model downloads
- Health permissions required for Google Fit/Health Connect integration
- OpenCL libraries required for GPU acceleration
- All LLM interactions are stateless (no chat history preservation)
- Model path resolution tries multiple locations for compatibility