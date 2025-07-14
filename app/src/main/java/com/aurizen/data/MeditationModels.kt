package com.aurizen.data

// Shared enums and data classes for meditation functionality

enum class BackgroundSound(val displayName: String, val fileName: String) {
    NONE("None", ""),
    RAIN("Rain", "rain"),
    OCEAN("Ocean Waves", "sea"),
    CRICKETS("Crickets", "crickets"),
    WHITE_NOISE("White Noise", "whitenoise"),
    BIRDS("Birds", "birds"),
    FOREST("Forest", "forest")
}

enum class BinauralTone(val displayName: String, val frequency: Float, val description: String) {
    NONE("None", 0f, "No binaural tone"),
    ANXIETY_RELIEF("Anxiety Relief", 6f, "6Hz - Theta waves for deep calm"),
    FOCUS("Focus & Clarity", 10f, "10Hz - Alpha waves for concentration"),
    MEDITATION("Deep Meditation", 4f, "4Hz - Theta waves for deep states"),
    SLEEP("Sleep Induction", 2f, "2Hz - Delta waves for sleep"),
    CREATIVITY("Creativity", 8f, "8Hz - Alpha waves for creative flow"),
    ALERTNESS("Mental Alertness", 14f, "14Hz - Beta waves for alertness")
}

// Audio settings data class
data class AudioSettings(
    val soundEnabled: Boolean,
    val backgroundSound: BackgroundSound,
    val binauralEnabled: Boolean,
    val binauralTone: BinauralTone,
    val ttsEnabled: Boolean
)

enum class MeditationSessionState {
    PREPARING,
    ACTIVE,
    PAUSED,
    COMPLETED
}

data class MeditationStep(
    val title: String,
    val description: String,
    val guidance: String,
    val durationSeconds: Int
)

data class MeditationStatistics(
    val totalSessions: Int,
    val totalMinutes: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val averageSessionLength: Float,
    val lastSessionDate: Long
)

// UPDATED: Simplified custom meditation config - no longer needs complex state tracking
data class CustomMeditationConfig(
    val sessionId: String,
    val totalDuration: Int, // in minutes
    val totalSteps: Int,
    val stepDuration: Int, // in seconds
    val focus: String,
    val mood: String,
    val experience: String
)