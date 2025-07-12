package com.aurizen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color

@Composable
internal fun MeditationRoute(
    onBack: () -> Unit,
    onStartSession: (String) -> Unit = {}
) {
    val context = LocalContext.current
    MeditationScreen(
        onBack = onBack,
        onStartSession = onStartSession,
        context = context
    )
}

// Fixed durations for predefined meditations
private fun getMeditationDuration(type: String): String {
    return when (type) {
        "stress_relief" -> "5 min"
        "focus_boost" -> "8 min"
        "sleep_prep" -> "10 min"
        "anxiety_ease" -> "7 min"
        "deep_relaxation" -> "20 min"
        "mindful_awareness" -> "15 min"
        "extended_focus" -> "30 min"
        "complete_zen" -> "45 min"
        else -> "5 min" // default
    }
}

@Composable
fun MeditationScreen(
    onBack: () -> Unit,
    onStartSession: (String) -> Unit,
    context: android.content.Context
) {

    val meditationSettings = remember { MeditationSettings.getInstance(context) }
    val stats = remember { meditationSettings.getMeditationStatistics() }
    var showSettings by remember { mutableStateOf(false) }
    var showCustomMeditationDialog by remember { mutableStateOf(false) }
    var showSavedMeditationsDialog by remember { mutableStateOf(false) }

    // Unified settings state management
    var soundEnabled by remember { mutableStateOf(meditationSettings.isSoundEnabled()) }
    var backgroundSound by remember { mutableStateOf(meditationSettings.getBackgroundSound()) }
    var binauralEnabled by remember { mutableStateOf(meditationSettings.isBinauralEnabled()) }
    var binauralTone by remember { mutableStateOf(meditationSettings.getBinauralTone()) }
    var ttsEnabled by remember { mutableStateOf(meditationSettings.isTtsEnabled()) }

    fun onSoundToggle() {
        val newValue = !soundEnabled
        soundEnabled = newValue
        meditationSettings.setSoundEnabled(newValue)
    }

    fun onBackgroundSoundChange(newSound: BackgroundSound) {
        backgroundSound = newSound
        meditationSettings.setBackgroundSound(newSound)
    }

    fun onBinauralToggle() {
        val newValue = !binauralEnabled
        binauralEnabled = newValue
        meditationSettings.setBinauralEnabled(newValue)
    }

    fun onBinauralToneChange(newTone: BinauralTone) {
        binauralTone = newTone
        meditationSettings.setBinauralTone(newTone)
    }

    fun onTtsToggle() {
        val newValue = !ttsEnabled
        ttsEnabled = newValue
        meditationSettings.setTtsEnabled(newValue)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Top bar with settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Guided Meditation",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Single unified settings button
                IconButton(
                    onClick = { showSettings = true }
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🧘‍♀️ Find Your Peace",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Choose a meditation session tailored to your current needs and available time.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Statistics card
        if (stats.totalSessions > 0) {
            item {
                MeditationStatsCard(stats)
            }
        }

        item {
            Text(
                text = "AuriZen Personalized Meditation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MeditationCard(
                    title = "AI-Generated Session",
                    duration = "Custom",
                    description = "Personalized meditation created by AI based on your preferences",
                    icon = Icons.Default.AutoAwesome,
                    onClick = { showCustomMeditationDialog = true },
                    modifier = Modifier.weight(1f),
                    useAuriZenIcon = true
                )
                MeditationCard(
                    title = "Saved Meditations",
                    duration = "Varied",
                    description = "Access your saved meditation sessions and templates",
                    icon = Icons.Default.BookmarkBorder,
                    onClick = { showSavedMeditationsDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                text = "Quick Sessions (5-10 min)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MeditationCard(
                    title = "Stress Relief",
                    duration = getMeditationDuration("stress_relief"),
                    description = "Quick calm for busy moments",
                    icon = Icons.Default.Healing,
                    onClick = { onStartSession("stress_relief") },
                    modifier = Modifier.weight(1f)
                )
                MeditationCard(
                    title = "Focus Boost",
                    duration = getMeditationDuration("focus_boost"),
                    description = "Enhance concentration",
                    icon = Icons.Default.Visibility,
                    onClick = { onStartSession("focus_boost") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MeditationCard(
                    title = "Sleep Prep",
                    duration = getMeditationDuration("sleep_prep"),
                    description = "Wind down for better rest",
                    icon = Icons.Default.Bedtime,
                    onClick = { onStartSession("sleep_prep") },
                    modifier = Modifier.weight(1f)
                )
                MeditationCard(
                    title = "Anxiety Ease",
                    duration = getMeditationDuration("anxiety_ease"),
                    description = "Gentle anxiety relief",
                    icon = Icons.Default.FavoriteBorder,
                    onClick = { onStartSession("anxiety_ease") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                text = "Longer Sessions (15-60 min)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MeditationCard(
                    title = "Deep Relaxation",
                    duration = getMeditationDuration("deep_relaxation"),
                    description = "Complete body and mind reset with progressive relaxation",
                    icon = Icons.Default.Spa,
                    onClick = { onStartSession("deep_relaxation") },
                    modifier = Modifier.weight(1f)
                )
                MeditationCard(
                    title = "Mindful Awareness",
                    duration = getMeditationDuration("mindful_awareness"),
                    description = "Develop present-moment awareness and mindful observation",
                    icon = Icons.Default.Psychology,
                    onClick = { onStartSession("mindful_awareness") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MeditationCard(
                    title = "Extended Focus",
                    duration = getMeditationDuration("extended_focus"),
                    description = "Deep concentration practice for advanced practitioners",
                    icon = Icons.Default.CenterFocusStrong,
                    onClick = { onStartSession("extended_focus") },
                    modifier = Modifier.weight(1f)
                )
                MeditationCard(
                    title = "Complete Zen",
                    duration = getMeditationDuration("complete_zen"),
                    description = "Full meditation experience with breathing, awareness, and stillness",
                    icon = Icons.Default.SelfImprovement,
                    onClick = { onStartSession("complete_zen") },
                    modifier = Modifier.weight(1f)
                )
            }
        }


        // Voice guidance info card (removed separate settings button, just info)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice Guidance Available",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Customize voice, audio mix, and meditation settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }

    // Unified Settings Dialog
    if (showSettings) {
        UnifiedMeditationSettingsDialog(
            settings = meditationSettings,
            context = context,
            soundEnabled = soundEnabled,
            onSoundToggle = ::onSoundToggle,
            backgroundSound = backgroundSound,
            onBackgroundSoundChange = ::onBackgroundSoundChange,
            binauralEnabled = binauralEnabled,
            onBinauralToggle = ::onBinauralToggle,
            binauralTone = binauralTone,
            onBinauralToneChange = ::onBinauralToneChange,
            ttsEnabled = ttsEnabled,
            onTtsToggle = ::onTtsToggle,
            onBackgroundVolumeChange = { /* No-op for this screen */ },
            onBinauralVolumeChange = { /* No-op for this screen */ },
            onTtsVolumeChange = { /* No-op for this screen */ },
            onTtsSpeedChange = { /* No-op for this screen */ },
            onTtsPitchChange = { /* No-op for this screen */ },
            onDismiss = { showSettings = false }
        )
    }

    if (showCustomMeditationDialog) {
        CustomMeditationDialog(
            context = context,
            onDismiss = { showCustomMeditationDialog = false },
            onStartCustomSession = { sessionId ->
                showCustomMeditationDialog = false
                onStartSession(sessionId)
            }
        )
    }

    if (showSavedMeditationsDialog) {
        SavedMeditationsDialog(
            context = context,
            onDismiss = { showSavedMeditationsDialog = false },
            onStartSavedMeditation = { sessionId ->
                showSavedMeditationsDialog = false
                onStartSession(sessionId)
            }
        )
    }
}

@Composable
private fun MeditationCard(
    title: String,
    duration: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useAuriZenIcon: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(180.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (useAuriZenIcon) {
                Image(
                    painter = painterResource(id = R.drawable.aurizen),
                    contentDescription = title,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = duration,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFF9800), // Warm orange color
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
        }
    }
}

@Composable
private fun MeditationStatsCard(stats: MeditationStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Your Progress",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFF9800) // Warm orange color
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    value = stats.totalSessions.toString(),
                    label = "Sessions",
                    icon = Icons.Default.PlayCircle
                )
                StatItem(
                    value = stats.totalMinutes.toString(),
                    label = "Minutes",
                    icon = Icons.Default.Timer
                )
                StatItem(
                    value = stats.currentStreak.toString(),
                    label = "Day Streak",
                    icon = Icons.Default.LocalFireDepartment
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFFFF9800) // Warm orange color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9800) // Warm orange color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun CustomMeditationDialog(
    context: android.content.Context,
    onDismiss: () -> Unit,
    onStartCustomSession: (String) -> Unit
) {
    var selectedDuration by remember { mutableStateOf(15) } // minutes
    var meditationFocus by remember { mutableStateOf("") }
    var currentMood by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }

    // Auto-calculate steps based on duration
    val calculatedSteps = calculateStepsForDuration(selectedDuration)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Create Custom Meditation",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text(
                        "AI will create a personalized meditation session based on your preferences",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                item {
                    Text(
                        "Duration: ${selectedDuration} minutes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = selectedDuration.toFloat(),
                        onValueChange = { selectedDuration = it.toInt() },
                        valueRange = 5f..60f,
                        steps = 10
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("5 min", style = MaterialTheme.typography.labelSmall)
                        Text("60 min", style = MaterialTheme.typography.labelSmall)
                    }
                }

                item {
                    Text(
                        "Steps: $calculatedSteps (auto-calculated)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Steps are automatically calculated based on duration for optimal pacing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                item {
                    OutlinedTextField(
                        value = meditationFocus,
                        onValueChange = { meditationFocus = it },
                        label = { Text("Focus/Goal") },
                        placeholder = { Text("e.g., stress relief, better sleep, creativity...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }

                item {
                    OutlinedTextField(
                        value = currentMood,
                        onValueChange = { currentMood = it },
                        label = { Text("Current Mood") },
                        placeholder = { Text("e.g., anxious, tired, excited...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }

                item {
                    Text(
                        "Experience Level",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Beginner", "Middle", "Advanced").forEach { level ->
                            FilterChip(
                                onClick = { experience = level },
                                label = { 
                                    Text(
                                        level,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                selected = experience == level,
                                modifier = Modifier.weight(.5f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Create session config and navigate immediately
                    val sessionId = "custom_ai_${System.currentTimeMillis()}"
                    val stepDuration = (selectedDuration * 60) / calculatedSteps
                    
                    // Store session configuration
                    val config = CustomMeditationConfig(
                        sessionId = sessionId,
                        totalDuration = selectedDuration,
                        totalSteps = calculatedSteps,
                        stepDuration = stepDuration,
                        focus = meditationFocus,
                        mood = currentMood,
                        experience = experience
                    )
                    
                    storeCustomMeditationConfig(context, config)
                    
                    // Navigate immediately to session screen
                    onStartCustomSession(sessionId)
                },
                enabled = experience.isNotEmpty()
            ) {
                Text("Create Meditation")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

// Start sequential custom meditation with just the first step
private fun startSequentialCustomMeditation(
    context: android.content.Context,
    duration: Int,
    steps: Int,
    focus: String,
    mood: String,
    experience: String,
    onProgress: (String) -> Unit,
    onComplete: (String) -> Unit,
    onError: () -> Unit
) {
    try {
        onProgress("Creating your personalized meditation session...")
        
        val sessionId = "custom_ai_${System.currentTimeMillis()}"
        val stepDuration = (duration * 60) / steps
        
        // Store session configuration
        val config = CustomMeditationConfig(
            sessionId = sessionId,
            totalDuration = duration,
            totalSteps = steps,
            stepDuration = stepDuration,
            focus = focus,
            mood = mood,
            experience = experience
        )
        
        storeCustomMeditationConfig(context, config)
        
        // Generate first step immediately
        generateSingleMeditationStep(
            context = context,
            config = config,
            stepIndex = 0,
            onProgress = onProgress,
            onComplete = { onComplete(sessionId) },
            onError = onError
        )
        
    } catch (e: Exception) {
        Log.e("CustomMeditation", "Error starting sequential meditation", e)
        onError()
    }
}

// Generate a single meditation step
private fun generateSingleMeditationStep(
    context: android.content.Context,
    config: CustomMeditationConfig,
    stepIndex: Int,
    onProgress: (String) -> Unit,
    onComplete: () -> Unit,
    onError: () -> Unit
) {
    try {
        onProgress("Generating step ${stepIndex + 1} of ${config.totalSteps}...")
        
        val inferenceModel = InferenceModel.getInstance(context)
        val prompt = buildSingleStepPrompt(config, stepIndex)
        
        Log.d("CustomMeditation", "Generating step ${stepIndex + 1} with prompt: ${prompt.take(100)}...")
        
        // Use coroutines to generate the step (similar to QuickChat)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).   launch {
            try {
                var fullResponse = ""
                
                val responseFuture = inferenceModel.generateResponseAsync(prompt) { partialResult, done ->
                    if (partialResult != null) {
                        fullResponse += partialResult
                    }
                    
                    if (done) {
                        try {
                            Log.d("CustomMeditation", "Step ${stepIndex + 1} response: $fullResponse")
                            
                            // Parse and store the single step
                            val step = parseSingleStepResponse(fullResponse, config.stepDuration, stepIndex)
                            storeSingleMeditationStep(context, config.sessionId, step, stepIndex)
                            
                            // Switch back to main thread for callback
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                onComplete()
                            }
                        } catch (e: Exception) {
                            Log.e("CustomMeditation", "Error parsing step ${stepIndex + 1}", e)
                            // Create fallback step
                            val fallbackStep = createFallbackStep(stepIndex, config.stepDuration)
                            storeSingleMeditationStep(context, config.sessionId, fallbackStep, stepIndex)
                            
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                onComplete()
                            }
                        }
                    }
                }
                
                // Wait for completion
                responseFuture.get()
                
            } catch (e: Exception) {
                Log.e("CustomMeditation", "Error in inference for step ${stepIndex + 1}", e)
                // Create fallback step and continue
                val fallbackStep = createFallbackStep(stepIndex, config.stepDuration)
                storeSingleMeditationStep(context, config.sessionId, fallbackStep, stepIndex)
                
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    onComplete()
                }
            }
        }
        
    } catch (e: Exception) {
        Log.e("CustomMeditation", "Error generating step", e)
        onError()
    }
}

// Store configuration for sequential generation
private fun storeCustomMeditationConfig(context: android.content.Context, config: CustomMeditationConfig) {
    val prefs = context.getSharedPreferences("custom_meditations", android.content.Context.MODE_PRIVATE)
    val editor = prefs.edit()
    
    // Clear any existing steps for this session to ensure fresh generation
    for (i in 0 until config.totalSteps) {
        editor.remove("${config.sessionId}_step_${i}_title")
        editor.remove("${config.sessionId}_step_${i}_description")
        editor.remove("${config.sessionId}_step_${i}_guidance")
        editor.remove("${config.sessionId}_step_${i}_duration")
    }
    
    editor.putString("${config.sessionId}_focus", config.focus)
    editor.putString("${config.sessionId}_mood", config.mood)
    editor.putString("${config.sessionId}_experience", config.experience)
    editor.putInt("${config.sessionId}_duration", config.totalDuration)
    editor.putInt("${config.sessionId}_total_steps", config.totalSteps)
    editor.putInt("${config.sessionId}_step_duration", config.stepDuration)
    
    editor.apply()
    Log.d("CustomMeditation", "Stored config for session ${config.sessionId} and cleared old steps")
}

// Build prompt for a single meditation step  
private fun buildSingleStepPrompt(config: CustomMeditationConfig, stepIndex: Int): String {
    val stepType = when {
        stepIndex == 0 -> "opening and settling"
        stepIndex == config.totalSteps - 1 -> "closing and integration"
        stepIndex == 1 -> "initial focus and grounding"
        stepIndex == config.totalSteps - 2 -> "deepening and preparation for completion"
        else -> "main practice"
    }
    
    return """Create a ${stepType} meditation step (${stepIndex + 1} of ${config.totalSteps}) for a ${config.totalDuration}-minute meditation.

CONTEXT:
- Focus: ${config.focus.ifEmpty { "general relaxation" }}
- Current mood: ${config.mood.ifEmpty { "neutral" }}
- Experience level: ${config.experience}
- Step duration: ${config.stepDuration} seconds

Create a brief title and 2-3 sentences of warm, clear meditation guidance for this ${stepType} phase.

Title: [Brief step name]
Guidance: [2-3 sentences of meditation instruction]"""
}

// Parse single step response
private fun parseSingleStepResponse(response: String, stepDuration: Int, stepIndex: Int): MeditationStep {
    return try {
        val lines = response.trim().split("\n")
        var title = "Meditation Step ${stepIndex + 1}"
        var guidance = "Focus on your breath and be present in this moment."
        
        for (line in lines) {
            when {
                line.startsWith("Title:", ignoreCase = true) -> {
                    title = line.substringAfter(":").trim()
                }
                line.startsWith("Guidance:", ignoreCase = true) -> {
                    guidance = line.substringAfter(":").trim()
                }
            }
        }
        
        MeditationStep(
            title = title,
            description = "Custom meditation step",
            guidance = guidance,
            durationSeconds = stepDuration
        )
    } catch (e: Exception) {
        Log.e("CustomMeditation", "Error parsing step response", e)
        createFallbackStep(stepIndex, stepDuration)
    }
}

// Create fallback step if generation fails
private fun createFallbackStep(stepIndex: Int, stepDuration: Int): MeditationStep {
    val fallbackSteps = listOf(
        "Take three deep breaths and settle into your meditation space.",
        "Notice your natural breathing rhythm and follow its gentle flow.",
        "Observe any thoughts that arise and let them pass like clouds in the sky.",
        "Feel the support of the ground beneath you and relax into this moment.",
        "Bring your attention to the present moment with gentle awareness.",
        "Take a moment to appreciate this time you've given yourself for peace.",
        "Breathe deeply and prepare to carry this calm with you."
    )
    
    val guidance = fallbackSteps.getOrNull(stepIndex) ?: fallbackSteps[0]
    
    return MeditationStep(
        title = "Mindful Moment ${stepIndex + 1}",
        description = "Guided meditation practice",
        guidance = guidance,
        durationSeconds = stepDuration
    )
}

// Store single meditation step
private fun storeSingleMeditationStep(
    context: android.content.Context,
    sessionId: String,
    step: MeditationStep,
    stepIndex: Int
) {
    val prefs = context.getSharedPreferences("custom_meditations", android.content.Context.MODE_PRIVATE)
    val editor = prefs.edit()
    
    editor.putString("${sessionId}_step_${stepIndex}_title", step.title)
    editor.putString("${sessionId}_step_${stepIndex}_description", step.description)
    editor.putString("${sessionId}_step_${stepIndex}_guidance", step.guidance)
    editor.putInt("${sessionId}_step_${stepIndex}_duration", step.durationSeconds)
    
    editor.apply()
    Log.d("CustomMeditation", "Stored step $stepIndex: ${step.title}")
}

// Calculate optimal step count based on duration
private fun calculateStepsForDuration(durationMinutes: Int): Int {
    return when {
        durationMinutes <= 5 -> 2    // Very short: 2 steps
        durationMinutes <= 10 -> 3   // Short: 3 steps  
        durationMinutes <= 15 -> 4   // Medium: 4 steps
        durationMinutes <= 30 -> 5   // Long: 5 steps
        durationMinutes <= 45 -> 6   // Very long: 6 steps
        else -> 7                    // Extended: 7 steps max
    }
}

@Composable
private fun SavedMeditationsDialog(
    context: android.content.Context,
    onDismiss: () -> Unit,
    onStartSavedMeditation: (String) -> Unit
) {
    var savedMeditations by remember { mutableStateOf(loadSavedMeditations(context)) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Saved Meditations",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            if (savedMeditations.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text(
                        "No saved meditations yet",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Create a custom meditation and save it to see it here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(400.dp)
                ) {
                    items(savedMeditations.size) { index ->
                        val meditation = savedMeditations[index]
                        SavedMeditationItem(
                            meditation = meditation,
                            onSelect = { onStartSavedMeditation(meditation.id) },
                            onDelete = { 
                                deleteSavedMeditation(context, meditation.id)
                                savedMeditations = loadSavedMeditations(context)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SavedMeditationItem(
    meditation: SavedMeditation,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meditation.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (meditation.description.isNotEmpty()) {
                    Text(
                        text = meditation.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "${meditation.totalDuration} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when (meditation.saveType) {
                            SavedMeditationType.EXACT_SESSION -> "Exact"
                            SavedMeditationType.CONFIG_TEMPLATE -> "Template"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun loadSavedMeditations(context: android.content.Context): List<SavedMeditation> {
    val prefs = context.getSharedPreferences("saved_meditations", android.content.Context.MODE_PRIVATE)
    val savedIds = prefs.getStringSet("saved_meditation_ids", emptySet()) ?: emptySet()
    
    return savedIds.mapNotNull { id ->
        try {
            val name = prefs.getString("${id}_name", null) ?: return@mapNotNull null
            val description = prefs.getString("${id}_description", "") ?: ""
            val duration = prefs.getInt("${id}_duration", 0)
            val steps = prefs.getInt("${id}_steps", 0)
            val created = prefs.getLong("${id}_created", 0)
            val used = prefs.getLong("${id}_used", 0)
            val typeString = prefs.getString("${id}_type", null) ?: return@mapNotNull null
            val saveType = SavedMeditationType.valueOf(typeString)
            
            SavedMeditation(
                id = id,
                name = name,
                description = description,
                totalDuration = duration,
                totalSteps = steps,
                createdAt = created,
                lastUsedAt = used,
                saveType = saveType
            )
        } catch (e: Exception) {
            Log.e("SavedMeditations", "Error loading saved meditation $id", e)
            null
        }
    }.sortedByDescending { it.lastUsedAt }
}

private fun deleteSavedMeditation(context: android.content.Context, id: String) {
    val prefs = context.getSharedPreferences("saved_meditations", android.content.Context.MODE_PRIVATE)
    val editor = prefs.edit()
    
    // Remove from saved list
    val savedIds = prefs.getStringSet("saved_meditation_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
    savedIds.remove(id)
    editor.putStringSet("saved_meditation_ids", savedIds)
    
    // Remove all data for this meditation
    val allKeys = prefs.all.keys.filter { it.startsWith("${id}_") }
    allKeys.forEach { key ->
        editor.remove(key)
    }
    
    editor.apply()
    Log.d("SavedMeditations", "Deleted saved meditation: $id")
}