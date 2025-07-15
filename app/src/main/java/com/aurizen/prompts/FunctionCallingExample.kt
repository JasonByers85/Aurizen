package com.aurizen.prompts

/**
 * Example implementation showing how to use Smart Function Calling
 * in your ViewModels or chat screens
 */
class FunctionCallingExample {
    
    /**
     * Example: Process user message with smart function injection
     */
    fun processUserMessage(
        userMessage: String,
        existingContext: PromptContext
    ): ProcessedMessage {
        // 1. Add user message to context for function detection
        val contextWithMessage = existingContext.withUserMessage(userMessage)
        
        // 2. Build prompt - functions are automatically injected based on keywords
        val prompt = PromptBuilder.build(PromptType.QUICK_CHAT, contextWithMessage)
        
        // 3. Generate response (this would be your actual LLM call)
        val llmResponse = simulateLLMResponse(prompt, userMessage)
        
        // 4. Parse function calls from response
        val functionCalls = SmartFunctionCalling.parseFunctionCalls(llmResponse)
        
        // 5. Get clean response text
        val cleanText = SmartFunctionCalling.cleanResponse(llmResponse)
        
        // 6. Execute detected functions
        functionCalls.forEach { call ->
            when (call) {
                is FunctionCall.RememberMemory -> {
                    // Store memory in your storage system
                    println("Storing memory: ${call.memory}")
                    // MemoryStorage.getInstance(context).addMemory(call.memory)
                }
                is FunctionCall.CreateMeditation -> {
                    // Navigate to meditation creation
                    println("Creating meditation: ${call.name} for ${call.duration} minutes")
                    // Navigate to meditation screen with params
                }
            }
        }
        
        return ProcessedMessage(
            originalMessage = userMessage,
            prompt = prompt,
            response = cleanText,
            functionCalls = functionCalls
        )
    }
    
    /**
     * Example: Show which functions would be injected for different messages
     */
    fun demonstrateFunctionInjection() {
        val examples = mapOf(
            "How can I reduce stress?" to "No functions injected",
            "Remember that I like evening walks" to "No functions injected (needs 'always')",
            "Always remember that I like evening walks" to "REMEMBER function injected",
            "Please always remember I prefer morning meditations" to "REMEMBER function injected",
            "Create a meditation for anxiety" to "CREATE_MED function injected",
            "Always remember to create meditation for sleep" to "Both REMEMBER and CREATE_MED injected"
        )
        
        examples.forEach { (message, expected) ->
            val detectionResult = PromptBuilder.testFunctionDetection(message)
            
            println("\nMessage: \"$message\"")
            println("Expected: $expected")
            println("Detected: $detectionResult")
        }
    }
    
    private fun simulateLLMResponse(prompt: String, userMessage: String): String {
        // Simulate different responses based on specific phrases
        return when {
            userMessage.contains("always remember", ignoreCase = true) -> 
                "I'll always remember that for you. REMEMBER:{\"memory\":\"User likes evening walks\"} This will help me provide more personalized suggestions."
            
            userMessage.contains("create meditation", ignoreCase = true) ->
                "I'll create a custom meditation for anxiety. CREATE_MED:{\"focus\":\"anxiety relief\",\"duration\":10} This meditation will help you find calm."
            
            else -> "Here's some helpful advice for reducing stress..."
        }
    }
}

data class ProcessedMessage(
    val originalMessage: String,
    val prompt: String,
    val response: String,
    val functionCalls: List<FunctionCall>
)