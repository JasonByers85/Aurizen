package com.aurizen.settings

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.aurizen.data.BackgroundSound
import com.aurizen.data.BinauralTone
import com.aurizen.data.CueFrequency
import com.aurizen.data.PauseLength
import com.aurizen.data.PersonalizationLevel
import com.aurizen.data.CueStyle
import com.aurizen.utils.MeditationAudioManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun UnifiedMeditationSettingsDialog(
    settings: MeditationSettings,
    context: Context,
    soundEnabled: Boolean,
    onSoundToggle: () -> Unit,
    backgroundSound: BackgroundSound,
    onBackgroundSoundChange: (BackgroundSound) -> Unit,
    binauralEnabled: Boolean,
    onBinauralToggle: () -> Unit,
    binauralTone: BinauralTone,
    onBinauralToneChange: (BinauralTone) -> Unit,
    ttsEnabled: Boolean,
    onTtsToggle: () -> Unit,
    onBackgroundVolumeChange: ((Float) -> Unit)? = null,
    onBinauralVolumeChange: ((Float) -> Unit)? = null,
    onTtsVolumeChange: ((Float) -> Unit)? = null,
    onTtsSpeedChange: ((Float) -> Unit)? = null,
    onTtsPitchChange: ((Float) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var currentTab by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header with tabs
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Meditation Settings",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        TabRow(
                            selectedTabIndex = currentTab,
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Tab(
                                selected = currentTab == 0,
                                onClick = { currentTab = 0 },
                                text = { Text("Audio Mix") }
                            )
                            Tab(
                                selected = currentTab == 1,
                                onClick = { currentTab = 1 },
                                text = { Text("Voice") }
                            )
                            Tab(
                                selected = currentTab == 2,
                                onClick = { currentTab = 2 },
                                text = { Text("Pacing") }
                            )
                        }
                    }
                }

                // Tab content
                when (currentTab) {
                    0 -> AudioMixerTab(
                        settings = settings,
                        context = context,
                        soundEnabled = soundEnabled,
                        onSoundToggle = onSoundToggle,
                        backgroundSound = backgroundSound,
                        onBackgroundSoundChange = onBackgroundSoundChange,
                        binauralEnabled = binauralEnabled,
                        onBinauralToggle = onBinauralToggle,
                        binauralTone = binauralTone,
                        onBinauralToneChange = onBinauralToneChange,
                        ttsEnabled = ttsEnabled,
                        onTtsToggle = onTtsToggle,
                        onBackgroundVolumeChange = onBackgroundVolumeChange,
                        onBinauralVolumeChange = onBinauralVolumeChange,
                        onTtsVolumeChange = onTtsVolumeChange
                    )
                    1 -> VoiceSettingsTab(settings, context, onTtsSpeedChange, onTtsPitchChange, onTtsVolumeChange)
                    2 -> PacingSettingsTab(settings)
                }
            }
        }
    }
}

