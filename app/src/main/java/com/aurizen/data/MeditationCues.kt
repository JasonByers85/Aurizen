package com.aurizen.data

/**
 * Gentle cue content system for meditation guidance
 * Provides context-appropriate subtle guidance during practice periods
 */

object MeditationCues {
    
    // Breathing-focused cues
    private val breathingCues = listOf(
        GentleCue("Notice your natural breath", CueStyle.BREATHING_FOCUSED, 3, 3),
        GentleCue("Allow your breathing to flow naturally", CueStyle.BREATHING_FOCUSED, 3, 3),
        GentleCue("Feel the rhythm of your breath", CueStyle.BREATHING_FOCUSED, 3, 2),
        GentleCue("Follow your breath in and out", CueStyle.BREATHING_FOCUSED, 3, 2),
        GentleCue("Let your breath guide you deeper", CueStyle.BREATHING_FOCUSED, 3, 1),
        GentleCue("Breathe with gentle awareness", CueStyle.BREATHING_FOCUSED, 3, 2),
        GentleCue("Rest in the natural flow of breathing", CueStyle.BREATHING_FOCUSED, 3, 1),
        GentleCue("Feel each breath as it comes and goes", CueStyle.BREATHING_FOCUSED, 4, 2),
        GentleCue("Allow your breath to be your anchor", CueStyle.BREATHING_FOCUSED, 3, 1),
        GentleCue("Simply breathe and be present", CueStyle.BREATHING_FOCUSED, 3, 2)
    )
    
    // Mindfulness-focused cues
    private val mindfulnessCues = listOf(
        GentleCue("Gently return to the present moment", CueStyle.MINDFULNESS_FOCUSED, 3, 3),
        GentleCue("Notice without judgment", CueStyle.MINDFULNESS_FOCUSED, 3, 3),
        GentleCue("Allow thoughts to pass like clouds", CueStyle.MINDFULNESS_FOCUSED, 3, 2),
        GentleCue("Rest in open awareness", CueStyle.MINDFULNESS_FOCUSED, 3, 2),
        GentleCue("Simply observe what is here", CueStyle.MINDFULNESS_FOCUSED, 3, 2),
        GentleCue("Be present with what arises", CueStyle.MINDFULNESS_FOCUSED, 3, 1),
        GentleCue("Notice the space of awareness", CueStyle.MINDFULNESS_FOCUSED, 3, 1),
        GentleCue("Let awareness be effortless", CueStyle.MINDFULNESS_FOCUSED, 3, 1),
        GentleCue("Rest in the stillness of this moment", CueStyle.MINDFULNESS_FOCUSED, 4, 1),
        GentleCue("Allow everything to be as it is", CueStyle.MINDFULNESS_FOCUSED, 3, 2)
    )
    
    // Relaxation-focused cues
    private val relaxationCues = listOf(
        GentleCue("Let your body sink deeper into relaxation", CueStyle.RELAXATION_FOCUSED, 4, 3),
        GentleCue("Release any tension you're holding", CueStyle.RELAXATION_FOCUSED, 3, 3),
        GentleCue("Feel yourself becoming more relaxed", CueStyle.RELAXATION_FOCUSED, 3, 2),
        GentleCue("Allow your muscles to soften", CueStyle.RELAXATION_FOCUSED, 3, 2),
        GentleCue("Let go and relax completely", CueStyle.RELAXATION_FOCUSED, 3, 2),
        GentleCue("Feel the weight of your body", CueStyle.RELAXATION_FOCUSED, 3, 1),
        GentleCue("Sink into deep, peaceful relaxation", CueStyle.RELAXATION_FOCUSED, 3, 1),
        GentleCue("Let relaxation flow through you", CueStyle.RELAXATION_FOCUSED, 3, 1),
        GentleCue("Rest in this peaceful state", CueStyle.RELAXATION_FOCUSED, 3, 2),
        GentleCue("Allow yourself to be completely at ease", CueStyle.RELAXATION_FOCUSED, 4, 1)
    )
    
    // Body awareness cues
    private val bodyAwarenessCues = listOf(
        GentleCue("Notice any sensations in your body", CueStyle.BODY_AWARENESS, 3, 3),
        GentleCue("Scan through your body with gentle awareness", CueStyle.BODY_AWARENESS, 4, 3),
        GentleCue("Feel your body supported and at ease", CueStyle.BODY_AWARENESS, 3, 2),
        GentleCue("Notice how your body feels right now", CueStyle.BODY_AWARENESS, 3, 2),
        GentleCue("Feel the contact points of your body", CueStyle.BODY_AWARENESS, 3, 1),
        GentleCue("Bring awareness to your physical presence", CueStyle.BODY_AWARENESS, 4, 1),
        GentleCue("Feel your body from the inside", CueStyle.BODY_AWARENESS, 3, 1),
        GentleCue("Notice the aliveness in your body", CueStyle.BODY_AWARENESS, 3, 1),
        GentleCue("Rest in the felt sense of your body", CueStyle.BODY_AWARENESS, 3, 1),
        GentleCue("Allow your body to be your guide", CueStyle.BODY_AWARENESS, 3, 2)
    )
    
