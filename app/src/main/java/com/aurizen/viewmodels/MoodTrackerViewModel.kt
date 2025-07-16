package com.aurizen.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.aurizen.prompts.PromptBuilder
import com.aurizen.prompts.PromptContext
import com.aurizen.prompts.PromptType
import com.aurizen.core.InferenceModel
import com.aurizen.data.UserProfile
import com.aurizen.data.MoodStorage
import com.aurizen.data.MemoryStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.aurizen.data.MoodEntry
import com.aurizen.data.PersonalGoalsStorage

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
    private val memoryStorage = MemoryStorage.getInstance(context)

    fun loadMoodHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _moodHistory.value = moodStorage.getAllMoodEntries().sortedBy { it.timestamp }
        }
    }

    fun saveMood(mood: String, note: String, energyLevel: Float = 3f, stressLevel: Float = 3f, triggers: List<String> = emptyList()) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = MoodEntry(
                mood = mood,
                note = note,
                timestamp = System.currentTimeMillis(),
                energyLevel = energyLevel,
                stressLevel = stressLevel,
                triggers = triggers
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

                val context = PromptContext(
                    additionalContext = mapOf("moodSummary" to moodSummary)
                )
                val systemPrompt = PromptBuilder.build(PromptType.MOOD_INSIGHTS, context)

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
        val recentEntries = history.takeLast(20) // Last 20 entries for better context
        val totalEntries = recentEntries.size
        
        // Get personal goals data
        val goalsStorage = PersonalGoalsStorage.getInstance(context)
        val allGoals = goalsStorage.getAllGoals()
        val activeGoals = allGoals.filter { !it.isCompleted }
        
        // Current date context
        val todayFormat = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault())
        val today = todayFormat.format(Date())
        
        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        // Group entries by date and get chronological mood journey
        val groupedByDate = recentEntries.groupBy { entry ->
            dayFormat.format(Date(entry.timestamp))
        }.toSortedMap()
        
        // Build chronological mood journey (last 7 days)
        val moodJourney = groupedByDate.entries.toList().takeLast(7).joinToString("\n") { (_, entriesForDay) ->
            val latestEntry = entriesForDay.maxByOrNull { entry -> entry.timestamp }!!
            val formattedDate = dateFormat.format(Date(latestEntry.timestamp))
            val note = if (latestEntry.note.isNotBlank()) " - \"${latestEntry.note}\"" else ""
            "$formattedDate: ${latestEntry.mood.replaceFirstChar { char -> char.uppercase() }}$note"
        }
        
        // Calculate trends
        val recentDays = groupedByDate.entries.toList().takeLast(7)
        val previousDays = groupedByDate.entries.toList().dropLast(7).takeLast(7)
        
        val recentPositive = recentDays.count { (_, entriesForDay) ->
            val latestMoodForDay = entriesForDay.maxByOrNull { entry -> entry.timestamp }?.mood
            latestMoodForDay in listOf("ecstatic", "happy", "confident", "calm")
        }
        val previousPositive = previousDays.count { (_, entriesForDay) ->
            val latestMoodForDay = entriesForDay.maxByOrNull { entry -> entry.timestamp }?.mood
            latestMoodForDay in listOf("ecstatic", "happy", "confident", "calm")
        }
        
        // Calculate averages for energy and stress
        val averageEnergy = recentEntries.map { it.energyLevel }.average()
        val averageStress = recentEntries.map { it.stressLevel }.average()
        
        // Simple trigger analysis based on notes
        val notesWithContent = recentEntries.filter { it.note.isNotBlank() }
        val triggersText = if (notesWithContent.isNotEmpty()) {
            "Based on ${notesWithContent.size} detailed entries"
        } else {
            "No detailed notes provided"
        }
        
        // Goals context with progress and timing
        val goalsContext = if (activeGoals.isNotEmpty()) {
            activeGoals.joinToString("\n") { goal ->
                val progressPercent = (goal.progress * 100).toInt()
                val daysLeft = ((goal.targetDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                val daysLeftText = when {
                    daysLeft < 0 -> "overdue by ${-daysLeft} days"
                    daysLeft == 0 -> "due today"
                    daysLeft == 1 -> "due tomorrow"
                    else -> "$daysLeft days remaining"
                }
                val notes = if (goal.notes.isNotBlank()) " - ${goal.notes}" else ""
                "- ${goal.title} (${goal.category.displayName}) - $progressPercent% complete, $daysLeftText$notes"
            }
        } else {
            "No active personal goals set"
        }
        
        // User memories context
        val userMemories = memoryStorage.getAllMemories()
        val memoriesContext = if (userMemories.isNotEmpty()) {
            userMemories.take(5).joinToString("\n") { memory ->
                "- ${memory.memory}"
            }
        } else {
            "No personal context stored"
        }
        
        // Quick stats
        val moodCounts = recentEntries.groupingBy { it.mood }.eachCount()
        val dominantMood = moodCounts.maxByOrNull { it.value }?.key?.replaceFirstChar { it.uppercase() } ?: "N/A"
        
        return """
**Today's Date:** $today

**Recent Mood Journey:** (Last 7 days)
$moodJourney

**Personal Goals Context:**
$goalsContext

**Personal Context to Remember:**
$memoriesContext

**Quick Analysis:**
- Average energy: ${"%.1f".format(averageEnergy)}/5, stress: ${"%.1f".format(averageStress)}/5
- Trend: ${if (recentPositive >= previousPositive) "Stable or improving" else "Facing some challenges lately"}
- Notes: $triggersText
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