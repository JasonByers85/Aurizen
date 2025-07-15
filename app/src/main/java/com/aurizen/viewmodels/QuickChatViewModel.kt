package com.aurizen.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.aurizen.prompts.PromptBuilder
import com.aurizen.prompts.PromptContext
import com.aurizen.prompts.PromptType
import com.aurizen.core.InferenceModel
import com.aurizen.data.UserProfile
import com.aurizen.data.PersonalGoalsStorage
import com.aurizen.data.MoodStorage
import com.aurizen.data.MemoryStorage
import com.aurizen.core.FunctionCallingSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class QuickChatViewModel(
    private val inferenceModel: InferenceModel,
    private val context: Context
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _response = MutableStateFlow("")
    val response: StateFlow<String> = _response.asStateFlow()

    private val _isInputEnabled = MutableStateFlow(true)
    val isInputEnabled: StateFlow<Boolean> = _isInputEnabled.asStateFlow()
    
    private val _functionCallResult = MutableStateFlow<FunctionCallingSystem.FunctionCallResult?>(null)
    val functionCallResult: StateFlow<FunctionCallingSystem.FunctionCallResult?> = _functionCallResult.asStateFlow()

    private val userProfile = UserProfile.getInstance(context)
    private val goalsStorage = PersonalGoalsStorage.getInstance(context)
    private val moodStorage = MoodStorage.getInstance(context)
    private val memoryStorage = MemoryStorage.getInstance(context)
    private val functionCallingSystem = FunctionCallingSystem.getInstance(context)

    private fun buildSystemPrompt(userMessage: String): String {
        // Check if this is a wellness guidance request
        val wellnessTopic = detectWellnessTopic(userMessage)
        
        if (wellnessTopic != null) {
            // Use specialized wellness guidance prompt with minimal context
            val recentMoods = moodStorage.getAllMoodEntries().takeLast(5)
            val userMemories = memoryStorage.getAllMemories().map { it.memory }
            
            val context = PromptContext(
                userMemories = userMemories,
                recentMoods = recentMoods,
                additionalContext = mapOf("wellnessTopic" to wellnessTopic)
            )
            
            return PromptBuilder.build(PromptType.WELLNESS_GUIDANCE, context)
        } else {
            // Use regular quick chat prompt with function calling
            val recentMoods = moodStorage.getAllMoodEntries().takeLast(5)
            val activeGoals = goalsStorage.getActiveGoals()
            val userMemories = memoryStorage.getAllMemories().map { it.memory }
            val recentTopics = userProfile.getRecentTopics()
            
            val context = PromptContext(
                userMemories = userMemories,
                recentMoods = recentMoods,
                personalGoals = activeGoals,
                recentTopics = recentTopics,
                additionalContext = mapOf("userMessage" to userMessage)
            )
            
            return PromptBuilder.build(PromptType.QUICK_CHAT, context)
        }
    }
    
    private fun detectWellnessTopic(userMessage: String): String? {
        val wellnessTopics = mapOf(
            // Mental Health
            "anxiety management and coping strategies" to "anxiety management and coping strategies",
            "stress reduction and stress management techniques" to "stress reduction and stress management techniques",
            "depression support and mood improvement strategies" to "depression support and mood improvement strategies",
            "emotional regulation and managing overwhelming emotions" to "emotional regulation and managing overwhelming emotions",
            "mood enhancement and emotional well-being" to "mood enhancement and emotional well-being",
            
            // Physical Health
            "sleep hygiene and improving sleep quality" to "sleep hygiene and improving sleep quality",
            "exercise motivation and building sustainable fitness habits" to "exercise motivation and building sustainable fitness habits",
            "nutrition guidance and healthy eating habits" to "nutrition guidance and healthy eating habits",
            "pain management and coping with chronic discomfort" to "pain management and coping with chronic discomfort",
            "natural energy enhancement and combating fatigue" to "natural energy enhancement and combating fatigue",
            
            // Personal Growth
            "goal setting and achieving personal objectives" to "goal setting and achieving personal objectives",
            "building positive habits and breaking negative patterns" to "building positive habits and breaking negative patterns",
            "self-confidence and self-esteem enhancement" to "self-confidence and self-esteem enhancement",
            "productivity improvement and time management" to "productivity improvement and time management",
            "motivation and maintaining positive momentum" to "motivation and maintaining positive momentum",
            
            // Daily Wellness
            "personalized daily wellness practices" to "personalized daily wellness practices",
            "mindfulness practices and present-moment awareness" to "mindfulness practices and present-moment awareness",
            "self-care practices and prioritizing personal well-being" to "self-care practices and prioritizing personal well-being",
            "work-life balance and creating life harmony" to "work-life balance and creating life harmony",
            "establishing healthy daily routines" to "establishing healthy daily routines"
        )
        
        return wellnessTopics.keys.find { topic -> 
            userMessage.contains(topic, ignoreCase = true)
        }?.let { wellnessTopics[it] }
    }

    fun sendMessage(userMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                _isInputEnabled.value = false
                _response.value = "" // Clear previous response

                // Build the complete prompt with fresh context including user message for function detection
                val systemPrompt = buildSystemPrompt(userMessage)
                val fullPrompt = """$systemPrompt

User request: $userMessage

Response:"""

                // Store accumulated response for function parsing
                var fullResponse = ""

                // Generate response with streaming
                val responseJob = inferenceModel.generateResponseAsync(fullPrompt) { partialResult, done ->
                    // This callback is called for each token/chunk as it's generated
                    if (partialResult.isNotEmpty()) {
                        fullResponse += partialResult
                        // Update the response in real-time with streaming text
                        _response.value = _response.value + partialResult

                        // Hide loading indicator as soon as we get the first chunk
                        if (_isLoading.value) {
                            _isLoading.value = false
                        }
                    }

                    if (done) {
                        // Generation complete - now parse and execute functions
                        val result = functionCallingSystem.processAIResponse(fullResponse)
                        
                        if (result.handled) {
                            // Update response with clean text
                            _response.value = result.response
                            
                            // Store function call result for UI
                            _functionCallResult.value = result
                        }
                        
                        _isLoading.value = false
                        _isInputEnabled.value = true

                        // Update user profile with general info
                        updateUserProfileFromInteraction(userMessage)
                    }
                }

                // Wait for completion
                responseJob.get()

            } catch (e: Exception) {
                _response.value = "I'm having trouble responding right now. Please try again in a moment. Remember that talking to a friend, family member, or counselor can also be very helpful. 💙"
                _isLoading.value = false
                _isInputEnabled.value = true
            }
        }
    }

    private fun updateUserProfileFromInteraction(userMessage: String) {
        // Extract mood indicators
        val lowerMessage = userMessage.lowercase()
        val mood = when {
            lowerMessage.contains("stress") || lowerMessage.contains("anxious") ||
                    lowerMessage.contains("worried") -> "stressed"
            lowerMessage.contains("sad") || lowerMessage.contains("down") ||
                    lowerMessage.contains("depressed") -> "sad"
            lowerMessage.contains("happy") || lowerMessage.contains("good") ||
                    lowerMessage.contains("great") -> "positive"
            lowerMessage.contains("tired") || lowerMessage.contains("exhausted") -> "tired"
            else -> "neutral"
        }

        // Extract topic
        val topic = when {
            lowerMessage.contains("sleep") -> "sleep issues"
            lowerMessage.contains("work") || lowerMessage.contains("job") -> "work stress"
            lowerMessage.contains("school") || lowerMessage.contains("study") -> "academic stress"
            lowerMessage.contains("relationship") || lowerMessage.contains("friend") -> "relationships"
            lowerMessage.contains("family") -> "family issues"
            lowerMessage.contains("exercise") || lowerMessage.contains("fitness") -> "physical health"
            lowerMessage.contains("motivation") -> "motivation"
            else -> "general wellness"
        }

        // Update profile
        userProfile.updateMood(mood)
        userProfile.addTopic(topic)
        userProfile.saveProfile(context)
    }
    
    fun clearFunctionCallResult() {
        _functionCallResult.value = null
    }

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                if (modelClass.isAssignableFrom(QuickChatViewModel::class.java)) {
                    val inferenceModel = InferenceModel.getInstance(context)
                    return QuickChatViewModel(inferenceModel, context) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}