# AuriZen Android App - Project Architecture Documentation

This document provides a comprehensive overview of all files in the AuriZen Android wellness application, their purposes, dependencies, and relationships.

## Project Overview
AuriZen is an Android wellness application that demonstrates MediaPipe LLM Inference capabilities. The app provides AI-powered wellness features including guided meditation, breathing exercises, mood tracking, dream interpretation, and quick AI chat.

---

## 📱 Core Application Files

### **MainActivity.kt**
- **Purpose**: Main navigation controller using Jetpack Compose Navigation
- **Key Functions**: Defines all navigation routes and screen transitions
- **Dependencies**: All screen route functions
- **Navigation Routes**: 
  - START_SCREEN → SelectionRoute()
  - HOME_SCREEN → HomeRoute()
  - MEDITATION_SCREEN → MeditationRoute()
  - BREATHING_SCREEN → BreathingRoute()
  - MOOD_TRACKER_SCREEN → MoodTrackerRoute()
  - DREAM_INTERPRETER_SCREEN → DreamInterpreterRoute()
  - And more...

---

## 🧠 AI/LLM Core Infrastructure

### **InferenceModel.kt**
- **Purpose**: Singleton wrapper for MediaPipe LLM Inference
- **Key Functions**: 
  - `generateResponseAsync()` - Streaming text generation
  - `generateResponse()` - Non-streaming text generation
  - Session management and model loading
- **Used By**: All AI-powered features (chat, meditation, dreams, mood tracking)
- **Dependencies**: Model.kt, MediaPipe LLM library

### **Model.kt**
- **Purpose**: Enum defining available LLM models and their configurations
- **Key Models**: GEMMA3N with download URLs and parameters
- **Used By**: InferenceModel.kt, LoadingScreen.kt, ModelDownloader.kt

### **ModelDownloader.kt**
- **Purpose**: Handles downloading LLM models from Hugging Face
- **Key Functions**: Download progress tracking, file validation, OAuth integration
- **Used By**: LoadingScreen.kt, LoginActivity.kt
- **Dependencies**: AuthConfig.kt, ModelExceptions.kt

### **ModelExceptions.kt**
- **Purpose**: Custom exception classes for model download errors
- **Used By**: ModelDownloader.kt

---

## 🏠 Main Application Screens

### **SelectionScreen.kt**
- **Purpose**: Initial app selection screen (user type selection)
- **Navigation**: START_SCREEN entry point
- **Flow**: SelectionScreen → HomeScreen or LoadingScreen

### **LoadingScreen.kt**
- **Purpose**: Model download and initialization screen
- **Key Functions**: Download progress, model validation, OAuth flow
- **Navigation**: LOAD_SCREEN
- **Dependencies**: ModelDownloader.kt, Model.kt, InferenceModel.kt

### **HomeScreen.kt**
- **Purpose**: Main dashboard with wellness feature tiles
- **Key Features**: Quick access to meditation, breathing, mood tracking, dreams, chat
- **Navigation**: HOME_SCREEN (main hub)
- **Dependencies**: UserProfile.kt for personalization

### **SettingsScreen.kt**
- **Purpose**: App-wide settings and configurations
- **Key Settings**: Theme mode, audio preferences, user profile
- **Navigation**: SETTINGS_SCREEN
- **Dependencies**: UserProfile.kt, MeditationSettings.kt

### **TTSSettingsScreen.kt**
- **Purpose**: Text-to-Speech configuration screen
- **Key Functions**: Voice selection, speed/pitch adjustment, test playback
- **Navigation**: TTS_SETTINGS_SCREEN
- **Dependencies**: MeditationSettings.kt

---

## 🧘 Meditation System

### **MeditationScreen.kt**
- **Purpose**: Meditation type selection screen
- **Key Functions**: Browse meditation categories, select session type
- **Navigation**: MEDITATION_SCREEN → UnifiedMeditationSessionScreen
- **Dependencies**: MeditationSettings.kt, UnifiedMeditationModels.kt

