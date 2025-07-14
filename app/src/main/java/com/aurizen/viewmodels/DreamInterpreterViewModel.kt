package com.aurizen.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.aurizen.prompts.PromptBuilder
import com.aurizen.prompts.PromptType
import com.aurizen.core.InferenceModel
import com.aurizen.data.DreamStorage
import com.aurizen.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.aurizen.data.DreamEntry

class DreamInterpreterViewModel(
    private val inferenceModel: InferenceModel,
    private val context: Context
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _interpretation = MutableStateFlow("")
    val interpretation: StateFlow<String> = _interpretation.asStateFlow()

    private val _isInputEnabled = MutableStateFlow(true)
    val isInputEnabled: StateFlow<Boolean> = _isInputEnabled.asStateFlow()

    private val _dreamHistory = MutableStateFlow<List<DreamEntry>>(emptyList())
    val dreamHistory: StateFlow<List<DreamEntry>> = _dreamHistory.asStateFlow()
    
    private val _selectedTab = MutableStateFlow(0) // 0 = Interpreter, 1 = Diary
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val dreamStorage = DreamStorage.getInstance(context)
    private val userProfile = UserProfile.getInstance(context)

    init {
        loadDreamHistory()
    }

    private fun loadDreamHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _dreamHistory.value = dreamStorage.getAllDreamEntries()
        }
    }

    private val systemPrompt = PromptBuilder.build(PromptType.DREAM_INTERPRETER)

    fun interpretDream(dreamDescription: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                _isInputEnabled.value = false
                _interpretation.value = ""

                val fullPrompt = """$systemPrompt

Dream description: "$dreamDescription"

Please provide a thoughtful interpretation of this dream, considering:
1. Common symbolic meanings of the elements
2. Possible emotional or psychological significance
3. How it might relate to the dreamer's waking life
4. Questions for further reflection

Response:"""

                var fullResponse = ""
                val responseJob = inferenceModel.generateResponseAsync(fullPrompt) { partialResult, done ->
                    if (partialResult.isNotEmpty()) {
                        fullResponse += partialResult
                        _interpretation.value = fullResponse

                        if (_isLoading.value) {
                            _isLoading.value = false
                        }
                    }

                    if (done) {
                        _isLoading.value = false
                        _isInputEnabled.value = true

                        // Save dream entry
                        saveDreamEntry(dreamDescription, fullResponse)
                    }
                }

                responseJob.get()

            } catch (e: Exception) {
                _interpretation.value = """I'm having trouble interpreting your dream right now. Here are some general insights about dreams:

**Common Dream Themes:**
Dreams often reflect our daily experiences, emotions, and subconscious thoughts. They can help us process feelings, solve problems, or explore our fears and desires.

**Reflection Questions:**
• How did you feel during the dream?
• What emotions lingered after waking?
• Do any elements remind you of recent experiences?
• What might your subconscious be trying to tell you?

**Remember:** Dreams are highly personal. The most meaningful interpretation is often the one that resonates with you. Consider keeping a dream journal to track patterns over time."""

                _isLoading.value = false
                _isInputEnabled.value = true
            }
        }
    }

    private fun saveDreamEntry(description: String, interpretation: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Generate brief summary for diary view
            val summary = generateDreamSummary(description, interpretation)
            
            val dreamEntry = DreamEntry(
                description = description,
                interpretation = interpretation,
                timestamp = System.currentTimeMillis(),
                summary = summary
            )

            dreamStorage.saveDreamEntry(dreamEntry)

            // Update user profile
            userProfile.addTopic("dreams")
            userProfile.saveProfile(context)

            // Reload history
            loadDreamHistory()
        }
    }
    
    private suspend fun generateDreamSummary(description: String, interpretation: String): String {
        return try {
            val summaryPrompt = """Based on this dream and its interpretation, provide a single brief sentence (maximum 15 words) that captures the core subconscious meaning or theme. Be precise and insightful.

Dream: "$description"
Interpretation: "${interpretation.take(500)}..."

Respond with only the summary sentence, nothing else."""

            var summary = ""
            val responseJob = inferenceModel.generateResponseAsync(summaryPrompt) { partialResult, done ->
                summary += partialResult
                if (done) {
                    summary = summary.trim().take(100) // Ensure it's brief
                }
            }
            responseJob.get()
            
            if (summary.isBlank()) {
                // Fallback summary based on description
                "A dream about ${description.take(30).lowercase()}${if (description.length > 30) "..." else ""}"
            } else {
                summary
            }
        } catch (e: Exception) {
            // Fallback summary
            "A dream about ${description.take(30).lowercase()}${if (description.length > 30) "..." else ""}"
        }
    }

    fun deleteDreamEntry(dreamEntry: DreamEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            dreamStorage.deleteDreamEntry(dreamEntry)
            loadDreamHistory()
        }
    }
    
    fun regenerateInterpretation(dreamEntry: DreamEntry) {
        // Delete the old entry and reinterpret
        viewModelScope.launch(Dispatchers.IO) {
            dreamStorage.deleteDreamEntry(dreamEntry)
            loadDreamHistory()
            
            // Switch back to main interpretation view and start new interpretation
            _interpretation.value = ""
            interpretDream(dreamEntry.description)
        }
    }
    
    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                if (modelClass.isAssignableFrom(DreamInterpreterViewModel::class.java)) {
                    val inferenceModel = InferenceModel.getInstance(context)
                    return DreamInterpreterViewModel(inferenceModel, context) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}