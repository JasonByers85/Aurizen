package com.aurizen.prompts

/**
 * Step-by-step integration guide for Smart Function Calling
 */

/*

HOW TO INTEGRATE INTO YOUR EXISTING VIEWMODELS:

1. UPDATE YOUR CHAT/TALK VIEWMODELS:

Before (in QuickChatViewModel or TalkViewModel):
```kotlin
private fun sendMessage(userMessage: String) {
    val context = PromptContext(
        userMemories = memoryStorage.getMemories(),
        recentMoods = moodStorage.getRecentMoods()
    )
    val prompt = PromptBuilder.build(PromptType.QUICK_CHAT, context)
    val response = inferenceModel.generateResponse(prompt + userMessage)
    // Display response
}
```

After:
```kotlin
private fun sendMessage(userMessage: String) {
    val context = PromptContext(
        userMemories = memoryStorage.getMemories(),
        recentMoods = moodStorage.getRecentMoods(),
        additionalContext = mapOf("userMessage" to userMessage) // ADD THIS LINE
    )
    val prompt = PromptBuilder.build(PromptType.QUICK_CHAT, context)
    val response = inferenceModel.generateResponse(prompt + userMessage)
    
    // ADD FUNCTION PARSING:
    val functionCalls = SmartFunctionCalling.parseFunctionCalls(response)
    val cleanResponse = SmartFunctionCalling.cleanResponse(response)
    
    // EXECUTE FUNCTIONS:
    functionCalls.forEach { call ->
        when (call) {
            is FunctionCall.RememberMemory -> {
                memoryStorage.addMemory(call.memory)
            }
            is FunctionCall.CreateMeditation -> {
                // Navigate to meditation or show creation dialog
                createMeditationFromFunction(call.focus, call.duration)
            }
        }
    }
    
    // Display clean response (without function calls)
    displayResponse(cleanResponse)
}
```

2. ADD HELPER FUNCTIONS TO YOUR VIEWMODEL:

```kotlin
private fun createMeditationFromFunction(focus: String, durationMinutes: Int) {
    // Option 1: Navigate to meditation screen with parameters
    // navigationHelper.navigateToMeditation(focus, durationMinutes)
    
    // Option 2: Show a dialog asking user to confirm
    // showMeditationConfirmationDialog(focus, durationMinutes)
    
    // Option 3: Directly create and start meditation
    // meditationManager.createAndStartMeditation(focus, durationMinutes)
}
```

3. TEST IT:

Send these messages:
- "Always remember that I prefer 10 minute meditations" → Should store memory
- "Create a meditation for sleep" → Should trigger meditation creation
- "Always remember to create meditation for anxiety" → Should do both

4. DEBUG IF NOT WORKING:

Add this to test detection:
```kotlin
private fun debugFunctionDetection(userMessage: String) {
    val result = PromptBuilder.testFunctionDetection(userMessage)
    Log.d("FunctionDetection", "Message: $userMessage")
    Log.d("FunctionDetection", "Result: $result")
}
```

5. KEYWORDS THAT WORK:

✅ "Always remember that..."
✅ "Please always remember..."
✅ "You should always remember..."
✅ "Create a meditation for..."
✅ "Make meditation for..."
✅ "Generate meditation..."

❌ "Remember that..." (missing "always")
❌ "Don't forget..." (different phrase)

*/