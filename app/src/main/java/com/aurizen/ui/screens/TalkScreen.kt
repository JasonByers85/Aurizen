package com.aurizen.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurizen.viewmodels.TalkViewModel
import com.aurizen.settings.TTSSettings
import com.aurizen.ui.theme.AuriZenGradientBackground
import kotlinx.coroutines.launch
import java.util.*
import com.aurizen.data.MultimodalChatMessage
import com.aurizen.data.isFromUser
import com.aurizen.data.getDisplayContent

@Composable
internal fun TalkRoute(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: TalkViewModel = viewModel(
        factory = TalkViewModel.getFactory(context)
    )
    
    TalkScreen(
        viewModel = viewModel,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkScreen(
    viewModel: TalkViewModel,
    onBack: () -> Unit
) {
    val chatHistory by viewModel.chatHistory.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val currentTranscript by viewModel.currentTranscript.collectAsState()
    val streamingResponse by viewModel.streamingResponse.collectAsState()
    
    var showTTSSettings by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-scroll to bottom when new messages arrive or during streaming
    LaunchedEffect(chatHistory.size, streamingResponse) {
        if (chatHistory.isNotEmpty() || streamingResponse.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    if (streamingResponse.isNotEmpty()) chatHistory.size 
                    else maxOf(0, chatHistory.size - 1)
                )
            }
        }
    }
    
    AuriZenGradientBackground {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Talk with AuriZen",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // TTS Settings button
                    IconButton(onClick = { showTTSSettings = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "TTS Settings"
                        )
                    }
                    
                    // Clear chat button
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear Chat"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
            
            // Chat Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (chatHistory.isEmpty()) {
                    item {
                        WelcomeMessage()
                    }
                } else {
                    items(chatHistory) { message ->
                        ChatMessageItem(message = message)
                    }
                }
                
                // Show streaming response or processing indicator
                if (streamingResponse.isNotEmpty()) {
                    item {
                        StreamingResponseItem(streamingResponse)
                    }
                } else if (isProcessing) {
                    item {
                        ProcessingIndicator()
                    }
                }
            }
            
            // Voice Input Section
            VoiceInputSection(
                isListening = isListening,
                isSpeaking = isSpeaking,
                isProcessing = isProcessing,
                currentTranscript = currentTranscript,
                viewModel = viewModel,
                onStopListening = { viewModel.stopListening() },
                onStopSpeaking = { viewModel.stopSpeaking() }
            )
        }
    }
    
    // TTS Settings Dialog
    if (showTTSSettings) {
        TTSSettingsDialog(
            onDismiss = { showTTSSettings = false }
        )
    }
}

