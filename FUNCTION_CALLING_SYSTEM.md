# AuriZen Function Calling System

## Overview

The AuriZen Function Calling System enables the AI to perform specific actions when explicitly requested by users. This system works with MediaPipe's LLM Inference API by using a prompt-based approach to detect and handle function calls.

## How It Works

1. **Prompt Engineering**: The AI is given instructions about available functions through the system prompt
2. **Function Call Detection**: The system detects special formatted function calls in the AI's response
3. **Streaming Support**: Function calls are hidden from users during streaming, showing a friendly label instead
4. **Action Handling**: After processing, the system can present action buttons for user interaction

## Available Functions

### 1. CREATE_MEDITATION
Creates a personalized meditation based on user needs.

**Trigger phrases:**
- "Create a meditation for..."
- "Make me a meditation..."
- "I need a meditation for..."

**Parameters:**
- `focus`: The meditation focus (e.g., "sleep", "anxiety", "stress relief")
- `mood`: User's current mood
- `duration`: Duration in minutes (default: 10)
- `user_request`: Original user request

**Example:**
```
User: "Can you create a 15-minute meditation for sleep?"
AI: "I'd be happy to create a meditation for you.
FUNCTION_CALL:CREATE_MEDITATION:{"focus":"sleep","mood":"relaxed","duration":15,"user_request":"15-minute meditation for sleep"}
```

### 2. STORE_MEMORY
Stores important information about the user for future personalization.

**Trigger phrases:**
- "Remember that..."
- "Always remember..."
- "Don't forget that..."

**Parameters:**
- `memory_content`: The information to remember
- `category`: Category of memory (auto-detected)

**Example:**
```
User: "Remember that I have anxiety attacks in crowded places"
AI: "I'll remember that information.
FUNCTION_CALL:STORE_MEMORY:{"memory_content":"has anxiety attacks in crowded places","category":"anxiety"}
```

## Implementation Details

### Function Call Format
```
FUNCTION_CALL:[FUNCTION_NAME]:{"param1":"value1","param2":"value2"}
```

### Streaming Behavior
During streaming responses:
1. Function calls are detected and hidden from the user
2. A friendly processing label is shown (e.g., ">ØB Creating a meditation for you...")
3. The final response is cleaned of function call syntax

### UI Integration
When a function is successfully called:
1. The response is updated with the result
2. Action buttons may appear (e.g., "Start Meditation")
3. Users can interact with these buttons to trigger further actions

## Security Considerations

1. **Explicit Request Only**: Functions are only called when explicitly requested by users
2. **Permission Asking**: The AI asks permission before storing personal information
3. **Limited Scope**: Functions are limited to wellness-related actions only
4. **No Automatic Execution**: All actions require user confirmation through UI buttons

## Testing

Use the `TestFunctionCalling` class to test the system:

```kotlin
val tester = TestFunctionCalling(context)
tester.runTests() // Run all test scenarios
tester.testWithAI("Create a meditation for stress") { response ->
    // Handle response
}
```

## Future Enhancements

1. **Voice Integration**: Seamless function calling during voice conversations
2. **More Functions**: 
   - Schedule reminders
   - Export wellness data
   - Share progress reports
3. **Context Awareness**: Use stored memories to personalize function execution
4. **Multi-step Functions**: Support for functions that require multiple interactions