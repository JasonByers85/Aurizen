package com.aurizen.ui.screens

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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
import androidx.compose.ui.unit.dp
import com.aurizen.ui.theme.AuriZenGradientBackground
import com.aurizen.settings.MeditationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

@Composable
internal fun TTSSettingsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    TTSSettingsScreen(onBack = onBack, context = context)
}

@Composable
fun TTSSettingsScreen(onBack: () -> Unit, context: Context) {
    var currentTab by remember { mutableStateOf(0) }

    // Create a mock audio manager for the voice settings tab
    val mockAudioManager = object {
        fun setVolume(volume: Float) {}
        fun getMeditationAudioManager() = object {
            fun setVolume(volume: Float) {}
            fun stopBackgroundSound() {}
            fun playBackgroundSound(sound: Any) {}
            fun setBinauralVolume(volume: Float) {}
            fun stopBinauralTone() {}
            fun playBinauralTone(tone: Any) {}
        }
        fun setBackgroundSound(sound: Any) {}
        fun setBinauralTone(tone: Any) {}
    }

    AuriZenGradientBackground {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with tabs
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            text = "Meditation Voice Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        // Empty space for balance
                        Spacer(modifier = Modifier.width(48.dp))
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
                0 -> MeditationAudioMixerTab(context, mockAudioManager)
                1 -> MeditationVoiceSettingsTab(context)
            }
        }
    }
}

@Composable
private fun MeditationAudioMixerTab(
    context: Context,
    audioManager: Any // Placeholder for audio manager
) {
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
                        text = "🧘 Meditation Audio Mix",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Audio mixing for meditation sessions is handled during the session. Use the Voice tab to configure voice settings.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun MeditationVoiceSettingsTab(
    context: Context
) {
    val meditationSettings = remember { MeditationSettings.getInstance(context) }
    
    var ttsEnabled by remember { mutableStateOf(meditationSettings.isTtsEnabled()) }
    var ttsSpeed by remember { mutableStateOf(meditationSettings.getTtsSpeed()) }
    var ttsPitch by remember { mutableStateOf(meditationSettings.getTtsPitch()) }
    var ttsVolume by remember { mutableStateOf(meditationSettings.getTtsVolume()) }
    var selectedVoice by remember { mutableStateOf(meditationSettings.getTtsVoice()) }
    var selectedGender by remember { mutableStateOf(meditationSettings.getTtsGender()) }
    var availableVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var testTts by remember { mutableStateOf<TextToSpeech?>(null) }

    // Initialize TTS and get available voices
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
                    
                    // Set saved voice if available
                    if (selectedVoice.isNotEmpty()) {
                        voices.find { it.name == selectedVoice }?.let { voice ->
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
            // TTS Enable/Disable
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice Guidance",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Enable spoken meditation instructions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = ttsEnabled,
                        onCheckedChange = {
                            ttsEnabled = it
                            meditationSettings.setTtsEnabled(it)
                            if (!it) {
                                testTts?.stop()
                            }
                        }
                    )
                }
            }
        }
        
        if (ttsEnabled) {
            item {
                // Speech controls
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Speech Rate
                        Text(
                            text = "Speech Speed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Speed: ${String.format("%.1f", ttsSpeed)}x",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = ttsSpeed,
                            onValueChange = {
                                ttsSpeed = it
                                meditationSettings.setTtsSpeed(it)
                                testTts?.setSpeechRate(it)
                            },
                            valueRange = 0.5f..1.5f
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Pitch
                        Text(
                            text = "Voice Pitch",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Pitch: ${String.format("%.1f", ttsPitch)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = ttsPitch,
                            onValueChange = {
                                ttsPitch = it
                                meditationSettings.setTtsPitch(it)
                                testTts?.setPitch(it)
                            },
                            valueRange = 0.6f..1.4f
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Volume
                        Text(
                            text = "Voice Volume",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Volume: ${String.format("%.0f", ttsVolume * 100)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = ttsVolume,
                            onValueChange = {
                                ttsVolume = it
                                meditationSettings.setTtsVolume(it)
                            },
                            valueRange = 0.0f..1.0f
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Test button
                        Button(
                            onClick = {
                                testTts?.let { tts ->
                                    tts.setSpeechRate(ttsSpeed)
                                    tts.setPitch(ttsPitch)
                                    
                                    if (selectedVoice.isNotEmpty()) {
                                        availableVoices.find { it.name == selectedVoice }?.let { voice ->
                                            tts.voice = voice
                                        }
                                    }
                                    
                                    val params = Bundle().apply {
                                        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
                                    }
                                    tts.speak(
                                        "Welcome to your meditation session. Take a deep breath and relax.",
                                        TextToSpeech.QUEUE_FLUSH,
                                        params,
                                        "meditation_test"
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
            
            item {
                // Gender Preference
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
                                        meditationSettings.setTtsGender(gender)
                                        
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
                                                meditationSettings.setTtsVoice(voice.name)
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
                                            meditationSettings.setTtsVoice(voice.name)
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
                                            meditationSettings.setTtsVoice(voice.name)
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
                                            meditationSettings.setTtsVoice(voice.name)
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