@Composable
private fun AudioMixerTab(
    settings: MeditationSettings,
    context: Context,
    soundEnabled: Boolean,
    onSoundToggle: () -> Unit,
    backgroundSound: BackgroundSound,
    onBackgroundSoundChange: (BackgroundSound) -> Unit,
    binauralEnabled: Boolean,
    onBinauralToggle: () -> Unit,
    binauralTone: BinauralTone,
    onBinauralToneChange: (BinauralTone) -> Unit,
    ttsEnabled: Boolean,
    onTtsToggle: () -> Unit,
    onBackgroundVolumeChange: ((Float) -> Unit)? = null,
    onBinauralVolumeChange: ((Float) -> Unit)? = null,
    onTtsVolumeChange: ((Float) -> Unit)? = null
) {
    // Load current values from settings instead of hardcoded values
    var voiceVolume by remember { mutableStateOf(settings.getTtsVolume()) }
    var backgroundVolume by remember { mutableStateOf(settings.getVolume()) }
    var binauralVolume by remember { mutableStateOf(settings.getBinauralVolume()) }

    var isVoicePlaying by remember { mutableStateOf(false) }
    var isBackgroundPlaying by remember { mutableStateOf(false) }
    var isBinauralPlaying by remember { mutableStateOf(false) }

    var testTts by remember { mutableStateOf<TextToSpeech?>(null) }
    var audioManager by remember { mutableStateOf<MeditationAudioManager?>(null) }

    var selectedBackground by remember { mutableStateOf(backgroundSound) }
    var selectedBinaural by remember { mutableStateOf(binauralTone) }

    // Initialize audio systems
    LaunchedEffect(Unit) {
        audioManager = MeditationAudioManager(context)
        testTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                testTts?.let { tts ->
                    tts.setLanguage(Locale.getDefault())
                    tts.setSpeechRate(settings.getTtsSpeed())
                    tts.setPitch(settings.getTtsPitch())
                    val savedVoice = settings.getTtsVoice()
                    if (savedVoice.isNotEmpty()) {
                        tts.voices?.find { it.name == savedVoice }?.let { voice ->
                            tts.voice = voice
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            testTts?.stop()
            testTts?.shutdown()
            audioManager?.release()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🎛️ Audio Mixer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Test and balance your meditation audio levels",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Voice Audio Section - Volume control moved to Voice Settings tab
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Voice Guidance",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Switch(
                            checked = ttsEnabled,
                            onCheckedChange = { onTtsToggle() }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Detailed voice settings available in the Voice tab",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Background Sound Section
        item {
            AudioChannelCard(
                title = "Background Sound",
                icon = Icons.Default.MusicNote,
                volume = backgroundVolume,
                isPlaying = isBackgroundPlaying,
                onVolumeChange = {
                    backgroundVolume = it
                    settings.setVolume(it)
                    audioManager?.setVolume(it)
                    onBackgroundVolumeChange?.invoke(it)
                },
                onPlayStop = {
                    if (isBackgroundPlaying) {
                        audioManager?.stopBackgroundSound()
                        isBackgroundPlaying = false
                    } else {
                        if (selectedBackground != BackgroundSound.NONE) {
                            audioManager?.setVolume(backgroundVolume)
                            audioManager?.playBackgroundSound(selectedBackground)
                            isBackgroundPlaying = true
                        }
                    }
                },
                extraContent = {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sound:", style = MaterialTheme.typography.labelSmall)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(BackgroundSound.values().size) { index ->
                            val sound = BackgroundSound.values()[index]
                            FilterChip(
                                onClick = {
                                    selectedBackground = sound
                                    settings.setBackgroundSound(sound)
                                    onBackgroundSoundChange(sound)
                                    if (isBackgroundPlaying) {
                                        audioManager?.setVolume(backgroundVolume)
                                        audioManager?.playBackgroundSound(sound)
                                    }
                                },
                                label = { Text(sound.displayName, style = MaterialTheme.typography.labelSmall) },
                                selected = selectedBackground == sound
                            )
                        }
                    }
                }
            )
        }

        // Binaural Tones Section
        item {
            AudioChannelCard(
                title = "Binaural Tones",
                icon = Icons.Default.GraphicEq,
                volume = binauralVolume,
                isPlaying = isBinauralPlaying,
                onVolumeChange = {
                    binauralVolume = it
                    settings.setBinauralVolume(it)
                    audioManager?.setBinauralVolume(it)
                    onBinauralVolumeChange?.invoke(it)
                },
                onPlayStop = {
                    if (isBinauralPlaying) {
                        audioManager?.stopBinauralTone()
                        isBinauralPlaying = false
                    } else {
                        if (selectedBinaural != BinauralTone.NONE) {
                            audioManager?.setBinauralVolume(binauralVolume)
                            audioManager?.playBinauralTone(selectedBinaural)
                            isBinauralPlaying = true
                        }
                    }
                },
                extraContent = {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Elegant binaural tone cards
                    BinauralTone.values().forEach { tone ->
                        val isSelected = selectedBinaural == tone
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedBinaural = tone
                                    settings.setBinauralTone(tone)
                                    onBinauralToneChange(tone)
                                    if (isBinauralPlaying) {
                                        if (tone != BinauralTone.NONE) {
                                            audioManager?.setBinauralVolume(binauralVolume)
                                            audioManager?.playBinauralTone(tone)
                                        } else {
                                            audioManager?.stopBinauralTone()
                                            isBinauralPlaying = false
                                        }
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                            ),
                            border = if (isSelected) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            } else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Frequency badge
                                if (tone != BinauralTone.NONE) {
                                    Card(
                                        modifier = Modifier.size(48.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                            }
                                        ),
                                        shape = CircleShape
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${tone.frequency.toInt()}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    // None option - show a different icon
                                    Card(
                                        modifier = Modifier.size(48.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) {
                                                MaterialTheme.colorScheme.outline
                                            } else {
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            }
                                        ),
                                        shape = CircleShape
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "—",
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                // Content
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = tone.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    if (tone != BinauralTone.NONE) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = tone.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            }
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "No binaural tone",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            }
                                        )
                                    }
                                }
                                
                                // Selection indicator
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }

        // Remove the separate description card since it's now integrated
        // Selected binaural tone description - REMOVED

        // Play All / Stop All
        item {
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            testTts?.stop()
                            audioManager?.stopBackgroundSound()
                            audioManager?.stopBinauralTone()
                            isVoicePlaying = false
                            isBackgroundPlaying = false
                            isBinauralPlaying = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop All")
                    }

                    Button(
                        onClick = {
                            if (selectedBackground != BackgroundSound.NONE) {
                                audioManager?.setVolume(backgroundVolume)
                                audioManager?.playBackgroundSound(selectedBackground)
                                isBackgroundPlaying = true
                            }
                            if (selectedBinaural != BinauralTone.NONE) {
                                audioManager?.setBinauralVolume(binauralVolume)
                                audioManager?.playBinauralTone(selectedBinaural)
                                isBinauralPlaying = true
                            }
                            testTts?.let { tts ->
                                tts.setSpeechRate(settings.getTtsSpeed())
                                tts.setPitch(settings.getTtsPitch())
                                
                                // Apply voice setting
                                val savedVoice = settings.getTtsVoice()
                                if (savedVoice.isNotEmpty()) {
                                    tts.voices?.find { it.name == savedVoice }?.let { voice ->
                                        tts.voice = voice
                                    }
                                }
                                
                                // Create bundle with volume parameter for TTS
                                val params = Bundle().apply {
                                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, voiceVolume)
                                }
                                tts.speak(
                                    "Testing complete meditation audio experience with voice guidance, background sounds, and binaural tones.",
                                    TextToSpeech.QUEUE_FLUSH,
                                    params,
                                    "full_test"
                                )
                            }
                            isVoicePlaying = true
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(8000)
                                isVoicePlaying = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Mix")
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioChannelCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    volume: Float,
    isPlaying: Boolean,
    onVolumeChange: (Float) -> Unit,
    onPlayStop: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(onClick = onPlayStop) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        tint = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Volume slider
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(40.dp)
                )
            }

            extraContent?.invoke()
        }
    }
}

