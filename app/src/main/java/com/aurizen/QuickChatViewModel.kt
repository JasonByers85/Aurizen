package com.aurizen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
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

    private val userProfile = UserProfile.getInstance(context)

    private val systemPrompt = """You are a supportive AI wellness companion. Provide helpful, concise advice for mental health, stress management, and general wellness.

Your guidelines:
• Keep responses helpful but concise (2-3 paragraphs max)
• Focus on practical, actionable advice
• Be warm and supportive without being overly emotional
• Provide specific techniques and strategies
• Don't diagnose - suggest professional help when appropriate
• Each conversation is independent, don't reference past interactions

Current user context:
- Mood: ${userProfile.mood}
- Recent topics: ${userProfile.getRecentTopics().joinToString(", ")}

Respond with practical wellness support:"""

    fun sendMessage(userMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                _isInputEnabled.value = false
                _response.value = "" // Clear previous response

                // Build the complete prompt
                val fullPrompt = """$systemPrompt

User request: $userMessage

Response:"""

                // Generate response with streaming
                val responseJob = inferenceModel.generateResponseAsync(fullPrompt) { partialResult, done ->
                    // This callback is called for each token/chunk as it's generated
                    if (partialResult.isNotEmpty()) {
                        // Update the response in real-time with streaming text
                        _response.value = _response.value + partialResult

                        // Hide loading indicator as soon as we get the first chunk
                        if (_isLoading.value) {
                            _isLoading.value = false
                        }
                    }

                    if (done) {
                        // Generation complete
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

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val inferenceModel = InferenceModel.getInstance(context)
                return QuickChatViewModel(inferenceModel, context) as T
            }
        }
    }
}