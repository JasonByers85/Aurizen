package com.aurizen

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import java.io.File

/** The maximum number of tokens the model can process. */
var MAX_TOKENS = 1024  // Increased for better responses

/**
 * Simplified inference model for wellness platform
 * Removed complex chat history management since we're doing single-turn interactions
 */
class InferenceModel private constructor(context: Context) {
    private lateinit var llmInference: LlmInference
    private lateinit var llmInferenceSession: LlmInferenceSession
    private val TAG = InferenceModel::class.qualifiedName
    private var isProcessing = false

    init {
        if (!modelExists(context)) {
            Log.e(TAG, "Model not found at path: ${modelPath(context)}")
            // Don't throw exception here, let LoadingScreen handle download
        } else {
            createEngine(context)
            createSession()
        }
    }

    fun close() {
        if (::llmInferenceSession.isInitialized) {
            llmInferenceSession.close()
        }
        if (::llmInference.isInitialized) {
            llmInference.close()
        }
    }

    fun resetSession() {
        if (::llmInferenceSession.isInitialized) {
            llmInferenceSession.close()
        }
        isProcessing = false
        createSession()
    }

    fun forceStopAndReset() {
        try {
            if (::llmInferenceSession.isInitialized) {
                try {
                    llmInferenceSession.close()
                } catch (e: IllegalStateException) {
                    if (e.message?.contains("Previous invocation still processing") == true) {
                        Log.w(TAG, "Session still processing, will skip force close to avoid crash")
                        return // Don't try to recreate if still processing
                    }
                    throw e
                }
            }
            // Force recreate the session to ensure clean state
            createSession()
            Log.d(TAG, "Force stopped and reset inference session")
        } catch (e: Exception) {
            Log.e(TAG, "Error during force stop and reset", e)
        }
    }

    private fun createEngine(context: Context) {
        val modelPath = modelPath(context)
        Log.d(TAG, "Attempting to load model from: $modelPath")

        val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(MAX_TOKENS)
            .apply { model.preferredBackend?.let { setPreferredBackend(it) } }
            .build()

        try {
            llmInference = LlmInference.createFromOptions(context, inferenceOptions)
            Log.d(TAG, "Model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Load model error: ${e.message}", e)
            throw ModelLoadFailException()
        }
    }

    private fun createSession() {
        if (!::llmInference.isInitialized) {
            throw ModelLoadFailException()
        }

        val sessionOptions = LlmInferenceSessionOptions.builder()
            .setTemperature(model.temperature)
            .setTopK(model.topK)
            .setTopP(model.topP)
            .build()

        try {
            llmInferenceSession = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
            Log.d(TAG, "Session created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "LlmInferenceSession create error: ${e.message}", e)
            throw ModelSessionCreateFailException()
        }
    }

    /**
     * Generate a response for a single prompt (no chat history)
     * This is perfect for our wellness platform where each interaction is independent
     */
    fun generateResponseAsync(prompt: String, progressListener: ProgressListener<String>): ListenableFuture<String> {
        if (!::llmInferenceSession.isInitialized) {
            throw ModelSessionCreateFailException()
        }

        if (isProcessing) {
            throw IllegalStateException("Previous invocation still processing. Wait for done=true.")
        }

        // Reset session for each new query to ensure clean state
        resetSession()
        isProcessing = true

        llmInferenceSession.addQueryChunk(prompt)
        val future = llmInferenceSession.generateResponseAsync(progressListener)
        
        // Add completion callback to reset processing flag
        future.addListener({
            isProcessing = false
            Log.d(TAG, "Inference completed, processing flag reset")
        }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
        
        return future
    }

    companion object {
        var model: Model = Model.GEMMA3N
        private var instance: InferenceModel? = null

        fun getInstance(context: Context): InferenceModel {
            return if (instance != null) {
                instance!!
            } else {
                InferenceModel(context).also { instance = it }
            }
        }

        fun resetInstance(context: Context): InferenceModel {
            instance?.close()
            return InferenceModel(context).also { instance = it }
        }

        fun forceReset(context: Context) {
            try {
                instance?.let { model ->
                    try {
                        model.isProcessing = false // Force reset processing flag
                        model.close()
                    } catch (e: IllegalStateException) {
                        if (e.message?.contains("Previous invocation still processing") == true) {
                            Log.w("InferenceModel", "Session still processing, marking instance as null without force close")
                            model.isProcessing = false // Reset flag even if we can't close
                            instance = null
                            return // Don't try to close if still processing
                        }
                        throw e
                    }
                }
                instance = null
                Log.d("InferenceModel", "🛑 Force reset inference model instance")
            } catch (e: Exception) {
                Log.e("InferenceModel", "Error during force reset", e)
            }
        }

        fun modelPathFromUrl(context: Context): String {
            if (model.url.isNotEmpty()) {
                val urlFileName = Uri.parse(model.url).lastPathSegment
                if (!urlFileName.isNullOrEmpty()) {
                    // Try multiple possible locations
                    val locations = listOf(
                        File(context.filesDir, urlFileName),
                        File(context.getExternalFilesDir(null), urlFileName),
                        File(context.cacheDir, urlFileName),
                        File("/data/app/${context.packageName}/files", urlFileName)
                    )

                    // Return the first location that exists, or default to filesDir
                    return locations.firstOrNull { it.exists() }?.absolutePath
                        ?: File(context.filesDir, urlFileName).absolutePath
                }
            }
            return ""
        }

        fun modelPath(context: Context): String {
            // Check if it's an absolute path (for backward compatibility)
            val modelFile = File(model.path)
            if (modelFile.exists() && modelFile.isAbsolute) {
                return model.path
            }

            // Otherwise, use app's internal storage
            val internalFile = File(context.filesDir, model.path)
            if (internalFile.exists()) {
                return internalFile.absolutePath
            }

            // Finally, try the download path
            return modelPathFromUrl(context)
        }

        fun modelExists(context: Context): Boolean {
            val path = modelPath(context)
            val exists = File(path).exists()
            Log.d("InferenceModel", "Checking model at path: $path, exists: $exists")
            return exists
        }
        
        fun isAvailable(): Boolean {
            return instance?.let { !it.isProcessing } ?: true
        }
    }
}