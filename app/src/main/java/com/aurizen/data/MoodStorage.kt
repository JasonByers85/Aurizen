package com.aurizen.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MoodStorage private constructor(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val PREFS_NAME = "mood_storage"
        private const val KEY_MOOD_ENTRIES = "mood_entries"
        
        @Volatile
        private var instance: MoodStorage? = null
        
        fun getInstance(context: Context): MoodStorage {
            return instance ?: synchronized(this) {
                instance ?: MoodStorage(context).also { instance = it }
            }
        }
    }
    
    fun saveMoodEntry(entry: MoodEntry) {
        val currentEntries = getAllMoodEntries().toMutableList()
        currentEntries.add(entry)
        
        // Keep only last 100 entries to prevent excessive storage
        val entriesToKeep = currentEntries.takeLast(100)
        
        val json = gson.toJson(entriesToKeep)
        prefs.edit().putString(KEY_MOOD_ENTRIES, json).apply()
    }
    
    fun getAllMoodEntries(): List<MoodEntry> {
        val json = prefs.getString(KEY_MOOD_ENTRIES, null) ?: return emptyList()
        
        return try {
            val type = object : TypeToken<List<MoodEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getMoodEntriesInRange(startTime: Long, endTime: Long): List<MoodEntry> {
        return getAllMoodEntries().filter { entry ->
            entry.timestamp in startTime..endTime
        }
    }
    
    fun getRecentMoodEntries(count: Int = 10): List<MoodEntry> {
        return getAllMoodEntries().takeLast(count)
    }
    
    fun clearAllMoodEntries() {
        prefs.edit().remove(KEY_MOOD_ENTRIES).apply()
    }
    
    fun getMoodStatistics(): MoodStatistics {
        val entries = getAllMoodEntries()
        if (entries.isEmpty()) {
            return MoodStatistics(
                totalEntries = 0,
                mostCommonMood = "N/A",
                moodDistribution = emptyMap(),
                averageEntriesPerWeek = 0.0,
                longestStreak = 0,
                averageEnergyLevel = 0f,
                averageStressLevel = 0f,
                mostCommonTriggers = emptyList()
            )
        }
        
        val moodCounts = entries.groupingBy { it.mood }.eachCount()
        val mostCommonMood = moodCounts.maxByOrNull { it.value }?.key ?: "N/A"
        
        // Calculate average entries per week
        val oldestEntry = entries.minByOrNull { it.timestamp }
        val newestEntry = entries.maxByOrNull { it.timestamp }
        val daysDiff = if (oldestEntry != null && newestEntry != null) {
            (newestEntry.timestamp - oldestEntry.timestamp) / (1000 * 60 * 60 * 24)
        } else 0
        val averageEntriesPerWeek = if (daysDiff > 0) {
            (entries.size.toDouble() / daysDiff) * 7
        } else 0.0
        
        // Calculate longest streak of consecutive days
        val longestStreak = calculateLongestStreak(entries)
        
        // Calculate average energy and stress levels
        val averageEnergyLevel = entries.map { it.energyLevel }.average().toFloat()
        val averageStressLevel = entries.map { it.stressLevel }.average().toFloat()
        
        // Find most common triggers (simplified)
        val mostCommonTriggers = emptyList<String>() // Simplified - no longer tracking triggers
        
        return MoodStatistics(
            totalEntries = entries.size,
            mostCommonMood = mostCommonMood,
            moodDistribution = moodCounts,
            averageEntriesPerWeek = averageEntriesPerWeek,
            longestStreak = longestStreak,
            averageEnergyLevel = averageEnergyLevel,
            averageStressLevel = averageStressLevel,
            mostCommonTriggers = mostCommonTriggers
        )
    }
    
    private fun calculateLongestStreak(entries: List<MoodEntry>): Int {
        if (entries.isEmpty()) return 0
        
        val sortedEntries = entries.sortedBy { it.timestamp }
        val daySet = mutableSetOf<String>()
        
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        
        // Get unique days
        for (entry in sortedEntries) {
            val date = dateFormat.format(java.util.Date(entry.timestamp))
            daySet.add(date)
        }
        
        val sortedDays = daySet.sorted()
        var currentStreak = 1
        var maxStreak = 1
        
        for (i in 1 until sortedDays.size) {
            val currentDate = dateFormat.parse(sortedDays[i])
            val previousDate = dateFormat.parse(sortedDays[i - 1])
            
            val diffInDays = ((currentDate?.time ?: 0) - (previousDate?.time ?: 0)) / (1000 * 60 * 60 * 24)
            
            if (diffInDays == 1L) {
                currentStreak++
                maxStreak = maxOf(maxStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }
        
        return maxStreak
    }
}

data class MoodEntry(
    val mood: String,
    val note: String,
    val timestamp: Long,
    val energyLevel: Float = 3f, // 1-5 scale
    val stressLevel: Float = 3f, // 1-5 scale
    val triggers: List<String> = emptyList() // work, deadline, good news, love, exercise, sleep, etc.
)

data class MoodStatistics(
    val totalEntries: Int,
    val mostCommonMood: String,
    val moodDistribution: Map<String, Int>,
    val averageEntriesPerWeek: Double,
    val longestStreak: Int,
    val averageEnergyLevel: Float = 0f,
    val averageStressLevel: Float = 0f,
    val mostCommonTriggers: List<String> = emptyList()
)