### **UnifiedMeditationSessionScreen.kt**
- **Purpose**: Main meditation session experience
- **Key Functions**: 
  - Audio playback with background sounds and binaural tones
  - TTS guidance with sentence-by-sentence streaming
  - Visual meditation guide with animations
  - Session controls (play/pause/stop)
- **Navigation**: MEDITATION_SESSION_SCREEN
- **Dependencies**: UnifiedMeditationSessionViewModel.kt, UnifiedMeditationSettings.kt

### **UnifiedMeditationSessionViewModel.kt**
- **Purpose**: State management for meditation sessions
- **Key Functions**:
  - AI-generated meditation content streaming
  - Audio manager coordination
  - Session progress tracking
  - Settings synchronization
- **Dependencies**: InferenceModel.kt, MeditationAudioManager.kt, MeditationSettings.kt

### **UnifiedMeditationSettings.kt**
- **Purpose**: In-session settings dialog for meditation
- **Key Functions**: Real-time audio adjustment, voice selection, volume controls
- **Used By**: UnifiedMeditationSessionScreen.kt
- **Dependencies**: MeditationSettings.kt, MeditationAudioManager.kt

### **MeditationSettings.kt**
- **Purpose**: Persistent storage for meditation preferences
- **Key Functions**: 
  - TTS settings (voice, speed, pitch, volume)
  - Audio settings (background sounds, binaural tones, volumes)
  - Session preferences
- **Storage**: SharedPreferences (singleton pattern)
- **Used By**: 8+ files across meditation and breathing systems

### **MeditationModels.kt**
- **Purpose**: Core data models and enums for meditation system
- **Key Components**:
  - `BackgroundSound` enum (Ocean, Rain, Forest, etc.)
  - `BinauralTone` enum (Alpha, Beta, Theta, etc.)
  - `MeditationSessionState` enum
  - Various data classes for session management
- **Used By**: All meditation and breathing files (critical dependency)

### **UnifiedMeditationModels.kt**
- **Purpose**: Extended data models for unified meditation system
- **Key Components**:
  - `UnifiedMeditationStep` - Individual meditation steps
  - `UnifiedMeditationSessionState` - Session state management
  - `SavedMeditationType` - Saved meditation configurations
- **Used By**: Unified meditation system files

### **MeditationAudioManager.kt**
- **Purpose**: Audio management for meditation sessions
- **Key Functions**:
  - Background sound playback (MediaPlayer)
  - Binaural tone generation (AudioTrack with sine waves)
  - Volume control and mixing
  - Audio session management
- **Used By**: UnifiedMeditationSessionViewModel.kt, BreathingPrograms.kt

---

## 🫁 Breathing Exercise System

### **BreathingPrograms.kt**
- **Purpose**: Breathing program selection and breathing audio management
- **Key Functions**:
  - Defines 9 breathing programs (Quick Calm, Box Breathing, 4-7-8, etc.)
  - `BreathingAudioManager` class for TTS and audio coordination
  - Navigation to breathing sessions
- **Navigation**: BREATHING_SCREEN → UnifiedBreathingSessionScreen
- **Dependencies**: BreathingSettings.kt, MeditationAudioManager.kt

### **UnifiedBreathingSessionScreen.kt**
- **Purpose**: Unified breathing session experience
- **Key Functions**:
  - Breathing cycle timing and visual guidance
  - TTS phase announcements
  - Audio background sounds and binaural tones
  - Visual breathing animation
- **Navigation**: BREATHING_SESSION_SCREEN
- **Dependencies**: BreathingSettings.kt, BreathingSettingsDialog.kt

### **BreathingSettings.kt**
- **Purpose**: Persistent storage for breathing exercise preferences
- **Key Functions**: 
  - TTS settings (voice, speed, pitch)
  - Audio settings (background sounds, binaural tones)
  - Session preferences
- **Storage**: SharedPreferences (singleton pattern)
- **Used By**: BreathingPrograms.kt, UnifiedBreathingSessionScreen.kt, BreathingSettingsDialog.kt

