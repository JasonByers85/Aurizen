package com.aurizen.data

// Unified models for both regular and custom meditations

// Common interface for all meditation steps
interface UnifiedMeditationStep {
    val title: String
    val guidance: String
    val durationSeconds: Int
    val description: String?
    val stepIndex: Int
    val isCustomGenerated: Boolean
}

// Unified session state that covers all meditation types
enum class UnifiedMeditationSessionState {
    PREPARING,   // Initial setup, might be generating content
    READY,       // Ready to start
    ACTIVE,      // Session running
    PAUSED,      // Session paused
    COMPLETED    // Session finished
}

// Unified progress tracking
data class UnifiedMeditationProgress(
    val currentStepIndex: Int,
    val totalSteps: Int,
    val timeRemainingInStep: Int,
    val totalTimeRemaining: Int,
    val isGenerating: Boolean = false,
    val generationStatus: String = "",
    val sessionState: UnifiedMeditationSessionState
)

// Unified session configuration
data class UnifiedMeditationConfig(
    val sessionId: String,
    val meditationType: String,
    val totalDuration: Int, // in minutes
    val totalSteps: Int,
    val isCustomGenerated: Boolean,
    // Custom meditation specific fields
    val focus: String = "",
    val mood: String = "",
    val experience: String = "",
    val stepDuration: Int = 0, // in seconds, for custom meditations
    val moodContext: String = "" // Recent mood patterns and context
)

// Extension functions to convert existing models to unified interface
fun MeditationStep.toUnified(index: Int = 0): UnifiedMeditationStep {
    return object : UnifiedMeditationStep {
        override val title: String = this@toUnified.title
        override val guidance: String = this@toUnified.guidance
        override val durationSeconds: Int = this@toUnified.durationSeconds
        override val description: String? = this@toUnified.description
        override val stepIndex: Int = index
        override val isCustomGenerated: Boolean = false
    }
}

// Convert unified back to specific types when needed
fun UnifiedMeditationStep.toMeditationStep(): MeditationStep {
    return MeditationStep(
        title = this.title,
        description = this.description ?: "Meditation step",
        guidance = this.guidance,
        durationSeconds = this.durationSeconds
    )
}

// Removed CustomMeditationStep conversion - no longer needed

// Unified state conversion functions
fun MeditationSessionState.toUnified(): UnifiedMeditationSessionState {
    return when (this) {
        MeditationSessionState.PREPARING -> UnifiedMeditationSessionState.PREPARING
        MeditationSessionState.ACTIVE -> UnifiedMeditationSessionState.ACTIVE
        MeditationSessionState.PAUSED -> UnifiedMeditationSessionState.PAUSED
        MeditationSessionState.COMPLETED -> UnifiedMeditationSessionState.COMPLETED
    }
}

// Removed CustomMeditationSessionState conversion - no longer needed

// Streaming support for custom meditation text
data class StreamingMeditationContent(
    val stepIndex: Int,
    val partialTitle: String = "",
    val partialGuidance: String = "",
    val isComplete: Boolean = false,
    val estimatedTotalLength: Int = 0,
    val currentProgress: Float = 0f // 0.0 to 1.0
)

// Generation status for streaming
sealed class MeditationGenerationStatus {
    object Idle : MeditationGenerationStatus()
    object Starting : MeditationGenerationStatus()
    data class Generating(val stepIndex: Int, val progress: Float) : MeditationGenerationStatus()
    data class StreamingContent(val content: StreamingMeditationContent) : MeditationGenerationStatus()
    data class Completed(val stepIndex: Int) : MeditationGenerationStatus()
    data class Error(val message: String, val canRetry: Boolean = true) : MeditationGenerationStatus()
}

// Saved meditation models
data class SavedMeditation(
    val id: String,
    val name: String,
    val description: String,
    val totalDuration: Int, // in minutes
    val totalSteps: Int,
    val createdAt: Long,
    val lastUsedAt: Long,
    val saveType: SavedMeditationType,
    // For exact session saves
    val savedSteps: List<SavedMeditationStep>? = null,
    // For config saves (AI will regenerate)
    val config: SavedMeditationConfig? = null
)

data class SavedMeditationStep(
    val title: String,
    val guidance: String,
    val durationSeconds: Int,
    val stepIndex: Int
)

data class SavedMeditationConfig(
    val focus: String,
    val mood: String,
    val experience: String,
    val totalDuration: Int, // in minutes
    val totalSteps: Int
)

enum class SavedMeditationType {
    EXACT_SESSION,  // Save exact generated content
    CONFIG_TEMPLATE // Save config for AI regeneration
}