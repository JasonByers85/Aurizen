package com.aurizen

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

class TalkViewModel(
    private val inferenceModel: InferenceModel,
    private val context: Context
) : ViewModel() {

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    private val _streamingResponse = MutableStateFlow("")
    val streamingResponse: StateFlow<String> = _streamingResponse.asStateFlow()

    private val userProfile = UserProfile.getInstance(context)
    private val memoryStorage = MemoryStorage.getInstance(context)
    private val speechToTextHelper = TalkSpeechToTextHelper(context)
    private var textToSpeech: TextToSpeech? = null
    private val ttsSettings = TTSSettings.getInstance(context)

    // Keep chat history limited for faster responses
    private val maxChatHistory = 10

    init {
        initializeTTS()
        initializeSpeechToText()
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

    private fun buildSystemPrompt(): String {
        // Get user memories for context
        val userMemories = memoryStorage.getAllMemories()
        val memoriesContext = if (userMemories.isNotEmpty()) {
            val memoriesSummary = userMemories.take(3).joinToString("; ") { it.memory }
            "Personal context: $memoriesSummary"
        } else {
            ""
        }

        // Shorter, conversational system prompt for faster responses
        return """You are AuriZen, a supportive wellness AI built into this comprehensive wellness app. Keep responses conversational, friendly, and concise (1-2 paragraphs max). Focus on practical wellness advice.

AuriZen's built-in features (recommend when relevant):
- Guided meditations (AI-generated & predefined programs)
- 9 breathing exercise programs (4-7-8, box breathing, etc.)
- Mood tracking, dream journaling, personal goals

${if (memoriesContext.isNotEmpty()) "Context: $memoriesContext\n" else ""}
Be natural and supportive in our conversation:"""
    }

    private fun buildChatPrompt(userMessage: String): String {
        val systemPrompt = buildSystemPrompt()
        
        // Include recent chat history for context
        val recentHistory = _chatHistory.value.takeLast(6) // Last 3 exchanges
        val historyText = if (recentHistory.isNotEmpty()) {
            val historyString = recentHistory.joinToString("\n") { message ->
                if (message.isFromUser) "User: ${message.content}" else "AuriZen: ${message.content}"
            }
            "Recent conversation:\n$historyString\n\n"
        } else {
            ""
        }

        return """$systemPrompt

${historyText}User: $userMessage

AuriZen:"""
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
                _isProcessing.value = true

                // Add user message to chat
                val userChatMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = userMessage,
                    isFromUser = true
                )

                _chatHistory.value = _chatHistory.value + userChatMessage

                // Generate AI response
                val prompt = buildChatPrompt(userMessage)
                var aiResponse = ""

                val responseJob = inferenceModel.generateResponseAsync(prompt) { partialResult, done ->
                    if (partialResult.isNotEmpty()) {
                        aiResponse += partialResult
                    }

                    if (done) {
                        // Add AI response to chat
                        val aiChatMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            content = aiResponse.trim(),
                            isFromUser = false
                        )

                        // Update chat history and limit size
                        val updatedHistory = (_chatHistory.value + aiChatMessage).takeLast(maxChatHistory)
                        _chatHistory.value = updatedHistory

                        _isProcessing.value = false

                        // Speak the AI response
                        speakResponse(aiResponse.trim())

                        // Update user profile with interaction data
                        updateUserProfile(userMessage)
                    }
                }

                responseJob.get()

            } catch (e: Exception) {
                val errorMessage = "I'm having trouble understanding right now. Could you try again?"

                val aiChatMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = errorMessage,
                    isFromUser = false
                )

                _chatHistory.value = _chatHistory.value + aiChatMessage
                _isProcessing.value = false

                speakResponse(errorMessage)
            }
        }
    }

    private fun speakResponse(text: String) {
        if (!ttsSettings.getTtsEnabled()) {
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

    fun clearChat() {
        _chatHistory.value = emptyList()
        stopSpeaking()
        stopListening()
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