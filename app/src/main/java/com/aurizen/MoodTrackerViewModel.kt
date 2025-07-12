package com.aurizen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MoodTrackerViewModel(
    private val inferenceModel: InferenceModel,
    private val context: Context
) : ViewModel() {

    private val _moodHistory = MutableStateFlow<List<MoodEntry>>(emptyList())
    val moodHistory: StateFlow<List<MoodEntry>> = _moodHistory.asStateFlow()

    private val _aiInsights = MutableStateFlow("")
    val aiInsights: StateFlow<String> = _aiInsights.asStateFlow()

    private val _isLoadingInsights = MutableStateFlow(false)
    val isLoadingInsights: StateFlow<Boolean> = _isLoadingInsights.asStateFlow()

    private val userProfile = UserProfile.getInstance(context)
    private val moodStorage = MoodStorage.getInstance(context)

    fun loadMoodHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _moodHistory.value = moodStorage.getAllMoodEntries()
        }
    }

    fun saveMood(mood: String, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = MoodEntry(
                mood = mood,
                note = note,
                timestamp = System.currentTimeMillis()
            )

            moodStorage.saveMoodEntry(entry)

            // Update user profile for compatibility
            userProfile.updateMood(mood)
            if (note.isNotEmpty()) {
                userProfile.addTopic(note)
            }
            userProfile.saveProfile(context)

            // Reload history
            loadMoodHistory()
        }
    }

    fun clearMoodHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            moodStorage.clearAllMoodEntries()
            userProfile.clearProfile(context)
            _moodHistory.value = emptyList()
            _aiInsights.value = ""
        }
    }

    fun generateMoodInsights() {
        viewModelScope.launch(Dispatchers.IO) {
            val history = _moodHistory.value
            if (history.isEmpty()) return@launch

            try {
                _isLoadingInsights.value = true
                _aiInsights.value = ""

                val moodSummary = analyzeMoodHistory(history)

                val systemPrompt = """You are AuriZen, a supportive wellness AI within an app that provides meditations and breathing exercises. Analyze mood patterns and provide encouraging, actionable insights. When offering guidance, suggest using the meditation and breathing tools within this app.

Mood Analysis Data:
$moodSummary

Provide insights in EXACTLY 4-5 short paragraphs (2-3 sentences each). Keep it concise and focused:
1. Brief observation about their mood patterns
2. Positive highlights and progress
3. 2-3 practical suggestions for wellness (mention app's meditations/breathing exercises when relevant)
4. Gentle encouragement for challenges
5. Motivational closing (optional)

IMPORTANT: Keep each paragraph SHORT (2-3 sentences max). Total response should be under 400 words. Be supportive, hopeful, and actionable. Avoid clinical language or diagnosing."""

                val prompt = """$systemPrompt

Based on this mood history, provide supportive insights in exactly 4-5 short paragraphs (2-3 sentences each):"""

                val responseJob = inferenceModel.generateResponseAsync(prompt) { partialResult, done ->
                    if (partialResult.isNotEmpty()) {
                        _aiInsights.value = _aiInsights.value + partialResult
                    }

                    if (done) {
                        _isLoadingInsights.value = false
                    }
                }

                responseJob.get()

            } catch (e: Exception) {
                _aiInsights.value = """I'm having trouble analyzing your mood data right now, but I can see you're taking positive steps by tracking your feelings!

**Quick Wellness Reminders:**
• **Celebrate small wins** - Notice positive moments each day
• **Practice self-compassion** - Be kind to yourself during tough times
• **Stay connected** - Reach out for support when needed
• **Maintain routines** - Regular sleep, exercise, and meals help emotional balance

Mood fluctuations are completely normal. Your commitment to tracking emotions shows great self-awareness!"""

                _isLoadingInsights.value = false
            }
        }
    }

    private fun analyzeMoodHistory(history: List<MoodEntry>): String {
        val recentEntries = history.takeLast(30) // Last 30 entries
        val moodCounts = recentEntries.groupingBy { it.mood }.eachCount()
        val totalEntries = recentEntries.size

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val startDate = if (recentEntries.isNotEmpty()) dateFormat.format(Date(recentEntries.first().timestamp)) else "N/A"
        val endDate = if (recentEntries.isNotEmpty()) dateFormat.format(Date(recentEntries.last().timestamp)) else "N/A"

        // Calculate trends by grouping by days
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val groupedByDate = recentEntries.groupBy { entry ->
            dayFormat.format(Date(entry.timestamp))
        }.toSortedMap()
        
        val recentDays = groupedByDate.entries.toList().takeLast(7)
        val previousDays = groupedByDate.entries.toList().dropLast(7).takeLast(7)

        val recentPositive = recentDays.count { (date, entriesForDay) ->
            val latestMoodForDay = entriesForDay.maxByOrNull { entry -> entry.timestamp }?.mood
            latestMoodForDay in listOf("ecstatic", "happy", "confident", "calm")
        }
        val previousPositive = previousDays.count { (date, entriesForDay) ->
            val latestMoodForDay = entriesForDay.maxByOrNull { entry -> entry.timestamp }?.mood
            latestMoodForDay in listOf("ecstatic", "happy", "confident", "calm")
        }

        // Common notes/themes
        val allNotes = recentEntries.mapNotNull { entry ->
            if (entry.note.isNotBlank()) entry.note else null
        }

        return """
        **Mood Tracking Summary:**
        - Total entries: $totalEntries (from $startDate to $endDate)
        - Most common mood: ${moodCounts.maxByOrNull { it.value }?.key?.replaceFirstChar { it.uppercase() } ?: "N/A"}
        
        **Mood Distribution:**
        ${moodCounts.entries.joinToString("\n") { "- ${it.key.replaceFirstChar { char -> char.uppercase() }}: ${it.value} times (${(it.value * 100 / totalEntries)}%)" }}
        
        **Recent Trends:**
        - Positive mood days this week: $recentPositive/${recentDays.size}
        - Positive mood days previous week: $previousPositive/${previousDays.size}
        - Trend: ${if (recentPositive >= previousPositive) "Stable or improving" else "Some challenges lately"}
        - Unique tracking days: ${groupedByDate.size}
        
        **User Notes & Themes:**
        ${if (allNotes.isNotEmpty()) allNotes.takeLast(5).joinToString("\n") { "- \"$it\"" } else "No detailed notes provided"}
        """.trimIndent()
    }
    
    fun generateMeditationParams(): Triple<String, String, String> {
        val history = _moodHistory.value
        if (history.isEmpty()) {
            return Triple("mood-guided wellness", "balanced", "Beginner")
        }
        
        // Create a comprehensive mood context for the meditation
        val moodContext = createMoodContext(history)
        
        // Use "mood-guided" as focus to trigger contextual meditation generation
        val focus = "mood-guided wellness"
        
        // Determine overall emotional state
        val recentMoods = history.takeLast(7)
        val moodCounts = recentMoods.groupingBy { it.mood }.eachCount()
        val dominantMood = moodCounts.maxByOrNull { it.value }?.key ?: "balanced"
        
        val moodState = when (dominantMood) {
            "ecstatic", "happy", "confident" -> "positive"
            "calm" -> "balanced"
            "sad", "anxious" -> "challenging"
            "stressed" -> "stressed"
            "tired" -> "low energy"
            else -> "balanced"
        }
        
        // Determine experience level based on meditation history
        val experience = if (history.size >= 14) "Intermediate" else "Beginner"
        
        return Triple(focus, moodState, experience)
    }
    
    private fun createMoodContext(history: List<MoodEntry>): String {
        val recentEntries = history.takeLast(10)
        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
        
        // Group by recent days to show patterns
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val groupedByDate = recentEntries.groupBy { entry ->
            dayFormat.format(Date(entry.timestamp))
        }.toSortedMap()
        
        val recentDays = groupedByDate.entries.toList().takeLast(7)
        
        val moodSummary = recentDays.joinToString("; ") { (date, entriesForDay) ->
            val readableDate = dateFormat.format(Date(entriesForDay.first().timestamp))
            val moods = entriesForDay.map { entry -> entry.mood }.distinct()
            val notes = entriesForDay.mapNotNull { entry -> if (entry.note.isNotBlank()) entry.note else null }
            
            if (notes.isNotEmpty()) {
                "$readableDate: ${moods.joinToString(", ")} (${notes.joinToString("; ")})"
            } else {
                "$readableDate: ${moods.joinToString(", ")}"
            }
        }
        
        // Calculate overall trends
        val moodCounts = recentEntries.groupingBy { it.mood }.eachCount()
        val patterns = moodCounts.entries.sortedByDescending { it.value }.take(3)
            .joinToString(", ") { "${it.key} (${it.value} times)" }
        
        return "Recent mood patterns: $patterns. Daily summary: $moodSummary"
    }
    
    fun getMoodContext(): String {
        return createMoodContext(_moodHistory.value)
    }

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                if (modelClass.isAssignableFrom(MoodTrackerViewModel::class.java)) {
                    val inferenceModel = InferenceModel.getInstance(context)
                    return MoodTrackerViewModel(inferenceModel, context) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}