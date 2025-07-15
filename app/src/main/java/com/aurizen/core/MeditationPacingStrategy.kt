package com.aurizen.core

import com.aurizen.data.*
import kotlin.math.max
import kotlin.math.min

/**
 * Intelligent pacing strategy for meditation sessions
 * Converts traditional meditation steps into structured segments with practice time and gentle cues
 */
class MeditationPacingStrategy(
    private val preferences: MeditationPacingPreferences
) {
    
    companion object {
        private const val MIN_INSTRUCTION_TIME = 10 // Minimum seconds for instruction
        private const val MIN_PRACTICE_TIME = 15 // Minimum seconds for practice
        private const val MAX_GENTLE_CUE_INTERVAL = 180 // Maximum seconds between cues
        private const val MIN_GENTLE_CUE_INTERVAL = 30 // Minimum seconds between cues
    }
    
    /**
     * Convert a traditional meditation step into structured segments
     */
    fun createSegmentsForStep(
        step: UnifiedMeditationStep,
        meditationType: MeditationType,
        stepIndex: Int
    ): List<MeditationSegment> {
        val segments = mutableListOf<MeditationSegment>()
        
        // Calculate instruction time based on content length
        val instructionTime = calculateInstructionTime(step.guidance)
        val totalDuration = step.durationSeconds
        val practiceTime = max(MIN_PRACTICE_TIME, totalDuration - instructionTime)
        
        // Add instruction segment
        segments.add(MeditationSegment(
            type = MeditationSegmentType.INSTRUCTION,
            content = step.guidance,
            duration = instructionTime,
            backgroundAudio = true,
            volumeLevel = 0.8f, // Slightly lower volume for instruction
            stepIndex = stepIndex
        ))
        
        // Add practice segments with gentle cues
        if (practiceTime > MIN_PRACTICE_TIME) {
            segments.addAll(createPracticeSegments(
                practiceTime = practiceTime,
                meditationType = meditationType,
                stepIndex = stepIndex,
                isFirstStep = stepIndex == 0,
                isLastStep = false // Will be determined by caller
            ))
        }
        
        return segments
    }
    
    /**
     * Create practice segments with appropriately timed gentle cues
     */
    private fun createPracticeSegments(
        practiceTime: Int,
        meditationType: MeditationType,
        stepIndex: Int,
        isFirstStep: Boolean,
        isLastStep: Boolean
    ): List<MeditationSegment> {
        val segments = mutableListOf<MeditationSegment>()
        
        if (!preferences.enableGentleCues || preferences.gentleCueFrequency == CueFrequency.NONE) {
            // Just add one long practice segment
            segments.add(MeditationSegment(
                type = MeditationSegmentType.PRACTICE,
                content = "",
                duration = practiceTime,
                backgroundAudio = true,
                volumeLevel = 1.0f,
                stepIndex = stepIndex
            ))
            return segments
        }
        
        // Calculate gentle cue intervals
        val cueInterval = calculateCueInterval(practiceTime, meditationType, isFirstStep)
        val cues = MeditationCues.getCuesForMeditationType(meditationType, 10)
        
        var remainingTime = practiceTime
        var cueIndex = 0
        
        while (remainingTime > 0) {
            if (remainingTime <= cueInterval || cueIndex >= cues.size) {
                // Add final practice segment
                segments.add(MeditationSegment(
                    type = MeditationSegmentType.PRACTICE,
                    content = "",
                    duration = remainingTime,
                    backgroundAudio = true,
                    volumeLevel = 1.0f,
                    stepIndex = stepIndex
                ))
                break
            }
            
            // Add practice segment before cue
            val practiceSegmentTime = min(cueInterval, remainingTime - cues[cueIndex].duration)
            segments.add(MeditationSegment(
                type = MeditationSegmentType.PRACTICE,
                content = "",
                duration = practiceSegmentTime,
                backgroundAudio = true,
                volumeLevel = 1.0f,
                stepIndex = stepIndex
            ))
            
            remainingTime -= practiceSegmentTime
            
            // Add gentle cue if there's time and cues left
            if (remainingTime > 0 && cueIndex < cues.size) {
                val cue = cues[cueIndex]
                segments.add(MeditationSegment(
                    type = MeditationSegmentType.GENTLE_CUE,
                    content = cue.content,
                    duration = cue.duration,
                    backgroundAudio = true,
                    volumeLevel = 0.7f, // Gentler volume for cues
                    stepIndex = stepIndex
                ))
                
                remainingTime -= cue.duration
                cueIndex++
            }
        }
        
        return segments
    }
    
    /**
     * Calculate appropriate interval between gentle cues
     */
    private fun calculateCueInterval(
        practiceTime: Int,
        meditationType: MeditationType,
        isFirstStep: Boolean
    ): Int {
        val baseInterval = when (preferences.gentleCueFrequency) {
            CueFrequency.NONE -> Int.MAX_VALUE
            CueFrequency.LOW -> 150 // 2.5 minutes
            CueFrequency.MEDIUM -> 90 // 1.5 minutes
            CueFrequency.HIGH -> 45 // 45 seconds
        }
        
        // Adjust for meditation type
        val typeMultiplier = when (meditationType) {
            MeditationType.CONCENTRATION -> 1.5f // Fewer interruptions
            MeditationType.MINDFULNESS -> 1.2f
            MeditationType.BREATHING -> 1.0f
            MeditationType.BODY_SCAN -> 0.8f // More frequent guidance
            MeditationType.LOVING_KINDNESS -> 0.9f
            MeditationType.VISUALIZATION -> 1.3f
            MeditationType.WALKING -> 0.9f
            MeditationType.CUSTOM -> 1.0f
        }
        
        // Adjust for first step (more guidance)
        val firstStepMultiplier = if (isFirstStep) 0.8f else 1.0f
        
        // Adjust for personalization level
        val personalizationMultiplier = when (preferences.personalizationLevel) {
            PersonalizationLevel.MINIMAL -> 1.5f
            PersonalizationLevel.ADAPTIVE -> 1.0f
            PersonalizationLevel.GUIDED -> 0.7f
        }
        
        val adjustedInterval = (baseInterval * typeMultiplier * firstStepMultiplier * personalizationMultiplier).toInt()
        return max(MIN_GENTLE_CUE_INTERVAL, min(MAX_GENTLE_CUE_INTERVAL, adjustedInterval))
    }
    
    /**
     * Calculate instruction time based on content length and TTS speed
     */
    private fun calculateInstructionTime(guidance: String): Int {
        if (guidance.isBlank()) return 0
        
        // Estimate reading time: average 150 words per minute
        val words = guidance.split("\\s+".toRegex()).size
        val baseTime = (words / 150.0 * 60).toInt()
        
        // Add time for sentence pauses (existing 800ms between sentences)
        val sentences = guidance.split("[.!?]+".toRegex()).filter { it.isNotBlank() }.size
        val pauseTime = (sentences * 0.8).toInt()
        
        // Add buffer time for natural speech rhythm
        val bufferTime = (baseTime * 0.2).toInt()
        
        return max(MIN_INSTRUCTION_TIME, baseTime + pauseTime + bufferTime)
    }
    
    /**
     * Determine meditation type from step content or configuration
     */
    fun inferMeditationType(
        steps: List<UnifiedMeditationStep>,
        config: UnifiedMeditationConfig
    ): MeditationType {
        if (config.isCustomGenerated) {
            // Check focus keywords for custom meditations
            val focus = config.focus.lowercase()
            return when {
                focus.contains("breath") || focus.contains("breathing") -> MeditationType.BREATHING
                focus.contains("body") || focus.contains("scan") -> MeditationType.BODY_SCAN
                focus.contains("loving") || focus.contains("kindness") || focus.contains("compassion") -> MeditationType.LOVING_KINDNESS
                focus.contains("walking") || focus.contains("movement") -> MeditationType.WALKING
                focus.contains("visual") || focus.contains("imagine") -> MeditationType.VISUALIZATION
                focus.contains("focus") || focus.contains("concentration") -> MeditationType.CONCENTRATION
                else -> MeditationType.CUSTOM
            }
        }
        
        // Analyze step content for predefined meditations
        val allContent = steps.joinToString(" ") { "${it.title} ${it.guidance}" }.lowercase()
        
        return when {
            allContent.contains("breath") && allContent.count { it.toString().contains("breath") } > 3 -> MeditationType.BREATHING
            allContent.contains("body") && allContent.contains("scan") -> MeditationType.BODY_SCAN
            allContent.contains("loving") || allContent.contains("kindness") -> MeditationType.LOVING_KINDNESS
            allContent.contains("walking") || allContent.contains("step") -> MeditationType.WALKING
            allContent.contains("visualize") || allContent.contains("imagine") -> MeditationType.VISUALIZATION
            allContent.contains("focus") || allContent.contains("concentrate") -> MeditationType.CONCENTRATION
            else -> MeditationType.MINDFULNESS
        }
    }
    
    /**
     * Create transition segments between meditation steps
     */
    fun createTransitionSegment(
        fromStep: UnifiedMeditationStep,
        toStep: UnifiedMeditationStep,
        stepIndex: Int
    ): MeditationSegment {
        val transitionCues = MeditationCues.getTransitionCues(1)
        val cue = transitionCues.firstOrNull() ?: GentleCue("Take a moment to transition", CueStyle.MIXED, 3)
        
        return MeditationSegment(
            type = MeditationSegmentType.TRANSITION,
            content = cue.content,
            duration = cue.duration,
            backgroundAudio = true,
            volumeLevel = 0.6f,
            stepIndex = stepIndex
        )
    }
    
    /**
     * Calculate total practice time ratio for a step
     */
    fun calculatePracticeTimeRatio(segments: List<MeditationSegment>): Float {
        val totalDuration = segments.sumOf { it.duration }
        if (totalDuration == 0) return 0f
        
        val practiceTime = segments.filter { 
            it.type == MeditationSegmentType.PRACTICE || 
            it.type == MeditationSegmentType.REFLECTION 
        }.sumOf { it.duration }
        
        return practiceTime.toFloat() / totalDuration.toFloat()
    }
}