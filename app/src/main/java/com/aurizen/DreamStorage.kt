package com.aurizen

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DreamStorage private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "dream_storage"
        private const val KEY_DREAM_ENTRIES = "dream_entries"

        @Volatile
        private var instance: DreamStorage? = null

        fun getInstance(context: Context): DreamStorage {
            return instance ?: synchronized(this) {
                instance ?: DreamStorage(context).also { instance = it }
            }
        }
    }

    fun saveDreamEntry(entry: DreamEntry) {
        val currentEntries = getAllDreamEntries().toMutableList()
        currentEntries.add(entry)

        // Keep only last 50 entries to prevent excessive storage
        val entriesToKeep = currentEntries.takeLast(50)

        val json = gson.toJson(entriesToKeep)
        prefs.edit().putString(KEY_DREAM_ENTRIES, json).apply()
    }

    fun getAllDreamEntries(): List<DreamEntry> {
        val json = prefs.getString(KEY_DREAM_ENTRIES, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<DreamEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getDreamEntriesInRange(startTime: Long, endTime: Long): List<DreamEntry> {
        return getAllDreamEntries().filter { entry ->
            entry.timestamp in startTime..endTime
        }
    }

    fun getRecentDreamEntries(count: Int = 10): List<DreamEntry> {
        return getAllDreamEntries().takeLast(count)
    }

    fun deleteDreamEntry(entryToDelete: DreamEntry) {
        val currentEntries = getAllDreamEntries().toMutableList()
        currentEntries.removeAll { entry ->
            entry.timestamp == entryToDelete.timestamp && 
            entry.description == entryToDelete.description
        }
        
        val json = gson.toJson(currentEntries)
        prefs.edit().putString(KEY_DREAM_ENTRIES, json).apply()
    }

    fun clearAllDreamEntries() {
        prefs.edit().remove(KEY_DREAM_ENTRIES).apply()
    }

    fun getDreamStatistics(): DreamStatistics {
        val entries = getAllDreamEntries()
        if (entries.isEmpty()) {
            return DreamStatistics(
                totalEntries = 0,
                averageEntriesPerWeek = 0.0,
                commonThemes = emptyList(),
                longestStreak = 0
            )
        }

        // Calculate average entries per week
        val oldestEntry = entries.minByOrNull { it.timestamp }
        val newestEntry = entries.maxByOrNull { it.timestamp }
        val daysDiff = if (oldestEntry != null && newestEntry != null) {
            (newestEntry.timestamp - oldestEntry.timestamp) / (1000 * 60 * 60 * 24)
        } else 0
        val averageEntriesPerWeek = if (daysDiff > 0) {
            (entries.size.toDouble() / daysDiff) * 7
        } else 0.0

        // Extract common themes from interpretations
        val commonThemes = extractCommonThemes(entries)

        // Calculate longest streak of consecutive days
        val longestStreak = calculateLongestStreak(entries)

        return DreamStatistics(
            totalEntries = entries.size,
            averageEntriesPerWeek = averageEntriesPerWeek,
            commonThemes = commonThemes,
            longestStreak = longestStreak
        )
    }

    private fun extractCommonThemes(entries: List<DreamEntry>): List<String> {
        val themes = mutableMapOf<String, Int>()
        val commonKeywords = listOf(
            "flying", "falling", "water", "animals", "family", "friends", "home", "school", "work",
            "chase", "lost", "death", "fear", "love", "nature", "travel", "childhood", "future"
        )

        entries.forEach { entry ->
            val text = (entry.description + " " + entry.interpretation).lowercase()
            commonKeywords.forEach { keyword ->
                if (text.contains(keyword)) {
                    themes[keyword] = themes.getOrDefault(keyword, 0) + 1
                }
            }
        }

        return themes.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key.capitalize() }
    }

    private fun calculateLongestStreak(entries: List<DreamEntry>): Int {
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

data class DreamStatistics(
    val totalEntries: Int,
    val averageEntriesPerWeek: Double,
    val commonThemes: List<String>,
    val longestStreak: Int
)