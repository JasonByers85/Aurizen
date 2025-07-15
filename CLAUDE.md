# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AuriZen is a privacy-focused Android wellness application that leverages on-device AI processing using MediaPipe LLM Inference. The app provides AI-powered wellness features including:

- Quick chat with AI (QuickChatScreen)
- Speech-enabled conversational AI (TalkScreen with STT/TTS)
- Guided meditation sessions (both predefined and AI-generated)
- Unified meditation system with customizable sessions
- Mood tracking and analysis
- Dream interpretation
- Breathing exercises with 9 different programs
- Personal goals tracking and management (both one-time and daily goals)
- Memory system for personalized AI interactions
- Function calling system for AI-triggered actions

**Core Philosophy**: 100% local processing, no cloud dependencies, encrypted data storage, and privacy-first design.

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
- Must run on physical device with GPU support (MediaPipe requirement)
- Dependencies: MediaPipe tasks-genai 0.10.25, Jetpack Compose BOM 2025.06.01
- OAuth redirect scheme configured in build.gradle.kts for Hugging Face authentication

## Architecture

### Core Components

**MainActivity.kt**: Navigation host using Jetpack Compose Navigation with screen constants and route definitions

**InferenceModel.kt**: Singleton wrapper for MediaPipe LLM Inference with session management and model loading. Uses single-turn approach for stability (preserveContext=false)

**Model.kt**: Enum defining available LLM models (currently GEMMA3N E2B with CPU backend preference and text-only optimizations for better performance)

**PromptBuilder.kt**: Centralized prompt construction system with function calling detection and context management

### Key Features Architecture

**Wellness Screens**: Each wellness feature follows MVVM pattern with:
- Screen composable (UI)
- ViewModel (state management)  
- Storage class (data persistence using EncryptedSharedPreferences)

**Key Features**:
- **TalkScreen**: Speech-to-text conversational AI with TTS responses using manual chat history approach
- **Personal Goals**: Goal tracking system with categories, progress tracking, daily goals with streak tracking, and AI integration
- **Memory System**: `MemoryStorage.kt` stores user context for personalized AI interactions
- **Function Calling System**: `FunctionCallingSystem.kt` enables AI to trigger specific actions (meditation creation, goal creation, memory storage) via structured prompts

**Authentication**: OAuth integration with Hugging Face for model downloads via `OAuthCallbackActivity` and `LoginActivity`

**Model Management**: 
- `ModelDownloader.kt`: Handles downloading models from Hugging Face with progress tracking
- Models stored in app's internal files directory with multiple path resolution
- License acknowledgment flow for restricted models via `LicenseAcknowledgmentActivity`
- Native audio modality support in MediaPipe 0.10.25 for direct audio input to LLM (currently disabled for text-only performance)

**Audio Integration**: 
- `MeditationAudioManager.kt`: Background audio playbook for meditation sessions
- TTS integration for AI-generated meditation guidance and speech responses
- Binaural tone generation and background sound mixing

### Data Storage

- **Secure Storage**: Uses Android EncryptedSharedPreferences for sensitive data
- **Model Storage**: LLM models stored in app's internal files directory
- **User Data**: Wellness data (moods, dreams, meditation history) stored locally
- **Memory Storage**: `MemoryStorage.kt` for AI context and user memories
- **Personal Goals**: `PersonalGoals.kt` with goal categories, progress tracking, and completion states. Supports both one-time and daily goals with streak tracking
- **TTS Settings**: `TTSSettings.kt` for voice customization preferences (speech rate, pitch, voice selection, gender preference)

### Theme System

- Material Design 3 with custom theming system
- Multiple theme modes: Light, Dark, System, Pastel, Nature, Minimal, Vibrant, Ocean, Sunset, Forest Green
- Theme-aware asset loading (e.g., `aurizen_logo_light.png` for light themes, `aurizen_logo.png` for dark themes)
- `ThemeManager.kt` handles theme persistence and switching
- `shouldUseLightLogo()` helper functions for theme-appropriate asset selection

## Important Notes

- Model downloads require authentication with Hugging Face
- App requires INTERNET permission for model downloads  
- Health permissions required for Google Fit/Health Connect integration
- Microphone permissions required for speech-to-text functionality
- OpenCL libraries required for GPU acceleration (currently CPU backend preferred in Model.kt for compatibility)
- LLM interactions maintain context through Memory System and manual chat history in TalkScreen
- Function calling system enables AI to trigger app actions via structured prompts (FUNCTION_CALL:ACTION:{json})
- Model path resolution tries multiple locations for compatibility across different Android versions
- TTS settings are customizable per user with voice parameter controls (rate, pitch, voice selection, gender preference)
- Native multimodal audio support: LLM can directly analyze WAV audio files for emotion/stress detection
- Voice analysis combines local signal processing with AI interpretation for wellness insights
- Simple function calling approach preferred over complex session management for stability
- Text-only optimizations enabled: multimodal features disabled for faster inference (setMaxNumImages(0))
- Gemma 3n E2B model used: ~2B parameters loaded vs 4B for full model, optimized for text-only performance

## Key Development Patterns

**ViewModels**: Use StateFlow for reactive UI updates, follow MVVM pattern consistently
**Storage**: All sensitive data uses EncryptedSharedPreferences with AES256 encryption  
**Navigation**: Jetpack Compose Navigation with screen constants in MainActivity.kt
**AI Integration**: Singleton InferenceModel with function calling system for enhanced interactions. All AI prompts are configured to recommend AuriZen's built-in features (meditations, breathing exercises) rather than external apps. Use single-turn approach (preserveContext=false) for stability
**Audio**: Multi-layer audio management supporting TTS, background sounds, and binaural tones
**Error Handling**: Comprehensive error handling for token limits, timeouts, and function call failures. Token limit management with proactive session resets
**Code Organization**: 
- `/core/` - AI infrastructure and model management
- `/data/` - Storage classes and data models  
- `/features/` - Feature-specific implementations
- `/ui/screens/` - Compose UI screens
- `/ui/theme/` - Theme system and styling
- `/viewmodels/` - State management with StateFlow
- `/utils/` - Utility classes and helpers
- `/settings/` - Configuration management