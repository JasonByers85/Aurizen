package com.aurizen.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Speech-to-Text helper using Android's built-in SpeechRecognizer
 * Provides accurate voice transcription instead of placeholder text
 */
class SpeechToTextHelper(
    private val context: Context,
    private val listener: SpeechToTextListener
) {
    companion object {
        private const val TAG = "SpeechToTextHelper"
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _transcriptionResult = MutableStateFlow("")
    val transcriptionResult: StateFlow<String> = _transcriptionResult.asStateFlow()
    
    interface SpeechToTextListener {
        fun onTranscriptionResult(text: String)
        fun onTranscriptionError(error: String)
        fun onListeningStarted()
        fun onListeningStopped()
    }
    
    fun initialize(): Boolean {
        return try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                listener.onTranscriptionError("Speech recognition not available on this device")
                return false
            }
            
            // Try to create SpeechRecognizer with specific service for Android 12+ compatibility
            speechRecognizer = try {
                // First try with Google's speech service specifically
                val googleServiceComponent = android.content.ComponentName(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
                )
                SpeechRecognizer.createSpeechRecognizer(context, googleServiceComponent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create with Google service, using default: ${e.message}")
                // Fallback to default service
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            
            speechRecognizer?.setRecognitionListener(recognitionListener)
            
            // Set up recognition intent with Android 12+ compatibility fixes
            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5) // Increased for better results
                
                // Android 12+ compatibility: More generous timing
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
                
                // Force online recognition for better accuracy
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                
                // Additional Android 12+ compatibility settings
                putExtra("android.speech.extra.DICTATION_MODE", true)
                putExtra(RecognizerIntent.EXTRA_SECURE, false)
            }
            
            Log.d(TAG, "Speech-to-text initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize speech-to-text", e)
            listener.onTranscriptionError("Failed to initialize speech recognition: ${e.message}")
            false
        }
    }
    
    fun startListening() {
        try {
            if (_isListening.value) {
                Log.w(TAG, "Already listening, stopping first")
                speechRecognizer?.cancel()
                _isListening.value = false
                Thread.sleep(300) // Give time for cleanup
            }
            
            _transcriptionResult.value = ""
            
            Log.d(TAG, "Starting speech recognition...")
            speechRecognizer?.startListening(recognizerIntent)
            _isListening.value = true
            
            Log.d(TAG, "Started listening for speech")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            _isListening.value = false
            listener.onTranscriptionError("Failed to start listening: ${e.message}")
        }
    }
    
    fun stopListening() {
        try {
            if (!_isListening.value) {
                return
            }
            
            speechRecognizer?.stopListening()
            _isListening.value = false
            
            Log.d(TAG, "Stopped listening for speech")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
        }
    }
    
    fun cancel() {
        try {
            speechRecognizer?.cancel()
            _isListening.value = false
            _transcriptionResult.value = ""
            
            Log.d(TAG, "Cancelled speech recognition")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling speech recognition", e)
        }
    }
    
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
            listener.onListeningStarted()
        }
        
        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech detected")
        }
        
        override fun onRmsChanged(rmsdB: Float) {
            // Audio level changes - could be used for visual feedback
        }
        
        override fun onBufferReceived(buffer: ByteArray?) {
            // Audio buffer received
        }
        
        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech detected")
            _isListening.value = false
            listener.onListeningStopped()
        }
        
        override fun onError(error: Int) {
            _isListening.value = false
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech input matched"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
                10 -> "Service binding error" // Custom error code 10
                else -> "Unknown recognition error"
            }
            
            Log.e(TAG, "Speech recognition error: $errorMessage (code: $error)")
            
            // Handle specific errors with detailed solutions
            when (error) {
                10 -> {
                    Log.e(TAG, "ERROR_SERVICE_BINDING (code 10): Cannot bind to speech recognition service")
                    Log.e(TAG, "This is common on MIUI/Xiaomi devices and some Android 12+ devices")
                    Log.e(TAG, "Switching to Intent-based speech recognition as fallback")
                    listener.onTranscriptionError("BINDING_ERROR")
                }
                SpeechRecognizer.ERROR_NO_MATCH -> {
                    Log.w(TAG, "ERROR_NO_MATCH: This is common on Android 12+ devices")
                    Log.w(TAG, "Possible solutions:")
                    Log.w(TAG, "1. Go to Settings > Apps > Default apps > Digital assistant app > Voice input")
                    Log.w(TAG, "2. Change from 'System Speech' to 'Speech services by Google'")
                    Log.w(TAG, "3. Ensure Google app has microphone permission")
                    listener.onTranscriptionResult("") // Return empty for graceful handling
                }
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    Log.d(TAG, "Speech timeout - user might not have spoken long enough")
                    listener.onTranscriptionResult("") // Return empty for graceful handling
                }
                SpeechRecognizer.ERROR_AUDIO -> {
                    Log.e(TAG, "Audio error - check microphone permissions and hardware")
                    listener.onTranscriptionError("Microphone access issue. Please check permissions.")
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    Log.e(TAG, "Permission error - microphone access denied")
                    listener.onTranscriptionError("Microphone permission required for voice input.")
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    Log.w(TAG, "Recognition service busy - this may resolve automatically")
                    listener.onTranscriptionError("Speech service is busy. Please try again.")
                }
                SpeechRecognizer.ERROR_CLIENT -> {
                    Log.w(TAG, "Client error - may be Android 12+ compatibility issue")
                    listener.onTranscriptionError("Speech recognition service issue. Try restarting the app.")
                }
                else -> {
                    listener.onTranscriptionError(errorMessage)
                }
            }
        }
        
        override fun onResults(results: Bundle?) {
            _isListening.value = false
            
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val transcription = matches[0]
                _transcriptionResult.value = transcription
                
                Log.d(TAG, "Speech recognition result: $transcription")
                listener.onTranscriptionResult(transcription)
            } else {
                Log.w(TAG, "No speech recognition results")
                listener.onTranscriptionResult("")
            }
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val partialTranscription = matches[0]
                _transcriptionResult.value = partialTranscription
                
                Log.d(TAG, "Partial speech result: $partialTranscription")
                // Could notify listener of partial results if needed
            }
        }
        
        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d(TAG, "Speech recognition event: $eventType")
        }
    }
    
    fun cleanup() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            recognizerIntent = null
            _isListening.value = false
            _transcriptionResult.value = ""
            
            Log.d(TAG, "Speech-to-text cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}