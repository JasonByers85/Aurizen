# 🌿 AuriZen
**Your private companion for mindfulness and clarity.**

In a noisy, always-on world, AuriZen offers a moment of quiet.  
A gentle, local AI built to support your emotional well-being —  
no cloud, no tracking, just you and your mind.

Auri stands for:

- **A**wareness
- **U**nderstanding
- **R**eflection
- **I**nner peace

Whether you're tracking your mood, practicing breathing,  
or simply pausing to check in with yourself,  
Auri is here to help — calmly, privately, and always on your side.

---

## ✨ Features

### 🧘 **Guided Meditation**
- AI-generated personalized meditation sessions
- Pre-defined meditation programs (mindfulness, body scan, loving-kindness)
- Background sounds (ocean, rain, forest, binaural tones)
- Text-to-speech guidance with customizable voice settings
- Session progress tracking and history

### 🫁 **Breathing Exercises**
- 9 breathing programs (4-7-8, Box Breathing, Quick Calm, etc.)
- Visual breathing guidance with animated prompts
- TTS coaching for breathing phases
- Customizable session duration and audio settings

### 😊 **Mood Tracking**
- Daily mood logging with 1-10 scale and emoji selection
- Mood history visualization and trend analysis
- AI-powered insights and pattern recognition
- Personalized meditation recommendations based on mood

### 🌙 **Dream Interpretation**
- AI-powered dream analysis and interpretation
- Dream diary with organized monthly/yearly view
- Dream summary generation and symbolic analysis
- Searchable dream history

### 💬 **Quick Chat**
- Direct AI conversation for wellness questions
- Context-aware responses for mental health support
- Privacy-focused local processing
- No conversation history stored

---

## 🌀 Local & Private
AuriZen runs fully on your device using MediaPipe LLM Inference.  
No data leaves your phone. Your thoughts are yours alone.

## 🌤️ Supportive & Grounding
From gentle reflections to soothing sounds,  
AuriZen helps you slow down, breathe, and reconnect.

## 🌱 Designed for Daily Moments
No pressure. No pop-ups.  
Just small, mindful moments that help you feel more present.

---

## 🛠️ Technical Architecture

### **Core Technologies**
- **Android SDK**: Minimum API 26 (Android 7.0), Target API 34
- **MediaPipe LLM Inference**: On-device AI processing with Gemma 3 model
- **Jetpack Compose**: Modern declarative UI framework
- **Material Design 3**: Pastel-themed design system
- **Kotlin Coroutines**: Asynchronous programming for smooth UX

### **AI & Machine Learning**
- **Model**: Gemma 3N (E2B-it-int4) optimized for mobile inference
- **Local Processing**: All AI interactions happen on-device
- **Model Storage**: Secure local file storage in app's internal directory
- **Authentication**: OAuth integration with Hugging Face for model downloads
- **Performance**: GPU acceleration with OpenCL support

### **Audio System**
- **Background Sounds**: MediaPlayer-based audio streaming
- **Binaural Tones**: Real-time AudioTrack generation with sine wave synthesis
- **Text-to-Speech**: Android TTS integration with customizable voice parameters
- **Audio Mixing**: Multi-layer audio management (TTS + background + binaural)

### **Data Management**
- **Secure Storage**: EncryptedSharedPreferences for sensitive user data
- **Local Persistence**: JSON-based storage for wellness data (moods, dreams, sessions)
- **User Profiles**: Encrypted preference management
- **No Cloud Sync**: 100% local data storage

### **Architecture Patterns**
- **MVVM**: Model-View-ViewModel with Compose state management
- **Singleton Pattern**: Centralized AI model instance management
- **Repository Pattern**: Data abstraction for storage operations
- **Navigation Component**: Type-safe screen navigation with Jetpack Compose


---

## 🚀 Getting Started

### **Prerequisites**
- Android Studio Hedgehog (2023.1.1) or later
- Physical Android device with GPU support (required for MediaPipe)
- Hugging Face account for model downloads

### **Setup**
1. Clone the repository
2. Build and install:
   ```bash
   ./gradlew installDebug
   ```

### **Development Commands**
```bash
# Build the project
./gradlew build

# Run tests
./gradlew test
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

---

## 📱 System Requirements

- **Android 7.0+** (API 26+)
- **GPU Support**: Required for MediaPipe LLM inference
- **RAM**: Minimum 4GB recommended for optimal AI performance
- **Storage**: ~2GB for AI model and app data
- **Permissions**: Internet (for model download), microphone (for TTS), activity recognition

---

## 🔒 Privacy & Security

- **100% Local Processing**: No data transmitted to external servers
- **Encrypted Storage**: All user data encrypted using Android Keystore
- **No Analytics**: No usage tracking or data collection
- **Open Source**: Transparent codebase for security auditing
- **Model Security**: Authenticated downloads with license compliance

---

**Feel lighter. Think clearer.**  
**Find your calm with AuriZen.**
