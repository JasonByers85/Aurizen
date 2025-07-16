package com.aurizen.viewmodels

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurizen.prompts.PromptBuilder
import com.aurizen.prompts.PromptContext
import com.aurizen.prompts.PromptType
import com.aurizen.core.InferenceModel
import com.aurizen.settings.MeditationSettings
import com.aurizen.utils.MeditationAudioManager
import com.aurizen.data.UnifiedMeditationSessionState
import com.aurizen.data.UnifiedMeditationProgress
import com.aurizen.data.UnifiedMeditationConfig
import com.aurizen.data.StreamingMeditationContent
import com.aurizen.data.MeditationGenerationStatus
import com.aurizen.data.SavedMeditation
import com.aurizen.data.SavedMeditationStep
import com.aurizen.data.SavedMeditationConfig
import com.aurizen.data.SavedMeditationType
import com.aurizen.data.UnifiedMeditationStep
import com.aurizen.data.BackgroundSound
import com.aurizen.data.BinauralTone
import com.aurizen.data.MeditationStep
import com.aurizen.data.AudioSettings
import com.aurizen.data.toUnified
import com.aurizen.data.EnhancedMeditationStep
import com.aurizen.data.MeditationSegment
import com.aurizen.data.MeditationSegmentType
import com.aurizen.data.MeditationSegmentProgress
import com.aurizen.data.MeditationPacingPreferences
import com.aurizen.core.MeditationTimingController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    private val _progress = MutableStateFlow(
        UnifiedMeditationProgress(
            0,
            1,
            0,
            0,
            false,
            "",
            UnifiedMeditationSessionState.PREPARING
        )
    )
    val progress: StateFlow<UnifiedMeditationProgress> = _progress.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _audioSettings = MutableStateFlow(
        AudioSettings(
            soundEnabled = true,
            backgroundSound = BackgroundSound.NONE,
            binauralEnabled = false,
            binauralTone = BinauralTone.NONE,
            ttsEnabled = true
        )
    )
    val audioSettings: StateFlow<AudioSettings> = _audioSettings.asStateFlow()

    private val _generationStatus =
        MutableStateFlow<MeditationGenerationStatus>(MeditationGenerationStatus.Idle)
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
    private val currentSentences: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var currentSentenceIndex: Int = 0
    private var isPlayingSentences: Boolean = false

    // Streaming generation support
    private val streamingBuffer: StringBuilder = StringBuilder()
    private var streamingTitle: String = ""
    private var streamingGuidance: String = ""
    private var isStreamingActive: Boolean = false
    private var hasStreamingStarted: Boolean = false
    private var lastProcessedGuidanceLength = 0

    // Internal state
    private val meditationSettings = MeditationSettings.getInstance(context)
    private val audioManager = MeditationAudioManager(context)

    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var timerJob: Job? = null
    private var generationJob: Job? = null
    private var remainingStepsJob: Job? = null
    private var currentInferenceFuture: ListenableFuture<String>? = null

    // Session configuration
    private var sessionConfig: UnifiedMeditationConfig? = null
    private val unifiedSteps = Collections.synchronizedList(mutableListOf<UnifiedMeditationStep>())
    private var currentStepIndex = 0
    private var totalSessionDuration = 0

    // Enhanced pacing system
    private var enhancedSteps: List<EnhancedMeditationStep> = emptyList()
    private var currentSegmentIndex = 0
    private var timingController: MeditationTimingController? = null
    private var pacingPreferences: MeditationPacingPreferences? = null
    private var currentSegment: MeditationSegment? = null

    // State flags to prevent race conditions
    private var isTransitioningStep = false
    private var isGeneratingNextStep = false
    private var lastPausedSentenceIndex = 0

    init {
        Log.d(TAG, "🚀 Creating unified meditation session for: $meditationType")
        initializeSession()
    }

    // Helper function for consistent error handling
    private suspend fun handleError(message: String, canRetry: Boolean = false) {
        withContext(Dispatchers.Main) {
            _generationStatus.value = MeditationGenerationStatus.Error(message)
            Log.e(TAG, "Session error: $message")
        }
    }

    // Helper function for consistent progress updates
    private fun updateProgress(
        stepIndex: Int,
        totalSteps: Int,
        timeRemaining: Int,
        totalTimeRemaining: Int,
        isGenerating: Boolean,
        status: String,
        sessionState: UnifiedMeditationSessionState
    ) {
        _progress.value = UnifiedMeditationProgress(
            stepIndex, totalSteps, timeRemaining, totalTimeRemaining,
            isGenerating, status, sessionState
        )
    }

    private fun initializeSession() {
        viewModelScope.launch {
            try {
                // Initialize pacing system
                pacingPreferences = meditationSettings.getPacingPreferences()
                timingController = MeditationTimingController(pacingPreferences!!)

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
                handleError("Failed to initialize meditation session: ${e.message}")
            }
        }
    }

    private fun createEnhancedSteps() {
        if (unifiedSteps.isEmpty()) return

        timingController?.let { controller ->
            sessionConfig?.let { config ->
                Log.d(TAG, "🎯 Creating enhanced steps with pacing system")
                enhancedSteps = controller.createEnhancedMeditation(unifiedSteps, config)

                // Log pacing statistics
                val stats = controller.getMeditationStats(enhancedSteps)
                Log.d(TAG, "📊 Enhanced steps created: ${enhancedSteps.size} steps, ${enhancedSteps.sumOf { it.segments.size }} segments, ${stats.totalGentleCues} cues")

                // Reset segment tracking
                currentSegmentIndex = 0
                currentSegment = enhancedSteps.firstOrNull()?.segments?.firstOrNull()
            }
        }
    }

    private suspend fun setupCustomMeditation() {
        Log.d(TAG, "🎯 Setting up custom meditation")

        // Load configuration immediately
        val config = loadCustomMeditationConfig()
        if (config == null) {
            handleError("Custom meditation configuration not found")
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
            handleError("No meditation steps found for type: $meditationType")
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

        // Create enhanced steps with pacing system
        createEnhancedSteps()

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

        // Show first sentence of guidance text instead of full paragraph
        val firstSentence = splitIntoSentences(unifiedSteps[0].guidance).firstOrNull() ?: unifiedSteps[0].guidance
        _currentSentence.value = firstSentence
        Log.d(TAG, "Set initial first sentence (regular): '$firstSentence'")

        _sessionState.value = UnifiedMeditationSessionState.READY
        _generationStatus.value = MeditationGenerationStatus.Idle
        _isFullyGenerated.value = true // Regular meditations are always fully available

        Log.d(TAG, "✅ Regular meditation ready: ${unifiedSteps.size} steps")
    }

    private suspend fun setupSavedMeditation() {
        Log.d(TAG, "💾 Setting up saved meditation")

        // Use the full meditation ID as it's stored
        val actualMeditationId = meditationType
        val savedMeditation = loadSavedMeditation(actualMeditationId)
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

        // Create enhanced steps with pacing system
        createEnhancedSteps()

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

        // Show first sentence of guidance text instead of full paragraph
        val firstSentence = splitIntoSentences(unifiedSteps[0].guidance).firstOrNull() ?: unifiedSteps[0].guidance
        _currentSentence.value = firstSentence
        Log.d(TAG, "Set initial first sentence (saved): '$firstSentence'")

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
        var retryCount = 0
        val maxRetries = 2

        while (retryCount <= maxRetries) {
            try {
                Log.d(TAG, "🌊 Starting streaming generation for first step (attempt ${retryCount + 1})")

                withContext(Dispatchers.Main) {
                    _generationStatus.value = MeditationGenerationStatus.Generating(0, 0.0f)
                    isStreamingActive = true
                    hasStreamingStarted = false
                    streamingBuffer.clear()
                    streamingTitle = ""
                    streamingGuidance = ""
                    currentSentences.clear()
                    currentSentenceIndex = 0
                    lastProcessedGuidanceLength = 0
                }

                val inferenceModel = InferenceModel.getInstance(context)

                // Check if model is available before starting generation
                if (!InferenceModel.isAvailable()) {
                    throw Exception("Model is busy processing")
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

                // DON'T WAIT FOR COMPLETION - streaming will handle everything!
                Log.d(TAG, "🚀 Streaming generation started, returning immediately")
                return  // Exit immediately to let streaming work

            } catch (e: Exception) {
                retryCount++
                Log.e(TAG, "Generation attempt $retryCount failed", e)

                if (retryCount > maxRetries) {
                    withContext(Dispatchers.Main) {
                        // Use fallback content instead of error
                        val fallbackStep = createFallbackUnifiedStep(0, config.stepDuration)
                        unifiedSteps.add(fallbackStep)
                        _currentStep.value = fallbackStep
                        _sessionState.value = UnifiedMeditationSessionState.READY
                        _generationStatus.value = MeditationGenerationStatus.Completed(0)

                        // Set first sentence for display
                        val firstSentence = splitIntoSentences(fallbackStep.guidance).firstOrNull() ?: fallbackStep.guidance
                        _currentSentence.value = firstSentence
                    }
                    return
                }

                // Wait before retry
                delay(1000L * retryCount)
            }
        }
    }

    /**
     * Process streaming text as it arrives from the AI model
     */
    private fun processStreamingText(
        partial: String,
        config: UnifiedMeditationConfig,
        stepIndex: Int
    ) {
        streamingBuffer.append(partial)

        // Try to extract title and guidance progressively
        parseStreamingContent()

        // Only process new content
        if (streamingGuidance.length > lastProcessedGuidanceLength) {
            // Check if we have new complete sentences
            val allSentences = splitIntoSentences(streamingGuidance)

            // Only add sentences we haven't seen before
            if (allSentences.size > currentSentences.size) {
                // Add all complete sentences except the last one (might be incomplete)
                for (i in currentSentences.size until allSentences.size - 1) {
                    val sentence = allSentences[i]
                    if (!currentSentences.contains(sentence) && sentence.isNotEmpty()) {
                        currentSentences.add(sentence)
                        Log.d(TAG, "🎯 New sentence ready: ${sentence.take(30)}...")

                        // Start session with first sentence
                        if (!hasStreamingStarted) {
                            Log.d(TAG, "🚀 Starting session with first sentence")
                            startStreamingSession(config, stepIndex)
                        }

                        // Queue for TTS if playing
                        if (hasStreamingStarted && _sessionState.value == UnifiedMeditationSessionState.ACTIVE && _audioSettings.value.ttsEnabled) {
                            queueNewSentenceForTts(sentence)
                        }
                    }
                }

                // Check if the last sentence is complete (ends with punctuation)
                if (allSentences.isNotEmpty()) {
                    val lastSentence = allSentences.last()
                    if (lastSentence.matches(Regex(".*[.!?]$")) && !currentSentences.contains(lastSentence)) {
                        currentSentences.add(lastSentence)
                        Log.d(TAG, "🎯 Complete sentence ready: ${lastSentence.take(30)}...")

                        if (!hasStreamingStarted) {
                            startStreamingSession(config, stepIndex)
                        }

                        if (hasStreamingStarted && _sessionState.value == UnifiedMeditationSessionState.ACTIVE && _audioSettings.value.ttsEnabled) {
                            queueNewSentenceForTts(lastSentence)
                        }
                    }
                }
            }

            lastProcessedGuidanceLength = streamingGuidance.length
        }

        // Update progress
        val progress = estimateProgress(streamingBuffer.length)
        _generationStatus.value = MeditationGenerationStatus.Generating(stepIndex, progress)
    }

    /**
     * Extract title and guidance from streaming JSON content (works with partial JSON)
     */
    private fun parseStreamingContent() {
        try {
            val jsonStart = streamingBuffer.indexOf("{")
            if (jsonStart < 0) return

            val jsonPortion = streamingBuffer.substring(jsonStart)

            // Use more robust JSON detection
            if (!jsonPortion.contains("\"title\"") && !jsonPortion.contains("\"guidance\"")) {
                return // Wait for more content
            }

            // Extract title safely
            if (streamingTitle.isEmpty()) {
                val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"")
                    .find(jsonPortion)
                titleMatch?.groupValues?.get(1)?.let { title ->
                    streamingTitle = unescapeJson(title)
                    Log.d(TAG, "📝 Title extracted: $streamingTitle")
                }
            }

            // Extract guidance safely with better regex
            val guidancePattern = Regex("\"guidance\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"?")
            val guidanceMatch = guidancePattern.find(jsonPortion)

            guidanceMatch?.groupValues?.get(1)?.let { rawGuidance ->
                val cleanGuidance = unescapeJson(rawGuidance).trim()
                if (cleanGuidance != streamingGuidance && cleanGuidance.isNotEmpty()) {
                    streamingGuidance = cleanGuidance
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing streaming content", e)
        }
    }

    private fun unescapeJson(text: String): String {
        return text
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    /**
     * Start the meditation session with the first streamed sentence
     */
    private fun startStreamingSession(config: UnifiedMeditationConfig, stepIndex: Int) {
        hasStreamingStarted = true

        // Create a streaming step with current content
        val streamingStep = object : UnifiedMeditationStep {
            override val title: String =
                streamingTitle.ifEmpty { "Meditation Step ${stepIndex + 1}" }
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

        Log.d(
            TAG,
            "🚀 Session ready with ${currentSentences.size} sentences, more streaming in background"
        )
    }

    /**
     * Add new sentence to TTS queue if currently playing
     */
    private fun queueNewSentenceForTts(sentence: String) {
        // If TTS was waiting for more sentences, restart it
        if (!isPlayingSentences && _isPlaying.value && _audioSettings.value.ttsEnabled && currentSentenceIndex < currentSentences.size) {
            startSentenceBasedTts()
        }
    }

    /**
     * Finalize the streaming step when generation is complete
     */
    private fun finalizeStreamingStep(config: UnifiedMeditationConfig, stepIndex: Int) {
        isStreamingActive = false

        // Create final step with all content
        val finalStep = parseCustomMeditationStep(streamingBuffer.toString(), stepIndex, config.stepDuration)

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

            // Set first sentence for display
            val firstSentence = splitIntoSentences(finalStep.guidance).firstOrNull() ?: finalStep.guidance
            _currentSentence.value = firstSentence

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

    private fun createCustomMeditationPrompt(
        config: UnifiedMeditationConfig,
        stepIndex: Int
    ): String {
        val stepType = when (stepIndex) {
            0 -> "opening"
            config.totalSteps - 1 -> "closing"
            else -> "continuation"
        }

        // Use mood-specific guidance if available
        return if (config.focus == "mood-guided wellness" && config.moodContext.isNotEmpty()) {
            val moodGuidanceInstruction = when (stepType) {
                "opening" -> "Deeply personalized 2-3 minute meditation guidance that references their specific mood patterns, validates their experiences, and guides them toward healing and balance. Start with breathing/relaxation but weave in their emotional journey. Write for spoken delivery - avoid parentheses, use natural speech patterns, and ensure each sentence flows uniquely without repetition."
                "continuation" -> "Deeply personalized 2-3 minute meditation guidance that continues building on their emotional journey from previous steps. Use transition phrases like 'Now', 'As you continue', 'Building on this foundation' rather than 'Begin with'. Reference their mood patterns while deepening the practice. Write for spoken delivery - avoid parentheses, use natural speech patterns, and ensure each sentence flows uniquely without repetition."
                "closing" -> "Deeply personalized 2-3 minute meditation guidance that brings closure to their emotional journey, helping them integrate insights and return to awareness with renewed balance. Write for spoken delivery - avoid parentheses, use natural speech patterns, and ensure each sentence flows uniquely without repetition."
                else -> "Deeply personalized 2-3 minute meditation guidance that references their specific mood patterns, validates their experiences, and guides them toward healing and balance. Start with breathing/relaxation but weave in their emotional journey. Write for spoken delivery - avoid parentheses, use natural speech patterns, and ensure each sentence flows uniquely without repetition."
            }

            """
            Create a personalized ${stepType} meditation step for someone based on their recent emotional journey.
            Step ${stepIndex + 1} of ${config.totalSteps} | Duration: ${config.stepDuration} seconds
            
            IMPORTANT: Reference their actual mood patterns naturally and supportively. Guide them through their recent experiences with compassion.
            
            IMPORTANT: This will be read aloud by text-to-speech. Avoid parentheses, brackets, or any formatting that doesn't speak naturally. Instead of writing "(and it will)" or "(take your time)", simply incorporate these thoughts into the natural flow of sentences.
            
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
              "guidance": "${moodGuidanceInstruction}"
            }
            """.trimIndent()
        } else {
            val guidanceInstruction = when (stepType) {
                "opening" -> "2-3 minute meditation guidance that starts immediately with breathing or relaxation instructions. Write for spoken delivery - avoid parentheses, use natural speech patterns, and ensure each sentence flows uniquely without repetition."
                "continuation" -> "2-3 minute meditation guidance that continues from previous steps, building on the established foundation. Use transition phrases like 'Now', 'Next', 'Continue', 'As you deepen', rather than 'Begin with'. Write for spoken delivery - avoid parentheses, use natural speech patterns, and ensure each sentence flows uniquely without repetition."
                "closing" -> "2-3 minute meditation guidance that brings the session to a peaceful close, helping them transition back to awareness. Write for spoken delivery - avoid parentheses, use natural speech patterns, and ensure each sentence flows uniquely without repetition."
                else -> "2-3 minute meditation guidance that starts immediately with breathing or relaxation instructions. Write for spoken delivery - avoid parentheses, use natural speech patterns, and ensure each sentence flows uniquely without repetition."
            }

            """
            Create a ${stepType} meditation step for someone focusing on: ${config.focus}
            Step ${stepIndex + 1} of ${config.totalSteps}
            Duration: ${config.stepDuration} seconds
            
            IMPORTANT: This will be read aloud by text-to-speech. Avoid parentheses, brackets, or any formatting that doesn't speak naturally. Instead of writing "(and it will)" or "(take your time)", simply incorporate these thoughts into the natural flow of sentences.
            
            Respond with JSON format:
            {
              "title": "Brief step title",
              "guidance": "${guidanceInstruction}"
            }
            """.trimIndent()
        }
    }

    private fun parseCustomMeditationStep(
        response: String,
        stepIndex: Int,
        duration: Int
    ): UnifiedMeditationStep {
        return try {
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonString = response.substring(jsonStart, jsonEnd)
                val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)
                val guidanceMatch = Regex("\"guidance\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)

                val title = titleMatch?.groupValues?.get(1) ?: "Meditation Step ${stepIndex + 1}"
                val guidance =
                    guidanceMatch?.groupValues?.get(1) ?: "Focus on your breath and be present."

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
                override val guidance: String =
                    "Take a deep breath and focus on this moment of peace."
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

        // Change session state to ACTIVE after setting up the content
        _sessionState.value = UnifiedMeditationSessionState.ACTIVE

        // Start timer
        startTimer()
    }

    private fun pauseSession() {
        Log.d(TAG, "⏸️ Pausing session")
        _isPlaying.value = false
        _sessionState.value = UnifiedMeditationSessionState.PAUSED
        timerJob?.cancel()
        audioManager.pauseBackgroundSound()
        audioManager.pauseBinauralTone()

        // Don't clear the current sentence - keep it visible
        if (isPlayingSentences || textToSpeech?.isSpeaking == true) {
            ttsIsPaused = true
            isPlayingSentences = false
            textToSpeech?.stop()

            // Store the current position for resume
            lastPausedSentenceIndex = currentSentenceIndex
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
                Log.d(
                    TAG,
                    "🔄 Resuming TTS from sentence ${currentSentenceIndex + 1}/${currentSentences.size}"
                )
                resumeTtsFromCurrentSentence()
            } else if (currentSentences.isNotEmpty() && currentSentenceIndex < currentSentences.size) {
                // If we have sentences in progress, continue from where we left off
                Log.d(
                    TAG,
                    "🔄 Continuing TTS from sentence ${currentSentenceIndex + 1}/${currentSentences.size}"
                )
                ttsIsPaused = false
                startSentenceBasedTts()
            } else if (_currentStep.value?.guidance?.isNotEmpty() == true) {
                // If no paused TTS but we have a current step, speak it
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

        // Reset generation flags
        isGeneratingNextStep = false
        isTransitioningStep = false

        // Cancel all jobs
        cancelAllJobs()

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
        streamingBuffer.clear()
        streamingTitle = ""
        streamingGuidance = ""
        lastProcessedGuidanceLength = 0

        // Record session completion
        sessionConfig?.let { config ->
            val duration = config.totalDuration
            meditationSettings.recordSessionCompletion(meditationType, duration)
        }
    }

    private fun startTimer() {
        timerJob?.cancel() // Always cancel existing timer first

        // Use enhanced segment timer if available
        if (enhancedSteps.isNotEmpty()) {
            Log.d(TAG, "🎯 Starting enhanced segment timer (${enhancedSteps.size} steps)")
            startSegmentTimer()
        } else {
            Log.d(TAG, "⏰ Using traditional timer")
            startRegularTimer()
        }
    }

    private fun startRegularTimer() {
        timerJob = viewModelScope.launch {
            while (isActive && _isPlaying.value && _progress.value.timeRemainingInStep > 0) {
                delay(1000)

                // Use synchronized state update
                synchronized(this@UnifiedMeditationSessionViewModel) {
                    if (!isActive || !_isPlaying.value) return@launch

                    val currentProgress = _progress.value
                    val newTimeInStep = (currentProgress.timeRemainingInStep - 1).coerceAtLeast(0)
                    val newTotalTime = (currentProgress.totalTimeRemaining - 1).coerceAtLeast(0)

                    _progress.value = currentProgress.copy(
                        timeRemainingInStep = newTimeInStep,
                        totalTimeRemaining = newTotalTime
                    )

                    // Trigger generation early but don't block
                    if (sessionConfig?.isCustomGenerated == true &&
                        newTimeInStep == 60 &&
                        !isGeneratingNextStep) {
                        isGeneratingNextStep = true
                        launch {
                            triggerNextStepGeneration()
                            isGeneratingNextStep = false
                        }
                    }

                    // Only move to next step if timer reaches 0 AND we're not already transitioning
                    if (newTimeInStep == 0 && !isTransitioningStep) {
                        isTransitioningStep = true
                        launch {
                            moveToNextStep()
                            isTransitioningStep = false
                        }
                        return@launch // Exit timer loop
                    }
                }
            }
        }
    }

    private fun startSegmentTimer() {
        timingController?.let { controller ->
            val nextSegment = controller.getNextSegment(enhancedSteps, currentStepIndex, currentSegmentIndex)
            if (nextSegment == null) {
                Log.d(TAG, "⏰ No more segments - session complete")
                _sessionState.value = UnifiedMeditationSessionState.COMPLETED
                return
            }

            currentSegment = nextSegment
            val segmentDuration = nextSegment.duration
            Log.d(TAG, "⏰ Starting segment timer: ${nextSegment.type} for ${segmentDuration}s")

            // Handle segment-specific behavior
            when (nextSegment.type) {
                MeditationSegmentType.INSTRUCTION,
                MeditationSegmentType.GENTLE_CUE,
                MeditationSegmentType.TRANSITION -> {
                    // Speak the content
                    if (_audioSettings.value.ttsEnabled && nextSegment.content.isNotBlank()) {
                        speakGuidance(nextSegment.content)
                    }
                }
                MeditationSegmentType.PRACTICE,
                MeditationSegmentType.REFLECTION,
                MeditationSegmentType.BREATHING_PAUSE -> {
                    // Silent practice - keep the last sentence displayed
                    Log.d(TAG, "Silent segment: keeping last sentence visible")
                }
            }

            // Apply segment-specific volume
            audioManager.setTtsVolume(nextSegment.volumeLevel)

            // Start segment countdown
            timerJob = viewModelScope.launch {
                var timeRemaining = segmentDuration

                while (_isPlaying.value && timeRemaining > 0) {
                    delay(1000)
                    if (_isPlaying.value) {
                        timeRemaining--
                        updateSegmentProgress(timeRemaining)
                    }
                }

                if (_isPlaying.value && timeRemaining == 0) {
                    moveToNextSegment()
                }
            }
        }
    }

    private fun updateSegmentProgress(timeRemainingInSegment: Int) {
        timingController?.let { controller ->
            val segmentProgress = controller.calculateProgress(
                enhancedSteps,
                currentStepIndex,
                currentSegmentIndex,
                timeRemainingInSegment
            )

            // Update the main progress state
            _progress.value = UnifiedMeditationProgress(
                currentStepIndex = segmentProgress.currentStepIndex,
                totalSteps = segmentProgress.totalSteps,
                timeRemainingInStep = segmentProgress.timeRemainingInStep,
                totalTimeRemaining = segmentProgress.totalTimeRemaining,
                isGenerating = false,
                generationStatus = "",
                sessionState = segmentProgress.sessionState
            )
        }
    }

    private fun moveToNextSegment() {
        timingController?.let { controller ->
            val (newStepIndex, newSegmentIndex) = controller.moveToNextSegment(
                enhancedSteps,
                currentStepIndex,
                currentSegmentIndex
            )

            if (controller.isSessionComplete(enhancedSteps, newStepIndex, newSegmentIndex)) {
                Log.d(TAG, "✅ Enhanced meditation session complete")
                _sessionState.value = UnifiedMeditationSessionState.COMPLETED
                return
            }

            // Update indices
            val oldStepIndex = currentStepIndex
            currentStepIndex = newStepIndex
            currentSegmentIndex = newSegmentIndex

            // Update current step if we moved to a new step
            if (oldStepIndex != newStepIndex && newStepIndex < unifiedSteps.size) {
                _currentStep.value = unifiedSteps[newStepIndex]
            }

            // Continue with next segment
            startSegmentTimer()
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
            Log.d(
                TAG,
                "✅ Moving to existing step ${nextIndex + 1}: '${nextStep.title}' (${nextStep.durationSeconds}s)"
            )

            withContext(Dispatchers.Main) {
                _currentStep.value = nextStep
                _progress.value = _progress.value.copy(
                    currentStepIndex = nextIndex,
                    timeRemainingInStep = nextStep.durationSeconds
                )

                // Only set guidance text if NOT using enhanced pacing system
                if (enhancedSteps.isEmpty()) {
                    val firstSentence = splitIntoSentences(nextStep.guidance).firstOrNull() ?: nextStep.guidance
                    _currentSentence.value = firstSentence

                    // Speak new step guidance
                    if (_audioSettings.value.ttsEnabled && _isPlaying.value) {
                        speakGuidance(nextStep.guidance)
                    }
                }

                // Restart timer for new step
                startTimer()
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
            return
        }

        if (nextStepIndex < unifiedSteps.size) {
            Log.d(TAG, "⏭️ Step ${nextStepIndex + 1} already exists, skipping generation")
            return
        }

        Log.d(TAG, "🤖 Triggering generation of step ${nextStepIndex + 1} (60s early)")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prompt = createCustomMeditationPrompt(config, nextStepIndex)
                val inferenceModel = InferenceModel.getInstance(context)

                currentInferenceFuture =
                    inferenceModel.generateResponseAsync(prompt) { partial, done ->
                        if (done) {
                            Log.d(TAG, "✅ Step ${nextStepIndex + 1} generated successfully")
                        }
                    }

                val response = currentInferenceFuture!!.get(30, TimeUnit.SECONDS)
                currentInferenceFuture = null
                val step =
                    parseCustomMeditationStep(response ?: "", nextStepIndex, config.stepDuration)

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

        if (_sessionState.value == UnifiedMeditationSessionState.COMPLETED) {
            Log.d(TAG, "🛑 Session stopped, not generating remaining steps")
            return
        }

        remainingStepsJob = viewModelScope.launch(Dispatchers.IO) {
            for (i in 1 until config.totalSteps) {
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
                        currentInferenceFuture =
                            inferenceModel.generateResponseAsync(prompt) { partial, done ->
                                if (partial != null) {
                                    result += partial
                                }
                            }

                        val response = currentInferenceFuture!!.get(12, TimeUnit.SECONDS)
                        currentInferenceFuture = null
                        val step =
                            parseCustomMeditationStep(response ?: result, i, config.stepDuration)

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

                            Log.d(
                                TAG,
                                "✅ Generated step ${i + 1}/${config.totalSteps}: '${step.title}' (${step.durationSeconds}s)"
                            )

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
        val prefs =
            context.getSharedPreferences("saved_meditations", android.content.Context.MODE_PRIVATE)
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
        val savedIds =
            prefs.getStringSet("saved_meditation_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        savedIds.add(meditation.id)
        editor.putStringSet("saved_meditation_ids", savedIds)

        editor.apply()
    }

    private fun loadSavedMeditation(meditationId: String): SavedMeditation? {
        val prefs =
            context.getSharedPreferences("saved_meditations", android.content.Context.MODE_PRIVATE)

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
        val prefs =
            context.getSharedPreferences("saved_meditations", android.content.Context.MODE_PRIVATE)
        val steps = prefs.getInt("${meditationId}_steps", 0)

        return (0 until steps).mapNotNull { index ->
            try {
                val title = prefs.getString("${meditationId}_step_${index}_title", null)
                    ?: return@mapNotNull null
                val guidance = prefs.getString("${meditationId}_step_${index}_guidance", null)
                    ?: return@mapNotNull null
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
        val prefs =
            context.getSharedPreferences("saved_meditations", android.content.Context.MODE_PRIVATE)

        return try {
            val focus = prefs.getString("${meditationId}_config_focus", null) ?: return null
            val mood = prefs.getString("${meditationId}_config_mood", null) ?: return null
            val experience =
                prefs.getString("${meditationId}_config_experience", null) ?: return null
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
        val newSettings =
            _audioSettings.value.copy(soundEnabled = !_audioSettings.value.soundEnabled)
        _audioSettings.value = newSettings
        meditationSettings.setSoundEnabled(newSettings.soundEnabled)

        if (newSettings.soundEnabled && _isPlaying.value) {
            audioManager.playBackgroundSound(newSettings.backgroundSound)
        } else {
            audioManager.stopBackgroundSound()
        }
    }

    fun toggleBinaural() {
        val newSettings =
            _audioSettings.value.copy(binauralEnabled = !_audioSettings.value.binauralEnabled)
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

        // Only show guidance text if NOT using enhanced pacing system
        if (enhancedSteps.isEmpty()) {
            _currentStep.value?.let { step ->
                val firstSentence = splitIntoSentences(step.guidance).firstOrNull() ?: step.guidance
                _currentSentence.value = firstSentence
            }
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
        meditationSettings.setTtsSpeed(speed)
        textToSpeech?.setSpeechRate(speed)
    }

    fun setTtsPitch(pitch: Float) {
        meditationSettings.setTtsPitch(pitch)
        textToSpeech?.setPitch(pitch)
    }

    fun setTtsVoice(voiceName: String) {
        meditationSettings.setTtsVoice(voiceName)
        textToSpeech?.let { tts ->
            tts.voices?.find { it.name == voiceName }?.let { voice ->
                tts.voice = voice
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
                                // TTS sentence started
                            }

                            override fun onDone(utteranceId: String?) {
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
                        Log.d(TAG, "TTS initialized successfully")
                    }
                } else {
                    Log.e(TAG, "TTS initialization failed")
                }
            }
        }
    }

    private fun speakGuidance(text: String) {
        if (text.isEmpty()) return

        // Clean text once
        val cleanText = text.replace(Regex("\\*+"), "")
            .replace(Regex("#+"), "")
            .replace("\\n", " ")
            .trim()

        // Split into sentences
        val sentences = splitIntoSentences(cleanText)

        // Always set the first sentence for display
        if (sentences.isNotEmpty()) {
            _currentSentence.value = sentences[0]
        }

        // If TTS is enabled, prepare for audio playback
        if (_audioSettings.value.ttsEnabled && isTtsReady && sentences.isNotEmpty()) {
            currentSentences.clear()
            currentSentences.addAll(sentences)
            currentSentenceIndex = 0
            currentTtsText = cleanText
            ttsIsPaused = false

            // Start speaking from first sentence
            startSentenceBasedTts()
        }
    }

    private fun splitIntoSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()

        // First split on major sentence boundaries
        val majorSentences = text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // If no major sentences found (no periods, etc.), split on other patterns
        if (majorSentences.size == 1 && majorSentences[0] == text.trim()) {
            // No sentence boundaries found, try splitting on other patterns
            val alternativeSentences = text.split(Regex("(?<=[,;:])\\s+|(?<=---)\\s+|(?<=--)\\s+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            // If still only one chunk, split on line breaks or at reasonable word boundaries
            if (alternativeSentences.size == 1) {
                val words = text.split(Regex("\\s+"))
                if (words.size > 20) {
                    // Split long paragraphs into chunks of ~10-15 words
                    val chunks = mutableListOf<String>()
                    var currentChunk = mutableListOf<String>()

                    for (word in words) {
                        currentChunk.add(word)
                        if (currentChunk.size >= 12) {
                            chunks.add(currentChunk.joinToString(" "))
                            currentChunk.clear()
                        }
                    }

                    if (currentChunk.isNotEmpty()) {
                        chunks.add(currentChunk.joinToString(" "))
                    }

                    sentences.addAll(chunks)
                } else {
                    sentences.add(text.trim())
                }
            } else {
                sentences.addAll(alternativeSentences)
            }
        } else {
            // Process normally found sentences
            for (sentence in majorSentences) {
                val chunks = mutableListOf<String>()

                // Split on commas, semicolons, colons, and dashes while preserving them
                val parts = sentence.split(Regex("(?<=[,;:])\\s+|(?<=--)\\s+|(?<=---)\\s+"))

                for (part in parts) {
                    val trimmedPart = part.trim()
                    if (trimmedPart.isNotEmpty()) {
                        chunks.add(trimmedPart)
                    }
                }

                // If no chunks were created, add the original sentence
                if (chunks.isEmpty()) {
                    chunks.add(sentence)
                }

                sentences.addAll(chunks)
            }
        }

        return sentences.filter { it.isNotEmpty() }
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
                    putFloat(
                        TextToSpeech.Engine.KEY_PARAM_VOLUME,
                        meditationSettings.getTtsVolume()
                    )
                }

                val utteranceId = "${ttsUtteranceId}_${currentSentenceIndex}"
                tts.speak(cleanedSentence, TextToSpeech.QUEUE_FLUSH, params, utteranceId)

                Log.d(
                    TAG,
                    "Speaking sentence ${currentSentenceIndex + 1}/${currentSentences.size}: ${
                        sentence.take(30)
                    }..."
                )
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
            .replace("--", "-")
            .replace(""", "\"")
            .replace(""", "\"")
            .replace("'", "'")
            .replace("'", "'")
            // Remove parentheses and brackets content that might be formatting
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(.*?\\)\\."), "") // Remove parentheses followed by period
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

    private fun calculatePauseTime(previousSentence: String): Long {
        return when {
            // Longer pause after sentences ending with periods, exclamation marks
            previousSentence.endsWith(".") || previousSentence.endsWith("!") -> 2000L
            // Medium pause after questions
            previousSentence.endsWith("?") -> 1800L
            // Shorter pause after commas, semicolons, colons
            previousSentence.endsWith(",") || previousSentence.endsWith(";") || previousSentence.endsWith(":") -> 1000L
            // Pause after dashes and ellipses
            previousSentence.endsWith("--") || previousSentence.endsWith("---") || previousSentence.endsWith("...") -> 1200L
            // Special pause for breathing cues
            previousSentence.lowercase().contains("breathe") ||
                    previousSentence.lowercase().contains("inhale") ||
                    previousSentence.lowercase().contains("exhale") -> 1500L
            // Special pause for counting sequences
            previousSentence.matches(Regex(".*\\d+.*")) -> 1200L
            // Pause for transition words
            previousSentence.lowercase().matches(Regex(".*(now|then|next|slowly|gently|softly).*")) -> 1300L
            // Default pause for other content
            else -> 1000L
        }
    }

    private fun playNextSentence() {
        currentSentenceIndex++
        if (currentSentenceIndex < currentSentences.size && isPlayingSentences && !ttsIsPaused) {
            // Dynamic pause based on sentence content
            viewModelScope.launch {
                val previousSentence = if (currentSentenceIndex > 0) currentSentences[currentSentenceIndex - 1] else ""
                val pauseTime = calculatePauseTime(previousSentence)

                // Keep the last sentence displayed during pause - don't clear it
                delay(pauseTime)
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

            // Only clear text if we're in enhanced mode AND moving to a silent segment
            // Otherwise, keep the last sentence displayed
            if (enhancedSteps.isNotEmpty()) {
                val currentSegment = timingController?.getCurrentSegmentType(enhancedSteps, currentStepIndex, currentSegmentIndex)
                if (currentSegment == MeditationSegmentType.PRACTICE ||
                    currentSegment == MeditationSegmentType.REFLECTION ||
                    currentSegment == MeditationSegmentType.BREATHING_PAUSE) {
                    Log.d(TAG, "Sentences completed, moving to silent segment")
                }
            }
        }
    }

    private fun resumeTtsFromCurrentSentence() {
        if (currentSentences.isNotEmpty() && currentSentenceIndex < currentSentences.size) {
            ttsIsPaused = false
            Log.d(
                TAG,
                "Resuming from sentence ${currentSentenceIndex + 1}/${currentSentences.size}"
            )
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

                    Log.d(
                        TAG,
                        "🎯 Parsing inline custom meditation: focus='$focus', mood='$mood', experience='$experience', duration=${durationMinutes}min"
                    )
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
            val experience =
                prefs.getString("${meditationType}_experience", "Beginner") ?: "Beginner"

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
                MeditationStep(
                    "Welcome",
                    "Beginning stress relief meditation",
                    "Welcome to your stress relief meditation. Begin by finding a quiet, comfortable position--whether you're sitting or lying down. Allow your body to settle and your hands to rest easily. Gently close your eyes. Bring your attention inward as you begin to breathe deeply. Inhale slowly through your nose, feeling your lungs fill up completely. Pause for a moment at the top of the breath, and then exhale gently through your mouth, releasing any tension. Let each breath invite a deeper sense of relaxation and presence.",
                    stepDuration
                ),
                MeditationStep(
                    "Body Scan",
                    "Notice areas of tension",
                    "Bring your awareness to the top of your head. Slowly begin to scan downward, part by part--your scalp, forehead, eyes, jaw. Notice any sensations of tightness or holding. With each breath, gently invite those areas to soften. Continue down your neck and shoulders. Let them drop away from your ears. Move your awareness through your arms, chest, abdomen, and lower back. Finally, scan your legs all the way to your toes. There's no need to change anything--just be present with what is. Observe with a sense of gentle curiosity and kindness.",
                    stepDuration
                ),
                MeditationStep(
                    "Breathing Focus",
                    "Focus on your natural breath",
                    "Now bring your attention to your breath. Feel the natural rise and fall of your abdomen or chest. Begin to silently count each exhale: one... two... three... up to ten. If your mind wanders, that's okay--simply notice it without judgment and return to the breath, beginning again at one. Let the counting anchor you in this moment. Notice the calming rhythm of your breathing and allow it to become your center. This simple awareness of breath can bring clarity, stillness, and relief.",
                    stepDuration
                )
            )

            "focus_boost" -> listOf(
                MeditationStep(
                    "Focus Preparation",
                    "Preparing your mind for clarity",
                    "Sit upright in a position that feels both stable and relaxed. Imagine a gentle thread lifting you from the crown of your head. Close your eyes and take three deep, cleansing breaths--inhaling through your nose and exhaling through your mouth. Let go of the tension with each exhale. Feel the shift as you transition from activity to stillness. With each breath, become more present and awake. Set an intention to be fully here, ready to cultivate mental clarity and focus.",
                    stepDuration
                ),
                MeditationStep(
                    "Mindful Awareness",
                    "Cultivate present-moment attention",
                    "Now bring your attention to your breath. Focus on the subtle sensations at the tip of your nose or the rhythm in your chest. Feel the coolness of each inhale, the warmth of each exhale. Each breath is unique. When thoughts arise--and they will--acknowledge them gently and guide your focus back to the breath. This returning is the heart of mindfulness. It's not about perfect stillness--it's about practicing presence, over and over again.",
                    stepDuration
                ),
                MeditationStep(
                    "Mental Clarity",
                    "Strengthen concentration",
                    "Now imagine a bright, clear light at the center of your forehead, between your eyebrows. This light represents your inner awareness and mental clarity. With each breath, allow the light to grow a little brighter, a little steadier. Let it fill your mind and illuminate your focus. As thoughts or distractions appear, simply return to this image. Breathe deeply, allowing this radiant clarity to strengthen and settle. Rest in this space of focused presence.",
                    stepDuration
                )
            )

            // ... (rest of meditation steps remain the same)

            else -> {
                Log.w(TAG, "⚠️ Unknown meditation type '$type', using basic fallback")
                listOf(
                    MeditationStep(
                        "Basic Meditation",
                        "Simple mindfulness practice",
                        "Focus gently on your breath. Each time your mind wanders, kindly return your attention to the breath. This is the practice of meditation - returning again and again with patience.",
                        300
                    )
                )
            }
        }.also { steps ->
            Log.d(
                TAG,
                "✅ Loaded ${steps.size} steps for '$type': ${steps.map { it.title }} (total: ${steps.sumOf { it.durationSeconds / 60 }}min)"
            )
        }
    }

    private fun cancelAllJobs() {
        timerJob?.cancel()
        generationJob?.cancel()
        remainingStepsJob?.cancel()
        currentInferenceFuture?.cancel(true)
        currentInferenceFuture = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelAllJobs()

        // Use coroutineScope to ensure cleanup completes
        runBlocking {
            try {
                withTimeout(1000) {
                    InferenceModel.forceReset(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Timeout during inference model reset", e)
            }
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