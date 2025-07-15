package com.aurizen.core

import com.aurizen.data.*
import android.util.Log

/**
 * Controls the timing and flow of meditation sessions with enhanced pacing
 * Manages segment-based meditation flow with intelligent pacing and gentle cues
 */
class MeditationTimingController(
    private val preferences: MeditationPacingPreferences
) {
    
    companion object {
        private const val TAG = "MeditationTimingController"
    }
    
    private val pacingStrategy = MeditationPacingStrategy(preferences)
    
    /**
     * Convert traditional meditation steps into enhanced paced meditation
     */
    fun createEnhancedMeditation(
        steps: List<UnifiedMeditationStep>,
        config: UnifiedMeditationConfig
    ): List<EnhancedMeditationStep> {
        Log.d(TAG, "Creating enhanced meditation with ${steps.size} steps")
        
        val meditationType = pacingStrategy.inferMeditationType(steps, config)
        Log.d(TAG, "Inferred meditation type: $meditationType")
        
        val enhancedSteps = mutableListOf<EnhancedMeditationStep>()
        
        steps.forEachIndexed { index, step ->
            val segments = pacingStrategy.createSegmentsForStep(
                step = step,
                meditationType = meditationType,
                stepIndex = index
            )
            
            // Add transition segment between steps (except for first step)
            val finalSegments = if (index > 0 && index < steps.size) {
                val transitionSegment = pacingStrategy.createTransitionSegment(
                    fromStep = steps[index - 1],
                    toStep = step,
                    stepIndex = index
                )
                listOf(transitionSegment) + segments
            } else {
                segments
            }
            
            val practiceTimeRatio = pacingStrategy.calculatePracticeTimeRatio(finalSegments)
            val gentleCueCount = finalSegments.count { it.type == MeditationSegmentType.GENTLE_CUE }
            
            val enhancedStep = EnhancedMeditationStep(
                originalStep = step,
                segments = finalSegments,
                meditationType = meditationType,
                totalSegmentDuration = finalSegments.sumOf { it.duration },
                practiceTimeRatio = practiceTimeRatio,
                gentleCueCount = gentleCueCount
            )
            
            enhancedSteps.add(enhancedStep)
            
            Log.d(TAG, "Step $index: ${finalSegments.size} segments, ${gentleCueCount} gentle cues, ${practiceTimeRatio * 100}% practice time")
        }
        
        return enhancedSteps
    }
    
    /**
     * Get the next segment to play in the meditation
     */
    fun getNextSegment(
        enhancedSteps: List<EnhancedMeditationStep>,
        currentStepIndex: Int,
        currentSegmentIndex: Int
    ): MeditationSegment? {
        if (currentStepIndex >= enhancedSteps.size) return null
        
        val currentStep = enhancedSteps[currentStepIndex]
        
        return if (currentSegmentIndex < currentStep.segments.size) {
            currentStep.segments[currentSegmentIndex]
        } else {
            // Move to next step
            val nextStepIndex = currentStepIndex + 1
            if (nextStepIndex < enhancedSteps.size) {
                enhancedSteps[nextStepIndex].segments.firstOrNull()
            } else {
                null
            }
        }
    }
    
    /**
     * Calculate progress for segment-based meditation
     */
    fun calculateProgress(
        enhancedSteps: List<EnhancedMeditationStep>,
        currentStepIndex: Int,
        currentSegmentIndex: Int,
        timeRemainingInSegment: Int
    ): MeditationSegmentProgress {
        val currentStep = enhancedSteps.getOrNull(currentStepIndex)
        val currentSegment = currentStep?.segments?.getOrNull(currentSegmentIndex)
        
        // Calculate time remaining in current step
        val timeRemainingInStep = if (currentStep != null && currentSegment != null) {
            val remainingSegmentsInStep = currentStep.segments.drop(currentSegmentIndex + 1)
            timeRemainingInSegment + remainingSegmentsInStep.sumOf { it.duration }
        } else {
            0
        }
        
        // Calculate total time remaining
        val remainingSteps = enhancedSteps.drop(currentStepIndex + 1)
        val totalTimeRemaining = timeRemainingInStep + remainingSteps.sumOf { it.totalSegmentDuration }
        
        return MeditationSegmentProgress(
            currentStepIndex = currentStepIndex,
            currentSegmentIndex = currentSegmentIndex,
            totalSteps = enhancedSteps.size,
            totalSegmentsInStep = currentStep?.segments?.size ?: 0,
            timeRemainingInSegment = timeRemainingInSegment,
            timeRemainingInStep = timeRemainingInStep,
            totalTimeRemaining = totalTimeRemaining,
            currentSegment = currentSegment ?: createEmptySegment(),
            sessionState = UnifiedMeditationSessionState.ACTIVE
        )
    }
    
    /**
     * Move to next segment and return new indices
     */
    fun moveToNextSegment(
        enhancedSteps: List<EnhancedMeditationStep>,
        currentStepIndex: Int,
        currentSegmentIndex: Int
    ): Pair<Int, Int> {
        val currentStep = enhancedSteps.getOrNull(currentStepIndex)
        
        return if (currentStep != null && currentSegmentIndex + 1 < currentStep.segments.size) {
            // Move to next segment in same step
            Pair(currentStepIndex, currentSegmentIndex + 1)
        } else {
            // Move to first segment of next step
            Pair(currentStepIndex + 1, 0)
        }
    }
    
    /**
     * Check if meditation session is complete
     */
    fun isSessionComplete(
        enhancedSteps: List<EnhancedMeditationStep>,
        currentStepIndex: Int,
        currentSegmentIndex: Int
    ): Boolean {
        if (currentStepIndex >= enhancedSteps.size) return true
        
        val currentStep = enhancedSteps[currentStepIndex]
        return currentStepIndex == enhancedSteps.size - 1 && 
               currentSegmentIndex >= currentStep.segments.size
    }
    
    /**
     * Get meditation statistics
     */
    fun getMeditationStats(enhancedSteps: List<EnhancedMeditationStep>): MeditationStats {
        val totalDuration = enhancedSteps.sumOf { it.totalSegmentDuration }
        val totalInstructionTime = enhancedSteps.sumOf { it.getInstructionTime() }
        val totalPracticeTime = enhancedSteps.sumOf { it.getPracticeTime() }
        val totalGentleCues = enhancedSteps.sumOf { it.gentleCueCount }
        val totalSegments = enhancedSteps.sumOf { it.segments.size }
        
        return MeditationStats(
            totalDuration = totalDuration,
            totalInstructionTime = totalInstructionTime,
            totalPracticeTime = totalPracticeTime,
            instructionToSilenceRatio = if (totalDuration > 0) totalInstructionTime.toFloat() / totalDuration.toFloat() else 0f,
            practiceToSilenceRatio = if (totalDuration > 0) totalPracticeTime.toFloat() / totalDuration.toFloat() else 0f,
            totalGentleCues = totalGentleCues,
            totalSegments = totalSegments,
            averageSegmentDuration = if (totalSegments > 0) totalDuration.toFloat() / totalSegments.toFloat() else 0f,
            meditationType = enhancedSteps.firstOrNull()?.meditationType ?: MeditationType.MINDFULNESS
        )
    }
    
    /**
     * Update pacing preferences and recreate strategy
     */
    fun updatePreferences(newPreferences: MeditationPacingPreferences): MeditationTimingController {
        return MeditationTimingController(newPreferences)
    }
    
    /**
     * Get current segment type for UI display
     */
    fun getCurrentSegmentType(
        enhancedSteps: List<EnhancedMeditationStep>,
        currentStepIndex: Int,
        currentSegmentIndex: Int
    ): MeditationSegmentType {
        return enhancedSteps.getOrNull(currentStepIndex)
            ?.segments?.getOrNull(currentSegmentIndex)
            ?.type ?: MeditationSegmentType.PRACTICE
    }
    
    /**
     * Check if current segment should play TTS
     */
    fun shouldPlayTTS(segmentType: MeditationSegmentType): Boolean {
        return when (segmentType) {
            MeditationSegmentType.INSTRUCTION -> true
            MeditationSegmentType.GENTLE_CUE -> true
            MeditationSegmentType.TRANSITION -> true
            MeditationSegmentType.PRACTICE -> false
            MeditationSegmentType.REFLECTION -> false
            MeditationSegmentType.BREATHING_PAUSE -> false
        }
    }
    
    /**
     * Get volume level for current segment
     */
    fun getSegmentVolume(
        enhancedSteps: List<EnhancedMeditationStep>,
        currentStepIndex: Int,
        currentSegmentIndex: Int
    ): Float {
        return enhancedSteps.getOrNull(currentStepIndex)
            ?.segments?.getOrNull(currentSegmentIndex)
            ?.volumeLevel ?: 1.0f
    }
    
    private fun createEmptySegment(): MeditationSegment {
        return MeditationSegment(
            type = MeditationSegmentType.PRACTICE,
            content = "",
            duration = 0,
            backgroundAudio = true,
            volumeLevel = 1.0f,
            stepIndex = 0
        )
    }
}

/**
 * Statistics for a meditation session
 */
data class MeditationStats(
    val totalDuration: Int,
    val totalInstructionTime: Int,
    val totalPracticeTime: Int,
    val instructionToSilenceRatio: Float,
    val practiceToSilenceRatio: Float,
    val totalGentleCues: Int,
    val totalSegments: Int,
    val averageSegmentDuration: Float,
    val meditationType: MeditationType
)