package com.aurizen.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurizen.settings.BreathingSettings
import com.aurizen.settings.BreathingSettingsDialog
import kotlinx.coroutines.delay
import com.aurizen.data.BackgroundSound
import com.aurizen.data.BinauralTone
import com.aurizen.features.breathing.BreathingProgram
import com.aurizen.features.breathing.BreathingAudioManager

// Breathing session states (copying meditation pattern)
enum class BreathingSessionState {
    READY,
    ACTIVE,
    PAUSED,
    COMPLETED
}

// Breathing progress data (copying meditation pattern)
data class BreathingProgress(
    val timeRemaining: Int,
    val currentCycle: Int,
    val totalCycles: Int,
    val currentPhase: String,
    val phaseProgress: Float,
    val phaseCountdown: Int
)

// Breathing audio settings (copying meditation pattern)
data class BreathingAudioSettings(
    val soundEnabled: Boolean,
    val backgroundSound: BackgroundSound,
    val binauralEnabled: Boolean,
    val binauralTone: BinauralTone,
    val ttsEnabled: Boolean
)

@Composable
internal fun UnifiedBreathingSessionRoute(
    program: BreathingProgram,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    
    var sessionState by remember { mutableStateOf(BreathingSessionState.READY) }
    var isActive by remember { mutableStateOf(false) }
    var currentPhase by remember { mutableStateOf("Ready") }
    var timeRemaining by remember { mutableIntStateOf(program.totalDuration) }
    var currentCycle by remember { mutableIntStateOf(0) }
    var phaseProgress by remember { mutableFloatStateOf(0f) }
    var phaseCountdown by remember { mutableIntStateOf(0) }
    
    // Audio setup (copying meditation pattern)
    val breathingSettings = remember { BreathingSettings.getInstance(context) }
    val audioManager = remember { BreathingAudioManager(context) }
    var isAudioReady by remember { mutableStateOf(false) }
    
    val audioSettings = BreathingAudioSettings(
        soundEnabled = breathingSettings.isSoundEnabled(),
        backgroundSound = breathingSettings.getBackgroundSound(),
        binauralEnabled = breathingSettings.isBinauralEnabled(),
        binauralTone = breathingSettings.getBinauralTone(),
        ttsEnabled = breathingSettings.isTtsEnabled()
    )
    
    val progress = BreathingProgress(
        timeRemaining = timeRemaining,
        currentCycle = currentCycle,
        totalCycles = program.totalDuration / (program.inhaleSeconds + program.holdSeconds + program.exhaleSeconds),
        currentPhase = currentPhase,
        phaseProgress = phaseProgress,
        phaseCountdown = phaseCountdown
    )
    
    // Initialize audio (copying meditation pattern)
    LaunchedEffect(Unit) {
        audioManager.initialize {
            isAudioReady = true
        }
    }
    
    // Cleanup audio on dispose (copying meditation pattern)
    DisposableEffect(Unit) {
        onDispose {
            audioManager.destroy()
        }
    }
    
    // Handle completion (copying meditation pattern)
    LaunchedEffect(sessionState) {
        if (sessionState == BreathingSessionState.COMPLETED) {
            delay(2000) // Show completion message for 2 seconds
            onComplete()
        }
    }
    
    // Breathing cycle logic (simplified from complex version)
    LaunchedEffect(isActive, isAudioReady) {
        if (isActive && isAudioReady) {
            audioManager.startAudio()
            
            val totalCycleTime = program.inhaleSeconds + program.holdSeconds + program.exhaleSeconds
            var currentCycleTime = 0
            var lastSpokenPhase = ""
            
            while (isActive && timeRemaining > 0) {
                delay(1000) // Simple 1-second updates like meditation
                currentCycleTime += 1
                timeRemaining = (timeRemaining - 1).coerceAtLeast(0)
                
                val cyclePosition = currentCycleTime % totalCycleTime
                currentCycle = currentCycleTime / totalCycleTime
                
                when {
                    cyclePosition < program.inhaleSeconds -> {
                        if (currentPhase != "Breathe In") {
                            currentPhase = "Breathe In"
                            if (lastSpokenPhase != currentPhase) {
                                audioManager.speak(program.inhaleMessage)
                                lastSpokenPhase = currentPhase
                            }
                        }
                        phaseProgress = (cyclePosition / program.inhaleSeconds.toFloat()).coerceIn(0f, 1f)
                        phaseCountdown = (program.inhaleSeconds - cyclePosition).coerceAtLeast(0)
                    }
                    cyclePosition < program.inhaleSeconds + program.holdSeconds -> {
                        if (currentPhase != "Hold") {
                            currentPhase = "Hold"
                            if (lastSpokenPhase != currentPhase) {
                                audioManager.speak("Hold your breath")
                                lastSpokenPhase = currentPhase
                            }
                        }
                        phaseProgress = 1f
                        val holdPosition = cyclePosition - program.inhaleSeconds
                        phaseCountdown = (program.holdSeconds - holdPosition).coerceAtLeast(0)
                    }
                    else -> {
                        if (currentPhase != "Breathe Out") {
                            currentPhase = "Breathe Out"
                            if (lastSpokenPhase != currentPhase) {
                                audioManager.speak(program.exhaleMessage)
                                lastSpokenPhase = currentPhase
                            }
                        }
                        val exhaleProgress = (cyclePosition - program.inhaleSeconds - program.holdSeconds) / program.exhaleSeconds.toFloat()
                        phaseProgress = (1f - exhaleProgress).coerceIn(0f, 1f)
                        val exhalePosition = cyclePosition - program.inhaleSeconds - program.holdSeconds
                        phaseCountdown = (program.exhaleSeconds - exhalePosition).coerceAtLeast(0)
                    }
                }
            }
            
            if (timeRemaining <= 0) {
                isActive = false
                sessionState = BreathingSessionState.COMPLETED
                audioManager.speak("Breathing session complete. Well done!")
            }
            
            audioManager.stopAudio()
        } else if (!isActive) {
            audioManager.stopAudio()
        }
    }
    
    UnifiedBreathingSessionScreen(
        program = program,
        sessionState = sessionState,
        progress = progress,
        isActive = isActive,
        audioSettings = audioSettings,
        onPlayPause = { 
            isActive = !isActive
            sessionState = if (isActive) BreathingSessionState.ACTIVE else BreathingSessionState.PAUSED
        },
        onStop = { 
            isActive = false
            sessionState = BreathingSessionState.READY
            timeRemaining = program.totalDuration
            currentPhase = "Ready"
            phaseProgress = 0f
            phaseCountdown = 0
            audioManager.stopAudio()
        },
        onSoundToggle = {
            val newEnabled = !breathingSettings.isSoundEnabled()
            breathingSettings.setSoundEnabled(newEnabled)
            audioManager.updateBackgroundAudio()
        },
        onBinauralToggle = {
            val newEnabled = !breathingSettings.isBinauralEnabled()
            breathingSettings.setBinauralEnabled(newEnabled)
            audioManager.updateBinauralAudio()
        },
        onTtsToggle = {
            val newEnabled = !breathingSettings.isTtsEnabled()
            breathingSettings.setTtsEnabled(newEnabled)
        },
        onBack = onBack,
        breathingSettings = breathingSettings,
        audioManager = audioManager,
        context = context
    )
}

