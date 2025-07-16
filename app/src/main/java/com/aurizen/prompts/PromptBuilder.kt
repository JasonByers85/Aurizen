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
    TEST_FUNCTION_CALLING,
    WELLNESS_GUIDANCE,
    GOAL_MOTIVATION
}

data class FunctionInjection(
    val keywords: List<String>,
    val instruction: String
)

data class PromptContext(
    val userMemories: List<String> = emptyList(),
    val recentMoods: List<MoodEntry> = emptyList(),
    val personalGoals: List<PersonalGoal> = emptyList(),
    val recentTopics: List<String> = emptyList(),
    val currentDate: Date = Date(),
    val additionalContext: Map<String, Any> = emptyMap() // Can include "userMessage" for function detection
)

class PromptBuilder {
    
    companion object {
        private const val APP_NAME = "AuriZen"
        private const val MAX_RESPONSE_WORDS = 400
        
        // Token-efficient feature list (was 6 lines, now 1 line)
        private const val FEATURES = "Features: meditations(AI+preset), breathing, mood/dream tracking, goals, voice chat"
        
        // Keyword-based guidelines (was 5 bullet points, now 1 sentence)
        private const val GUIDELINES = "Be: helpful, concise(1-2para), practical, supportive, positive. No diagnoses. Suggest features naturally."
        
        // Function injection definitions
        private val MEMORY_FUNCTION = FunctionInjection(
            keywords = listOf(
                "always remember", "please remember", "you should always remember",
                "always keep in mind", "always note that"
            ),
            instruction = "\nWhen user wants you to remember something, use: FUNCTION_CALL:STORE_MEMORY:{\"memory\":\"what to remember\"}\nStore the actual information the user wants remembered, not example text.\n"
        )
        
        private val MEDITATION_FUNCTION = FunctionInjection(
            keywords = listOf(
                "create meditation", "make meditation", "generate meditation",
                "meditation for", "meditate on", "guided meditation",
                "custom meditation", "personalized meditation"
            ),
            instruction = "\nWhen user requests meditation creation, use: FUNCTION_CALL:CREATE_MEDITATION:{\"focus\":\"meditation focus area\",\"mood\":\"target mood\",\"duration\":10,\"user_request\":\"original request\"}\nSet focus to what they want to meditate on, mood to desired outcome, duration in minutes.\n"
        )
        
        private fun getGoalFunction(): FunctionInjection {
            return FunctionInjection(
                keywords = listOf(
                    "create goal", "set goal", "add goal", "make goal",
                    "goal to", "want to achieve", "help me achieve",
                    "track my progress", "personal goal", "set target",
                    "daily goal", "daily habit", "every day", "daily routine",
                    "exercise daily", "meditate daily", "read daily",
                    "drink water daily", "daily practice"
                ),
                instruction = "\nWhen user wants to create a goal, use: FUNCTION_CALL:CREATE_GOAL:{\"title\":\"goal title\",\"category\":\"FITNESS\",\"goal_type\":\"DAILY\",\"target_date\":\"\",\"notes\":\"context\"}\nCategories: HEALTH,FITNESS,MENTAL_WELLNESS,WEIGHT,LEARNING,CAREER,OTHER\nTypes: ONE_TIME (single goals) or DAILY (daily habits)\nUse DAILY for habits like 'exercise daily', 'meditate daily'. Use ONE_TIME for single achievements.\n"
            )
        }
        
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
                PromptType.WELLNESS_GUIDANCE -> buildWellnessGuidancePrompt(context)
                PromptType.GOAL_MOTIVATION -> buildGoalMotivationPrompt(context)
            }
        }
        
        private fun buildQuickChatPrompt(context: PromptContext): String {
            val userMessage = context.additionalContext["userMessage"] as? String ?: ""
            val functions = detectAndInjectFunctions(userMessage)
            
            // If we detected functions, use optimized function-only prompt
            if (functions.isNotEmpty()) {
                return buildOptimizedFunctionPrompt(context, functions)
            }
            
            // Otherwise use full context prompt
            val ctx = formatCompactContext(context)
            return "AI wellness companion. $GUIDELINES $FEATURES\n${if (ctx.isNotEmpty()) "Context: $ctx" else ""}"
        }
        
        private fun buildTalkPrompt(context: PromptContext): String {
            val userMessage = context.additionalContext["userMessage"] as? String ?: ""
            val functions = detectAndInjectFunctions(userMessage)
            
            // If we detected functions, use optimized function-only prompt
            if (functions.isNotEmpty()) {
                val optimizedPrompt = buildOptimizedFunctionPrompt(context, functions)
                println("🔧 TALK: Function detected, using optimized prompt (${optimizedPrompt.length} chars)")
                return optimizedPrompt
            }
            
            // Otherwise use full context prompt
            val mem = context.userMemories.take(3).joinToString("; ")
            val fullPrompt = "$APP_NAME AI. Conversational, friendly, brief chat. $FEATURES\n${if (mem.isNotEmpty()) "Context: $mem\n" else ""}Be supportive:"
            println("🔧 TALK: No functions, using full prompt (${fullPrompt.length} chars)")
            return fullPrompt
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
            // Legacy - now using smart injection
            return "${MEMORY_FUNCTION.instruction}${MEDITATION_FUNCTION.instruction}Use when requested."
        }
        
        private fun buildTestFunctionCallingPrompt(): String {
            return "Wellness AI w/ functions. ${buildFunctionCallingPrompt()} Use when explicitly requested."
        }
        
        private fun buildWellnessGuidancePrompt(context: PromptContext): String {
            val wellnessTopic = context.additionalContext["wellnessTopic"] as? String ?: "general wellness"
            
            // Only include minimal, relevant context
            val recentMoods = context.recentMoods.take(5).joinToString(", ") { it.mood }
            val userMemories = context.userMemories.take(3).joinToString("; ")
            
            val moodContext = if (recentMoods.isNotEmpty()) "\nRecent moods: $recentMoods" else ""
            val memoryContext = if (userMemories.isNotEmpty()) "\nUser context: $userMemories" else ""
            
            return """You are a compassionate wellness expert providing evidence-based guidance for: $wellnessTopic

Provide genuine, therapeutic insights and practical strategies. Be specific, actionable, and empathetic. Draw from psychology, mindfulness, cognitive behavioral therapy, and holistic wellness approaches.$moodContext$memoryContext

IMPORTANT:
- NO app recommendations or digital tools
- NO function calls or technical features  
- Focus purely on human wisdom and practical techniques
- 2-3 focused paragraphs with specific, actionable steps
- Address the person directly with warmth and understanding
- Include both immediate relief techniques and longer-term strategies"""
        }
        
        private fun buildGoalMotivationPrompt(context: PromptContext): String {
            val goal = context.additionalContext["goal"] as? PersonalGoal
            val userMemories = context.userMemories.take(3).joinToString("; ")
            
            if (goal == null) {
                return """Provide brief, encouraging motivation for personal growth. Keep it realistic and supportive in 1-2 sentences."""
            }
            
            val goalType = goal.getEffectiveGoalType()
            val category = goal.category.displayName
            val progress = goal.progress
            val dailyProgress = goal.getEffectiveDailyProgress()
            val isCompleted = goal.isCompleted
            
            val progressContext = when {
                isCompleted -> "completed"
                goalType == com.aurizen.data.GoalType.DAILY -> {
                    when {
                        dailyProgress.currentStreak >= 7 -> "${dailyProgress.currentStreak}-day streak"
                        dailyProgress.currentStreak >= 3 -> "${dailyProgress.currentStreak}-day streak"
                        dailyProgress.totalDaysCompleted > 0 -> "${dailyProgress.totalDaysCompleted} days completed"
                        else -> "just starting"
                    }
                } 
                else -> {
                    when {
                        progress >= 0.75f -> "75% complete"
                        progress >= 0.5f -> "50% complete"
                        progress >= 0.25f -> "25% complete"
                        else -> "just starting"
                    }
                }
            }
            
            val memoryContext = if (userMemories.isNotEmpty()) "\nUser context: $userMemories" else ""
            
            return """You are a supportive wellness coach. Give realistic, encouraging motivation for: "${goal.title}" ($category, $progressContext).$memoryContext

Requirements:
- 1-2 sentences maximum
- Acknowledge their current progress
- Be genuine and supportive, not overly enthusiastic
- Focus on the specific goal and category
- Use their personal context if relevant
- Include 1 appropriate emoji"""
        }
        
        /**
         * Optimized prompt for when we know a function call is needed
         * Much smaller and faster than full context prompts
         */
        private fun buildOptimizedFunctionPrompt(context: PromptContext, functions: String): String {
            // Minimal context for function calls
            val memories = context.userMemories.take(2).joinToString("; ")
            val memoryContext = if (memories.isNotEmpty()) "\nUser context: $memories" else ""
            
            return "$APP_NAME AI. Execute function calls and respond briefly.${functions}${memoryContext}\n\nIMPORTANT: Use the function call format AND respond with only a short confirmation (1-2 sentences max). No code blocks, no technical details."
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
        
        // Smart function injection based on phrase detection
        private fun detectAndInjectFunctions(userMessage: String): String {
            val lowerMessage = userMessage.lowercase().trim()
            val functions = mutableListOf<String>()
            val detectedTypes = mutableListOf<String>()
            
            // Check for memory-related phrases (must contain "always remember")
            if (MEMORY_FUNCTION.keywords.any { phrase -> 
                lowerMessage.contains(phrase.lowercase())
            }) {
                functions.add(MEMORY_FUNCTION.instruction)
                detectedTypes.add("MEMORY")
            }
            
            // Check for meditation-related keywords
            if (MEDITATION_FUNCTION.keywords.any { keyword -> 
                lowerMessage.contains(keyword.lowercase())
            }) {
                functions.add(MEDITATION_FUNCTION.instruction)
                detectedTypes.add("MEDITATION")
            }
            
            // Check for goal-related keywords
            val goalFunction = getGoalFunction()
            if (goalFunction.keywords.any { keyword -> 
                lowerMessage.contains(keyword.lowercase())
            }) {
                functions.add(goalFunction.instruction)
                detectedTypes.add("GOAL")
            }
            
            if (detectedTypes.isNotEmpty()) {
                println("🔍 FUNCTION DETECTION: '$userMessage' -> ${detectedTypes.joinToString(", ")}")
            } else {
                println("🔍 FUNCTION DETECTION: '$userMessage' -> NONE")
            }
            
            return if (functions.isNotEmpty()) {
                functions.joinToString("")
            } else {
                ""
            }
        }
        
        // Debug function to test keyword detection
        fun testFunctionDetection(userMessage: String): String {
            val detected = detectAndInjectFunctions(userMessage)
            return if (detected.isNotEmpty()) {
                "DETECTED: $detected"
            } else {
                "NO FUNCTIONS DETECTED"
            }
        }
    }
}