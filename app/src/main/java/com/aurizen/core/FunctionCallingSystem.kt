package com.aurizen.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.aurizen.prompts.PromptBuilder
import com.aurizen.prompts.PromptType
import com.aurizen.data.SavedMeditation
import com.aurizen.data.SavedMeditationType
import com.aurizen.data.SavedMeditationConfig
import com.aurizen.data.MemoryStorage
import com.aurizen.data.UserMemory
import com.aurizen.data.PersonalGoalsStorage
import com.aurizen.data.PersonalGoal
import com.aurizen.data.GoalCategory
import com.aurizen.data.GoalType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import java.util.UUID

/**
 * Function calling system for AuriZen
 * Handles AI-triggered function calls with JSON filtering for streaming responses
 */
class FunctionCallingSystem private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("function_calling", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val TAG = "FunctionCallingSystem"
    private val memoryStorage = MemoryStorage.getInstance(context)
    private val goalsStorage = PersonalGoalsStorage.getInstance(context)

    companion object {
        private const val KEY_USER_MEMORIES = "user_memories"

        @Volatile
        private var instance: FunctionCallingSystem? = null

        fun getInstance(context: Context): FunctionCallingSystem {
            return instance ?: synchronized(this) {
                instance ?: FunctionCallingSystem(context).also { instance = it }
            }
        }
    }

    /**
     * Data class for function call results
     */
    data class FunctionCallResult(
        val handled: Boolean,
        val response: String,
        val actionButton: ActionButton? = null,
        val resultType: ResultType? = null,
        val resultDetails: Any? = null
    )
    
    /**
     * Types of function call results
     */
    enum class ResultType {
        MEDITATION_CREATED,
        GOAL_CREATED,
        MEMORY_STORED
    }
    
    /**
     * Data class for meditation creation details
     */
    data class MeditationCreationDetails(
        val name: String,
        val focus: String,
        val duration: Int,
        val description: String
    )
    
    /**
     * Data class for goal creation details
     */
    data class GoalCreationDetails(
        val title: String,
        val category: String,
        val categoryEmoji: String,
        val goalType: String,
        val targetDate: String,
        val notes: String
    )
    
    /**
     * Data class for memory storage details
     */
    data class MemoryStorageDetails(
        val memoryContent: String
    )

    /**
     * Data class for action buttons
     */
    data class ActionButton(
        val text: String,
        val action: ButtonAction,
        val parameter: String? = null
    )

    /**
     * Available button actions
     */
    enum class ButtonAction {
        NAVIGATE_TO_MEDITATION,
        NAVIGATE_TO_SAVED_MEDITATIONS,
        START_MEDITATION,
        VIEW_GOALS,
        VIEW_MEMORIES
    }


    /**
     * Data class for streaming response filtering
     */
    data class StreamingResponse(
        val visibleText: String,
        val hiddenFunctionCalls: List<String> = emptyList(),
        val isComplete: Boolean = false,
        val functionLabel: String? = null
    )

    /**
     * Gets function calling prompt - short and simple
     */
    fun getFunctionCallingPrompt(): String {
        return PromptBuilder.build(PromptType.FUNCTION_CALLING)
    }

    /**
     * Gets the system instruction for function calling context
     */
    fun getSystemInstruction(): String {
        return getFunctionCallingPrompt()
    }

    /**
     * Filters streaming text to hide function calls from user
     */
    fun filterStreamingResponse(partialText: String): StreamingResponse {
        val functionCallPattern = Regex("FUNCTION_CALL:([A-Z_]+):(\\{[^}]*\\})")
        val hiddenFunctionCalls = mutableListOf<String>()
        var functionLabel: String? = null

        // Extract function calls and create labels
        val matches = functionCallPattern.findAll(partialText)
        for (match in matches) {
            hiddenFunctionCalls.add(match.value)

            // Generate user-friendly label for the function
            val functionName = match.groupValues[1]
            functionLabel = when (functionName) {
                "CREATE_MEDITATION" -> "🧘‍♂️ Creating a meditation for you..."
                "STORE_MEMORY" -> "💭 Storing this information..."
                else -> "⚡ Processing your request..."
            }
        }

        // Remove function calls but preserve proper spacing
        var visibleText = partialText
        for (match in matches) {
            val before = visibleText.substring(0, match.range.first)
            val after = visibleText.substring(match.range.last + 1)

            // Preserve spacing around the removed function call
            val needsSpaceBefore = before.isNotEmpty() && !before.endsWith(" ") && !before.endsWith("\n")
            val needsSpaceAfter = after.isNotEmpty() && !after.startsWith(" ") && !after.startsWith("\n")

            val replacement = when {
                needsSpaceBefore && needsSpaceAfter -> " "
                else -> ""
            }

            visibleText = before + replacement + after
        }

        // Clean up extra whitespace
        visibleText = visibleText.replace(Regex("\\s+"), " ").trim()

        return StreamingResponse(
            visibleText = visibleText,
            hiddenFunctionCalls = hiddenFunctionCalls,
            isComplete = false,
            functionLabel = functionLabel
        )
    }

    /**
     * Handles function calls from custom implementation
     */
    fun handleFunctionCall(functionName: String, arguments: Map<String, Any>): String {
        Log.d(TAG, "Handling function call: $functionName with args: $arguments")

        return when (functionName) {
            "create_meditation" -> {
                val result = handleCreateMeditation(arguments)
                result.response
            }
            "store_memory" -> {
                val result = handleStoreMemory(arguments)
                result.response
            }
            else -> {
                Log.w(TAG, "Unknown function: $functionName")
                "Function not supported"
            }
        }
    }
    
    /**
     * Processes AI response for function calls only
     */
    fun processUserInputAndAIResponse(userInput: String, aiResponse: String): FunctionCallResult {
        return processAIResponse(aiResponse)
    }
    
    /**
     * Processes AI response to detect and handle function calls (meditation only now)
     */
    fun processAIResponse(response: String): FunctionCallResult {
        Log.d(TAG, "🔍 Processing AI response for function calls:")
        Log.d(TAG, "📄 Full response: '$response'")
        Log.d(TAG, "📏 Response length: ${response.length}")
        
        val functionCallPattern = Regex("FUNCTION_CALL:([A-Z_]+):(\\{[^}]*\\})")
        Log.d(TAG, "🔍 Using regex pattern: ${functionCallPattern.pattern}")
        
        val matches = functionCallPattern.findAll(response)
        val matchList = matches.toList()
        Log.d(TAG, "🔍 Found ${matchList.size} regex matches")
        
        if (matchList.isNotEmpty()) {
            Log.d(TAG, "✅ Found function calls in response")
            matchList.forEachIndexed { index, match ->
                Log.d(TAG, "📋 Match $index: '${match.value}'")
                Log.d(TAG, "📋 Function: '${match.groupValues[1]}'")
                Log.d(TAG, "📋 Args: '${match.groupValues[2]}'")
            }
            for (match in matches) {
                val functionName = match.groupValues[1]
                val argumentsJson = match.groupValues[2]
                
                Log.d(TAG, "Processing function: $functionName with args: $argumentsJson")
                
                try {
                    @Suppress("UNCHECKED_CAST")
                    val arguments = gson.fromJson(argumentsJson, Map::class.java) as Map<String, Any>
                    
                    val result = when (functionName) {
                        "CREATE_MEDITATION" -> handleCreateMeditation(arguments)
                        "STORE_MEMORY" -> handleStoreMemory(arguments)
                        "CREATE_GOAL" -> handleCreateGoal(arguments)
                        else -> {
                            Log.w(TAG, "Unknown function name: $functionName")
                            continue
                        }
                    }
                    
                    if (result.handled) {
                        // Clean the response by removing function calls
                        val cleanResponse = response.replace(functionCallPattern, "").trim()
                        Log.d(TAG, "Function handled successfully. Clean response: '$cleanResponse'")
                        return FunctionCallResult(
                            handled = true,
                            response = if (cleanResponse.isNotEmpty()) cleanResponse else result.response,
                            actionButton = result.actionButton,
                            resultType = result.resultType,
                            resultDetails = result.resultDetails
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing function call arguments: $argumentsJson", e)
                }
            }
        } else {
            Log.d(TAG, "No function calls found in response")
        }
        
        return FunctionCallResult(false, response)
    }
    
    /**
     * Handles meditation creation function call
     */
    private fun handleCreateMeditation(arguments: Map<String, Any>): FunctionCallResult {
        val focus = arguments["focus"] as? String ?: "mindfulness"
        val mood = arguments["mood"] as? String ?: "balanced"
        val duration = (arguments["duration"] as? Double)?.toInt() ?: 10
        val userRequest = arguments["user_request"] as? String ?: ""
        
        Log.d(TAG, "Creating meditation - Focus: $focus, Mood: $mood, Duration: $duration")
        
        val meditationId = createMeditationTemplate(focus, mood, duration, userRequest)
        
        // Get the created meditation details
        val nameAndDescription = Pair(
            generateFallbackName(focus, userRequest),
            generateFallbackDescription(focus, userRequest)
        )
        
        val meditationDetails = MeditationCreationDetails(
            name = nameAndDescription.first,
            focus = focus,
            duration = duration,
            description = nameAndDescription.second
        )
        
        val actionButton = ActionButton(
            text = "🧘‍♂️ Start Now",
            action = ButtonAction.START_MEDITATION,
            parameter = meditationId
        )
        
        return FunctionCallResult(
            handled = true,
            response = "Meditation created successfully!",
            actionButton = actionButton,
            resultType = ResultType.MEDITATION_CREATED,
            resultDetails = meditationDetails
        )
    }
    
    /**
     * Handles memory storage function call
     */
    private fun handleStoreMemory(arguments: Map<String, Any>): FunctionCallResult {
        val memoryContent = arguments["memory"] as? String ?: ""
        
        if (memoryContent.isNotEmpty()) {
            storeUserMemoryInternal(memoryContent)
            
            val memoryDetails = MemoryStorageDetails(
                memoryContent = memoryContent
            )
            
            return FunctionCallResult(
                handled = true,
                response = "Memory stored successfully!",
                actionButton = ActionButton(
                    text = "📝 View Memories",
                    action = ButtonAction.VIEW_MEMORIES
                ),
                resultType = ResultType.MEMORY_STORED,
                resultDetails = memoryDetails
            )
        }
        
        return FunctionCallResult(
            handled = false,
            response = ""
        )
    }
    
    /**
     * Handles goal creation function call
     */
    private fun handleCreateGoal(arguments: Map<String, Any>): FunctionCallResult {
        val title = arguments["title"] as? String ?: "New Goal"
        val categoryString = arguments["category"] as? String ?: "OTHER"
        val goalTypeString = arguments["goal_type"] as? String ?: "ONE_TIME"
        val targetDateString = arguments["target_date"] as? String ?: ""
        val notes = arguments["notes"] as? String ?: ""
        
        Log.d(TAG, "Creating goal - Title: $title, Category: $categoryString, Type: $goalTypeString, Target: $targetDateString")
        
        // Parse category
        val category = try {
            GoalCategory.valueOf(categoryString)
        } catch (e: Exception) {
            GoalCategory.OTHER
        }
        
        // Parse goal type
        val goalType = try {
            GoalType.valueOf(goalTypeString)
        } catch (e: Exception) {
            GoalType.ONE_TIME
        }
        
        // Parse target date (format: YYYY-MM-DD)
        val targetDate = try {
            if (targetDateString.isBlank()) {
                Log.d(TAG, "No target date provided, using default based on goal type")
                if (goalType == GoalType.DAILY) {
                    // For daily goals, set target to 30 days from now as initial period
                    System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                } else {
                    // For one-time goals, default to 3 months from now
                    System.currentTimeMillis() + (90L * 24 * 60 * 60 * 1000)
                }
            } else {
                val parts = targetDateString.split("-")
                if (parts.size == 3) {
                    val year = parts[0].toInt()
                    val month = parts[1].toInt() - 1 // Calendar months are 0-based
                    val day = parts[2].toInt()
                    val calendar = java.util.Calendar.getInstance()
                    calendar.set(year, month, day, 23, 59, 59) // Set to end of day
                    calendar.set(java.util.Calendar.MILLISECOND, 999)
                    Log.d(TAG, "Parsed target date: $targetDateString -> ${calendar.time}")
                    calendar.timeInMillis
                } else {
                    Log.w(TAG, "Invalid date format: $targetDateString, using default")
                    if (goalType == GoalType.DAILY) {
                        System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                    } else {
                        System.currentTimeMillis() + (90L * 24 * 60 * 60 * 1000)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing date: $targetDateString", e)
            if (goalType == GoalType.DAILY) {
                System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
            } else {
                System.currentTimeMillis() + (90L * 24 * 60 * 60 * 1000)
            }
        }
        
        val goal = PersonalGoal(
            title = title,
            category = category,
            goalType = goalType,
            targetDate = targetDate,
            notes = notes
        )
        
        goalsStorage.saveGoal(goal)
        
        val formattedTargetDate = if (goalType == GoalType.DAILY) {
            "Daily goal (Track until ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(targetDate))})"
        } else {
            java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(targetDate))
        }
        
        val goalDetails = GoalCreationDetails(
            title = title,
            category = category.displayName,
            categoryEmoji = category.emoji,
            goalType = goalType.displayName,
            targetDate = formattedTargetDate,
            notes = notes
        )
        
        val actionButton = ActionButton(
            text = "📋 View My Goals",
            action = ButtonAction.VIEW_GOALS
        )
        
        val responseText = if (goalType == GoalType.DAILY) {
            "Daily goal created successfully! You can track your progress each day."
        } else {
            "Goal created successfully!"
        }
        
        return FunctionCallResult(
            handled = true,
            response = responseText,
            actionButton = actionButton,
            resultType = ResultType.GOAL_CREATED,
            resultDetails = goalDetails
        )
    }
    
    /**
     * Creates a meditation template and saves it
     */
    private fun createMeditationTemplate(focus: String, mood: String, duration: Int, originalInput: String): String {
        val meditationId = "saved_fc_${UUID.randomUUID().toString().take(8)}"
        val timestamp = System.currentTimeMillis()
        
        // Generate proper name and description using intelligent fallbacks
        val nameAndDescription = Pair(
            generateFallbackName(focus, originalInput),
            generateFallbackDescription(focus, originalInput)
        )
        
        val savedMeditation = SavedMeditation(
            id = meditationId,
            name = nameAndDescription.first,
            description = nameAndDescription.second,
            totalDuration = duration,
            totalSteps = 3,
            createdAt = timestamp,
            lastUsedAt = timestamp,
            saveType = SavedMeditationType.CONFIG_TEMPLATE,
            savedSteps = null,
            config = SavedMeditationConfig(
                focus = focus,
                mood = mood,
                experience = "Intermediate",
                totalDuration = duration,
                totalSteps = 3
            )
        )
        
        saveMeditationTemplate(savedMeditation)
        
        Log.d(TAG, "Created meditation template: $meditationId for focus: $focus")
        return meditationId
    }
    
    
    /**
     * Generates intelligent fallback name based on focus and input
     */
    private fun generateFallbackName(focus: String, originalInput: String): String {
        val input = originalInput.lowercase()
        val focusLower = focus.lowercase()
        
        return when {
            // Pain-related
            focusLower.contains("pain") || input.contains("pain") -> "Pain Relief Meditation"
            focusLower.contains("headache") || input.contains("headache") -> "Headache Relief Session"
            
            // Stress & Anxiety
            focusLower.contains("stress") || input.contains("stress") -> "Stress Relief Session"
            focusLower.contains("anxiety") || input.contains("anxiety") || input.contains("anxious") -> "Calming Meditation"
            focusLower.contains("worry") || input.contains("worry") -> "Worry Relief Practice"
            
            // Sleep & Rest
            focusLower.contains("sleep") || input.contains("sleep") -> "Sleep Preparation"
            focusLower.contains("insomnia") || input.contains("insomnia") -> "Restful Sleep Session"
            focusLower.contains("tired") || input.contains("tired") -> "Energy Restoration"
            
            // Focus & Concentration
            focusLower.contains("focus") || input.contains("focus") -> "Focus Enhancement"
            focusLower.contains("concentration") || input.contains("concentration") -> "Concentration Boost"
            focusLower.contains("clarity") || input.contains("clarity") -> "Mental Clarity Session"
            
            // Emotional & Mood
            focusLower.contains("sad") || input.contains("sad") -> "Mood Lifting Practice"
            focusLower.contains("angry") || input.contains("angry") -> "Anger Release Meditation"
            focusLower.contains("depression") || input.contains("depression") -> "Uplifting Meditation"
            
            // Physical & Health
            focusLower.contains("breathing") || input.contains("breathing") -> "Breathing Wellness"
            focusLower.contains("healing") || input.contains("healing") -> "Healing Meditation"
            
            // Work & Life
            focusLower.contains("work") || input.contains("work") -> "Work Stress Relief"
            focusLower.contains("confidence") || input.contains("confidence") -> "Confidence Building"
            
            // Default based on focus area
            else -> {
                val cleanFocus = focus.replace("_", " ").split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }
                "$cleanFocus Meditation"
            }
        }
    }
    
    /**
     * Generates intelligent fallback description based on focus and input
     */
    private fun generateFallbackDescription(focus: String, originalInput: String): String {
        val input = originalInput.lowercase()
        val focusLower = focus.lowercase()
        
        return when {
            // Pain-related
            focusLower.contains("pain") || input.contains("pain") -> 
                "A soothing meditation designed to help manage pain and promote healing through mindful relaxation techniques."
            focusLower.contains("headache") || input.contains("headache") -> 
                "A gentle practice to ease headache tension and restore mental clarity through calming breathwork."
            
            // Stress & Anxiety
            focusLower.contains("stress") || input.contains("stress") -> 
                "A calming session to release stress and tension, bringing peace and balance to your mind and body."
            focusLower.contains("anxiety") || input.contains("anxiety") || input.contains("anxious") -> 
                "A grounding meditation to ease anxiety and create a sense of calm, safety, and inner peace."
            focusLower.contains("worry") || input.contains("worry") -> 
                "A peaceful practice to quiet worried thoughts and cultivate a sense of trust and serenity."
            
            // Sleep & Rest
            focusLower.contains("sleep") || input.contains("sleep") -> 
                "A restful meditation to prepare your mind and body for deep, rejuvenating sleep."
            focusLower.contains("insomnia") || input.contains("insomnia") -> 
                "A gentle practice designed to ease insomnia and guide you toward peaceful, restorative rest."
            focusLower.contains("tired") || input.contains("tired") -> 
                "An energizing yet calming session to restore vitality and refresh your mind and spirit."
            
            // Focus & Concentration
            focusLower.contains("focus") || input.contains("focus") -> 
                "A centering meditation to enhance focus and concentration, bringing clarity to your thoughts."
            focusLower.contains("concentration") || input.contains("concentration") -> 
                "A mindful practice to strengthen your ability to concentrate and maintain mental clarity."
            focusLower.contains("clarity") || input.contains("clarity") -> 
                "A clearing meditation to dissolve mental fog and bring sharp, focused awareness."
            
            // Emotional & Mood
            focusLower.contains("sad") || input.contains("sad") -> 
                "A compassionate practice to gently lift your spirits and nurture emotional healing."
            focusLower.contains("angry") || input.contains("angry") -> 
                "A calming meditation to release anger and cultivate inner peace and emotional balance."
            focusLower.contains("depression") || input.contains("depression") -> 
                "An uplifting practice to bring light to dark moments and foster hope and resilience."
            
            // Physical & Health
            focusLower.contains("breathing") || input.contains("breathing") -> 
                "A breath-focused meditation to enhance respiratory wellness and promote deep relaxation."
            focusLower.contains("healing") || input.contains("healing") -> 
                "A restorative practice to support your body's natural healing processes and promote well-being."
            
            // Work & Life
            focusLower.contains("work") || input.contains("work") -> 
                "A refreshing meditation to release work-related stress and restore work-life balance."
            focusLower.contains("confidence") || input.contains("confidence") -> 
                "An empowering practice to build inner confidence and cultivate self-trust and strength."
            
            // Default
            else -> {
                val cleanFocus = focus.replace("_", " ")
                "A personalized meditation session designed to help with $cleanFocus. Created specifically for your needs to promote relaxation and well-being."
            }
        }
    }
    
    /**
     * Saves meditation template to storage
     */
    private fun saveMeditationTemplate(meditation: SavedMeditation) {
        val prefs = context.getSharedPreferences("saved_meditations", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Save basic info
        editor.putString("${meditation.id}_name", meditation.name)
        editor.putString("${meditation.id}_description", meditation.description)
        editor.putInt("${meditation.id}_duration", meditation.totalDuration)
        editor.putInt("${meditation.id}_steps", meditation.totalSteps)
        editor.putLong("${meditation.id}_created", meditation.createdAt)
        editor.putLong("${meditation.id}_used", meditation.lastUsedAt)
        editor.putString("${meditation.id}_type", meditation.saveType.name)
        
        // Save config for template
        meditation.config?.let { config ->
            editor.putString("${meditation.id}_config_focus", config.focus)
            editor.putString("${meditation.id}_config_mood", config.mood)
            editor.putString("${meditation.id}_config_experience", config.experience)
            editor.putInt("${meditation.id}_config_duration", config.totalDuration)
            editor.putInt("${meditation.id}_config_steps", config.totalSteps)
        }
        
        // Add to saved list
        val savedIds = prefs.getStringSet("saved_meditation_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        savedIds.add(meditation.id)
        editor.putStringSet("saved_meditation_ids", savedIds)
        
        editor.apply()
    }
    
    /**
     * Stores user memory (public method for external use)
     */
    fun storeUserMemory(memoryContent: String) {
        storeUserMemoryInternal(memoryContent)
    }
    
    /**
     * Stores user memory (internal method)
     */
    private fun storeUserMemoryInternal(memoryContent: String) {
        memoryStorage.storeMemory(memoryContent)
    }
    
    
    /**
     * Gets all user memories
     */
    fun getUserMemories(): List<UserMemory> {
        return memoryStorage.getAllMemories()
    }
    
    /**
     * Gets memories context for system prompts
     */
    fun getMemoriesContext(): String {
        val memories = memoryStorage.getAllMemories()
        if (memories.isEmpty()) {
            return ""
        }
        
        val recentMemories = memories.takeLast(10)
        val memoriesText = recentMemories.joinToString("; ") { it.memory }
        
        return "\nImportant user context to remember: $memoriesText"
    }
    
    /**
     * Deletes a specific memory by ID
     */
    fun deleteMemory(memoryId: String) {
        memoryStorage.deleteMemory(memoryId)
        Log.d(TAG, "Deleted memory with ID: $memoryId")
    }
    
    /**
     * Clears all user memories
     */
    fun clearUserMemories() {
        memoryStorage.clearAllMemories()
        Log.d(TAG, "Cleared all user memories")
    }
    
    /**
     * Clears all saved meditations
     */
    fun clearAllSavedMeditations() {
        val prefs = context.getSharedPreferences("saved_meditations", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Get all saved meditation IDs and clear all related data
        val savedIds = prefs.getStringSet("saved_meditation_ids", emptySet()) ?: emptySet()
        savedIds.forEach { id ->
            val allKeys = prefs.all.keys.filter { it.startsWith("${id}_") }
            allKeys.forEach { key ->
                editor.remove(key)
            }
        }
        
        // Clear the saved IDs list
        editor.remove("saved_meditation_ids")
        editor.apply()
        
        Log.d(TAG, "Cleared all saved meditations")
    }
}