@Composable
private fun VoiceSettingsTab(
    settings: MeditationSettings,
    context: Context,
    onTtsSpeedChange: ((Float) -> Unit)? = null,
    onTtsPitchChange: ((Float) -> Unit)? = null,
    onTtsVolumeChange: ((Float) -> Unit)? = null
) {
    var ttsSpeed by remember { mutableStateOf(settings.getTtsSpeed()) }
    var ttsPitch by remember { mutableStateOf(settings.getTtsPitch()) }
    var ttsVolume by remember { mutableStateOf(settings.getTtsVolume()) }
    var selectedVoice by remember { mutableStateOf(settings.getTtsVoice()) }
    var selectedGender by remember { mutableStateOf(settings.getTtsGender()) }
    var availableVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var testTts by remember { mutableStateOf<TextToSpeech?>(null) }

    LaunchedEffect(Unit) {
        testTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                testTts?.let { tts ->
                    val voices = tts.voices?.filter { voice ->
                        (voice.locale.language == Locale.getDefault().language ||
                                voice.locale.language == "en") &&
                        !voice.isNetworkConnectionRequired &&
                        voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
                    }?.sortedWith(compareBy(
                        { getVoiceGenderFromName(it.name) }, // Sort by gender
                        { it.locale.displayName },
                        { it.name }
                    )) ?: emptyList()
                    availableVoices = voices
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            testTts?.stop()
            testTts?.shutdown()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Speech Speed: ${String.format("%.1f", ttsSpeed)}x")
                    Slider(
                        value = ttsSpeed,
                        onValueChange = {
                            ttsSpeed = it
                            settings.setTtsSpeed(it)
                            testTts?.setSpeechRate(it)
                            onTtsSpeedChange?.invoke(it)
                        },
                        valueRange = 0.5f..1.5f
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Voice Volume: ${String.format("%.1f", ttsVolume)}")
                    Slider(
                        value = ttsVolume,
                        onValueChange = {
                            ttsVolume = it
                            settings.setTtsVolume(it)
                            onTtsVolumeChange?.invoke(it)
                        },
                        valueRange = 0.0f..1.0f
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Voice Pitch: ${String.format("%.1f", ttsPitch)}")
                    Slider(
                        value = ttsPitch,
                        onValueChange = {
                            ttsPitch = it
                            settings.setTtsPitch(it)
                            testTts?.setPitch(it)
                            onTtsPitchChange?.invoke(it)
                        },
                        valueRange = 0.6f..1.4f
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            testTts?.let { tts ->
                                tts.setPitch(ttsPitch)
                                tts.setSpeechRate(ttsSpeed)
                                
                                if (selectedVoice.isNotEmpty()) {
                                    tts.voices?.find { it.name == selectedVoice }?.let { voice ->
                                        tts.voice = voice
                                    }
                                }
                                
                                val params = Bundle().apply {
                                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
                                }
                                tts.speak(
                                    "This is a test of your meditation voice settings. Notice how the speed, pitch, and volume affect your meditation guidance.",
                                    TextToSpeech.QUEUE_FLUSH,
                                    params,
                                    "voice_test"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Voice Settings")
                    }
                }
            }
        }

        // Gender Preference
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Voice Gender Preference",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val genderOptions = listOf("Any", "Male", "Female")
                    genderOptions.forEach { gender ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedGender == gender,
                                onClick = {
                                    selectedGender = gender
                                    settings.setTtsGender(gender)
                                    
                                    // Auto-apply gender preference to voice selection
                                    if (gender != "Any") {
                                        val filteredVoices = availableVoices.filter { voice ->
                                            when (gender) {
                                                "Male" -> getVoiceGenderFromName(voice.name) == "male"
                                                "Female" -> getVoiceGenderFromName(voice.name) == "female"
                                                else -> true
                                            }
                                        }
                                        filteredVoices.firstOrNull()?.let { voice ->
                                            selectedVoice = voice.name
                                            settings.setTtsVoice(voice.name)
                                            testTts?.voice = voice
                                        }
                                    }
                                }
                            )
                            Text(
                                text = gender,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (availableVoices.isNotEmpty()) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Voice Selection",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Group voices by gender
                        val femaleVoices = availableVoices.filter { voice ->
                            getVoiceGenderFromName(voice.name) == "female"
                        }
                        val maleVoices = availableVoices.filter { voice ->
                            getVoiceGenderFromName(voice.name) == "male"
                        }
                        val unknownVoices = availableVoices.filter { voice ->
                            getVoiceGenderFromName(voice.name) == "unknown"
                        }
                        
                        // Show female voices
                        if (femaleVoices.isNotEmpty()) {
                            Text(
                                text = "Female Voices",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        femaleVoices.forEach { voice ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedVoice == voice.name,
                                    onClick = {
                                        selectedVoice = voice.name
                                        settings.setTtsVoice(voice.name)
                                        testTts?.voice = voice
                                    }
                                )
                                Text(voice.name.replace("_", " "))
                            }
                        }
                        
                        // Show male voices
                        if (maleVoices.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Male Voices",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        maleVoices.forEach { voice ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedVoice == voice.name,
                                    onClick = {
                                        selectedVoice = voice.name
                                        settings.setTtsVoice(voice.name)
                                        testTts?.voice = voice
                                    }
                                )
                                Text(voice.name.replace("_", " "))
                            }
                        }
                        
                        // Show unknown gender voices
                        if (unknownVoices.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Other Voices",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        unknownVoices.forEach { voice ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedVoice == voice.name,
                                    onClick = {
                                        selectedVoice = voice.name
                                        settings.setTtsVoice(voice.name)
                                        testTts?.voice = voice
                                    }
                                )
                                Text(voice.name.replace("_", " "))
                            }
                        }
                    }
                }
            }
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