@Composable
private fun WelcomeMessage() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.RecordVoiceOver,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Welcome to Talk Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Tap the microphone to start a voice conversation with AuriZen. I'll listen to your questions and respond with both text and speech.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ChatMessageItem(message: MultimodalChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser()) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isFromUser()) {
            // AI Avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromUser()) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isFromUser()) 16.dp else 4.dp,
                bottomEnd = if (message.isFromUser()) 4.dp else 16.dp
            )
        ) {
            MessageContent(message = message)
        }
        
        if (message.isFromUser()) {
            Spacer(modifier = Modifier.width(8.dp))
            
            // User Avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageContent(message: MultimodalChatMessage) {
    when (message) {
        is MultimodalChatMessage.TextMessage -> {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isFromUser()) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        
        is MultimodalChatMessage.AudioClipMessage -> {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Audio message",
                        tint = if (message.isFromUser()) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Text(
                        text = "🎤 Voice message (${String.format("%.1f", message.getDurationInSeconds())}s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message.isFromUser()) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        }
                    )
                }
                
                message.transcription?.let { transcription ->
                    if (transcription.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"$transcription\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (message.isFromUser()) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
        
    }
}


@Composable
private fun StreamingResponseItem(streamingText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        // AI Avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "A",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = streamingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Typing indicator
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "typing...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        // AI Avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "A",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VoiceInputSection(
    isListening: Boolean,
    isSpeaking: Boolean,
    isProcessing: Boolean,
    currentTranscript: String,
    viewModel: TalkViewModel,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }
    
    // Speech recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            spokenText?.let { results ->
                if (results.isNotEmpty()) {
                    // Process the speech result directly through ViewModel
                    viewModel.processDirectSpeechInput(results[0])
                }
            }
        }
    }
    
    fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to AuriZen...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechLauncher.launch(intent)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Current transcript
            if (currentTranscript.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = currentTranscript,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Voice control buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    isSpeaking -> {
                        // Stop speaking button
                        FloatingActionButton(
                            onClick = onStopSpeaking,
                            containerColor = MaterialTheme.colorScheme.error
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop Speaking"
                            )
                        }
                        
                        Text(
                            text = "Speaking...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    
                    isListening -> {
                        // Stop listening button
                        FloatingActionButton(
                            onClick = onStopListening,
                            containerColor = MaterialTheme.colorScheme.error
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop Listening"
                            )
                        }
                        
                        Text(
                            text = "Listening...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    isProcessing -> {
                        // Processing state
                        FloatingActionButton(
                            onClick = { },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        
                        Text(
                            text = "Processing...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    
                    else -> {
                        // Start listening button
                        FloatingActionButton(
                            onClick = {
                                if (hasPermission) {
                                    startSpeechRecognition()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Start Speaking"
                            )
                        }
                        
                        Text(
                            text = if (hasPermission) "Tap to speak" else "Tap to enable microphone",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
        }
    }
}

@Composable
private fun TTSSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val ttsSettings = remember { TTSSettings.getInstance(context) }
    
    var ttsEnabled by remember { mutableStateOf(ttsSettings.getTtsEnabled()) }
    var speechRate by remember { mutableFloatStateOf(ttsSettings.getSpeechRate()) }
    var pitch by remember { mutableFloatStateOf(ttsSettings.getPitch()) }
    var volume by remember { mutableFloatStateOf(ttsSettings.getVolume()) }
    var genderPreference by remember { mutableStateOf(ttsSettings.getGenderPreference()) }
    var selectedVoice by remember { mutableStateOf(ttsSettings.getVoice()) }
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
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Talk Voice Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }
                
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
                                    text = "Voice Response",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Enable spoken responses during conversation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = ttsEnabled,
                                onCheckedChange = { ttsEnabled = it }
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
                                    text = "Speed: ${String.format("%.1f", speechRate)}x",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = speechRate,
                                    onValueChange = { speechRate = it },
                                    valueRange = 0.5f..2.0f,
                                    steps = 14
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
                                    text = "Pitch: ${String.format("%.1f", pitch)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = pitch,
                                    onValueChange = { pitch = it },
                                    valueRange = 0.5f..2.0f,
                                    steps = 14
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
                                    text = "Volume: ${String.format("%.0f", volume * 100)}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = volume,
                                    onValueChange = { volume = it },
                                    valueRange = 0.1f..1.0f,
                                    steps = 8
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Test button
                                Button(
                                    onClick = {
                                        testTts?.let { tts ->
                                            tts.setSpeechRate(speechRate)
                                            tts.setPitch(pitch)
                                            
                                            if (selectedVoice.isNotEmpty()) {
                                                availableVoices.find { it.name == selectedVoice }?.let { voice ->
                                                    tts.voice = voice
                                                }
                                            }
                                            
                                            val params = Bundle().apply {
                                                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                                            }
                                            tts.speak(
                                                "Hello, this is a test of your Talk voice settings.",
                                                TextToSpeech.QUEUE_FLUSH,
                                                params,
                                                "talk_test"
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
                                            selected = genderPreference == gender,
                                            onClick = {
                                                genderPreference = gender
                                                
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
                    
                    item {
                        // Save/Cancel buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    ttsSettings.setTtsEnabled(ttsEnabled)
                                    ttsSettings.setSpeechRate(speechRate)
                                    ttsSettings.setPitch(pitch)
                                    ttsSettings.setVolume(volume)
                                    ttsSettings.setGenderPreference(genderPreference)
                                    ttsSettings.setVoice(selectedVoice)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save")
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