### **BreathingSettingsDialog.kt**
- **Purpose**: In-session settings dialog for breathing exercises
- **Key Functions**: Real-time audio adjustment during breathing sessions
- **Used By**: UnifiedBreathingSessionScreen.kt
- **Dependencies**: BreathingSettings.kt

---

## 😊 Mood Tracking System

### **MoodTrackerScreen.kt**
- **Purpose**: Mood tracking interface and history visualization
- **Key Functions**:
  - Daily mood selection (1-10 scale with emoji)
  - Mood history charts and trends
  - AI-powered mood insights
- **Navigation**: MOOD_TRACKER_SCREEN
- **Dependencies**: MoodTrackerViewModel.kt

### **MoodTrackerViewModel.kt**
- **Purpose**: State management for mood tracking
- **Key Functions**:
  - Mood data persistence
  - AI analysis of mood patterns
  - Chart data generation
- **Dependencies**: InferenceModel.kt, MoodStorage.kt, UserProfile.kt

### **MoodStorage.kt**
- **Purpose**: Persistent storage for mood tracking data
- **Key Functions**:
  - Save/retrieve mood entries
  - Calculate mood statistics and trends
  - Data export capabilities
- **Storage**: SharedPreferences with JSON serialization
- **Used By**: MoodTrackerViewModel.kt

---

## 🌙 Dream Interpretation System

### **DreamInterpreterScreen.kt**
- **Purpose**: Dream interpretation interface with tabbed layout
- **Key Functions**:
  - Dream input and AI interpretation
  - Dream diary with month/year grouping
  - Dream management (delete, reinterpret)
- **Navigation**: DREAM_INTERPRETER_SCREEN
- **Dependencies**: DreamInterpreterViewModel.kt

### **DreamInterpreterViewModel.kt**
- **Purpose**: State management for dream interpretation
- **Key Functions**:
  - AI-powered dream interpretation
  - Dream summary generation
  - Tab state management
  - Dream history management
- **Dependencies**: InferenceModel.kt, DreamStorage.kt, UserProfile.kt

### **DreamStorage.kt**
- **Purpose**: Persistent storage for dream interpretation data
- **Key Functions**:
  - Save/retrieve dream entries (description, interpretation, summary)
  - Dream statistics and trends
  - Dream search and filtering
- **Storage**: SharedPreferences with JSON serialization
- **Used By**: DreamInterpreterViewModel.kt

---

## 💬 Quick Chat System

### **QuickChatScreen.kt**
- **Purpose**: Direct AI chat interface for quick wellness questions
- **Key Functions**: Real-time AI conversation, chat history
- **Navigation**: QUICK_CHAT_SCREEN
- **Dependencies**: QuickChatViewModel.kt

### **QuickChatViewModel.kt**
- **Purpose**: State management for AI chat functionality
- **Key Functions**: Message handling, AI response generation, chat persistence
- **Dependencies**: InferenceModel.kt

---

## 🔐 Authentication & User Management

### **LoginActivity.kt**
- **Purpose**: OAuth login flow for Hugging Face authentication
- **Key Functions**: WebView-based OAuth, token handling
- **Dependencies**: AuthConfig.kt, ModelDownloader.kt

### **OAuthCallbackActivity.kt**
- **Purpose**: Handles OAuth callback and token exchange
- **Key Functions**: Authorization code processing, token storage
- **Dependencies**: AuthConfig.kt

### **LicenseAcknowledgmentActivity.kt**
- **Purpose**: Model license agreement flow
- **Key Functions**: License display, user consent tracking
- **Dependencies**: AuthConfig.kt

### **AuthConfig.kt**
- **Purpose**: OAuth configuration and token management
- **Key Functions**: 
  - Hugging Face OAuth setup
  - Token storage and validation
  - Authentication state management
- **Storage**: SecureStorage.kt
- **Used By**: Login activities, ModelDownloader.kt

---

## 💾 Data Management & Storage

### **UserProfile.kt**
- **Purpose**: User data and preferences management
- **Key Functions**:
  - User profile information
  - Topic interests tracking
  - Usage statistics
  - Profile synchronization
