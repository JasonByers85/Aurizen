package com.aurizen.data

/**
 * Enhanced meditation pacing system for intelligent guidance flow
 * Creates structured meditation segments with instructions, practice time, and gentle cues
 */

// Core segment types for meditation flow
enum class MeditationSegmentType {
    INSTRUCTION,     // Spoken guidance
    PRACTICE,        // Silent practice with optional gentle cues
    REFLECTION,      // Contemplation time
    BREATHING_PAUSE, // Synchronized breathing
    TRANSITION,      // Moving between topics
    GENTLE_CUE      // Subtle encouragement during practice
}

// Individual meditation segment
data class MeditationSegment(
    val type: MeditationSegmentType,
    val content: String = "",
    val duration: Int, // seconds
    val backgroundAudio: Boolean = true,
    val volumeLevel: Float = 1.0f, // 0.0 to 1.0
    val breathingSync: Boolean = false,
    val stepIndex: Int = 0 // Which meditation step this belongs to
)

// Meditation type categories for different pacing strategies
enum class MeditationType {
    MINDFULNESS,
    BODY_SCAN,
    LOVING_KINDNESS,
    BREATHING,
    WALKING,
    VISUALIZATION,
    CONCENTRATION,
    CUSTOM // AI-generated
}

// User preferences for pacing customization
enum class CueFrequency {
    NONE,     // Pure silence during practice
    LOW,      // Every 2-3 minutes
    MEDIUM,   // Every 60-90 seconds  
    HIGH      // Every 30-45 seconds
}

enum class PauseLength {
    SHORT,    // 15-30 seconds
    MEDIUM,   // 30-60 seconds
    LONG      // 60-120 seconds
}

enum class PersonalizationLevel {
    MINIMAL,   // Basic instruction + practice
    ADAPTIVE,  // Adapts to user behavior
    GUIDED     // More frequent gentle guidance
}

// User preferences for meditation pacing
data class MeditationPacingPreferences(
    val gentleCueFrequency: CueFrequency = CueFrequency.MEDIUM,
    val pauseLength: PauseLength = PauseLength.MEDIUM,
    val breathingSync: Boolean = false,
    val personalizationLevel: PersonalizationLevel = PersonalizationLevel.ADAPTIVE,
    val instructionToSilenceRatio: Float = 0.3f, // 30% instruction, 70% practice
    val enableGentleCues: Boolean = true,
    val fadeInOut: Boolean = true, // Fade gentle cues in/out
    val preferredCueStyle: CueStyle = CueStyle.BREATHING_FOCUSED
)

// Different styles of gentle cues
enum class CueStyle {
    BREATHING_FOCUSED,
    MINDFULNESS_FOCUSED,
    RELAXATION_FOCUSED,
    BODY_AWARENESS,
    MIXED
}

// Gentle cue content organized by style
data class GentleCue(
    val content: String,
    val style: CueStyle,
    val duration: Int = 3, // seconds for TTS
    val priority: Int = 1 // Higher priority cues used more frequently
)

// Breathing rhythm for synchronized cues
data class BreathingCue(
    val phase: BreathingPhase,
    val duration: Int, // seconds
    val guidance: String = ""
)

enum class BreathingPhase {
    INHALE,
    HOLD_IN,
    EXHALE,
    HOLD_OUT
}

// Enhanced meditation step with pacing information
data class EnhancedMeditationStep(
    val originalStep: UnifiedMeditationStep,
    val segments: List<MeditationSegment>,
    val meditationType: MeditationType,
    val totalSegmentDuration: Int, // Sum of all segments
    val practiceTimeRatio: Float, // Percentage of step that is practice vs instruction
    val gentleCueCount: Int
) {
    fun getDurationSeconds(): Int = segments.sumOf { it.duration }
    fun getInstructionTime(): Int = segments.filter { it.type == MeditationSegmentType.INSTRUCTION }.sumOf { it.duration }
    fun getPracticeTime(): Int = segments.filter { it.type == MeditationSegmentType.PRACTICE }.sumOf { it.duration }
}

// Progress tracking for segment-based meditation
data class MeditationSegmentProgress(
    val currentStepIndex: Int,
    val currentSegmentIndex: Int,
    val totalSteps: Int,
    val totalSegmentsInStep: Int,
    val timeRemainingInSegment: Int,
    val timeRemainingInStep: Int,
    val totalTimeRemaining: Int,
    val currentSegment: MeditationSegment,
    val sessionState: UnifiedMeditationSessionState,
    val isGenerating: Boolean = false,
    val generationStatus: String = ""
)