@Composable
fun PacingSettingsTab(settings: MeditationSettings) {
    var gentleCueFrequency by remember { mutableStateOf(settings.getCueFrequency()) }
    var pauseLength by remember { mutableStateOf(settings.getPauseLength()) }
    var personalizationLevel by remember { mutableStateOf(settings.getPersonalizationLevel()) }
    var enableGentleCues by remember { mutableStateOf(settings.getEnableGentleCues()) }
    var breathingSync by remember { mutableStateOf(settings.getBreathingSync()) }
    var fadeInOut by remember { mutableStateOf(settings.getFadeInOut()) }
    var preferredCueStyle by remember { mutableStateOf(settings.getPreferredCueStyle()) }
    var instructionRatio by remember { mutableStateOf(settings.getInstructionToSilenceRatio()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Meditation Pacing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Customize how your meditation sessions are paced with intelligent guidance and practice periods.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Gentle Cues",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = enableGentleCues,
                            onCheckedChange = {
                                enableGentleCues = it
                                settings.setEnableGentleCues(it)
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Gentle reminders during practice periods to help maintain focus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (enableGentleCues) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Gentle Cue Frequency",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        CueFrequency.values().forEach { frequency ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = gentleCueFrequency == frequency,
                                    onClick = {
                                        gentleCueFrequency = frequency
                                        settings.setCueFrequency(frequency)
                                    }
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = when (frequency) {
                                            CueFrequency.NONE -> "None"
                                            CueFrequency.LOW -> "Low"
                                            CueFrequency.MEDIUM -> "Medium"
                                            CueFrequency.HIGH -> "High"
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = when (frequency) {
                                            CueFrequency.NONE -> "Pure silence during practice"
                                            CueFrequency.LOW -> "Every 2-3 minutes"
                                            CueFrequency.MEDIUM -> "Every 60-90 seconds"
                                            CueFrequency.HIGH -> "Every 30-45 seconds"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Cue Style",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        CueStyle.values().forEach { style ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = preferredCueStyle == style,
                                    onClick = {
                                        preferredCueStyle = style
                                        settings.setPreferredCueStyle(style)
                                    }
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = when (style) {
                                            CueStyle.BREATHING_FOCUSED -> "Breathing Focused"
                                            CueStyle.MINDFULNESS_FOCUSED -> "Mindfulness Focused"
                                            CueStyle.RELAXATION_FOCUSED -> "Relaxation Focused"
                                            CueStyle.BODY_AWARENESS -> "Body Awareness"
                                            CueStyle.MIXED -> "Mixed"
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = when (style) {
                                            CueStyle.BREATHING_FOCUSED -> "Focus on natural breathing"
                                            CueStyle.MINDFULNESS_FOCUSED -> "Present moment awareness"
                                            CueStyle.RELAXATION_FOCUSED -> "Deep relaxation cues"
                                            CueStyle.BODY_AWARENESS -> "Body sensations and scanning"
                                            CueStyle.MIXED -> "Variety of gentle reminders"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Personalization Level",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    PersonalizationLevel.values().forEach { level ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = personalizationLevel == level,
                                onClick = {
                                    personalizationLevel = level
                                    settings.setPersonalizationLevel(level)
                                }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = when (level) {
                                        PersonalizationLevel.MINIMAL -> "Minimal"
                                        PersonalizationLevel.ADAPTIVE -> "Adaptive"
                                        PersonalizationLevel.GUIDED -> "Guided"
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = when (level) {
                                        PersonalizationLevel.MINIMAL -> "Basic instruction with minimal guidance"
                                        PersonalizationLevel.ADAPTIVE -> "Adapts to your meditation experience"
                                        PersonalizationLevel.GUIDED -> "More frequent gentle guidance"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Instruction to Practice Ratio",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Instruction: ${(instructionRatio * 100).toInt()}% • Practice: ${((1f - instructionRatio) * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Slider(
                        value = instructionRatio,
                        onValueChange = {
                            instructionRatio = it
                            settings.setInstructionToSilenceRatio(it)
                        },
                        valueRange = 0.1f..0.7f,
                        steps = 11
                    )
                    
                    Text(
                        text = "Balance between spoken guidance and silent practice time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Breathing Sync",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = breathingSync,
                            onCheckedChange = {
                                breathingSync = it
                                settings.setBreathingSync(it)
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Sync cues with natural breathing rhythm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fade Effects",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = fadeInOut,
                            onCheckedChange = {
                                fadeInOut = it
                                settings.setFadeInOut(it)
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Gentle fade in/out for cues and transitions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}