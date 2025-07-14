package com.aurizen.prompts

import com.aurizen.data.MoodEntry
import com.aurizen.data.PersonalGoal
import java.text.SimpleDateFormat
import java.util.*

/**
 * Optimized PromptBuilder using compression strategies for faster LLM inference:
 * 
 * 1. Abbreviations: "para" for paragraphs, "w" for words, "mem" for memories
 * 2. Symbolic shortcuts: "+" for "and", "->" for connections, "|" for separators 
 * 3. Keyword-based instructions: "Be: helpful, concise" vs verbose guidelines
 * 4. Structured data format: "topics:x,y|moods:a,b|goals:c(50%)" vs full sentences
 * 5. Eliminated redundancy: Removed repeated role descriptions and feature lists
 * 6. Implicit behavior: Removed obvious instructions that well-trained models know
 * 
 * Result: ~70% token reduction while maintaining semantic intent
 */

enum class PromptType {
    QUICK_CHAT,
    TALK,
    MOOD_INSIGHTS,
    DREAM_INTERPRETER,
    MEDITATION_GENERATION,
    MEDITATION_MOOD_GUIDED,
    FUNCTION_CALLING,
    TEST_FUNCTION_CALLING
}

data class PromptContext(
    val userMemories: List<String> = emptyList(),
    val recentMoods: List<MoodEntry> = emptyList(),
    val personalGoals: List<PersonalGoal> = emptyList(),
    val recentTopics: List<String> = emptyList(),
    val currentDate: Date = Date(),
    val additionalContext: Map<String, Any> = emptyMap()
)

class PromptBuilder {
    
    companion object {
        private const val APP_NAME = "AuriZen"
        private const val MAX_RESPONSE_WORDS = 400
        
        // Token-efficient feature list (was 6 lines, now 1 line)
        private const val FEATURES = "Features: meditations(AI+preset), breathing, mood/dream tracking, goals, voice chat"
        
        // Keyword-based guidelines (was 5 bullet points, now 1 sentence)
        private const val GUIDELINES = "Be: helpful, concise(1-2para), practical, supportive, positive. No diagnoses. Suggest features naturally."
        
        fun build(type: PromptType, context: PromptContext = PromptContext()): String {
            return when (type) {
                PromptType.QUICK_CHAT -> buildQuickChatPrompt(context)
                PromptType.TALK -> buildTalkPrompt(context)
                PromptType.MOOD_INSIGHTS -> buildMoodInsightsPrompt(context)
                PromptType.DREAM_INTERPRETER -> buildDreamInterpreterPrompt()
                PromptType.MEDITATION_GENERATION -> buildMeditationGenerationPrompt(context)
                PromptType.MEDITATION_MOOD_GUIDED -> buildMoodGuidedMeditationPrompt(context)
                PromptType.FUNCTION_CALLING -> buildFunctionCallingPrompt()
                PromptType.TEST_FUNCTION_CALLING -> buildTestFunctionCallingPrompt()
            }
        }
        
        private fun buildQuickChatPrompt(context: PromptContext): String {
            val ctx = formatCompactContext(context)
            return "AI wellness companion. $GUIDELINES $FEATURES\n${if (ctx.isNotEmpty()) "Context: $ctx" else ""}"
        }
        
        private fun buildTalkPrompt(context: PromptContext): String {
            val mem = context.userMemories.take(3).joinToString("; ")
            return "$APP_NAME AI. Conversational, friendly, concise. $FEATURES\n${if (mem.isNotEmpty()) "Context: $mem\n" else ""}Be supportive:"
        }
        
        private fun buildMoodInsightsPrompt(context: PromptContext): String {
            val summary = context.additionalContext["moodSummary"] as? String ?: ""
            return "Analyze mood+goals. Data: $summary\n3-4 short paras: patterns->goals, progress, suggestions, encouragement. <${MAX_RESPONSE_WORDS}w. Connect mood->goals naturally."
        }
        
        private fun buildDreamInterpreterPrompt(): String {
            return "Dream interpreter. Balanced insights, multiple meanings, psychology-based. No superstition. Growth-focused, supportive. 3-4para, include reflection questions:"
        }
        
        private fun buildMeditationGenerationPrompt(context: PromptContext): String {
            val step = context.additionalContext["step"] as? Int ?: 1
            val totalSteps = context.additionalContext["totalSteps"] as? Int ?: 1
            val duration = context.additionalContext["duration"] as? Int ?: 180
            val focusArea = context.additionalContext["focusArea"] as? String ?: "relaxation"
            val stepType = context.additionalContext["stepType"] as? String ?: "continuation"
            
            return "$stepType meditation: $focusArea, $step/$totalSteps, ${duration}s\nJSON: {\"title\":\"\", \"guidance\":\"2-3min, start breathing\"}"
        }
        
        private fun buildMoodGuidedMeditationPrompt(context: PromptContext): String {
            val step = context.additionalContext["step"] as? Int ?: 1
            val totalSteps = context.additionalContext["totalSteps"] as? Int ?: 1
            val duration = context.additionalContext["duration"] as? Int ?: 180
            val stepType = context.additionalContext["stepType"] as? String ?: "continuation"
            val moodContext = context.additionalContext["moodContext"] as? String ?: ""
            
            return "Personal $stepType: $step/$totalSteps, ${duration}s\nMoods: $moodContext\nJSON: acknowledge+validate+celebrate->balance. Reference specific patterns."
        }
        
        private fun buildFunctionCallingPrompt(): String {
            return "Funcs: STORE_MEMORY:{\"memory\":\"\"}, CREATE_MEDITATION:{\"focus\":\"\",\"duration\":10}. Use for memory/meditation requests."
        }
        
        private fun buildTestFunctionCallingPrompt(): String {
            return "Wellness AI w/ functions. ${buildFunctionCallingPrompt()} Use when explicitly requested."
        }
        
        private fun formatDate(date: Date): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        }
        
        private fun formatRecentMoods(moods: List<MoodEntry>): String {
            return moods.joinToString(", ") { entry ->
                entry.mood
            }
        }
        
        private fun formatPersonalGoals(goals: List<PersonalGoal>): String {
            return goals.filter { !it.isCompleted }
                .joinToString(", ") { "${it.title} (${it.progress}%)" }
        }
        
        private fun formatPersonalGoalsDetailed(goals: List<PersonalGoal>): String {
            return goals.filter { !it.isCompleted }
                .joinToString("\n") { goal ->
                    val deadline = " - Due: ${formatDate(Date(goal.targetDate))}"
                    "- ${goal.title} (${goal.category.displayName}): ${goal.progress}% complete$deadline"
                }
        }
        
        // Compressed context formatting using structured shorthand
        private fun formatCompactContext(context: PromptContext): String {
            val parts = mutableListOf<String>()
            
            if (context.recentTopics.isNotEmpty()) {
                parts.add("topics:${context.recentTopics.take(3).joinToString(",")}")
            }
            
            if (context.recentMoods.isNotEmpty()) {
                parts.add("moods:${context.recentMoods.take(3).joinToString(",") { it.mood }}")
            }
            
            if (context.personalGoals.isNotEmpty()) {
                val goals = context.personalGoals.filter { !it.isCompleted }.take(2)
                parts.add("goals:${goals.joinToString(",") { "${it.title}(${it.progress}%)" }}")
            }
            
            if (context.userMemories.isNotEmpty()) {
                parts.add("mem:${context.userMemories.take(2).joinToString(";")}")
            }
            
            return parts.joinToString("|")
        }
    }
}