package com.aurizen

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
fun BreathingSettingsDialog(
    breathingSettings: BreathingSettings,
    context: Context,
    audioManager: BreathingAudioManager,
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
                                text = "Breathing Settings",
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
                        }
                    }
                }

                // Tab content
                when (currentTab) {
                    0 -> BreathingAudioMixerTab(breathingSettings, context, audioManager)
                    1 -> BreathingVoiceSettingsTab(breathingSettings, context)
                }
            }
        }
    }
}

@Composable
private fun BreathingAudioMixerTab(
    breathingSettings: BreathingSettings,
    context: Context,
    mainAudioManager: BreathingAudioManager
) {
    var voiceVolume by remember { mutableStateOf(breathingSettings.getTtsVolume()) }
    var backgroundVolume by remember { mutableStateOf(breathingSettings.getVolume()) }
    var binauralVolume by remember { mutableStateOf(breathingSettings.getBinauralVolume()) }

    var isVoicePlaying by remember { mutableStateOf(false) }
    var isBackgroundPlaying by remember { mutableStateOf(false) }
    var isBinauralPlaying by remember { mutableStateOf(false) }

    var testTts by remember { mutableStateOf<TextToSpeech?>(null) }

    var selectedBackground by remember { mutableStateOf(breathingSettings.getBackgroundSound()) }
    var selectedBinaural by remember { mutableStateOf(breathingSettings.getBinauralTone()) }

    // Initialize only TTS for testing voice
    LaunchedEffect(Unit) {
        testTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                testTts?.let { tts ->
                    tts.setLanguage(Locale.getDefault())
                    tts.setSpeechRate(breathingSettings.getTtsSpeed())
                    tts.setPitch(breathingSettings.getTtsPitch())
                    val savedVoice = breathingSettings.getTtsVoice()
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
                        text = "🫁 Breathing Audio Mixer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Test and balance your breathing session audio levels",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Voice Audio Section
        item {
            AudioChannelCard(
                title = "Voice Guidance",
                icon = Icons.Default.RecordVoiceOver,
                volume = voiceVolume,
                isPlaying = isVoicePlaying,
                onVolumeChange = {
                    voiceVolume = it
                    breathingSettings.setTtsVolume(it)
                },
                onPlayStop = {
                    if (isVoicePlaying) {
                        testTts?.stop()
                        isVoicePlaying = false
                    } else {
                        testTts?.let { tts ->
                            tts.setSpeechRate(breathingSettings.getTtsSpeed())
                            tts.setPitch(breathingSettings.getTtsPitch())
                            
                            val params = Bundle().apply {
                                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, voiceVolume)
                            }
                            tts.speak(
                                "Breathe in positive energy, breathe out stress and tension.",
                                TextToSpeech.QUEUE_FLUSH,
                                params,
                                "voice_test"
                            )
                        }
                        isVoicePlaying = true
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(4000)
                            isVoicePlaying = false
                        }
                    }
                }
            )
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
                    breathingSettings.setVolume(it)
                    mainAudioManager.getMeditationAudioManager().setVolume(it)
                },
                onPlayStop = {
                    if (isBackgroundPlaying) {
                        mainAudioManager.getMeditationAudioManager().stopBackgroundSound()
                        isBackgroundPlaying = false
                    } else {
                        if (selectedBackground != BackgroundSound.NONE) {
                            mainAudioManager.getMeditationAudioManager().setVolume(backgroundVolume)
                            mainAudioManager.getMeditationAudioManager().playBackgroundSound(selectedBackground)
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
                                    breathingSettings.setBackgroundSound(sound)
                                    mainAudioManager.setBackgroundSound(sound)
                                    if (isBackgroundPlaying) {
                                        mainAudioManager.getMeditationAudioManager().setVolume(backgroundVolume)
                                        mainAudioManager.getMeditationAudioManager().playBackgroundSound(sound)
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
                    breathingSettings.setBinauralVolume(it)
                    mainAudioManager.getMeditationAudioManager().setBinauralVolume(it)
                },
                onPlayStop = {
                    if (isBinauralPlaying) {
                        mainAudioManager.getMeditationAudioManager().stopBinauralTone()
                        isBinauralPlaying = false
                    } else {
                        if (selectedBinaural != BinauralTone.NONE) {
                            mainAudioManager.getMeditationAudioManager().setBinauralVolume(binauralVolume)
                            mainAudioManager.getMeditationAudioManager().playBinauralTone(selectedBinaural)
                            isBinauralPlaying = true
                        }
                    }
                },
                extraContent = {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    BinauralTone.values().forEach { tone ->
                        val isSelected = selectedBinaural == tone
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedBinaural = tone
                                    breathingSettings.setBinauralTone(tone)
                                    mainAudioManager.setBinauralTone(tone)
                                    if (isBinauralPlaying) {
                                        if (tone != BinauralTone.NONE) {
                                            mainAudioManager.getMeditationAudioManager().setBinauralVolume(binauralVolume)
                                            mainAudioManager.getMeditationAudioManager().playBinauralTone(tone)
                                        } else {
                                            mainAudioManager.getMeditationAudioManager().stopBinauralTone()
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
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tone.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    if (tone != BinauralTone.NONE) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = tone.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "No binaural tone",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                
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
    }
}

@Composable
private fun BreathingVoiceSettingsTab(
    breathingSettings: BreathingSettings,
    context: Context
) {
    var ttsSpeed by remember { mutableStateOf(breathingSettings.getTtsSpeed()) }
    var ttsPitch by remember { mutableStateOf(breathingSettings.getTtsPitch()) }
    var ttsVolume by remember { mutableStateOf(breathingSettings.getTtsVolume()) }
    var selectedVoice by remember { mutableStateOf(breathingSettings.getTtsVoice()) }
    var availableVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var testTts by remember { mutableStateOf<TextToSpeech?>(null) }

    LaunchedEffect(Unit) {
        testTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                testTts?.let { tts ->
                    val voices = tts.voices?.filter { voice ->
                        voice.locale.language == Locale.getDefault().language ||
                                voice.locale.language == "en"
                    }?.sortedBy { it.name } ?: emptyList()
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
                            breathingSettings.setTtsSpeed(it)
                            testTts?.setSpeechRate(it)
                        },
                        valueRange = 0.5f..1.5f
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Voice Volume: ${String.format("%.1f", ttsVolume)}")
                    Slider(
                        value = ttsVolume,
                        onValueChange = {
                            ttsVolume = it
                            breathingSettings.setTtsVolume(it)
                        },
                        valueRange = 0.0f..1.0f
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Voice Pitch: ${String.format("%.1f", ttsPitch)}")
                    Slider(
                        value = ttsPitch,
                        onValueChange = {
                            ttsPitch = it
                            breathingSettings.setTtsPitch(it)
                            testTts?.setPitch(it)
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
                                    "This is a test of your breathing voice settings. Breathe in positive energy, breathe out stress.",
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

        if (availableVoices.isNotEmpty()) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Voice Selection",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        availableVoices.take(4).forEach { voice ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedVoice == voice.name,
                                    onClick = {
                                        selectedVoice = voice.name
                                        breathingSettings.setTtsVoice(voice.name)
                                        // Apply voice immediately to test TTS
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