package com.aurizen

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Test class for demonstrating and testing the Function Calling System
 * This shows how the AI can call functions like creating meditations or storing memories
 */
class TestFunctionCalling(private val context: Context) {
    
    private val TAG = "TestFunctionCalling"
    private val functionCallingSystem = FunctionCallingSystem.getInstance(context)
    private val inferenceModel = InferenceModel.getInstance(context)
    
    /**
     * Test scenarios for function calling
     */
    fun runTests() {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Starting Function Calling Tests...")
            
            // Test 1: Create Meditation
            testCreateMeditation()
            
            // Test 2: Store Memory
            testStoreMemory()
            
            // Test 3: Mixed conversation with function call
            testMixedConversation()
            
            // Test 4: Function call detection in streaming
            testStreamingFunctionCall()
        }
    }
    
    /**
     * Test meditation creation function call
     */
    private fun testCreateMeditation() {
        Log.d(TAG, "\n=== Test 1: Create Meditation ===")
        
        val testPrompts = listOf(
            "Can you create a meditation for sleep?",
            "I need a 15 minute meditation for anxiety",
            "Make me a meditation to help with stress at work"
        )
        
        testPrompts.forEach { prompt ->
            Log.d(TAG, "Testing prompt: $prompt")
            
            // Simulate AI response with function call
            val simulatedResponse = """
                I'd be happy to help you with that. Let me create a personalized meditation for you.
                
                FUNCTION_CALL:CREATE_MEDITATION:{"focus":"sleep","mood":"relaxed","duration":10,"user_request":"$prompt"}
                
                I've created a calming meditation focused on sleep. This will guide you through progressive relaxation.
            """.trimIndent()
            
            val result = functionCallingSystem.processAIResponse(simulatedResponse)
            
            Log.d(TAG, "Function handled: ${result.handled}")
            Log.d(TAG, "Response: ${result.response}")
            Log.d(TAG, "Action button: ${result.actionButton?.text}")
        }
    }
    
    /**
     * Test memory storage function call
     */
    private fun testStoreMemory() {
        Log.d(TAG, "\n=== Test 2: Store Memory ===")
        
        val testPrompts = listOf(
            "Remember that I have trouble sleeping on Sunday nights",
            "Always remember my name is Sarah and I prefer morning meditations",
            "Remember I get anxious before presentations"
        )
        
        testPrompts.forEach { prompt ->
            Log.d(TAG, "Testing prompt: $prompt")
            
            // Extract memory content from prompt
            val memoryContent = prompt.replace("Remember that ", "").replace("Always remember ", "")
            
            // Simulate AI response with function call
            val simulatedResponse = """
                I'll remember that information for you.
                
                FUNCTION_CALL:STORE_MEMORY:{"memory_content":"$memoryContent","category":"personal"}
                
                This will help me provide more personalized support in our future conversations.
            """.trimIndent()
            
            val result = functionCallingSystem.processAIResponse(simulatedResponse)
            
            Log.d(TAG, "Function handled: ${result.handled}")
            Log.d(TAG, "Response: ${result.response}")
        }
        
        // Show stored memories
        val memories = functionCallingSystem.getUserMemories()
        Log.d(TAG, "\nStored memories: ${memories.size}")
        memories.forEach { memory ->
            Log.d(TAG, "- ${memory.memory}")
        }
    }
    
    /**
     * Test mixed conversation with function calls
     */
    private fun testMixedConversation() {
        Log.d(TAG, "\n=== Test 3: Mixed Conversation ===")
        
        val conversation = """
            Thanks for asking! I understand you're looking for help with stress.
            
            Here are some quick tips:
            1. Try deep breathing exercises
            2. Take short breaks during work
            3. Practice mindfulness
            
            FUNCTION_CALL:CREATE_MEDITATION:{"focus":"stress relief","mood":"anxious","duration":5,"user_request":"quick stress relief"}
            
            I've also created a quick 5-minute stress relief meditation for you. Would you like to try it now?
            
            Remember, it's important to address stress regularly, not just when it becomes overwhelming.
        """.trimIndent()
        
        val result = functionCallingSystem.processAIResponse(conversation)
        
        Log.d(TAG, "Mixed conversation result:")
        Log.d(TAG, "Response length: ${result.response.length}")
        Log.d(TAG, "Has action button: ${result.actionButton != null}")
        Log.d(TAG, "Clean response preview: ${result.response.take(100)}...")
    }
    
    /**
     * Test streaming function call detection
     */
    private fun testStreamingFunctionCall() {
        Log.d(TAG, "\n=== Test 4: Streaming Function Call ===")
        
        val fullResponse = """Let me create a meditation for you. FUNCTION_CALL:CREATE_MEDITATION:{"focus":"sleep","mood":"calm","duration":10,"user_request":"help me sleep"} This meditation will help you relax."""
        
        // Simulate streaming by processing chunks
        var accumulated = ""
        val chunkSize = 20
        
        for (i in fullResponse.indices step chunkSize) {
            val chunk = fullResponse.substring(i, minOf(i + chunkSize, fullResponse.length))
            accumulated += chunk
            
            val streamingResult = functionCallingSystem.filterStreamingResponse(accumulated)
            
            Log.d(TAG, "Chunk ${i/chunkSize + 1}: visible='${streamingResult.visibleText}'")
            if (streamingResult.functionLabel != null) {
                Log.d(TAG, "Function detected: ${streamingResult.functionLabel}")
            }
        }
        
        // Final processing
        val finalResult = functionCallingSystem.processAIResponse(fullResponse)
        Log.d(TAG, "\nFinal result:")
        Log.d(TAG, "Response: ${finalResult.response}")
        Log.d(TAG, "Action: ${finalResult.actionButton?.text}")
    }
    
    /**
     * Test function calling with actual AI model
     */
    fun testWithAI(prompt: String, callback: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val systemPrompt = buildSystemPromptWithFunctions()
                val fullPrompt = "$systemPrompt\n\nUser: $prompt\n\nAssistant:"
                
                val responseBuilder = StringBuilder()
                
                inferenceModel.generateResponseAsync(fullPrompt) { partialResult, done ->
                    responseBuilder.append(partialResult)
                    
                    val currentText = responseBuilder.toString()
                    val streamingResult = functionCallingSystem.filterStreamingResponse(currentText)
                    
                    // Show streaming updates
                    callback(streamingResult.visibleText)
                    
                    if (done) {
                        // Process final result
                        val functionResult = functionCallingSystem.processAIResponse(currentText)
                        callback(functionResult.response)
                        
                        if (functionResult.actionButton != null) {
                            Log.d(TAG, "Action available: ${functionResult.actionButton.text}")
                        }
                    }
                }.get()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error testing with AI", e)
                callback("Error: ${e.message}")
            }
        }
    }
    
    /**
     * Build system prompt with function calling instructions
     */
    private fun buildSystemPromptWithFunctions(): String {
        return """You are a helpful AI wellness assistant with access to special functions.

${functionCallingSystem.getFunctionCallingPrompt()}

Respond naturally and use functions when the user explicitly requests them."""
    }
    
    /**
     * Clear all test data
     */
    fun clearTestData() {
        functionCallingSystem.clearUserMemories()
        Log.d(TAG, "Test data cleared")
    }
}