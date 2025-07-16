package com.aurizen.viewmodels

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.aurizen.prompts.PromptBuilder
import com.aurizen.prompts.PromptContext
import com.aurizen.prompts.PromptType
import com.aurizen.core.FunctionCallingSystem
import com.aurizen.core.InferenceModel
import com.aurizen.data.UserProfile
import com.aurizen.data.MemoryStorage
import com.aurizen.utils.TalkSpeechToTextHelper
import com.aurizen.settings.TTSSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import com.aurizen.data.MultimodalChatMessage
import com.aurizen.data.MessageSide
import com.aurizen.data.getDisplayContent
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.aurizen.data.MultimodalChatMessageTypeAdapter

class TalkViewModel(
    private val inferenceModel: InferenceModel,
    private val context: Context
) : ViewModel() {
    
    private val TAG = "TalkViewModel"

    private val _chatHistory = MutableStateFlow<List<MultimodalChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<MultimodalChatMessage>> = _chatHistory.asStateFlow()
    
    private val chatPrefs = context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
    private val gson = GsonBuilder()
        .registerTypeAdapter(MultimodalChatMessage::class.java, MultimodalChatMessageTypeAdapter())
        .create()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()
    
    private val _functionCallResult = MutableStateFlow<FunctionCallingSystem.FunctionCallResult?>(null)
    val functionCallResult: StateFlow<FunctionCallingSystem.FunctionCallResult?> = _functionCallResult.asStateFlow()

    private val _streamingResponse = MutableStateFlow("")
    val streamingResponse: StateFlow<String> = _streamingResponse.asStateFlow()
    
    private val _isVoiceEnabled = MutableStateFlow(true)
    val isVoiceEnabled: StateFlow<Boolean> = _isVoiceEnabled.asStateFlow()

    private val userProfile = UserProfile.getInstance(context)
    private val memoryStorage = MemoryStorage.getInstance(context)
    private val functionCallingSystem = FunctionCallingSystem.getInstance(context)
    private val speechToTextHelper = TalkSpeechToTextHelper(context)
    private var textToSpeech: TextToSpeech? = null
    private val ttsSettings = TTSSettings.getInstance(context)
    

    // Keep chat history limited for faster responses
    private val maxChatHistory = 10
    
    // Single-turn approach - no session management needed

    init {
        Log.d(TAG, "🚀 TalkViewModel initializing...")
        initializeTTS()
        initializeSpeechToText()
        
        // Initialize voice toggle from settings
        _isVoiceEnabled.value = ttsSettings.getTtsEnabled()
        
        // Load persisted chat history immediately
        loadPersistedChatHistory()
        
        Log.d(TAG, "✅ TalkViewModel initialized with ${_chatHistory.value.size} messages, voice enabled: ${_isVoiceEnabled.value}")
    }

    private fun initializeTTS() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                updateTTSSettings()
            }
        }
    }

    private fun updateTTSSettings() {
        textToSpeech?.let { tts ->
            tts.setSpeechRate(ttsSettings.getSpeechRate())
            tts.setPitch(ttsSettings.getPitch())
            
            // Apply voice selection if available
            val selectedVoice = ttsSettings.getVoice()
            val genderPreference = ttsSettings.getGenderPreference()
            
            when {
                selectedVoice.isNotEmpty() -> {
                    tts.voices?.find { it.name == selectedVoice }?.let { voice ->
                        tts.voice = voice
                    }
                }
                genderPreference != "Any" -> {
                    // Find voice by gender preference
                    val filteredVoices = tts.voices?.filter { voice ->
                        when (genderPreference) {
                            "Male" -> getVoiceGenderFromName(voice.name) == "male"
                            "Female" -> getVoiceGenderFromName(voice.name) == "female"
                            else -> true
                        }
                    }
                    filteredVoices?.firstOrNull()?.let { voice ->
                        tts.voice = voice
                    }
                }
                else -> {
                    // Default case - no specific voice configuration needed
                }
            }

        }
    }

    private fun initializeSpeechToText() {
        speechToTextHelper.setOnResultListener { transcript ->
            _currentTranscript.value = transcript
        }

        speechToTextHelper.setOnFinalResultListener { finalTranscript ->
            if (finalTranscript.isNotBlank()) {
                processUserInput(finalTranscript)
            }
            _currentTranscript.value = ""
            _isListening.value = false
        }

        speechToTextHelper.setOnErrorListener { error ->
            _isListening.value = false
            _currentTranscript.value = ""
            // Could show error message to user
        }
    }
    
    private fun loadPersistedChatHistory() {
        try {
            val historyJson = chatPrefs.getString("messages", null)
            Log.d(TAG, "🔄 Loading chat history. JSON exists: ${!historyJson.isNullOrEmpty()}")
            
            if (!historyJson.isNullOrEmpty()) {
                Log.d(TAG, "📄 JSON content length: ${historyJson.length}")
                Log.d(TAG, "📄 JSON preview: ${historyJson.take(100)}...")
                
                val type = object : TypeToken<List<MultimodalChatMessage>>() {}.type
                val messages = gson.fromJson<List<MultimodalChatMessage>>(historyJson, type)
                
                Log.d(TAG, "🔍 Parsed ${messages.size} messages from JSON")
                
                // Directly set the StateFlow value - this is the key fix
                _chatHistory.value = messages
                
                Log.d(TAG, "📊 StateFlow set to ${_chatHistory.value.size} messages")
                Log.d(TAG, "📊 Verification: StateFlow contains ${_chatHistory.value.size} messages")
                
                // History loaded successfully
                
                Log.d(TAG, "✅ Successfully loaded ${messages.size} persisted chat messages")
            } else {
                Log.d(TAG, "❌ No persisted chat history found, setting empty list")
                _chatHistory.value = emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error loading chat history", e)
            // Clear corrupted data
            chatPrefs.edit().remove("messages").apply()
            _chatHistory.value = emptyList()
        }
    }
    
    private fun saveChatHistory() {
        try {
            // Limit the persisted history to prevent excessive storage
            val messagesToSave = _chatHistory.value.takeLast(maxChatHistory)
            val historyJson = gson.toJson(messagesToSave)
            chatPrefs.edit().putString("messages", historyJson).apply()
            
            Log.d(TAG, "💾 Saved ${messagesToSave.size} chat messages (from ${_chatHistory.value.size} total)")
            Log.d(TAG, "💾 JSON length: ${historyJson.length}")
            Log.d(TAG, "💾 JSON preview: ${historyJson.take(100)}...")
            
            // Verify save by reading back
            val savedJson = chatPrefs.getString("messages", null)
            if (savedJson == historyJson) {
                Log.d(TAG, "✅ Chat history save verified successfully")
            } else {
                Log.e(TAG, "❌ Chat history save verification failed!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error saving chat history", e)
        }
    }

    private fun buildSystemPrompt(userMessage: String = ""): String {
        // Get user memories for context
        val userMemories = memoryStorage.getAllMemories()
        val memoriesContext = userMemories.take(3).map { it.memory }
        
        // Create PromptContext with user memories and user message for function detection
        val promptContext = PromptContext(
            userMemories = memoriesContext,
            additionalContext = if (userMessage.isNotEmpty()) mapOf("userMessage" to userMessage) else emptyMap()
        )
        
        // Use PromptBuilder to build the system prompt
        val systemPrompt = PromptBuilder.build(PromptType.TALK, promptContext)
        Log.d(TAG, "🔧 Built system prompt (${systemPrompt.length} chars): ${systemPrompt.take(100)}...")
        
        // Debug function detection
        if (userMessage.isNotEmpty()) {
            val functionDetectionResult = PromptBuilder.testFunctionDetection(userMessage)
            Log.d(TAG, "🔍 Function detection for '$userMessage': $functionDetectionResult")
        }
        
        return systemPrompt
    }
    

    private fun buildChatPrompt(userMessage: String): String {
        // Simple approach: always include system prompt + recent history + user message
        val systemPrompt = buildSystemPrompt(userMessage)
        val recentHistory = buildSimpleHistoryContext()
        
        Log.d(TAG, "🎯 Building prompt with system + history + user message")
        
        return if (recentHistory.isNotEmpty()) {
            "$systemPrompt\n\nRecent conversation:\n$recentHistory\n\nUser: $userMessage"
        } else {
            "$systemPrompt\n\nUser: $userMessage"
        }
    }
    
    private fun buildSimpleHistoryContext(): String {
        // Simple approach: include last few messages exactly as they are
        val recentMessages = _chatHistory.value.takeLast(6) // Last 3 exchanges (6 messages)
        
        Log.d(TAG, "📚 Building simple history from ${recentMessages.size} recent messages")
        
        return recentMessages.joinToString("\n") { message ->
            val role = if (message.side == MessageSide.USER) "User" else "Assistant"
            val content = message.getDisplayContent().take(200) // Allow more content
            "$role: $content"
        }
    }

    fun startListening() {
        if (!_isProcessing.value && !_isSpeaking.value) {
            _isListening.value = true
            _currentTranscript.value = ""
            speechToTextHelper.startListening()
        }
    }

    fun stopListening() {
        _isListening.value = false
        speechToTextHelper.stopListening()
        _currentTranscript.value = ""
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        _isSpeaking.value = false
    }

    private fun processUserInput(userMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🎯 processUserInput started: '$userMessage'")
                Log.d(TAG, "📊 Current chat history size: ${_chatHistory.value.size}")
                
                // Chat history size logged above for debugging
                
                _isProcessing.value = true

                // Generate AI response (before adding user message to history)
                val prompt = buildChatPrompt(userMessage)
                
                // Add user message to chat after building prompt
                val userChatMessage = MultimodalChatMessage.TextMessage(
                    content = userMessage,
                    messageSide = MessageSide.USER
                )

                _chatHistory.value = _chatHistory.value + userChatMessage
                Log.d(TAG, "📝 Added user message. New chat history size: ${_chatHistory.value.size}")
                saveChatHistory()
                Log.d(TAG, "🤖 Built prompt (${prompt.length} chars): ${prompt.take(200)}...")
                Log.d(TAG, "🤖 Full prompt preview: ${prompt.take(500)}...")
                var aiResponse = ""

                Log.d(TAG, "🚀 Starting generateResponseAsync call...")
                Log.d(TAG, "🤖 InferenceModel state: ${inferenceModel.javaClass.simpleName}")
                
                try {
                    val responseJob = inferenceModel.generateResponseAsync(
                    prompt, null, { partialResult, done ->
                        Log.d(TAG, "📥 Response callback: partialResult='${partialResult.take(50)}...', done=$done")
                        if (partialResult.isNotEmpty()) {
                            aiResponse += partialResult
                            Log.d(TAG, "📈 Total response length: ${aiResponse.length}")
                        }

                        if (done) {
                            Log.d(TAG, "✅ Response generation complete. Total response: '${aiResponse.take(200)}...'")
                            Log.d(TAG, "🔍 Full AI response for function call analysis:")
                            Log.d(TAG, "📄 RESPONSE: $aiResponse")
                            Log.d(TAG, "🔍 Processing function calls...")
                            // Process function calls first
                            val result = functionCallingSystem.processAIResponse(aiResponse.trim())
                            
                            if (result.handled) {
                                _functionCallResult.value = result
                                
                                // Clean the AI response and provide a concise confirmation
                                val functionCallPattern = Regex("FUNCTION_CALL:([A-Z_]+):(\\{[^}]*\\})")
                                val cleanResponse = aiResponse.replace(functionCallPattern, "").trim()
                                val responseText = when (result.resultType) {
                                    FunctionCallingSystem.ResultType.GOAL_CREATED -> "✅ Goal created! You can track your progress in Personal Goals."
                                    FunctionCallingSystem.ResultType.MEDITATION_CREATED -> "🧘‍♂️ Meditation created! Ready to start when you are."
                                    FunctionCallingSystem.ResultType.MEMORY_STORED -> "💭 Got it! I'll remember that."
                                    else -> "✅ Done!"
                                }.let { defaultResponse ->
                                    // Clean and check AI response
                                    val sanitizedResponse = cleanResponse
                                        .replace(Regex("```[\\s\\S]*?```"), "") // Remove code blocks
                                        .replace("tool_code", "")
                                        .replace(Regex("\\{[^}]*\\}"), "") // Remove JSON objects
                                        .replace(Regex("\\s+"), " ") // Normalize whitespace
                                        .trim()
                                    
                                    // Use cleaned AI response if it's short and clean, otherwise use default
                                    if (sanitizedResponse.isNotBlank() && 
                                        sanitizedResponse.length < 150 && 
                                        !sanitizedResponse.contains("FUNCTION_CALL")) {
                                        sanitizedResponse
                                    } else {
                                        defaultResponse
                                    }
                                }
                                
                                // Add AI response to chat
                                val aiChatMessage = MultimodalChatMessage.TextMessage(
                                    content = responseText,
                                    messageSide = MessageSide.ASSISTANT
                                )
                                
                                // Update chat history and limit size
                                val updatedHistory = (_chatHistory.value + aiChatMessage).takeLast(maxChatHistory)
                                _chatHistory.value = updatedHistory
                                saveChatHistory()
                                
                                _isProcessing.value = false
                                
                                // Speak the AI response
                                speakResponse(responseText)
                            } else {
                                // No function call detected, use the original response
                                val finalResponse = aiResponse.trim()
                                
                                // Add AI response to chat
                                val aiChatMessage = MultimodalChatMessage.TextMessage(
                                    content = finalResponse,
                                    messageSide = MessageSide.ASSISTANT
                                )

                                // Update chat history and limit size
                                val updatedHistory = (_chatHistory.value + aiChatMessage).takeLast(maxChatHistory)
                                _chatHistory.value = updatedHistory
                                saveChatHistory()

                                _isProcessing.value = false

                                // Speak the AI response
                                speakResponse(finalResponse)
                            }

                            // Update user profile with interaction data
                            updateUserProfile(userMessage)
                        }
                    }, false // preserveContext = false for single-turn stability
                    )

                    Log.d(TAG, "⏳ Waiting for response job to complete...")
                    // Add timeout to prevent hanging forever
                    try {
                        responseJob.get(30, java.util.concurrent.TimeUnit.SECONDS)
                        Log.d(TAG, "🏁 Response job completed successfully")
                    } catch (timeout: java.util.concurrent.TimeoutException) {
                        Log.e(TAG, "⏰ Response timed out after 30 seconds")
                        throw timeout
                    }
                    
                } catch (inferenceException: Exception) {
                    Log.e(TAG, "💥 InferenceModel exception: ${inferenceException.message}", inferenceException)
                    throw inferenceException // Re-throw to be caught by outer catch
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in processUserInput", e)
                
                val errorMessage = when {
                    e.message?.contains("Input is too long") == true || 
                    e.message?.contains("maxTokens") == true -> {
                        Log.w(TAG, "Token limit exceeded - prompt too long")
                        "Your message was too long for me to process. Could you try a shorter message?"
                    }
                    e is java.util.concurrent.TimeoutException -> {
                        Log.w(TAG, "Response timed out")
                        "I took too long to respond. Let's try again - what would you like to talk about?"
                    }
                    e.message?.contains("Previous invocation still processing") == true -> {
                        Log.w(TAG, "Previous invocation still processing")
                        "I'm still thinking about your previous message. Please wait a moment before sending another."
                    }
                    else -> "I'm having trouble understanding right now. Could you try again?"
                }

                val aiChatMessage = MultimodalChatMessage.TextMessage(
                    content = errorMessage,
                    messageSide = MessageSide.ASSISTANT
                )

                _chatHistory.value = _chatHistory.value + aiChatMessage
                saveChatHistory()
                _isProcessing.value = false

                speakResponse(errorMessage)
            }
        }
    }

    private fun speakResponse(text: String) {
        if (!ttsSettings.getTtsEnabled() || !_isVoiceEnabled.value) {
            return
        }

        textToSpeech?.let { tts ->
            _isSpeaking.value = true

            // Apply current settings before speaking
            updateTTSSettings()

            // Prepare TTS parameters with volume
            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsSettings.getVolume())
            }

            tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }
            })

            // Clean text before speaking
            val cleanedText = cleanTextForTTS(text)
            tts.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, params, "TalkResponse")
        }
    }

    private fun cleanTextForTTS(text: String): String {
        return text
            // Remove function call patterns (safety net) - matches FunctionCallingSystem pattern
            .replace(Regex("FUNCTION_CALL:([A-Z_]+):(\\{[^}]*\\})"), "")
            // Remove markdown formatting
            .replace("**", "")
            .replace("*", "")
            .replace("_", "")
            .replace("#", "")
            .replace("`", "")
            // Remove special characters that TTS might pronounce awkwardly
            .replace("•", "")
            .replace("→", "")
            .replace("←", "")
            .replace("↑", "")
            .replace("↓", "")
            .replace("…", "...")
            .replace("–", "-")
            .replace("—", "-")
            .replace(""", "\"")
            .replace(""", "\"")
            .replace("'", "'")
            .replace("'", "'")
            // Remove parentheses and brackets content that might be formatting
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            // Replace multiple spaces with single space
            .replace(Regex("\\s+"), " ")
            // Remove emojis with correct Unicode syntax for Kotlin
            .replace(Regex("[\uD83D\uDE00-\uD83D\uDE4F]"), "") // Emoticons
            .replace(Regex("[\uD83C\uDF00-\uD83D\uDDFF]"), "") // Misc Symbols and Pictographs
            .replace(Regex("[\uD83D\uDE80-\uD83D\uDEFF]"), "") // Transport and Map
            .replace(Regex("[\uD83C\uDDE0-\uD83C\uDDFF]"), "") // Flags
            .replace(Regex("[\u2600-\u26FF]"), "") // Misc symbols
            .replace(Regex("[\u2700-\u27BF]"), "") // Dingbats
            .trim()
    }

    private fun updateUserProfile(userMessage: String) {
        // Extract mood indicators for profile
        val lowerMessage = userMessage.lowercase()
        val mood = when {
            lowerMessage.contains("good") || lowerMessage.contains("great") ||
                    lowerMessage.contains("happy") || lowerMessage.contains("wonderful") -> "positive"
            lowerMessage.contains("stress") || lowerMessage.contains("anxious") ||
                    lowerMessage.contains("worried") -> "stressed"
            lowerMessage.contains("sad") || lowerMessage.contains("down") ||
                    lowerMessage.contains("depressed") -> "sad"
            lowerMessage.contains("tired") || lowerMessage.contains("exhausted") -> "tired"
            else -> "neutral"
        }

        // Extract topic
        val topic = when {
            lowerMessage.contains("sleep") -> "sleep"
            lowerMessage.contains("work") || lowerMessage.contains("job") -> "work"
            lowerMessage.contains("relationship") || lowerMessage.contains("friend") -> "relationships"
            lowerMessage.contains("family") -> "family"
            lowerMessage.contains("exercise") || lowerMessage.contains("fitness") -> "fitness"
            else -> "general conversation"
        }

        // Update profile
        userProfile.updateMood(mood)
        userProfile.addTopic(topic)
        userProfile.saveProfile(context)
    }

    fun processDirectSpeechInput(speechText: String) {
        processUserInput(speechText)
    }
    
    fun sendTextMessage(message: String) {
        if (message.isNotBlank()) {
            processUserInput(message)
        }
    }
    
    fun toggleVoice() {
        _isVoiceEnabled.value = !_isVoiceEnabled.value
        
        // Save the setting to TTSSettings
        ttsSettings.setTtsEnabled(_isVoiceEnabled.value)
        
        // Stop speaking if voice is disabled
        if (!_isVoiceEnabled.value) {
            stopSpeaking()
        }
    }

    fun clearChat() {
        _chatHistory.value = emptyList()
        saveChatHistory() // Save empty history to persist the clear action
        stopSpeaking()
        stopListening()
        _functionCallResult.value = null
    }
    
    fun clearFunctionCallResult() {
        _functionCallResult.value = null
    }
    
    fun refreshChatHistory() {
        viewModelScope.launch {
            Log.d(TAG, "🔄 Manually refreshing chat history...")
            Log.d(TAG, "🔄 Current chatHistory size before refresh: ${_chatHistory.value.size}")
            
            // Force clear the current history to ensure StateFlow triggers
            _chatHistory.value = emptyList()
            
            // Small delay to ensure UI observes the empty state
            kotlinx.coroutines.delay(10)
            
            // Load from persistence
            loadPersistedChatHistory()
            
            Log.d(TAG, "🔄 Chat history refresh completed. Final size: ${_chatHistory.value.size}")
        }
    }
    
    // Force update StateFlow to ensure UI recomposition
    private fun forceUpdateChatHistory(messages: List<MultimodalChatMessage>) {
        _chatHistory.value = messages
        Log.d(TAG, "⚡ Force updated chat history with ${messages.size} messages")
    }
    
    fun refreshVoiceSettings() {
        Log.d(TAG, "Refreshing voice settings from TTSSettings...")
        _isVoiceEnabled.value = ttsSettings.getTtsEnabled()
        updateTTSSettings()
    }

    
    override fun onCleared() {
        super.onCleared()
        textToSpeech?.shutdown()
        speechToTextHelper.destroy()
    }

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                if (modelClass.isAssignableFrom(TalkViewModel::class.java)) {
                    val inferenceModel = InferenceModel.getInstance(context)
                    return TalkViewModel(inferenceModel, context) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    private fun getVoiceGenderFromName(name: String): String {
        val lowerName = name.lowercase()
        return when {
            // Common male indicators
            lowerName.contains("male") && !lowerName.contains("female") -> "male"
            lowerName.contains("man") && !lowerName.contains("woman") -> "male"
            lowerName.contains("guy") -> "male"
            lowerName.contains("boy") -> "male"
            // Specific TTS engine male voices
            lowerName.contains("_m_") -> "male"
            lowerName.contains("-m-") -> "male"
            lowerName.contains("#male") -> "male"
            
            // Common female indicators
            lowerName.contains("female") -> "female"
            lowerName.contains("woman") -> "female"
            lowerName.contains("girl") -> "female"
            lowerName.contains("lady") -> "female"
            // Specific TTS engine female voices
            lowerName.contains("_f_") -> "female"
            lowerName.contains("-f-") -> "female"
            lowerName.contains("#female") -> "female"
            
            // Default female for common voice names that are typically female
            lowerName.contains("samantha") -> "female"
            lowerName.contains("susan") -> "female"
            lowerName.contains("karen") -> "female"
            lowerName.contains("alice") -> "female"
            lowerName.contains("victoria") -> "female"
            
            // Default male for common voice names that are typically male
            lowerName.contains("james") -> "male"
            lowerName.contains("robert") -> "male"
            lowerName.contains("daniel") -> "male"
            lowerName.contains("david") -> "male"
            lowerName.contains("alex") && !lowerName.contains("alexa") -> "male"
            
            else -> "unknown"
        }
    }
}