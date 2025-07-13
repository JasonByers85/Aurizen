# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android wellness application called "AuriZen" that demonstrates MediaPipe LLM Inference capabilities. The app provides AI-powered wellness features including:

- Quick chat with AI (QuickChatScreen)
- Speech-enabled conversational AI (TalkScreen with STT/TTS)
- Guided meditation sessions (both predefined and AI-generated)
- Unified meditation system with customizable sessions
- Mood tracking and analysis
- Dream interpretation
- Breathing exercises with 9 different programs
- Personal goals tracking and management
- Health data integration (Google Fit, Health Connect)
- Memory system for personalized AI interactions

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
- Minimum Android SDK 26 (Android 7.0), Target SDK 34, Compile SDK 36
- Must run on physical device with GPU support
- Dependencies: MediaPipe tasks-genai 0.10.25, Jetpack Compose BOM 2025.06.01

## Architecture

### Core Components

**MainActivity.kt**: Navigation host using Jetpack Compose Navigation with screen constants and route definitions

**InferenceModel.kt**: Singleton wrapper for MediaPipe LLM Inference with session management and model loading

**Model.kt**: Enum defining available LLM models (currently GEMMA3N with specific download URLs and parameters)

### Key Features Architecture

**Wellness Screens**: Each wellness feature follows MVVM pattern with:
- Screen composable (UI)
- ViewModel (state management)  
- Storage class (data persistence using EncryptedSharedPreferences)

**New Features**:
- **TalkScreen**: Speech-to-text conversational AI with TTS responses
- **Personal Goals**: Goal tracking system with categories, progress tracking, and AI integration
- **Memory System**: `MemoryStorage.kt` stores user context for personalized AI interactions
- **Function Calling System**: `FunctionCallingSystem.kt` enables AI to trigger specific actions (meditation creation, etc.)

**Authentication**: OAuth integration with Hugging Face for model downloads via `OAuthCallbackActivity` and `LoginActivity`

**Model Management**: 
- `ModelDownloader.kt`: Handles downloading models from Hugging Face
- Models stored in app's internal files directory
- License acknowledgment flow for restricted models

**Audio Integration**: 
- `MeditationAudioManager.kt`: Background audio playbook for meditation sessions
- TTS integration for AI-generated meditation guidance and speech responses
- Binaural tone generation and background sound mixing

### Data Storage

- **Secure Storage**: Uses Android EncryptedSharedPreferences for sensitive data
- **Model Storage**: LLM models stored in app's internal files directory
- **User Data**: Wellness data (moods, dreams, meditation history) stored locally
- **Memory Storage**: `MemoryStorage.kt` for AI context and user memories
- **Personal Goals**: `PersonalGoals.kt` with goal categories, progress tracking, and completion states
- **TTS Settings**: `TTSSettings.kt` for voice customization preferences

### Theme System

- Material Design 3 with custom theming
- Pastel color scheme defined in `ui/theme/PastelTheme.kt`
- Theme mode support (light/dark)

## Important Notes

- Model downloads require authentication with Hugging Face
- App requires INTERNET permission for model downloads  
- Health permissions required for Google Fit/Health Connect integration
- Microphone permissions required for speech-to-text functionality
- OpenCL libraries required for GPU acceleration (currently CPU backend preferred in Model.kt:24)
- LLM interactions now maintain context through Memory System
- Function calling system enables AI to trigger app actions via structured prompts
- Model path resolution tries multiple locations for compatibility
- TTS settings are customizable per user with voice parameter controls

## Key Development Patterns

**ViewModels**: Use StateFlow for reactive UI updates, follow MVVM pattern consistently
**Storage**: All sensitive data uses EncryptedSharedPreferences with AES256 encryption  
**Navigation**: Jetpack Compose Navigation with screen constants in MainActivity.kt
**AI Integration**: Singleton InferenceModel with function calling system for enhanced interactions. All AI prompts are configured to recommend AuriZen's built-in features (meditations, breathing exercises) rather than external apps
**Audio**: Multi-layer audio management supporting TTS, background sounds, and binaural tones