@Composable
fun UnifiedBreathingSessionScreen(
    program: BreathingProgram,
    sessionState: BreathingSessionState,
    progress: BreathingProgress,
    isActive: Boolean,
    audioSettings: BreathingAudioSettings,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSoundToggle: () -> Unit,
    onBinauralToggle: () -> Unit,
    onTtsToggle: () -> Unit,
    onBack: () -> Unit,
    breathingSettings: BreathingSettings,
    audioManager: BreathingAudioManager,
    context: android.content.Context
) {
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Copy exact gradient background from meditation
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
            .padding(16.dp)
    ) {
        // Copy exact top bar with audio controls from meditation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // Copy exact audio controls from meditation
            Row {
                // TTS toggle
                IconButton(onClick = onTtsToggle) {
                    Icon(
                        if (audioSettings.ttsEnabled) Icons.Default.RecordVoiceOver else Icons.Default.VoiceOverOff,
                        contentDescription = if (audioSettings.ttsEnabled) "Disable Voice" else "Enable Voice",
                        tint = if (audioSettings.ttsEnabled) Color(0xFF64B5F6) else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Background sound toggle
                IconButton(onClick = onSoundToggle) {
                    Icon(
                        if (audioSettings.soundEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff,
                        contentDescription = if (audioSettings.soundEnabled) "Disable Background Sound" else "Enable Background Sound",
                        tint = if (audioSettings.soundEnabled) Color(0xFF64B5F6) else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Binaural tone toggle
                IconButton(onClick = onBinauralToggle) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = if (audioSettings.binauralEnabled) "Disable Binaural" else "Enable Binaural",
                        tint = if (audioSettings.binauralEnabled) Color(0xFF64B5F6) else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Settings dialog
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Copy exact session progress from meditation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cycle ${progress.currentCycle + 1} of ${progress.totalCycles}",
                color = Color(0xFF64B5F6),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = formatTime(progress.timeRemaining),
                color = Color(0xFF64B5F6),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Copy exact progress bar from meditation
        LinearProgressIndicator(
            progress = { if (program.totalDuration > 0) (program.totalDuration - progress.timeRemaining).toFloat() / program.totalDuration.toFloat() else 0f },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF64B5F6),
            trackColor = Color.White.copy(alpha = 0.2f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Main content area (copying meditation pattern)
        when (sessionState) {
            BreathingSessionState.READY -> {
                ReadyBreathingContent(
                    program = program,
                    onStart = onPlayPause
                )
            }
            
            BreathingSessionState.ACTIVE,
            BreathingSessionState.PAUSED -> {
                ActiveBreathingContent(
                    program = program,
                    progress = progress,
                    isActive = isActive,
                    onPlayPause = onPlayPause,
                    onStop = onStop
                )
            }
            
            BreathingSessionState.COMPLETED -> {
                CompletedBreathingContent()
            }
        }
    }

    // Copy exact settings dialog pattern from meditation
    if (showSettingsDialog) {
        BreathingSettingsDialog(
            breathingSettings = breathingSettings,
            context = context,
            audioManager = audioManager,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun ReadyBreathingContent(
    program: BreathingProgram,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = program.name,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = program.description,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "${program.inhaleSeconds}s inhale • ${program.holdSeconds}s hold • ${program.exhaleSeconds}s exhale",
            color = Color(0xFF64B5F6),
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            ),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Breathing")
        }
    }
}

@Composable
private fun ActiveBreathingContent(
    program: BreathingProgram,
    progress: BreathingProgress,
    isActive: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = progress.currentPhase,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Copy exact visual meditation guide pattern
        Card(
            modifier = Modifier.size(200.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            ),
            shape = CircleShape
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                BreathingVisual(
                    isActive = isActive,
                    phase = progress.currentPhase,
                    progress = progress.phaseProgress,
                    countdown = progress.phaseCountdown,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Copy exact text content area from meditation
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = when (progress.currentPhase) {
                        "Breathe In" -> program.inhaleMessage
                        "Breathe Out" -> program.exhaleMessage
                        "Hold" -> "Feel the stillness and peace within"
                        else -> "Follow your natural breathing rhythm"
                    },
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Start
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Copy exact control buttons from meditation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Gray
                ),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop")
            }
            
            Button(
                onClick = onPlayPause,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color(0xFFFF9800) else Color(0xFF4CAF50)
                ),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(
                    if (isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isActive) "Pause" else "Resume")
            }
        }
    }
}

@Composable
private fun CompletedBreathingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Breathing Complete",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Well done! Take a moment to notice how you feel.",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BreathingVisual(
    isActive: Boolean,
    phase: String,
    progress: Float,
    countdown: Int,
    modifier: Modifier = Modifier
) {
    // Copy meditation's simple animation pattern but adapt for breathing
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing), // Slightly longer for breathing
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val baseRadius = size.minDimension / 6
            
            if (isActive) {
                drawBreathingVisual(centerX, centerY, baseRadius, breathingScale, phase)
            } else {
                drawStaticBreathingVisual(centerX, centerY, baseRadius)
            }
        }
        
        // Copy countdown display from meditation but simpler
        if (isActive && countdown > 0) {
            Text(
                text = countdown.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

private fun DrawScope.drawBreathingVisual(centerX: Float, centerY: Float, baseRadius: Float, scale: Float, phase: String) {
    val radius = baseRadius * scale
    val color = when (phase) {
        "Breathe In" -> Color(0xFF4CAF50) // Green for inhale
        "Breathe Out" -> Color(0xFF2196F3) // Blue for exhale  
        "Hold" -> Color(0xFFFF9800) // Orange for hold
        else -> Color(0xFF64B5F6)
    }
    
    // Simple circles like meditation
    drawCircle(
        color = color.copy(alpha = 0.3f),
        radius = radius,
        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
    )
    drawCircle(
        color = color.copy(alpha = 0.1f),
        radius = radius * 1.5f,
        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
    )
}

private fun DrawScope.drawStaticBreathingVisual(centerX: Float, centerY: Float, baseRadius: Float) {
    drawCircle(
        color = Color(0xFF64B5F6).copy(alpha = 0.3f),
        radius = baseRadius,
        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
    )
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}