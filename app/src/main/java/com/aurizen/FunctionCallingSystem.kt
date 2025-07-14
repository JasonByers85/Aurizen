package com.aurizen

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.aurizen.prompts.PromptBuilder
import com.aurizen.prompts.PromptType
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
        val actionButton: ActionButton? = null
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
        START_MEDITATION
    }

    /**
     * Data class for stored user memories
     */
    data class UserMemory(
        val id: String,
        val memory: String,
        val timestamp: Long
    )

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
        val functionCallPattern = Regex("FUNCTION_CALL:([A-Z_]+):(\\{[^}]*\\})")
        val matches = functionCallPattern.findAll(response)
        
        if (matches.any()) {
            for (match in matches) {
                val functionName = match.groupValues[1]
                val argumentsJson = match.groupValues[2]
                
                try {
                    @Suppress("UNCHECKED_CAST")
                    val arguments = gson.fromJson(argumentsJson, Map::class.java) as Map<String, Any>
                    
                    val result = when (functionName) {
                        "CREATE_MEDITATION" -> handleCreateMeditation(arguments)
                        "STORE_MEMORY" -> handleStoreMemory(arguments)
                        else -> continue
                    }
                    
                    if (result.handled) {
                        // Clean the response by removing function calls
                        val cleanResponse = response.replace(functionCallPattern, "").trim()
                        return FunctionCallResult(
                            handled = true,
                            response = if (cleanResponse.isNotEmpty()) cleanResponse else result.response,
                            actionButton = result.actionButton
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing function call arguments: $argumentsJson", e)
                }
            }
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
        
        val response = """I've created a personalized meditation for you!

✨ **Meditation Created Successfully!**

**Focus:** $focus
**Duration:** $duration minutes
**Saved to your library**

Would you like to start it now?"""
        
        val actionButton = ActionButton(
            text = "🧘‍♂️ Start Meditation",
            action = ButtonAction.START_MEDITATION,
            parameter = "saved_$meditationId"
        )
        
        return FunctionCallResult(
            handled = true,
            response = response,
            actionButton = actionButton
        )
    }
    
    /**
     * Handles memory storage function call
     */
    private fun handleStoreMemory(arguments: Map<String, Any>): FunctionCallResult {
        val memoryContent = arguments["memory"] as? String ?: ""
        
        if (memoryContent.isNotEmpty()) {
            storeUserMemoryInternal(memoryContent)
            
            val response = "I'll remember: $memoryContent"
            
            return FunctionCallResult(
                handled = true,
                response = response
            )
        }
        
        return FunctionCallResult(
            handled = false,
            response = ""
        )
    }
    
    /**
     * Creates a meditation template and saves it
     */
    private fun createMeditationTemplate(focus: String, mood: String, duration: Int, originalInput: String): String {
        val meditationId = "fc_${UUID.randomUUID().toString().take(8)}"
        val timestamp = System.currentTimeMillis()
        
        val savedMeditation = SavedMeditation(
            id = meditationId,
            name = "Custom: ${focus.replaceFirstChar { it.uppercase() }}",
            description = "AI-created meditation: \"$originalInput\"",
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
        val memories = getUserMemories().toMutableList()
        
        // Check for duplicates
        val isDuplicate = memories.any { it.memory.equals(memoryContent, ignoreCase = true) }
        if (isDuplicate) {
            return
        }
        
        val newMemory = UserMemory(
            id = UUID.randomUUID().toString(),
            memory = memoryContent,
            timestamp = System.currentTimeMillis()
        )
        
        memories.add(newMemory)
        
        // Keep only last 50 memories
        val memoriesToKeep = memories.takeLast(50)
        
        val json = gson.toJson(memoriesToKeep)
        prefs.edit().putString(KEY_USER_MEMORIES, json).apply()
    }
    
    
    /**
     * Gets all user memories
     */
    fun getUserMemories(): List<UserMemory> {
        val json = prefs.getString(KEY_USER_MEMORIES, null) ?: return emptyList()
        val type = object : TypeToken<List<UserMemory>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing user memories", e)
            emptyList()
        }
    }
    
    /**
     * Gets memories context for system prompts
     */
    fun getMemoriesContext(): String {
        val memories = getUserMemories()
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
        val memories = getUserMemories().toMutableList()
        val updatedMemories = memories.filter { it.id != memoryId }
        
        val json = gson.toJson(updatedMemories)
        prefs.edit().putString(KEY_USER_MEMORIES, json).apply()
        
        Log.d(TAG, "Deleted memory with ID: $memoryId")
    }
    
    /**
     * Clears all user memories
     */
    fun clearUserMemories() {
        prefs.edit().remove(KEY_USER_MEMORIES).apply()
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