    // Universal cues that work across all styles
    private val universalCues = listOf(
        GentleCue("Rest in this moment", CueStyle.MIXED, 3, 3),
        GentleCue("Allow yourself to simply be", CueStyle.MIXED, 3, 3),
        GentleCue("Let go of any effort", CueStyle.MIXED, 3, 2),
        GentleCue("Rest in natural awareness", CueStyle.MIXED, 3, 2),
        GentleCue("Be gentle with yourself", CueStyle.MIXED, 3, 2),
        GentleCue("There's nothing you need to do", CueStyle.MIXED, 3, 1),
        GentleCue("Trust the process", CueStyle.MIXED, 3, 1),
        GentleCue("Let yourself be held by this moment", CueStyle.MIXED, 4, 1),
        GentleCue("Rest in inner stillness", CueStyle.MIXED, 3, 1),
        GentleCue("Allow peace to arise naturally", CueStyle.MIXED, 3, 1)
    )
    
    // Transition cues for moving between meditation phases
    private val transitionCues = listOf(
        GentleCue("Gently shift your attention", CueStyle.MIXED, 3, 3),
        GentleCue("Allow your focus to settle", CueStyle.MIXED, 3, 2),
        GentleCue("Take a moment to arrive", CueStyle.MIXED, 3, 2),
        GentleCue("Let yourself settle into this practice", CueStyle.MIXED, 4, 2),
        GentleCue("Allow yourself to go deeper", CueStyle.MIXED, 3, 1),
        GentleCue("Rest more fully in this experience", CueStyle.MIXED, 3, 1)
    )
    
    // Get cues by style with priority weighting
    fun getCuesByStyle(style: CueStyle, count: Int = 5): List<GentleCue> {
        val cues = when (style) {
            CueStyle.BREATHING_FOCUSED -> breathingCues
            CueStyle.MINDFULNESS_FOCUSED -> mindfulnessCues
            CueStyle.RELAXATION_FOCUSED -> relaxationCues
            CueStyle.BODY_AWARENESS -> bodyAwarenessCues
            CueStyle.MIXED -> universalCues
        }
        
        // Weight by priority (higher priority = more likely to be selected)
        return cues.sortedByDescending { it.priority }.take(count)
    }
    
    // Get mixed cues from multiple styles
    fun getMixedCues(
        primaryStyle: CueStyle,
        secondaryStyle: CueStyle? = null,
        count: Int = 5
    ): List<GentleCue> {
        val primaryCues = getCuesByStyle(primaryStyle, count / 2 + 1)
        val secondaryCues = secondaryStyle?.let { getCuesByStyle(it, count / 2) } ?: emptyList()
        val universalCues = getCuesByStyle(CueStyle.MIXED, 2)
        
        return (primaryCues + secondaryCues + universalCues).shuffled().take(count)
    }
    
    // Get transition cues
    fun getTransitionCues(count: Int = 3): List<GentleCue> {
        return transitionCues.shuffled().take(count)
    }
    
    // Get cues based on meditation type
    fun getCuesForMeditationType(type: MeditationType, count: Int = 5): List<GentleCue> {
        return when (type) {
            MeditationType.MINDFULNESS -> getCuesByStyle(CueStyle.MINDFULNESS_FOCUSED, count)
            MeditationType.BODY_SCAN -> getCuesByStyle(CueStyle.BODY_AWARENESS, count)
            MeditationType.BREATHING -> getCuesByStyle(CueStyle.BREATHING_FOCUSED, count)
            MeditationType.LOVING_KINDNESS -> getMixedCues(CueStyle.MINDFULNESS_FOCUSED, CueStyle.MIXED, count)
            MeditationType.VISUALIZATION -> getMixedCues(CueStyle.RELAXATION_FOCUSED, CueStyle.MINDFULNESS_FOCUSED, count)
            MeditationType.CONCENTRATION -> getMixedCues(CueStyle.BREATHING_FOCUSED, CueStyle.MINDFULNESS_FOCUSED, count)
            MeditationType.WALKING -> getMixedCues(CueStyle.BODY_AWARENESS, CueStyle.MINDFULNESS_FOCUSED, count)
            MeditationType.CUSTOM -> getMixedCues(CueStyle.MIXED, CueStyle.BREATHING_FOCUSED, count)
        }
    }
    
    // Get a single random cue for a specific context
    fun getRandomCue(
        style: CueStyle = CueStyle.MIXED,
        preferHighPriority: Boolean = true
    ): GentleCue {
        val cues = getCuesByStyle(style, 10)
        return if (preferHighPriority) {
            // Weighted random selection favoring higher priority
            val weights = cues.map { it.priority }
            val totalWeight = weights.sum()
            val randomWeight = (1..totalWeight).random()
            
            var currentWeight = 0
            for (i in cues.indices) {
                currentWeight += weights[i]
                if (randomWeight <= currentWeight) {
                    return cues[i]
                }
            }
            cues.first()
        } else {
            cues.random()
        }
    }
}