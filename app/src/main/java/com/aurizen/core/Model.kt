package com.aurizen.core

import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend

// Simple single model configuration using Gallery's working setup
enum class Model(
    val path: String,
    val url: String,
    val licenseUrl: String,
    val needsAuth: Boolean,
    val preferredBackend: Backend?,
    val thinking: Boolean,
    val temperature: Float,
    val topK: Int,
    val topP: Float,
    val textOnlyOptimized: Boolean = true, // New parameter to enable text-only optimizations
) {
    GEMMA3N(
        // Use Gallery's exact E2B model (smaller, more compatible)
        // E2B is optimized for text-only with ~2B parameters vs 4B for full model
        path = "/data/user/0/com.aurizen/files/gemma-3n-E2B-it-int4.task",
        // Use Gallery's exact download URL format
        url = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task?download=true",
        licenseUrl = "https://ai.google.dev/gemma/terms",
        needsAuth = true,
        preferredBackend = Backend.CPU, // CPU preferred for text-only stability
        thinking = false,
        // Optimized configuration for text-only performance
        temperature = 1.0f,
        topK = 64,
        topP = 0.95f,
        textOnlyOptimized = true // Enable text-only optimizations
    )
}