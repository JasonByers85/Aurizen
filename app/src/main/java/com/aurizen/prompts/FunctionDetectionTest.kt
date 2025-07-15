package com.aurizen.prompts

/**
 * Quick test to verify function detection is working
 * Run this to check if the keyword detection works properly
 */
fun main() {
    println("Testing Function Detection...")
    println("=" * 50)
    
    val testMessages = listOf(
        // Should NOT trigger (missing "always")
        "Remember that I like morning coffee",
        "Please remember to call mom",
        "Don't forget this important detail",
        
        // SHOULD trigger (contains "always remember")
        "Always remember that I prefer tea over coffee",
        "Please always remember I get anxious on Sundays", 
        "You should always remember my meditation preference",
        "ALWAYS REMEMBER that I like evening walks",  // Test case insensitive
        
        // Meditation tests
        "Create a meditation for stress relief",
        "Make meditation for sleep",
        "Generate meditation for focus",
        
        // Combined tests
        "Always remember to create meditation for anxiety",
        "Create a meditation and always remember my preference"
    )
    
    testMessages.forEach { message ->
        val result = PromptBuilder.testFunctionDetection(message)
        println("\nMessage: \"$message\"")
        println("Result: $result")
        
        // Also test full prompt generation
        val context = PromptContext(additionalContext = mapOf("userMessage" to message))
        val prompt = PromptBuilder.build(PromptType.QUICK_CHAT, context)
        
        val hasRemember = prompt.contains("REMEMBER")
        val hasCreateMed = prompt.contains("CREATE_MED")
        
        println("Injected: ${when {
            hasRemember && hasCreateMed -> "REMEMBER + CREATE_MED"
            hasRemember -> "REMEMBER only"
            hasCreateMed -> "CREATE_MED only"
            else -> "None"
        }}")
    }
    
    println("\n" + "=" * 50)
    println("If 'always remember' messages show 'REMEMBER only' or 'REMEMBER + CREATE_MED', it's working!")
}

operator fun String.times(n: Int): String = this.repeat(n)