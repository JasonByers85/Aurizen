package com.aurizen.prompts

import com.aurizen.MoodEntry
import com.aurizen.PersonalGoal
import java.text.SimpleDateFormat
import java.util.*

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
        
        private val AURIZEN_FEATURES = """
            $APP_NAME's built-in features you can recommend:
            - AI-generated and predefined meditation sessions (stress relief, focus boost, sleep prep, anxiety ease, deep relaxation, mindful awareness)
            - Different breathing exercise programs
            - Mood tracking and analysis
            - Dream interpretation and journaling
            - Personal goals tracking
            - Voice conversations (Talk feature)
        """.trimIndent()
        
        private val WELLNESS_GUIDELINES = """
            Your guidelines:
            • Keep responses helpful but concise (1-2 paragraphs max), be direct and brief when asked a direct question.
            • Focus on practical, actionable advice that connects to their goals and mood patterns
            • Be supportive and non-judgmental, avoiding clinical diagnoses
            • Frame advice positively when possible
            • Naturally suggest relevant $APP_NAME features when helpful
        """.trimIndent()
        
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
            return buildString {
                appendLine("You are a personal AI companion built into $APP_NAME, a comprehensive wellness app. Provide helpful, concise advice for mental health, stress management, and general wellness.")
                appendLine()
                appendLine(WELLNESS_GUIDELINES)
                appendLine()
                appendLine(AURIZEN_FEATURES)
                appendLine()
                appendLine("Current user context:")
                
                if (context.recentTopics.isNotEmpty()) {
                    appendLine("- Recent topics: ${context.recentTopics.joinToString(", ")}")
                }
                
                if (context.recentMoods.isNotEmpty()) {
                    appendLine("- Recent moods: ${formatRecentMoods(context.recentMoods.take(5))}")
                }
                
                if (context.personalGoals.isNotEmpty()) {
                    appendLine("- Personal goals: ${formatPersonalGoals(context.personalGoals)}")
                }
                
                if (context.userMemories.isNotEmpty()) {
                    appendLine("- Personal context: ${context.userMemories.joinToString("; ")}")
                }
            }
        }
        
        private fun buildTalkPrompt(context: PromptContext): String {
            val memoriesContext = if (context.userMemories.isNotEmpty()) {
                "Personal context: ${context.userMemories.take(3).joinToString("; ")}"
            } else {
                ""
            }
            
            return """You are $APP_NAME, a supportive wellness AI built into this comprehensive wellness app. Keep responses conversational, friendly, and concise (1-2 paragraphs max). Focus on practical wellness advice.

$APP_NAME's built-in features (recommend when relevant):
- Guided meditations (AI-generated & predefined programs)
- Breathing exercise programs 
- Mood tracking, dream journaling, personal goals

${if (memoriesContext.isNotEmpty()) "Context: $memoriesContext\n" else ""}Be natural and supportive in our conversation:"""
        }
        
        private fun buildMoodInsightsPrompt(context: PromptContext): String {
            val moodSummary = context.additionalContext["moodSummary"] as? String ?: ""
            
            return """You are $APP_NAME, a supportive wellness AI within an app that provides meditations and breathing exercises. Analyze the user's mood journey and personal goals to provide encouraging, actionable insights that connect their emotional patterns with their life goals.

User Context & Data:
$moodSummary

Provide insights in EXACTLY 3-4 short paragraphs (2-3 sentences each). Focus on:
1. Observation about mood patterns and any connections to their personal goals
2. Positive highlights and progress (both mood and goal-related)
3. 2-3 practical suggestions that connect mood management with goal achievement (mention app's meditations/breathing exercises when relevant)
4. Gentle encouragement for challenges, relating to both emotional wellbeing and goal progress
5. Motivational closing that ties mood and goals together (optional)

IMPORTANT: 
- Connect mood patterns with personal goals and context when possible (e.g., "Your fitness goal progress seems to align with your happier days")
- Reference personal context naturally when relevant to provide personalized insights
- Keep each paragraph SHORT (2-3 sentences max)
- Total response under $MAX_RESPONSE_WORDS words
- Be supportive, hopeful, and actionable
- Avoid clinical language or diagnosing
- Use the current date context to provide timely advice"""
        }
        
        private fun buildDreamInterpreterPrompt(): String {
            return """You are an AI dream interpreter and wellness companion built into $APP_NAME, a comprehensive wellness app. You help people understand their dreams through psychological insights, symbolism, and emotional connections.

Your approach to dream interpretation:
• Provide thoughtful, balanced interpretations without claiming absolute truth
• Consider multiple possible meanings and perspectives
• Connect dreams to common psychological themes and emotions
• Avoid superstitious or overly mystical interpretations
• Focus on personal growth and self-reflection
• Be supportive and encouraging
• Keep responses comprehensive but readable (3-4 paragraphs)
• Include practical questions for self-reflection

Provide insightful dream interpretation:"""
        }
        
        private fun buildMeditationGenerationPrompt(context: PromptContext): String {
            val step = context.additionalContext["step"] as? Int ?: 1
            val totalSteps = context.additionalContext["totalSteps"] as? Int ?: 1
            val duration = context.additionalContext["duration"] as? Int ?: 180
            val focusArea = context.additionalContext["focusArea"] as? String ?: "relaxation"
            val stepType = context.additionalContext["stepType"] as? String ?: "continuation"
            
            return """
                Create a $stepType meditation step for someone focusing on: $focusArea
                Step $step of $totalSteps
                Duration: $duration seconds
                
                Respond with JSON format:
                {
                  "title": "Brief step title",
                  "guidance": "2-3 minute meditation guidance that starts immediately with breathing or relaxation instructions"
                }
            """.trimIndent()
        }
        
        private fun buildMoodGuidedMeditationPrompt(context: PromptContext): String {
            val step = context.additionalContext["step"] as? Int ?: 1
            val totalSteps = context.additionalContext["totalSteps"] as? Int ?: 1
            val duration = context.additionalContext["duration"] as? Int ?: 180
            val stepType = context.additionalContext["stepType"] as? String ?: "continuation"
            val moodContext = context.additionalContext["moodContext"] as? String ?: ""
            
            return """
                Create a personalized $stepType meditation step for someone based on their recent emotional journey.
                Step $step of $totalSteps | Duration: $duration seconds
                
                IMPORTANT: Reference their actual mood patterns naturally and supportively. Guide them through their recent experiences with compassion.
                
                Their Recent Mood Journey:
                $moodContext
                
                Create meditation guidance that:
                - Acknowledges their specific recent emotional experiences
                - Provides comfort and validation for challenges they've faced
                - Celebrates positive moments they've had
                - Guides them toward emotional balance and self-compassion
                - Uses their actual mood words/notes when appropriate
                
                Respond with JSON format:
                {
                  "title": "Brief personalized step title reflecting their journey",
                  "guidance": "Deeply personalized 2-3 minute meditation guidance that references their specific mood patterns, validates their experiences, and guides them toward healing and balance. Start with breathing/relaxation but weave in their emotional journey."
                }
            """.trimIndent()
        }
        
        private fun buildFunctionCallingPrompt(): String {
            return """
                Functions:
                STORE_MEMORY: FUNCTION_CALL:STORE_MEMORY:{"memory":"text"}
                CREATE_MEDITATION: FUNCTION_CALL:CREATE_MEDITATION:{"focus":"sleep","duration":10}
                
                Use when user wants to remember something or create meditation.
            """.trimIndent()
        }
        
        private fun buildTestFunctionCallingPrompt(): String {
            return """
                You are a helpful AI wellness assistant with access to special functions.
                
                ${buildFunctionCallingPrompt()}
                
                Respond naturally and use functions when the user explicitly requests them.
            """.trimIndent()
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
    }
}