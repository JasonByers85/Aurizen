package com.aurizen.prompts

/**
 * Smart Function Calling System
 * 
 * This system uses keyword detection to inject only relevant functions into prompts,
 * reducing token usage while maintaining functionality.
 * 
 * How it works:
 * 1. User message is analyzed for keywords BEFORE sending to LLM
 * 2. Only relevant function instructions are injected
 * 3. LLM receives minimal prompt with just the functions it needs
 * 
 * Example usage:
 * ```kotlin
 * // In your ViewModel or screen
 * val userMessage = "Always remember that I prefer morning meditations"
 * 
 * val context = PromptContext(
 *     additionalContext = mapOf("userMessage" to userMessage),
 *     userMemories = existingMemories
 * )
 * 
 * val prompt = PromptBuilder.build(PromptType.QUICK_CHAT, context)
 * // Prompt will automatically include REMEMBER function only if "always remember" is detected
 * 
 * // Parse LLM response for function calls
 * val response = llm.generate(prompt + userMessage)
 * val functionCalls = parseFunctionCalls(response)
 * ```
 */
object SmartFunctionCalling {
    
    // Function call pattern for parsing responses
    private val FUNCTION_PATTERN = """(REMEMBER|CREATE_MED):\{([^}]+)\}""".toRegex()
    
    /**
     * Parse function calls from LLM response
     */
    fun parseFunctionCalls(response: String): List<FunctionCall> {
        val calls = mutableListOf<FunctionCall>()
        
        FUNCTION_PATTERN.findAll(response).forEach { match ->
            val functionName = match.groupValues[1]
            val jsonParams = "{${match.groupValues[2]}}"
            
            when (functionName) {
                "REMEMBER" -> {
                    // Extract memory parameter
                    val memory = extractJsonValue(jsonParams, "memory")
                    if (memory != null) {
                        calls.add(FunctionCall.RememberMemory(memory))
                    }
                }
                "CREATE_MED" -> {
                    // Extract meditation parameters
                    val name = extractJsonValue(jsonParams, "name") ?: "Custom Meditation"
                    val goal = extractJsonValue(jsonParams, "goal") ?: "relaxation and wellness"
                    val duration = extractJsonValue(jsonParams, "duration")?.toIntOrNull() ?: 10
                    calls.add(FunctionCall.CreateMeditation(name, goal, duration))
                }
            }
        }
        
        return calls
    }
    
    /**
     * Remove function calls from response to get clean text
     */
    fun cleanResponse(response: String): String {
        return response.replace(FUNCTION_PATTERN, "").trim()
    }
    
    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = """"$key"\s*:\s*"([^"]+)"""".toRegex()
        val match = pattern.find(json)
        return match?.groupValues?.get(1)
    }
}

/**
 * Sealed class representing different function calls
 */
sealed class FunctionCall {
    data class RememberMemory(val memory: String) : FunctionCall()
    data class CreateMeditation(val name: String, val goal: String, val duration: Int) : FunctionCall()
}

/**
 * Extension function to make it easy to add user message to context
 */
fun PromptContext.withUserMessage(message: String): PromptContext {
    return this.copy(
        additionalContext = this.additionalContext + ("userMessage" to message)
    )
}