- **Storage**: SecureStorage.kt
- **Used By**: HomeScreen.kt, SettingsScreen.kt, ViewModels

### **SecureStorage.kt**
- **Purpose**: Encrypted data storage wrapper
- **Key Functions**: 
  - EncryptedSharedPreferences wrapper
  - Secure key-value storage
  - Data encryption/decryption
- **Used By**: UserProfile.kt, AuthConfig.kt, all storage classes

---

## 🎨 UI Theme System

### **ui/theme/Theme.kt**
- **Purpose**: Main theme configuration and provider
- **Key Functions**: Material Design 3 theme setup, theme switching

### **ui/theme/PastelTheme.kt**
- **Purpose**: Custom pastel color scheme definition
- **Key Functions**: Light/dark theme color palettes

### **ui/theme/Color.kt**
- **Purpose**: Color definitions and constants
- **Key Functions**: Custom color values, theme-specific colors

### **ui/theme/Type.kt**
- **Purpose**: Typography system configuration
- **Key Functions**: Font families, text styles, typography scale

### **ui/theme/ThemeMode.kt**
- **Purpose**: Theme mode management (light/dark/system)
- **Key Functions**: Theme preference storage and application

### **ui/theme/GradientBackground.kt**
- **Purpose**: Reusable gradient background components
- **Key Functions**: Consistent gradient backgrounds across screens

---

## 🗑️ Removed/Legacy Files (Marked with .remove)

### **HomeScreen_backup.remove**
- **Status**: Unused backup file
- **Safe to delete**: Yes

### **WellnessScreens.kt.remove**
- **Status**: Legacy wellness screens implementation
- **Replacement**: Individual screen files and BreathingPrograms.kt
- **Safe to delete**: Yes

### **BreathingSession.kt.remove**
- **Status**: Old breathing session implementation
- **Replacement**: UnifiedBreathingSessionScreen.kt
- **Safe to delete**: Yes

### **MeditationSessionScreen.kt** (deleted)
- **Status**: Already deleted from repository
- **Replacement**: UnifiedMeditationSessionScreen.kt

### **MeditationSessionViewModel.kt** (deleted)
- **Status**: Already deleted from repository
- **Replacement**: UnifiedMeditationSessionViewModel.kt

---

## 🔄 System Architecture Patterns

### **Navigation Flow**
```
MainActivity (Navigation Controller)
├── SelectionScreen (Entry Point)
├── LoadingScreen (Model Download)
├── HomeScreen (Main Hub)
├── Feature Screens (Meditation, Breathing, etc.)
└── Settings Screens
```

### **Data Flow**
```
UI Layer (Compose Screens)
├── ViewModels (State Management)
├── Storage Classes (Data Persistence)
├── Settings Classes (User Preferences)
└── InferenceModel (AI Integration)
```

### **Audio Architecture**
```
MeditationAudioManager (Core Audio)
├── Background Sounds (MediaPlayer)
├── Binaural Tones (AudioTrack)
└── TTS Integration (TextToSpeech)
```

### **Settings Hierarchy**
```
App-Wide Settings
├── MeditationSettings (Meditation & Breathing)
├── UserProfile (User Data)
├── AuthConfig (Authentication)
└── ThemeMode (UI Preferences)
```

---

## 📊 File Usage Statistics

- **Total Active Files**: ~50 Kotlin files
- **Navigation Entry Points**: 10 main screens
- **ViewModels**: 4 (QuickChat, MoodTracker, DreamInterpreter, UnifiedMeditationSession)
- **Storage Classes**: 5 (SecureStorage, UserProfile, MoodStorage, DreamStorage, plus Settings)
- **Audio Files**: 2 (MeditationAudioManager, integrated in BreathingPrograms)
- **Theme Files**: 6 (complete Material Design 3 implementation)
- **Removed Files**: 3 legacy implementations marked with .remove suffix

This architecture provides a clean separation of concerns with unified implementations for core features while maintaining compatibility with established data models and settings systems.