package com.aurizen

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    val meditationSettings = remember { MeditationSettings.getInstance(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var ttsEnabled by remember { mutableStateOf(meditationSettings.isTtsEnabled()) }
    var ttsSpeed by remember { mutableStateOf(meditationSettings.getTtsSpeed()) }
    var ttsPitch by remember { mutableStateOf(meditationSettings.getTtsPitch()) }
    var ttsVolume by remember { mutableStateOf(meditationSettings.getTtsVolume()) }
    var selectedVoice by remember { mutableStateOf(meditationSettings.getTtsVoice()) }
    var availableVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var isTestPlaying by remember { mutableStateOf(false) }
    var currentTestType by remember { mutableStateOf("") } // Track what type of test is playing

    // TTS for testing
    var testTts by remember { mutableStateOf<TextToSpeech?>(null) }

    // Function to stop any playing TTS
    fun stopTTS() {
        testTts?.stop()
        isTestPlaying = false
        currentTestType = ""
    }

    // Function to play test with current settings
    fun playTestWithCurrentSettings(text: String, testType: String) {
        stopTTS() // Stop any current playback

        isTestPlaying = true
        currentTestType = testType

        testTts?.let { tts ->
            tts.setSpeechRate(ttsSpeed)
            tts.setPitch(ttsPitch)
            
            // Apply TTS volume via Bundle parameters instead of system volume
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
            }

            // Apply selected voice
            if (selectedVoice.isNotEmpty()) {
                availableVoices.find { it.name == selectedVoice }?.let { voice ->
                    tts.voice = voice
                }
            }

            tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    isTestPlaying = false
                    currentTestType = ""
                }
                override fun onError(utteranceId: String?) {
                    isTestPlaying = false
                    currentTestType = ""
                }
            })

            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, testType)
        }
    }

    // Initialize TTS and get available voices
    LaunchedEffect(Unit) {
        testTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                testTts?.let { tts ->
                    // Get available voices
                    val voices = tts.voices?.filter { voice ->
                        voice.locale.language == Locale.getDefault().language ||
                                voice.locale.language == "en"
                    }?.sortedBy { it.name } ?: emptyList()

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
            stopTTS()
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
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Voice Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
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
                        text = "🎙️ Voice Guidance Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Customize the voice that guides you through meditation sessions.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            // Enable/Disable TTS
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
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
                                stopTTS() // Stop any playing audio when disabled
                            }
                        }
                    )
                }
            }
        }

        if (ttsEnabled) {
            item {
                // Speech Speed
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
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
                                // Apply immediately if test is playing
                                if (isTestPlaying) {
                                    testTts?.setSpeechRate(it)
                                }
                            },
                            valueRange = 0.5f..1.5f,
                            steps = 10
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Slower", style = MaterialTheme.typography.labelSmall)
                            Text("Faster", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            item {
                // Voice Pitch
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
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
                                // Apply immediately if test is playing
                                if (isTestPlaying) {
                                    testTts?.setPitch(it)
                                }
                            },
                            valueRange = 0.6f..1.4f,
                            steps = 8
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Lower", style = MaterialTheme.typography.labelSmall)
                            Text("Higher", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            item {
                // Voice Volume
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
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
                                // Restart test TTS if currently playing to apply new volume immediately
                                if (isTestPlaying && currentTestType.isNotEmpty()) {
                                    playTestWithCurrentSettings(
                                        "Testing voice volume at ${(ttsVolume * 100).toInt()}%",
                                        currentTestType
                                    )
                                }
                            },
                            valueRange = 0.0f..1.0f,
                            steps = 10
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Quiet", style = MaterialTheme.typography.labelSmall)
                            Text("Loud", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            item {
                // Voice Selection
                if (availableVoices.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Voice Selection",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Choose your preferred voice",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            availableVoices.take(6).forEach { voice ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedVoice == voice.name,
                                        onClick = {
                                            selectedVoice = voice.name
                                            meditationSettings.setTtsVoice(voice.name)
                                            // If test is playing, restart with new voice
                                            if (isTestPlaying && currentTestType == "voice_test") {
                                                playTestWithCurrentSettings(
                                                    "Hello, this is a test of this meditation voice. Take a deep breath and relax.",
                                                    "voice_test"
                                                )
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = voice.name.replace("_", " ").replace("#", ""),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "${voice.locale.displayLanguage} - ${getVoiceQuality(voice)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }

                                    // Test voice button
                                    IconButton(
                                        onClick = {
                                            if (isTestPlaying && currentTestType == "voice_test") {
                                                stopTTS()
                                            } else {
                                                playTestWithCurrentSettings(
                                                    "Hello, this is a test of this meditation voice. Take a deep breath and relax.",
                                                    "voice_test"
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            if (isTestPlaying && currentTestType == "voice_test")
                                                Icons.Default.Stop
                                            else
                                                Icons.Default.PlayArrow,
                                            contentDescription = if (isTestPlaying && currentTestType == "voice_test") "Stop" else "Test Voice",
                                            tint = if (isTestPlaying && currentTestType == "voice_test")
                                                MaterialTheme.colorScheme.error
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                // Test Current Settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Test Your Settings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Listen to how your voice will sound during meditation",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (isTestPlaying && currentTestType == "meditation_test") {
                                    stopTTS()
                                } else {
                                    playTestWithCurrentSettings(
                                        "Welcome to your meditation session. Find a comfortable position and close your eyes. Take a deep breath in, and slowly exhale. Let yourself relax completely.",
                                        "meditation_test"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTestPlaying && currentTestType == "meditation_test") {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stop Test")
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Meditation Voice")
                            }
                        }
                    }
                }
            }

            item {
                // Tips
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Voice Tips",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Choose a slower speed (0.7-0.9x) for deeper relaxation\n" +
                                    "• Lower pitch voices often feel more calming\n" +
                                    "• Test different voices to find what resonates with you\n" +
                                    "• You can adjust these settings anytime during meditation\n" +
                                    "• Changes apply immediately while testing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

private fun getVoiceQuality(voice: Voice): String {
    return when (voice.quality) {
        Voice.QUALITY_VERY_HIGH -> "Very High Quality"
        Voice.QUALITY_HIGH -> "High Quality"
        Voice.QUALITY_NORMAL -> "Normal Quality"
        Voice.QUALITY_LOW -> "Low Quality"
        Voice.QUALITY_VERY_LOW -> "Very Low Quality"
        else -> "Unknown Quality"
    }
}