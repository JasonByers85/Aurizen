package com.aurizen

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import com.google.common.util.concurrent.ListenableFuture

class UnifiedMeditationSessionViewModel(
    private val context: Context,
    private val meditationType: String
) : ViewModel() {

    private val TAG = "UnifiedMeditationVM"

    // Core session state
    private val _sessionState = MutableStateFlow(UnifiedMeditationSessionState.PREPARING)
    val sessionState: StateFlow<UnifiedMeditationSessionState> = _sessionState.asStateFlow()

    private val _currentStep = MutableStateFlow<UnifiedMeditationStep?>(null)
    val currentStep: StateFlow<UnifiedMeditationStep?> = _currentStep.asStateFlow()

    private val _progress = MutableStateFlow(UnifiedMeditationProgress(0, 1, 0, 0, false, "", UnifiedMeditationSessionState.PREPARING))
    val progress: StateFlow<UnifiedMeditationProgress> = _progress.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _audioSettings = MutableStateFlow(AudioSettings(
        soundEnabled = true,
        backgroundSound = BackgroundSound.NONE,
        binauralEnabled = false,
        binauralTone = BinauralTone.NONE,
        ttsEnabled = true
    ))
    val audioSettings: StateFlow<AudioSettings> = _audioSettings.asStateFlow()

    private val _generationStatus = MutableStateFlow<MeditationGenerationStatus>(MeditationGenerationStatus.Idle)
    val generationStatus: StateFlow<MeditationGenerationStatus> = _generationStatus.asStateFlow()

    private val _showSaveDialog = MutableStateFlow(false)
    val showSaveDialog: StateFlow<Boolean> = _showSaveDialog.asStateFlow()

    private val _isFullyGenerated = MutableStateFlow(false)
    val isFullyGenerated: StateFlow<Boolean> = _isFullyGenerated.asStateFlow()

    private val _currentSentence = MutableStateFlow("")
    val currentSentence: StateFlow<String> = _currentSentence.asStateFlow()

    // TTS sentence-by-sentence support
    private var currentTtsText: String = ""
    private var ttsIsPaused = false
    private var ttsUtteranceId = "meditation_guidance"
    private var currentSentences: MutableList<String> = mutableListOf()
    private var currentSentenceIndex: Int = 0
    private var isPlayingSentences: Boolean = false
    
    // Streaming generation support
    private var streamingBuffer: String = ""
    private var streamingTitle: String = ""
    private var streamingGuidance: String = ""
    private var isStreamingActive: Boolean = false
    private var hasStreamingStarted: Boolean = false

    // Internal state
    private val meditationSettings = MeditationSettings.getInstance(context)
    private val audioManager = MeditationAudioManager(context)
    // Removed StreamingMeditationGenerator - streaming logic is now built-in
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var timerJob: Job? = null
    private var generationJob: Job? = null
    private var remainingStepsJob: Job? = null
    private var currentInferenceFuture: ListenableFuture<String>? = null
    
    // Session configuration
    private var sessionConfig: UnifiedMeditationConfig? = null
    private val unifiedSteps = mutableListOf<UnifiedMeditationStep>()
    private var currentStepIndex = 0
    private var totalSessionDuration = 0

    init {
        Log.d(TAG, "🚀 Creating unified meditation session for: $meditationType")
        initializeSession()
    }

    private fun initializeSession() {
        viewModelScope.launch {
            try {
                // Determine meditation type
                when {
                    meditationType.startsWith("custom:") -> setupCustomMeditation()
                    meditationType.startsWith("custom_ai_") -> setupCustomMeditation()
                    meditationType.startsWith("saved_") -> setupSavedMeditation()
                    else -> setupRegularMeditation()
                }
                
                // Load settings in background
                loadAudioSettings()
                initializeTTS()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize session", e)
                _generationStatus.value = MeditationGenerationStatus.Error(
                    "Failed to initialize meditation session: ${e.message}"
                )
            }
        }
    }

    private suspend fun setupCustomMeditation() {
        Log.d(TAG, "🎯 Setting up custom meditation")
        
        // Load configuration immediately  
        val config = loadCustomMeditationConfig()
        if (config == null) {
            withContext(Dispatchers.Main) {
                _generationStatus.value = MeditationGenerationStatus.Error(
                    "Custom meditation configuration not found"
                )
            }
            return
        }
        
        sessionConfig = config
        
        // Immediately update UI to show we're ready to start generating
        withContext(Dispatchers.Main) {
            _generationStatus.value = MeditationGenerationStatus.Starting
            _progress.value = UnifiedMeditationProgress(
                currentStepIndex = 0,
                totalSteps = config.totalSteps,
                timeRemainingInStep = config.stepDuration,
                totalTimeRemaining = config.totalDuration * 60,
                isGenerating = true,
                generationStatus = "Preparing your personalized meditation...",
                sessionState = UnifiedMeditationSessionState.PREPARING
            )
            _sessionState.value = UnifiedMeditationSessionState.PREPARING
        }
        
        // Start AI generation in background - don't wait for it to complete
        generationJob = viewModelScope.launch(Dispatchers.IO) {
            generateFirstStepAsync(config)
        }
    }

    private suspend fun setupRegularMeditation() {
        Log.d(TAG, "📖 Setting up regular meditation")
        
        // Load predefined meditation steps
        val steps = getMeditationSteps(meditationType)
        if (steps.isEmpty()) {
            _generationStatus.value = MeditationGenerationStatus.Error(
                "No meditation steps found for type: $meditationType"
            )
            return
        }
        
        // Convert to unified steps
        unifiedSteps.clear()
        unifiedSteps.addAll(steps.mapIndexed { index, step -> step.toUnified(index) })
        
        totalSessionDuration = unifiedSteps.sumOf { it.durationSeconds }
        
        // Set up session configuration
        sessionConfig = UnifiedMeditationConfig(
            sessionId = "regular_$meditationType",
            meditationType = meditationType,
            totalDuration = totalSessionDuration / 60,
            totalSteps = unifiedSteps.size,
            isCustomGenerated = false,
            moodContext = ""
        )
        
        // Set first step and progress
        _currentStep.value = unifiedSteps[0]
        _progress.value = UnifiedMeditationProgress(
            currentStepIndex = 0,
            totalSteps = unifiedSteps.size,
            timeRemainingInStep = unifiedSteps[0].durationSeconds,
            totalTimeRemaining = totalSessionDuration,
            isGenerating = false,
            generationStatus = "",
            sessionState = UnifiedMeditationSessionState.READY
        )
        
        // Always show the guidance text
        _currentSentence.value = unifiedSteps[0].guidance
        
        _sessionState.value = UnifiedMeditationSessionState.READY
        _generationStatus.value = MeditationGenerationStatus.Idle
        _isFullyGenerated.value = true // Regular meditations are always fully available
        
        Log.d(TAG, "✅ Regular meditation ready: ${unifiedSteps.size} steps")
    }

    private suspend fun setupSavedMeditation() {
        Log.d(TAG, "💾 Setting up saved meditation")
        
        val savedMeditation = loadSavedMeditation(meditationType)
        if (savedMeditation == null) {
            withContext(Dispatchers.Main) {
                _generationStatus.value = MeditationGenerationStatus.Error(
                    "Saved meditation not found"
                )
            }
            return
        }
        
        when (savedMeditation.saveType) {
            SavedMeditationType.EXACT_SESSION -> {
                // Load exact saved steps
                setupExactSavedMeditation(savedMeditation)
            }
            SavedMeditationType.CONFIG_TEMPLATE -> {
                // Regenerate using saved config
                setupTemplateSavedMeditation(savedMeditation)
            }
        }
    }

    private suspend fun setupExactSavedMeditation(savedMeditation: SavedMeditation) {
        Log.d(TAG, "📖 Setting up exact saved meditation")
        
        val savedSteps = loadSavedMeditationSteps(savedMeditation.id)
        if (savedSteps.isEmpty()) {
            withContext(Dispatchers.Main) {
                _generationStatus.value = MeditationGenerationStatus.Error(
                    "No steps found for saved meditation"
                )
            }
            return
        }
        
        // Convert saved steps to unified steps
        unifiedSteps.clear()
        unifiedSteps.addAll(savedSteps.map { savedStep ->
            object : UnifiedMeditationStep {
                override val title: String = savedStep.title
                override val guidance: String = savedStep.guidance
                override val durationSeconds: Int = savedStep.durationSeconds
                override val description: String? = null
                override val stepIndex: Int = savedStep.stepIndex
                override val isCustomGenerated: Boolean = true
            }
        })
        
        totalSessionDuration = unifiedSteps.sumOf { it.durationSeconds }
        
        // Set up session configuration
        sessionConfig = UnifiedMeditationConfig(
            sessionId = savedMeditation.id,
            meditationType = meditationType,
            totalDuration = savedMeditation.totalDuration,
            totalSteps = savedMeditation.totalSteps,
            isCustomGenerated = true,
            moodContext = ""
        )
        
        // Set first step and ready state
        _currentStep.value = unifiedSteps[0]
        _progress.value = UnifiedMeditationProgress(
            currentStepIndex = 0,
            totalSteps = unifiedSteps.size,
            timeRemainingInStep = unifiedSteps[0].durationSeconds,
            totalTimeRemaining = totalSessionDuration,
            isGenerating = false,
            generationStatus = "",
            sessionState = UnifiedMeditationSessionState.READY
        )
        
        // Always show the guidance text
        _currentSentence.value = unifiedSteps[0].guidance
        
        _sessionState.value = UnifiedMeditationSessionState.READY
        _generationStatus.value = MeditationGenerationStatus.Idle
        _isFullyGenerated.value = true // Exact saved meditations are fully available
        
        Log.d(TAG, "✅ Exact saved meditation ready: ${unifiedSteps.size} steps")
    }

    private suspend fun setupTemplateSavedMeditation(savedMeditation: SavedMeditation) {
        Log.d(TAG, "🎯 Setting up template saved meditation")
        
        val savedConfig = loadSavedMeditationConfig(savedMeditation.id)
        if (savedConfig == null) {
            withContext(Dispatchers.Main) {
                _generationStatus.value = MeditationGenerationStatus.Error(
                    "Template configuration not found"
                )
            }
            return
        }
        
        // Create unified config from saved template
        val config = UnifiedMeditationConfig(
            sessionId = savedMeditation.id,
            meditationType = meditationType,
            totalDuration = savedConfig.totalDuration,
            totalSteps = savedConfig.totalSteps,
            isCustomGenerated = true,
            focus = savedConfig.focus,
            mood = savedConfig.mood,
            experience = savedConfig.experience,
            stepDuration = (savedConfig.totalDuration * 60) / savedConfig.totalSteps,
            moodContext = ""
        )
        
        sessionConfig = config
        
        // Set preparing state and start generation
        withContext(Dispatchers.Main) {
            _generationStatus.value = MeditationGenerationStatus.Starting
            _progress.value = UnifiedMeditationProgress(
                currentStepIndex = 0,
                totalSteps = config.totalSteps,
                timeRemainingInStep = config.stepDuration,
                totalTimeRemaining = config.totalDuration * 60,
                isGenerating = true,
                generationStatus = "Regenerating from template...",
                sessionState = UnifiedMeditationSessionState.PREPARING
            )
            _sessionState.value = UnifiedMeditationSessionState.PREPARING
        }
        
        // Start AI generation in background
        generationJob = viewModelScope.launch(Dispatchers.IO) {
            generateFirstStepAsync(config)
        }
        
        Log.d(TAG, "✅ Template saved meditation setup complete")
    }

    private suspend fun generateFirstStepAsync(config: UnifiedMeditationConfig) {
        try {
            Log.d(TAG, "🌊 Starting streaming generation for first step")
            
            withContext(Dispatchers.Main) {
                _generationStatus.value = MeditationGenerationStatus.Generating(0, 0.0f)
                isStreamingActive = true
                hasStreamingStarted = false
                streamingBuffer = ""
                streamingTitle = ""
                streamingGuidance = ""
                currentSentences.clear()
                currentSentenceIndex = 0
            }
            
            val inferenceModel = InferenceModel.getInstance(context)
            
            // Check if model is available before starting generation
            if (!InferenceModel.isAvailable()) {
                Log.w(TAG, "Model not available, cannot start generation")
                withContext(Dispatchers.Main) {
                    _generationStatus.value = MeditationGenerationStatus.Error(
                        "Model is busy processing. Please wait a moment and try again.",
                        canRetry = true
                    )
                }
                return
            }
            
            val prompt = createCustomMeditationPrompt(config, 0)
            
            // Start streaming generation WITHOUT blocking
            currentInferenceFuture = inferenceModel.generateResponseAsync(prompt) { partial, done ->
                if (partial != null) {
                    // Process streaming text immediately
                    viewModelScope.launch(Dispatchers.Main) {
                        processStreamingText(partial, config, 0)
                    }
                }
                if (done) {
                    Log.d(TAG, "🌊 Streaming generation complete")
                    currentInferenceFuture = null
                    viewModelScope.launch(Dispatchers.Main) {
                        finalizeStreamingStep(config, 0)
                    }
                }
            }
            
            // Don't wait for completion - streaming handles the rest!
            Log.d(TAG, "🚀 Streaming generation started, returning immediately")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start streaming generation", e)
            withContext(Dispatchers.Main) {
                _generationStatus.value = MeditationGenerationStatus.Error(
                    "Failed to start meditation generation: ${e.message}",
                    canRetry = true
                )
            }
        }
    }

    /**
     * Process streaming text as it arrives from the AI model
     */
    private fun processStreamingText(partial: String, config: UnifiedMeditationConfig, stepIndex: Int) {
        streamingBuffer += partial
        Log.d(TAG, "🌊 Received ${partial.length} chars, buffer now ${streamingBuffer.length} chars")
        
        // Try to extract title and guidance progressively
        parseStreamingContent()
        
        // Extract sentences from the clean guidance text (not raw JSON)
        val newSentences = if (streamingGuidance.isNotEmpty()) {
            extractCompleteSentences(streamingGuidance)
        } else {
            emptyList()
        }
            
        // Add any new complete sentences to our TTS queue
        newSentences.forEach { sentence ->
            if (!currentSentences.contains(sentence)) {
                currentSentences.add(sentence)
                Log.d(TAG, "🎯 New sentence ready: ${sentence.take(30)}...")
                
                // Start session as soon as we have first sentence!
                if (!hasStreamingStarted && currentSentences.size >= 1) {
                    Log.d(TAG, "🚀 Starting session with ${currentSentences.size} sentence(s)")
                    startStreamingSession(config, stepIndex)
                }
                
                // Add sentence to TTS if we're already playing
                if (hasStreamingStarted && _sessionState.value == UnifiedMeditationSessionState.ACTIVE && _audioSettings.value.ttsEnabled) {
                    queueNewSentenceForTts(sentence)
                }
            }
        }
        
        // Update progress
        val progress = estimateProgress(streamingBuffer.length)
        _generationStatus.value = MeditationGenerationStatus.Generating(stepIndex, progress)
    }
    
    /**
     * Extract title and guidance from streaming JSON content (works with partial JSON)
     */
    private fun parseStreamingContent() {
        val jsonStart = streamingBuffer.indexOf("{")
        
        if (jsonStart >= 0) {
            // Work with partial JSON from the start position
            val jsonPortion = streamingBuffer.substring(jsonStart)
            
            // Try to extract title
            if (streamingTitle.isEmpty()) {
                val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(jsonPortion)
                titleMatch?.groupValues?.get(1)?.let { title ->
                    streamingTitle = title
                    Log.d(TAG, "📝 Title extracted: $streamingTitle")
                }
            }
            
            // Try to extract guidance (properly handle JSON escaping and partial content)
            val guidancePattern = Regex("\"guidance\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"?")
            val guidanceMatch = guidancePattern.find(jsonPortion)
            
            if (guidanceMatch != null) {
                val rawGuidance = guidanceMatch.groupValues[1]
                // Properly unescape JSON content
                val cleanGuidance = rawGuidance
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .trim()
                
                if (cleanGuidance != streamingGuidance && cleanGuidance.isNotEmpty()) {
                    streamingGuidance = cleanGuidance
                    Log.d(TAG, "📖 Guidance updated (${cleanGuidance.length} chars): ${streamingGuidance.take(50)}...")
                }
            }
        }
    }
    
    /**
     * Extract complete sentences from clean guidance text
     */
    private fun extractCompleteSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        
        // Extract sentences from clean guidance text (no JSON parsing needed)
        // Look for complete sentences ending with punctuation
        val sentencePattern = Regex("([^.!?]{10,}[.!?])(?=\\s|$)")
        
        sentencePattern.findAll(text).forEach { match ->
            val sentence = match.groupValues[1].trim()
            if (sentence.isNotEmpty() && sentence.length > 10) {
                sentences.add(sentence)
                Log.d(TAG, "📝 Extracted sentence: ${sentence.take(50)}...")
            }
        }
        
        return sentences
    }
    
    /**
     * Start the meditation session with the first streamed sentence
     */
    private fun startStreamingSession(config: UnifiedMeditationConfig, stepIndex: Int) {
        hasStreamingStarted = true
        
        // Create a streaming step with current content
        val streamingStep = object : UnifiedMeditationStep {
            override val title: String = streamingTitle.ifEmpty { "Meditation Step ${stepIndex + 1}" }
            override val guidance: String = currentSentences.joinToString(" ")
            override val durationSeconds: Int = config.stepDuration
            override val description: String? = null
            override val stepIndex: Int = stepIndex
            override val isCustomGenerated: Boolean = true
        }
        
        // Add to steps and set as current
        unifiedSteps.add(streamingStep)
        _currentStep.value = streamingStep
        
        // Update progress and state
        _progress.value = _progress.value.copy(
            timeRemainingInStep = config.stepDuration,
            isGenerating = false,
            generationStatus = "Streaming more content..."
        )
        _sessionState.value = UnifiedMeditationSessionState.READY
        
        Log.d(TAG, "🚀 Session ready with ${currentSentences.size} sentences, more streaming in background")
    }
    
    /**
     * Add new sentence to TTS queue if currently playing
     */
    private fun queueNewSentenceForTts(sentence: String) {
        Log.d(TAG, "🎵 Queued sentence for TTS: ${sentence.take(30)}...")
        
        // If TTS was waiting for more sentences, restart it
        if (!isPlayingSentences && _isPlaying.value && _audioSettings.value.ttsEnabled && currentSentenceIndex < currentSentences.size) {
            Log.d(TAG, "🔄 Restarting TTS with new sentence")
            startSentenceBasedTts()
        }
    }
    
    /**
     * Finalize the streaming step when generation is complete
     */
    private fun finalizeStreamingStep(config: UnifiedMeditationConfig, stepIndex: Int) {
        isStreamingActive = false
        
        // Create final step with all content
        val finalStep = parseCustomMeditationStep(streamingBuffer, stepIndex, config.stepDuration)
        
        if (hasStreamingStarted && unifiedSteps.isNotEmpty()) {
            // Update existing step
            unifiedSteps[stepIndex] = finalStep
            _currentStep.value = finalStep
            
            // Update sentences with final content
            val allSentences = splitIntoSentences(finalStep.guidance)
            currentSentences.clear()
            currentSentences.addAll(allSentences)
            
            Log.d(TAG, "✅ Finalized streaming step with ${allSentences.size} total sentences")
        } else {
            // Fallback if streaming didn't start properly
            unifiedSteps.add(finalStep)
            _currentStep.value = finalStep
            _sessionState.value = UnifiedMeditationSessionState.READY
            
            Log.d(TAG, "✅ Created fallback step")
        }
        
        // Update final state
        _progress.value = _progress.value.copy(
            isGenerating = false,
            generationStatus = ""
        )
        _generationStatus.value = MeditationGenerationStatus.Completed(stepIndex)
    }
    
    /**
     * Estimate progress based on accumulated text length
     */
    private fun estimateProgress(textLength: Int): Float {
        // Rough estimate: expect about 200-300 characters for a meditation step
        val estimatedTotal = 250f
        return (textLength / estimatedTotal).coerceAtMost(0.9f)
    }

    private fun createCustomMeditationPrompt(config: UnifiedMeditationConfig, stepIndex: Int): String {
        val stepType = when (stepIndex) {
            0 -> "opening"
            config.totalSteps - 1 -> "closing"
            else -> "continuation"
        }
        
        // Use mood-specific guidance if available
        return if (config.focus == "mood-guided wellness" && config.moodContext.isNotEmpty()) {
            """
            Create a personalized ${stepType} meditation step for someone based on their recent emotional journey.
            Step ${stepIndex + 1} of ${config.totalSteps} | Duration: ${config.stepDuration} seconds
            
            IMPORTANT: Reference their actual mood patterns naturally and supportively. Guide them through their recent experiences with compassion.
            
            Their Recent Mood Journey:
            ${config.moodContext}
            
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
        } else {
            """
            Create a ${stepType} meditation step for someone focusing on: ${config.focus}
            Step ${stepIndex + 1} of ${config.totalSteps}
            Duration: ${config.stepDuration} seconds
            
            Respond with JSON format:
            {
              "title": "Brief step title",
              "guidance": "2-3 minute meditation guidance that starts immediately with breathing or relaxation instructions"
            }
            """.trimIndent()
        }
    }

    private fun parseCustomMeditationStep(response: String, stepIndex: Int, duration: Int): UnifiedMeditationStep {
        return try {
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonString = response.substring(jsonStart, jsonEnd)
                val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)
                val guidanceMatch = Regex("\"guidance\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)
                
                val title = titleMatch?.groupValues?.get(1) ?: "Meditation Step ${stepIndex + 1}"
                val guidance = guidanceMatch?.groupValues?.get(1) ?: "Focus on your breath and be present."
                
                object : UnifiedMeditationStep {
                    override val title: String = title
                    override val guidance: String = guidance.replace("\\n", "\n")
                    override val durationSeconds: Int = duration
                    override val description: String? = null
                    override val stepIndex: Int = stepIndex
                    override val isCustomGenerated: Boolean = true
                }
            } else {
                throw Exception("Invalid JSON format")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse meditation step", e)
            // Return fallback step
            object : UnifiedMeditationStep {
                override val title: String = "Meditation Step ${stepIndex + 1}"
                override val guidance: String = "Take a deep breath and focus on this moment of peace."
                override val durationSeconds: Int = duration
                override val description: String? = null
                override val stepIndex: Int = stepIndex
                override val isCustomGenerated: Boolean = true
            }
        }
    }

    // Session control methods
    fun togglePlayPause() {
        if (_isPlaying.value) {
            pauseSession()
        } else {
            if (_sessionState.value == UnifiedMeditationSessionState.PAUSED) {
                resumeSession()
            } else {
                startSession()
            }
        }
    }

    private fun startSession() {
        Log.d(TAG, "▶️ Starting session")
        _isPlaying.value = true
        _sessionState.value = UnifiedMeditationSessionState.ACTIVE
        
        // Start audio
        val settings = _audioSettings.value
        
        // Apply volumes from saved settings
        audioManager.setVolume(meditationSettings.getVolume())
        audioManager.setBinauralVolume(meditationSettings.getBinauralVolume())
        audioManager.setTtsVolume(meditationSettings.getTtsVolume())
        
        if (settings.soundEnabled) {
            audioManager.playBackgroundSound(settings.backgroundSound)
        }
        if (settings.binauralEnabled) {
            audioManager.playBinauralTone(settings.binauralTone)
        }
        
        // Speak current step guidance
        if (settings.ttsEnabled && _currentStep.value != null) {
            speakGuidance(_currentStep.value!!.guidance)
        }
        
        // Start timer
        startTimer()
        
        // For custom meditations, we'll generate next steps when current step is almost complete
        // NOT immediately - this prevents the 13-second delay issue
    }

    private fun pauseSession() {
        Log.d(TAG, "⏸️ Pausing session")
        _isPlaying.value = false
        _sessionState.value = UnifiedMeditationSessionState.PAUSED
        timerJob?.cancel()
        audioManager.pauseBackgroundSound()
        audioManager.pauseBinauralTone()
        
        // Pause TTS - stop current sentence immediately
        if (isPlayingSentences || textToSpeech?.isSpeaking == true) {
            ttsIsPaused = true
            isPlayingSentences = false
            textToSpeech?.stop()
            _currentSentence.value = ""
            Log.d(TAG, "TTS paused at sentence ${currentSentenceIndex + 1} of ${currentSentences.size}")
        }
    }

    private fun resumeSession() {
        Log.d(TAG, "▶️ Resuming session")
        _isPlaying.value = true
        _sessionState.value = UnifiedMeditationSessionState.ACTIVE
        
        // Resume audio
        val settings = _audioSettings.value
        
        // Re-apply volumes in case they changed
        audioManager.setVolume(meditationSettings.getVolume())
        audioManager.setBinauralVolume(meditationSettings.getBinauralVolume())
        audioManager.setTtsVolume(meditationSettings.getTtsVolume())
        
        if (settings.soundEnabled) {
            audioManager.playBackgroundSound(settings.backgroundSound)
        }
        if (settings.binauralEnabled) {
            audioManager.playBinauralTone(settings.binauralTone)
        }
        
        // Resume TTS from current sentence
        if (settings.ttsEnabled) {
            if (ttsIsPaused && currentSentences.isNotEmpty()) {
                Log.d(TAG, "🔄 Resuming TTS from sentence ${currentSentenceIndex + 1}/${currentSentences.size}")
                resumeTtsFromCurrentSentence()
            } else if (currentSentences.isNotEmpty() && currentSentenceIndex < currentSentences.size) {
                // If we have sentences in progress, continue from where we left off
                Log.d(TAG, "🔄 Continuing TTS from sentence ${currentSentenceIndex + 1}/${currentSentences.size}")
                ttsIsPaused = false
                startSentenceBasedTts()
            } else if (_currentStep.value?.guidance?.isNotEmpty() == true) {
                // If no paused TTS but we have a current step, speak it
                Log.d(TAG, "🔄 Starting fresh TTS for current step")
                speakGuidance(_currentStep.value!!.guidance)
            }
        }
        
        // Resume timer
        Log.d(TAG, "🔄 Resuming timer with ${_progress.value.timeRemainingInStep} seconds remaining")
        startTimer()
    }

    fun stopSession() {
        Log.d(TAG, "⏹️ Stopping session")
        _isPlaying.value = false
        _sessionState.value = UnifiedMeditationSessionState.COMPLETED
        timerJob?.cancel()
        
        // Cancel any ongoing AI generation
        generationJob?.cancel()
        remainingStepsJob?.cancel()
        currentInferenceFuture?.cancel(true)
        currentInferenceFuture = null
        Log.d(TAG, "🛑 Cancelled AI generation jobs and inference future")
        
        // Force stop and reset inference model to cancel any ongoing inference
        viewModelScope.launch(Dispatchers.IO) {
            try {
                InferenceModel.forceReset(context)
                // Small delay to ensure cleanup completes
                delay(100)
                // Immediately recreate instance to ensure it's ready for next use
                InferenceModel.getInstance(context)
                Log.d(TAG, "🛑 Force reset and recreated inference model instance")
            } catch (e: Exception) {
                Log.w(TAG, "Could not force reset inference model", e)
            }
        }
        
        // Update generation status
        _generationStatus.value = MeditationGenerationStatus.Idle
        
        audioManager.stopBackgroundSound()
        audioManager.stopBinauralTone()
        textToSpeech?.stop()
        
        // Reset TTS sentence tracking
        isPlayingSentences = false
        ttsIsPaused = false
        currentSentences.clear()
        currentSentenceIndex = 0
        currentTtsText = ""
        _currentSentence.value = ""
        
        // Reset streaming state
        isStreamingActive = false
        hasStreamingStarted = false
        streamingBuffer = ""
        streamingTitle = ""
        streamingGuidance = ""
        
        // Record session completion
        sessionConfig?.let { config ->
            val duration = config.totalDuration
            meditationSettings.recordSessionCompletion(meditationType, duration)
        }
    }

    private fun startTimer() {
        // Only start/restart timer if we're actually playing
        if (!_isPlaying.value) {
            Log.d(TAG, "⏰ Timer start requested but session not playing - skipping")
            return
        }
        
        val currentTime = _progress.value.timeRemainingInStep
        Log.d(TAG, "⏰ Starting timer with ${currentTime}s remaining (was job active: ${timerJob?.isActive})")
        
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_isPlaying.value && _progress.value.timeRemainingInStep > 0) {
                delay(1000)
                if (_isPlaying.value) {
                    val currentProgress = _progress.value
                    val newTimeInStep = (currentProgress.timeRemainingInStep - 1).coerceAtLeast(0)
                    val newTotalTime = (currentProgress.totalTimeRemaining - 1).coerceAtLeast(0)
                    
                    _progress.value = currentProgress.copy(
                        timeRemainingInStep = newTimeInStep,
                        totalTimeRemaining = newTotalTime
                    )
                    
                    // For custom meditations, start generating next step when 60 seconds remaining
                    if (sessionConfig?.isCustomGenerated == true && newTimeInStep == 60) {
                        triggerNextStepGeneration()
                    }
                    
                    // Move to next step only when timer actually reaches 0
                    if (newTimeInStep == 0) {
                        Log.d(TAG, "⏰ Step timer reached 0, moving to next step")
                        moveToNextStep()
                        break // Exit the loop to prevent further execution
                    }
                }
            }
            Log.d(TAG, "⏰ Timer loop ended (playing: ${_isPlaying.value}, timeLeft: ${_progress.value.timeRemainingInStep})")
        }
    }

    private suspend fun moveToNextStep() {
        val nextIndex = currentStepIndex + 1
        val totalSteps = sessionConfig?.totalSteps ?: unifiedSteps.size
        val availableSteps = unifiedSteps.size
        
        Log.d(TAG, "⏰ Moving to next step: $nextIndex/$totalSteps (available: $availableSteps)")
        
        if (nextIndex >= totalSteps) {
            // Session complete
            Log.d(TAG, "🏁 Session completed - all steps finished")
            withContext(Dispatchers.Main) {
                stopSession()
            }
            return
        }
        
        currentStepIndex = nextIndex
        
        if (nextIndex < unifiedSteps.size) {
            // Move to existing step
            val nextStep = unifiedSteps[nextIndex]
            Log.d(TAG, "✅ Moving to existing step ${nextIndex + 1}: '${nextStep.title}' (${nextStep.durationSeconds}s)")
            
            withContext(Dispatchers.Main) {
                _currentStep.value = nextStep
                _progress.value = _progress.value.copy(
                    currentStepIndex = nextIndex,
                    timeRemainingInStep = nextStep.durationSeconds
                )
                
                // Always show the guidance text
                _currentSentence.value = nextStep.guidance
                
                // Restart timer for new step
                startTimer()
                
                // Speak new step guidance
                if (_audioSettings.value.ttsEnabled && _isPlaying.value) {
                    speakGuidance(nextStep.guidance)
                }
            }
        } else {
            // Wait for custom step generation
            Log.d(TAG, "⏳ Waiting for custom step ${nextIndex + 1} to be generated...")
            withContext(Dispatchers.Main) {
                _progress.value = _progress.value.copy(
                    currentStepIndex = nextIndex,
                    isGenerating = true,
                    generationStatus = "Generating step ${nextIndex + 1}...",
                    timeRemainingInStep = sessionConfig?.stepDuration ?: 300
                )
                
                // Don't restart timer yet, wait for step to be ready
            }
        }
    }

    private fun triggerNextStepGeneration() {
        val config = sessionConfig ?: return
        if (!config.isCustomGenerated) return
        
        val nextStepIndex = currentStepIndex + 1
        if (nextStepIndex >= config.totalSteps) {
            // No next step needed - we've reached the end
            return
        }
        
        if (nextStepIndex < unifiedSteps.size) {
            // Step already exists
            Log.d(TAG, "⏭️ Step ${nextStepIndex + 1} already exists, skipping generation")
            return
        }
        
        Log.d(TAG, "🤖 Triggering generation of step ${nextStepIndex + 1} (60s early)")
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prompt = createCustomMeditationPrompt(config, nextStepIndex)
                val inferenceModel = InferenceModel.getInstance(context)
                
                currentInferenceFuture = inferenceModel.generateResponseAsync(prompt) { partial, done ->
                    if (done) {
                        Log.d(TAG, "✅ Step ${nextStepIndex + 1} generated successfully")
                    }
                }
                
                val response = currentInferenceFuture!!.get(30, TimeUnit.SECONDS)
                currentInferenceFuture = null
                val step = parseCustomMeditationStep(response ?: "", nextStepIndex, config.stepDuration)
                
                withContext(Dispatchers.Main) {
                    unifiedSteps.add(step)
                    Log.d(TAG, "✅ Step ${nextStepIndex + 1} ready for playback")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to generate step ${nextStepIndex + 1}", e)
                // Create fallback step
                val fallbackStep = createFallbackUnifiedStep(nextStepIndex, config.stepDuration)
                withContext(Dispatchers.Main) {
                    unifiedSteps.add(fallbackStep)
                    Log.d(TAG, "⚠️ Using fallback for step ${nextStepIndex + 1}")
                }
            }
        }
    }

    private fun generateRemainingSteps() {
        val config = sessionConfig ?: return
        if (!config.isCustomGenerated) return
        
        // Don't start new generation if session is already completed
        if (_sessionState.value == UnifiedMeditationSessionState.COMPLETED) {
            Log.d(TAG, "🛑 Session stopped, not generating remaining steps")
            return
        }
        
        remainingStepsJob = viewModelScope.launch(Dispatchers.IO) {
            for (i in 1 until config.totalSteps) {
                // Check if session was stopped during generation
                if (_sessionState.value == UnifiedMeditationSessionState.COMPLETED) {
                    Log.d(TAG, "🛑 Session stopped during generation, cancelling remaining steps")
                    break
                }
                
                if (i >= unifiedSteps.size) {
                    try {
                        Log.d(TAG, "🤖 Generating step ${i + 1}")
                        val prompt = createCustomMeditationPrompt(config, i)
                        val inferenceModel = InferenceModel.getInstance(context)
                        
                        var result = ""
                        currentInferenceFuture = inferenceModel.generateResponseAsync(prompt) { partial, done ->
                            if (partial != null) {
                                result += partial
                            }
                        }
                        
                        val response = currentInferenceFuture!!.get(12, TimeUnit.SECONDS)
                        currentInferenceFuture = null
                        val step = parseCustomMeditationStep(response ?: result, i, config.stepDuration)
                        
                        withContext(Dispatchers.Main) {
                            unifiedSteps.add(step)
                            
                            // If this is the step we're waiting for
                            if (currentStepIndex == i) {
                                Log.d(TAG, "🎯 Setting up waited step ${i + 1}: '${step.title}'")
                                _currentStep.value = step
                                _progress.value = _progress.value.copy(
                                    currentStepIndex = i,
                                    timeRemainingInStep = step.durationSeconds,
                                    isGenerating = false,
                                    generationStatus = ""
                                )
                                
                                // Only restart timer if we were actually waiting (timer wasn't running)
                                if (timerJob?.isActive != true) {
                                    Log.d(TAG, "🔄 Timer was not active, restarting for new step")
                                    startTimer()
                                } else {
                                    Log.d(TAG, "⏰ Timer still active, letting it continue")
                                }
                                
                                if (_audioSettings.value.ttsEnabled && _isPlaying.value) {
                                    speakGuidance(step.guidance)
                                }
                            }
                            
                            Log.d(TAG, "✅ Generated step ${i + 1}/${config.totalSteps}: '${step.title}' (${step.durationSeconds}s)")
                            
                            // Check if all steps are now generated
                            if (unifiedSteps.size == config.totalSteps) {
                                _isFullyGenerated.value = true
                                Log.d(TAG, "🎉 All ${config.totalSteps} steps fully generated!")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to generate step ${i + 1}", e)
                        currentInferenceFuture = null
                    }
                }
            }
        }
    }

    private fun createFallbackUnifiedStep(stepIndex: Int, duration: Int): UnifiedMeditationStep {
        val fallbackSteps = listOf(
            "Take a moment to notice your breath and relax into this peaceful practice.",
            "Let your awareness expand and feel the calm settling in around you.",
            "Continue to breathe naturally and allow your mind to become quiet and still.",
            "Rest in this sense of peace and know that you are exactly where you need to be.",
            "Feel grateful for this time you've given yourself for inner peace and reflection.",
            "Prepare to complete this meditation feeling refreshed and centered."
        )
        
        val guidance = fallbackSteps.getOrNull(stepIndex) ?: fallbackSteps[0]
        
        return object : UnifiedMeditationStep {
            override val title: String = "Mindful Moment ${stepIndex + 1}"
            override val guidance: String = guidance
            override val durationSeconds: Int = duration
            override val description: String? = "Peaceful meditation practice"
            override val stepIndex: Int = stepIndex
            override val isCustomGenerated: Boolean = true
        }
    }

    fun retryGeneration() {
        val config = sessionConfig ?: return
        if (!config.isCustomGenerated) return
        
        Log.d(TAG, "🔄 Retrying generation")
        unifiedSteps.clear()
        currentStepIndex = 0
        
        // Reset state immediately
        _generationStatus.value = MeditationGenerationStatus.Starting
        _sessionState.value = UnifiedMeditationSessionState.PREPARING
        
        // Start generation in background
        generationJob = viewModelScope.launch(Dispatchers.IO) {
            generateFirstStepAsync(config)
        }
    }

    // Save functionality
    fun showSaveDialog() {
        _showSaveDialog.value = true
    }

    fun hideSaveDialog() {
        _showSaveDialog.value = false
    }

    fun saveAsExactSession(name: String, description: String) {
        val config = sessionConfig ?: return
        if (!config.isCustomGenerated || unifiedSteps.isEmpty()) return

        val savedSteps = unifiedSteps.map { step ->
            SavedMeditationStep(
                title = step.title,
                guidance = step.guidance,
                durationSeconds = step.durationSeconds,
                stepIndex = step.stepIndex
            )
        }

        val savedMeditation = SavedMeditation(
            id = "saved_${System.currentTimeMillis()}",
            name = name,
            description = description,
            totalDuration = config.totalDuration,
            totalSteps = config.totalSteps,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            saveType = SavedMeditationType.EXACT_SESSION,
            savedSteps = savedSteps,
            config = null
        )

        saveMeditationToStorage(savedMeditation)
        _showSaveDialog.value = false
        Log.d(TAG, "💾 Saved exact session: $name")
    }

    fun saveAsTemplate(name: String, description: String) {
        val config = sessionConfig ?: return
        if (!config.isCustomGenerated) return

        val savedConfig = SavedMeditationConfig(
            focus = config.focus,
            mood = config.mood,
            experience = config.experience,
            totalDuration = config.totalDuration,
            totalSteps = config.totalSteps
        )

        val savedMeditation = SavedMeditation(
            id = "saved_${System.currentTimeMillis()}",
            name = name,
            description = description,
            totalDuration = config.totalDuration,
            totalSteps = config.totalSteps,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            saveType = SavedMeditationType.CONFIG_TEMPLATE,
            savedSteps = null,
            config = savedConfig
        )

        saveMeditationToStorage(savedMeditation)
        _showSaveDialog.value = false
        Log.d(TAG, "💾 Saved template: $name")
    }

    private fun saveMeditationToStorage(meditation: SavedMeditation) {
        val prefs = context.getSharedPreferences("saved_meditations", android.content.Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Save basic info
        editor.putString("${meditation.id}_name", meditation.name)
        editor.putString("${meditation.id}_description", meditation.description)
        editor.putInt("${meditation.id}_duration", meditation.totalDuration)
        editor.putInt("${meditation.id}_steps", meditation.totalSteps)
        editor.putLong("${meditation.id}_created", meditation.createdAt)
        editor.putLong("${meditation.id}_used", meditation.lastUsedAt)
        editor.putString("${meditation.id}_type", meditation.saveType.name)
        
        when (meditation.saveType) {
            SavedMeditationType.EXACT_SESSION -> {
                meditation.savedSteps?.forEachIndexed { index, step ->
                    editor.putString("${meditation.id}_step_${index}_title", step.title)
                    editor.putString("${meditation.id}_step_${index}_guidance", step.guidance)
                    editor.putInt("${meditation.id}_step_${index}_duration", step.durationSeconds)
                }
            }
            SavedMeditationType.CONFIG_TEMPLATE -> {
                meditation.config?.let { config ->
                    editor.putString("${meditation.id}_config_focus", config.focus)
                    editor.putString("${meditation.id}_config_mood", config.mood)
                    editor.putString("${meditation.id}_config_experience", config.experience)
                }
            }
        }
        
        // Add to saved list
        val savedIds = prefs.getStringSet("saved_meditation_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        savedIds.add(meditation.id)
        editor.putStringSet("saved_meditation_ids", savedIds)
        
        editor.apply()
    }

    private fun loadSavedMeditation(meditationId: String): SavedMeditation? {
        val prefs = context.getSharedPreferences("saved_meditations", android.content.Context.MODE_PRIVATE)
        
        return try {
            val name = prefs.getString("${meditationId}_name", null) ?: return null
            val description = prefs.getString("${meditationId}_description", "") ?: ""
            val duration = prefs.getInt("${meditationId}_duration", 0)
            val steps = prefs.getInt("${meditationId}_steps", 0)
            val created = prefs.getLong("${meditationId}_created", 0)
            val used = prefs.getLong("${meditationId}_used", 0)
            val typeString = prefs.getString("${meditationId}_type", null) ?: return null
            val saveType = SavedMeditationType.valueOf(typeString)
            
            SavedMeditation(
                id = meditationId,
                name = name,
                description = description,
                totalDuration = duration,
                totalSteps = steps,
                createdAt = created,
                lastUsedAt = used,
                saveType = saveType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved meditation $meditationId", e)
            null
        }
    }

    private fun loadSavedMeditationSteps(meditationId: String): List<SavedMeditationStep> {
        val prefs = context.getSharedPreferences("saved_meditations", android.content.Context.MODE_PRIVATE)
        val steps = prefs.getInt("${meditationId}_steps", 0)
        
        return (0 until steps).mapNotNull { index ->
            try {
                val title = prefs.getString("${meditationId}_step_${index}_title", null) ?: return@mapNotNull null
                val guidance = prefs.getString("${meditationId}_step_${index}_guidance", null) ?: return@mapNotNull null
                val duration = prefs.getInt("${meditationId}_step_${index}_duration", 0)
                
                SavedMeditationStep(
                    title = title,
                    guidance = guidance,
                    durationSeconds = duration,
                    stepIndex = index
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading saved meditation step $index for $meditationId", e)
                null
            }
        }
    }

    private fun loadSavedMeditationConfig(meditationId: String): SavedMeditationConfig? {
        val prefs = context.getSharedPreferences("saved_meditations", android.content.Context.MODE_PRIVATE)
        
        return try {
            val focus = prefs.getString("${meditationId}_config_focus", null) ?: return null
            val mood = prefs.getString("${meditationId}_config_mood", null) ?: return null
            val experience = prefs.getString("${meditationId}_config_experience", null) ?: return null
            val duration = prefs.getInt("${meditationId}_duration", 0)
            val steps = prefs.getInt("${meditationId}_steps", 0)
            
            SavedMeditationConfig(
                focus = focus,
                mood = mood,
                experience = experience,
                totalDuration = duration,
                totalSteps = steps
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved meditation config for $meditationId", e)
            null
        }
    }

    // Audio control methods
    fun toggleSound() {
        val newSettings = _audioSettings.value.copy(soundEnabled = !_audioSettings.value.soundEnabled)
        _audioSettings.value = newSettings
        meditationSettings.setSoundEnabled(newSettings.soundEnabled)
        
        if (newSettings.soundEnabled && _isPlaying.value) {
            audioManager.playBackgroundSound(newSettings.backgroundSound)
        } else {
            audioManager.stopBackgroundSound()
        }
    }

    fun toggleBinaural() {
        val newSettings = _audioSettings.value.copy(binauralEnabled = !_audioSettings.value.binauralEnabled)
        _audioSettings.value = newSettings
        meditationSettings.setBinauralEnabled(newSettings.binauralEnabled)
        
        if (newSettings.binauralEnabled && _isPlaying.value) {
            audioManager.playBinauralTone(newSettings.binauralTone)
        } else {
            audioManager.stopBinauralTone()
        }
    }

    fun toggleTts() {
        val newSettings = _audioSettings.value.copy(ttsEnabled = !_audioSettings.value.ttsEnabled)
        _audioSettings.value = newSettings
        meditationSettings.setTtsEnabled(newSettings.ttsEnabled)
        
        if (!newSettings.ttsEnabled) {
            textToSpeech?.stop()
        }
        
        // Always show the guidance text regardless of TTS settings
        _currentStep.value?.let { step ->
            _currentSentence.value = step.guidance
        }
    }

    fun setBackgroundSound(sound: BackgroundSound) {
        val newSettings = _audioSettings.value.copy(backgroundSound = sound)
        _audioSettings.value = newSettings
        meditationSettings.setBackgroundSound(sound)
        
        if (newSettings.soundEnabled && _isPlaying.value) {
            audioManager.playBackgroundSound(sound)
        }
    }

    fun setBinauralTone(tone: BinauralTone) {
        val newSettings = _audioSettings.value.copy(binauralTone = tone)
        _audioSettings.value = newSettings
        meditationSettings.setBinauralTone(tone)
        
        if (newSettings.binauralEnabled && _isPlaying.value) {
            audioManager.playBinauralTone(tone)
        }
    }

    fun setBackgroundVolume(volume: Float) {
        meditationSettings.setVolume(volume)
        audioManager.setVolume(volume)
    }

    fun setBinauralVolume(volume: Float) {
        meditationSettings.setBinauralVolume(volume)
        audioManager.setBinauralVolume(volume)
    }

    fun setTtsVolume(volume: Float) {
        meditationSettings.setTtsVolume(volume)
        audioManager.setTtsVolume(volume)
    }

    fun setTtsSpeed(speed: Float) {
        Log.d(TAG, "🔧 Setting TTS speed to ${speed} (playing: ${_isPlaying.value})")
        meditationSettings.setTtsSpeed(speed)
        textToSpeech?.setSpeechRate(speed)
    }

    fun setTtsPitch(pitch: Float) {
        Log.d(TAG, "🔧 Setting TTS pitch to ${pitch} (playing: ${_isPlaying.value})")
        meditationSettings.setTtsPitch(pitch)
        textToSpeech?.setPitch(pitch)
    }

    fun setTtsVoice(voiceName: String) {
        meditationSettings.setTtsVoice(voiceName)
        textToSpeech?.let { tts ->
            tts.voices?.find { it.name == voiceName }?.let { voice ->
                tts.voice = voice
                Log.d(TAG, "TTS voice changed to ${voice.name}")
            }
        }
    }

    // Helper methods
    private suspend fun loadAudioSettings() {
        withContext(Dispatchers.Main) {
            _audioSettings.value = AudioSettings(
                soundEnabled = meditationSettings.isSoundEnabled(),
                backgroundSound = meditationSettings.getBackgroundSound(),
                binauralEnabled = meditationSettings.isBinauralEnabled(),
                binauralTone = meditationSettings.getBinauralTone(),
                ttsEnabled = meditationSettings.isTtsEnabled()
            )
        }
    }

    private suspend fun initializeTTS() {
        withContext(Dispatchers.Main) {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.let { tts ->
                        // Set language
                        val result = tts.setLanguage(Locale.getDefault())
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e(TAG, "Language not supported for TTS")
                            tts.setLanguage(Locale.ENGLISH)
                        }

                        // Apply saved TTS settings
                        tts.setPitch(meditationSettings.getTtsPitch())
                        tts.setSpeechRate(meditationSettings.getTtsSpeed())

                        // Apply saved voice if available
                        val savedVoice = meditationSettings.getTtsVoice()
                        if (savedVoice.isNotEmpty()) {
                            tts.voices?.find { it.name == savedVoice }?.let { voice ->
                                tts.voice = voice
                                Log.d(TAG, "Applied saved voice: ${voice.name}")
                            }
                        }

                        // Set up utterance listener for sentence-by-sentence playback
                        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                Log.d(TAG, "TTS started sentence: $utteranceId")
                            }

                            override fun onDone(utteranceId: String?) {
                                Log.d(TAG, "TTS completed sentence: $utteranceId")
                                // Move to next sentence if we're playing and not paused
                                if (isPlayingSentences && !ttsIsPaused && _isPlaying.value) {
                                    playNextSentence()
                                }
                            }

                            override fun onError(utteranceId: String?) {
                                Log.e(TAG, "TTS error: $utteranceId")
                                // Try next sentence on error
                                if (isPlayingSentences && !ttsIsPaused && _isPlaying.value) {
                                    playNextSentence()
                                }
                            }
                        })

                        isTtsReady = true
                        Log.d(TAG, "TTS initialized successfully with voice: ${tts.voice?.name}")
                    }
                } else {
                    Log.e(TAG, "TTS initialization failed")
                }
            }
        }
    }

    private fun speakGuidance(text: String) {
        if (_audioSettings.value.ttsEnabled && isTtsReady && text.isNotEmpty()) {
            // Clean and prepare text
            val cleanText = text.replace(Regex("\\*+"), "")
                .replace(Regex("#+"), "")
                .replace("\\n", " ")
                .trim()
            
            // Split into sentences
            currentSentences.clear()
            currentSentences.addAll(splitIntoSentences(cleanText))
            currentSentenceIndex = 0
            currentTtsText = cleanText
            ttsIsPaused = false
            
            Log.d(TAG, "Starting sentence-by-sentence TTS: ${currentSentences.size} sentences")
            
            // Start speaking from first sentence
            startSentenceBasedTts()
        }
    }
    
    private fun splitIntoSentences(text: String): List<String> {
        // Split on sentence endings, but keep the punctuation
        return text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    
    private fun startSentenceBasedTts() {
        if (currentSentences.isNotEmpty() && currentSentenceIndex < currentSentences.size) {
            isPlayingSentences = true
            playCurrentSentence()
        }
    }
    
    private fun playCurrentSentence() {
        if (currentSentenceIndex < currentSentences.size && _audioSettings.value.ttsEnabled && isTtsReady) {
            textToSpeech?.let { tts ->
                val sentence = currentSentences[currentSentenceIndex]
                
                // Update current sentence for UI display (original text for reading)
                _currentSentence.value = sentence
                
                // Clean sentence for TTS (remove special characters and emojis)
                val cleanedSentence = cleanTextForTTS(sentence)
                
                // Apply current TTS settings before speaking
                tts.setPitch(meditationSettings.getTtsPitch())
                tts.setSpeechRate(meditationSettings.getTtsSpeed())
                
                // Apply saved voice if available
                val savedVoice = meditationSettings.getTtsVoice()
                if (savedVoice.isNotEmpty()) {
                    tts.voices?.find { it.name == savedVoice }?.let { voice ->
                        tts.voice = voice
                    }
                }
                
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, meditationSettings.getTtsVolume())
                }
                
                val utteranceId = "${ttsUtteranceId}_${currentSentenceIndex}"
                tts.speak(cleanedSentence, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                
                Log.d(TAG, "Speaking sentence ${currentSentenceIndex + 1}/${currentSentences.size}: ${sentence.take(30)}...")
            }
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

    private fun playNextSentence() {
        currentSentenceIndex++
        if (currentSentenceIndex < currentSentences.size && isPlayingSentences && !ttsIsPaused) {
            // Small delay between sentences for natural flow
            viewModelScope.launch {
                delay(800) // 800ms pause between sentences
                if (isPlayingSentences && !ttsIsPaused && _isPlaying.value) {
                    playCurrentSentence()
                }
            }
        } else if (isStreamingActive) {
            // If streaming is still active, wait for more sentences
            Log.d(TAG, "Waiting for more sentences from stream...")
            isPlayingSentences = false
            // Will be restarted when new sentences arrive
        } else {
            // All sentences completed
            isPlayingSentences = false
            Log.d(TAG, "All sentences completed")
        }
    }
    
    private fun resumeTtsFromCurrentSentence() {
        if (currentSentences.isNotEmpty() && currentSentenceIndex < currentSentences.size) {
            ttsIsPaused = false
            Log.d(TAG, "Resuming from sentence ${currentSentenceIndex + 1}/${currentSentences.size}")
            startSentenceBasedTts()
        }
    }


    private fun loadCustomMeditationConfig(): UnifiedMeditationConfig? {
        return try {
            // Check if this is the new inline format: custom:focus|mood|experience|duration
            if (meditationType.startsWith("custom:")) {
                val parts = meditationType.substringAfter("custom:").split("|")
                if (parts.size >= 3) {
                    val focus = parts[0]
                    val mood = parts[1]
                    val moodContextOrExperience = parts[2]
                    val durationMinutes = parts.getOrNull(3)?.toIntOrNull() ?: 10
                    
                    // Decode URL-encoded mood context if it's a mood-guided meditation
                    val (experience, moodContext) = if (focus == "mood-guided wellness") {
                        val decodedContext = try {
                            java.net.URLDecoder.decode(moodContextOrExperience, "UTF-8")
                        } catch (e: Exception) {
                            moodContextOrExperience
                        }
                        Pair("Beginner", decodedContext)
                    } else {
                        Pair(moodContextOrExperience, "")
                    }
                    
                    Log.d(TAG, "🎯 Parsing inline custom meditation: focus='$focus', mood='$mood', experience='$experience', duration=${durationMinutes}min")
                    if (moodContext.isNotEmpty()) {
                        Log.d(TAG, "📝 Mood context: ${moodContext.take(100)}...")
                    }
                    
                    return UnifiedMeditationConfig(
                        sessionId = meditationType,
                        meditationType = meditationType,
                        totalDuration = durationMinutes,
                        totalSteps = 3, // Default to 3 steps for mood-based meditations
                        isCustomGenerated = true,
                        focus = focus,
                        mood = mood,
                        experience = experience,
                        stepDuration = (durationMinutes * 60) / 3, // Divide duration evenly
                        moodContext = moodContext
                    )
                }
            }
            
            // Fallback to old SharedPreferences format
            val prefs = context.getSharedPreferences("custom_meditations", Context.MODE_PRIVATE)
            
            val duration = prefs.getInt("${meditationType}_duration", 15)
            val totalSteps = prefs.getInt("${meditationType}_total_steps", 4)
            val stepDuration = prefs.getInt("${meditationType}_step_duration", 225)
            val focus = prefs.getString("${meditationType}_focus", "") ?: ""
            val mood = prefs.getString("${meditationType}_mood", "") ?: ""
            val experience = prefs.getString("${meditationType}_experience", "Beginner") ?: "Beginner"
            
            if (focus.isEmpty()) {
                Log.w(TAG, "Custom meditation config missing focus")
                return null
            }
            
            UnifiedMeditationConfig(
                sessionId = meditationType,
                meditationType = meditationType,
                totalDuration = duration,
                totalSteps = totalSteps,
                isCustomGenerated = true,
                focus = focus,
                mood = mood,
                experience = experience,
                stepDuration = stepDuration,
                moodContext = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load custom meditation config", e)
            null
        }
    }

    private fun getMeditationSteps(type: String): List<MeditationStep> {
        Log.d(TAG, "🎯 Loading meditation steps for type: '$type'")
        
        // Simple system: define total duration and auto-distribute steps
        val (totalMinutes, stepCount) = when (type) {
            "stress_relief" -> 5 to 3
            "focus_boost" -> 8 to 3
            "sleep_prep" -> 10 to 3
            "anxiety_ease" -> 7 to 3
            "deep_relaxation" -> 20 to 4
            "mindful_awareness" -> 15 to 4
            "extended_focus" -> 30 to 5
            "complete_zen" -> 45 to 6
            else -> 5 to 3
        }
        
        val totalSeconds = totalMinutes * 60
        val stepDuration = totalSeconds / stepCount
        
        return when (type) {
            "stress_relief" -> listOf(
                MeditationStep("Welcome", "Beginning stress relief meditation",
                    "Welcome to your stress relief meditation. Begin by finding a quiet, comfortable position—whether you're sitting or lying down. Allow your body to settle and your hands to rest easily. Gently close your eyes. Bring your attention inward as you begin to breathe deeply. Inhale slowly through your nose, feeling your lungs fill up completely. Pause for a moment at the top of the breath, and then exhale gently through your mouth, releasing any tension. Let each breath invite a deeper sense of relaxation and presence.", stepDuration),

                MeditationStep("Body Scan", "Notice areas of tension",
                    "Bring your awareness to the top of your head. Slowly begin to scan downward, part by part—your scalp, forehead, eyes, jaw. Notice any sensations of tightness or holding. With each breath, gently invite those areas to soften. Continue down your neck and shoulders. Let them drop away from your ears. Move your awareness through your arms, chest, abdomen, and lower back. Finally, scan your legs all the way to your toes. There's no need to change anything—just be present with what is. Observe with a sense of gentle curiosity and kindness.", stepDuration),

                MeditationStep("Breathing Focus", "Focus on your natural breath",
                    "Now bring your attention to your breath. Feel the natural rise and fall of your abdomen or chest. Begin to silently count each exhale: one... two... three... up to ten. If your mind wanders, that's okay—simply notice it without judgment and return to the breath, beginning again at one. Let the counting anchor you in this moment. Notice the calming rhythm of your breathing and allow it to become your center. This simple awareness of breath can bring clarity, stillness, and relief.", stepDuration)
            )

            
            "focus_boost" -> listOf(
                MeditationStep("Focus Preparation", "Preparing your mind for clarity",
                    "Sit upright in a position that feels both stable and relaxed. Imagine a gentle thread lifting you from the crown of your head. Close your eyes and take three deep, cleansing breaths—inhaling through your nose and exhaling through your mouth. Let go of the tension with each exhale. Feel the shift as you transition from activity to stillness. With each breath, become more present and awake. Set an intention to be fully here, ready to cultivate mental clarity and focus.", stepDuration),

                MeditationStep("Mindful Awareness", "Cultivate present-moment attention",
                    "Now bring your attention to your breath. Focus on the subtle sensations at the tip of your nose or the rhythm in your chest. Feel the coolness of each inhale, the warmth of each exhale. Each breath is unique. When thoughts arise—and they will—acknowledge them gently and guide your focus back to the breath. This returning is the heart of mindfulness. It’s not about perfect stillness—it’s about practicing presence, over and over again.", stepDuration),

                MeditationStep("Mental Clarity", "Strengthen concentration",
                    "Now imagine a bright, clear light at the center of your forehead, between your eyebrows. This light represents your inner awareness and mental clarity. With each breath, allow the light to grow a little brighter, a little steadier. Let it fill your mind and illuminate your focus. As thoughts or distractions appear, simply return to this image. Breathe deeply, allowing this radiant clarity to strengthen and settle. Rest in this space of focused presence.", stepDuration)
            )
            
            "sleep_prep" -> listOf(
                MeditationStep("Evening Preparation", "Preparing for restful sleep",
                    "Lie down comfortably, allowing your body to be fully supported by the surface beneath you. Adjust any pillows or blankets to feel as cozy as possible. Gently close your eyes and bring your awareness to the sensation of your body resting. Begin to breathe slowly and softly. With each exhale, imagine releasing the weight of the day—letting go of any thoughts, worries, or physical tension. Allow yourself to fully arrive in this moment of rest and care.", stepDuration),

                MeditationStep("Progressive Relaxation", "Releasing physical tension",
                    "Starting at your toes, gently bring your attention to each part of your body. Wiggle or tense your toes, then allow them to relax completely. Move slowly upward—your ankles, calves, knees, and thighs—softening each area as you go. Let your pelvis and hips release, your belly soften, and your chest expand freely with breath. Feel your shoulders drop away from your ears. Relax your arms, hands, neck, and jaw. Soften your eyes and forehead. Feel your entire body melting into comfort and ease.", stepDuration),

                MeditationStep("Peaceful Drift", "Settling into sleep",
                    "Let your breathing become slow and gentle. Feel your body completely at rest. Allow your thoughts to drift like leaves on a stream—coming and going without needing to follow them. Invite peace to fill your awareness. With each breath, feel yourself sinking deeper into stillness. Let the boundary between wakefulness and sleep begin to blur. Trust this process. You are safe, supported, and gently drifting into the comfort of sleep.", stepDuration)
            )

            "anxiety_ease" -> listOf(
                MeditationStep(
                    "Calming Arrival",
                    "Finding your safe space",
                    """
        Settle into a comfortable position. Allow your body to be supported by the surface beneath you. 
        Gently close your eyes. Bring your attention inward. 
        Place one hand on your chest and one on your belly. Feel the rise and fall of each breath.
        Notice the rhythm, the movement, the warmth of your body. 
        With each breath, allow tension to melt away. Let yourself arrive fully in this moment.
        This is your safe space. Nothing else matters right now.
        """.trimIndent(),
                    stepDuration
                ),
                MeditationStep(
                    "Soothing Breath",
                    "Calming the nervous system",
                    """
                        Begin to focus on your breath. Inhale deeply through your nose for a count of four… 
                        1… 2… 3… 4. Hold that breath gently for four… 1… 2… 3… 4. 
                        Now exhale slowly and fully through your mouth for a count of six… 1… 2… 3… 4… 5… 6.
                        
                        As you continue this pattern, notice your body softening. Each exhale signals your nervous system that it’s safe to relax.
                        Allow your shoulders to drop, your jaw to unclench, your thoughts to slow. 
                        Feel your whole system shifting into calm and balance.
                        """.trimIndent(),
                                    stepDuration
                                ),
                                MeditationStep(
                                    "Peaceful Grounding",
                                    "Returning to calm",
                                    """
                        Bring your awareness to your environment or imagination. 
                        Notice five things you can feel: the floor beneath you, the fabric against your skin, the air on your face… 
                        Four things you can hear: perhaps your breath, a soft hum, the sound of silence… 
                        Three things you can see in your mind’s eye: colors, patterns, or familiar peaceful places.
                
                        Each layer of awareness gently anchors you to the present moment.
                        With every breath, feel yourself becoming more grounded, rooted like a tree, steady and calm.
                        You are safe. You are present. You are at peace.
                        """.trimIndent(),
                                    stepDuration
                                )
                            )

            "deep_relaxation" -> listOf(
                MeditationStep(
                    "Settling Deep",
                    "Beginning profound relaxation",
                    """
                        Find a position where your body feels completely supported—whether lying down or reclining. 
                        Let your arms and legs relax. Allow the weight of your body to sink gently into the surface beneath you.
                        
                        Begin to take slower, deeper breaths. With each inhale, imagine drawing in calm and stillness.
                        With each exhale, release any tension. let it melt away from your shoulders, your face, your chest.
                        Soften into this stillness. Let this moment be just for you.
                        """.trimIndent(),
                                    stepDuration
                ),
                MeditationStep(
                    "Full Body Release",
                    "Complete physical relaxation",
                    """
                        Bring awareness to the top of your head. Gently invite it to relax. 
                        Slowly scan downward—your forehead, eyes, jaw, neck. Let go of any tension as you pass each area.
                
                        Move to your shoulders, arms, hands… then your chest, stomach, hips. 
                        Keep going—down through your legs, all the way to your toes. 
                        Imagine a wave of warmth flowing through you, releasing every tight spot.
                
                        Notice the shift between holding and letting go. Allow your whole body to surrender into peace.
                        """.trimIndent(),
                    stepDuration
                ),
                MeditationStep(
                    "Mental Stillness",
                    "Deep inner calm",
                    """
                        As your body rests fully, turn now to your mind. 
                        Imagine your thoughts as leaves floating gently on a stream—observe them without following.
                
                        With each breath, allow your mind to quiet, like a still pond with no ripples.
                        There is nothing to do, nothing to fix, nothing to change.
                
                        Just be here in this space of calm awareness. Let the silence within you grow.
                        You are deeply relaxed. You are safe. You are whole.
                        """.trimIndent(),
                    stepDuration
                ),
                MeditationStep(
                    "Integration",
                    "Absorbing deep peace",
                    """
                        Stay in this restful state, allowing the deep peace you've created to settle into your entire being.
                        Feel it in your muscles, your breath, your heartbeat. Imagine each cell of your body being nourished by stillness.
                
                        There’s no rush. Let this experience soak in and become a part of you.
                        When you're ready, begin to wiggle your fingers and toes. Gently bring your awareness back to the room.
                
                        Carry this sense of ease with you as you return. Know that this calm is always available to you.
                        """.trimIndent(),
                    stepDuration
                )
            )


            "mindful_awareness" -> listOf(
                MeditationStep("Present Moment", "Arriving in awareness",
                "Close your eyes and take three deep, conscious breaths. With each exhale, let go of any distractions and feel yourself arriving in this moment. Notice the sensation of your breath, the weight of your body, the sounds around you. Remind yourself that you don’t need to be anywhere else. This moment is enough. Feel the aliveness in your body—the gentle pulse of life. Presence begins now, with awareness of this simple, vivid experience.", stepDuration),

                MeditationStep("Watching Thoughts", "Mindful observation",
                    "Begin to notice the stream of thoughts in your mind. Watch them arise—memories, plans, judgments—and let them pass like clouds in the sky. Don’t try to push them away. Don’t follow them. Just observe, without getting caught. See how each thought has a beginning, a middle, and an end. Beneath this stream is the quiet space of awareness. The more you watch without reacting, the more peace you uncover. You are not your thoughts—you are the one who observes them.", stepDuration),

                MeditationStep("Body Awareness", "Sensing the present",
                    "Now bring your attention to your body. Notice the sensations of pressure, warmth, or tingling. Feel the breath moving in your chest or abdomen. Sense the energy or stillness within. If your mind drifts, gently return to these sensations—they are your anchor to the now. Stay connected to your body as a home base of presence. Let this awareness deepen. You are here, alive, grounded in your body. Let this sensing be enough.", stepDuration),
            )
            
            "extended_focus" -> listOf(
                MeditationStep("Focus Foundation", "Building concentration",
                    "Sit comfortably with your spine tall and relaxed. Let your hands rest easily in your lap. Close your eyes and bring your attention to the breath—specifically, the point where the air enters and leaves your nostrils. Choose this spot as your anchor. Whenever your mind wanders, gently return to this anchor without judgment. This simple act of returning is how focus is strengthened. Build the foundation of your concentration with kindness and consistency.", stepDuration),

                MeditationStep("Sustained Attention", "Deepening concentration",
                    "Continue to rest your awareness on the breath. Watch each inhale and each exhale, with steady interest. When thoughts or emotions arise, note them silently—'thinking', 'planning', 'remembering'—and return to the breath. No need to analyze or resist. Just gently come back. This repeated return trains the mind, like strengthening a muscle. Let the breath be your home, your point of stillness in the flow of thoughts.", stepDuration),

                MeditationStep("Stable Focus", "Strengthening attention",
                    "Now, begin to notice the growing stability in your attention. Maybe you can stay with the breath for longer stretches. The distractions may still arise, but they pass more quickly. Your awareness feels clearer. Continue to rest with the breath as a steady friend. Each time you notice a shift, stay with the experience of returning. Concentration doesn't mean forcing—it means allowing the mind to settle naturally, again and again.", stepDuration),

                MeditationStep("Deep Concentration", "Absorbed focus",
                    "Let yourself become fully immersed in the breath. Let it fill your awareness completely. There is no room for distraction—only breath and presence. Time slows. Thought fades. You may begin to experience a subtle joy in this simplicity. This is not about perfection—it's about being here, fully absorbed in one thing. Let your attention rest in this pure, focused awareness.", stepDuration),

                MeditationStep("Effortless Focus", "Natural concentration",
                    "Notice how your concentration has evolved. You're no longer trying to focus—it’s simply happening. Your breath and awareness are one. Let the effort dissolve into natural presence. You are resting in clarity and alertness. Stay in this spacious focus, letting it nourish your mind. This effortless awareness is available anytime you return to the breath.", stepDuration)
            )
            
            "complete_zen" -> listOf(
                MeditationStep("Zen Posture", "Establishing perfect balance",
                    "Sit in a position that is grounded yet light—spine erect, hands resting gently. Imagine a string gently pulling the top of your head upward. Your chin is slightly tucked, jaw relaxed. Feel your body stable, symmetrical, and still. Let the posture itself bring awareness. In Zen, posture and presence are not separate. Be fully in your body, in this exact moment.", stepDuration),

                MeditationStep("Just Sitting", "Pure meditation practice",
                    "Let go of all techniques. Simply sit. Don’t seek to gain or attain. Just be with what is, exactly as it is. You may notice the breath, or simply the raw sense of sitting. Let go of control. Let go of effort. Just sit. If thoughts come, don’t resist or chase them. Let them come and go, like wind through an open window. There is nowhere to get to—this is it.", stepDuration),

                MeditationStep("Witnessing Mind", "Observing without attachment",
                    "Allow yourself to become the silent witness. Notice whatever arises—sensations, sounds, feelings, thoughts—but remain unmoved. You are not what appears. You are the space in which it appears. Don’t cling or push away. Just observe, effortlessly. The mind becomes quiet when it is not resisted. Awareness remains untouched, like the sky watching clouds.", stepDuration),

                MeditationStep("Empty Presence", "Resting in spaciousness",
                    "Now feel into the vast spaciousness of being. There is no center, no edges—just pure awareness. There is nothing to solve or fix. Just be empty, open, and present. This is not emptiness in the negative sense—but the fullness of no resistance. Feel the quiet aliveness of this moment. Let go into the simplicity of pure presence.", stepDuration),

                MeditationStep("Buddha Nature", "Recognizing your true nature",
                    "Recognize that this peaceful awareness—this silent presence—is your original nature. It has always been here. You are not your thoughts, roles, or emotions. Beneath all appearances, your true nature is luminous, awake, and free. There is nothing to add or remove. Rest in this knowing, and let it shine quietly through your whole being.", stepDuration),

                MeditationStep("Integration", "Carrying zen into life",
                    "As you prepare to close this session, remember that Zen is not separate from life. This peaceful awareness goes with you into each action, each breath. Bring this stillness to a conversation, a walk, or a simple task. There is no boundary between meditation and life. Let your true nature express itself gently in everything you do.", stepDuration)
            )
            
            "mindfulness", "basic", "breathing" -> listOf(
                MeditationStep("Mindful Beginning", "Starting your practice",
                    "Settle into a comfortable position. Close your eyes gently. Begin to notice your natural breathing without trying to change it.", 60),
                MeditationStep("Present Awareness", "Cultivating mindfulness", 
                    "Focus on your breath as it flows in and out. When thoughts arise, acknowledge them kindly and return to your breath. This is the practice of mindfulness.", 240),
                MeditationStep("Peaceful Closing", "Completing your session",
                    "Take three deep breaths. Slowly wiggle your fingers and toes. When you're ready, gently open your eyes. Carry this sense of calm with you.", 60)
            )
            
            else -> {
                Log.w(TAG, "⚠️ Unknown meditation type '$type', using basic fallback")
                listOf(
                    MeditationStep("Basic Meditation", "Simple mindfulness practice", 
                        "Focus gently on your breath. Each time your mind wanders, kindly return your attention to the breath. This is the practice of meditation - returning again and again with patience.", 300)
                )
            }
        }.also { steps ->
            Log.d(TAG, "✅ Loaded ${steps.size} steps for '$type': ${steps.map { it.title }} (total: ${steps.sumOf { it.durationSeconds / 60 }}min)")
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        
        // Cancel any ongoing AI generation
        generationJob?.cancel()
        remainingStepsJob?.cancel()
        currentInferenceFuture?.cancel(true)
        currentInferenceFuture = null
        Log.d(TAG, "🛑 Cancelled AI generation jobs and inference future on cleanup")
        
        // Force stop and reset inference model to cancel any ongoing inference
        try {
            InferenceModel.forceReset(context)
            Log.d(TAG, "🛑 Force reset inference model instance on cleanup")
        } catch (e: Exception) {
            Log.w(TAG, "Could not force reset inference model on cleanup", e)
        }
        
        textToSpeech?.shutdown()
        audioManager.release()
    }

    companion object {
        fun getFactory(context: Context, meditationType: String): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(UnifiedMeditationSessionViewModel::class.java)) {
                        return UnifiedMeditationSessionViewModel(context, meditationType) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
}