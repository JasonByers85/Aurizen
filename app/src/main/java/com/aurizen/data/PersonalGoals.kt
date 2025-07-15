package com.aurizen.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*
import java.text.SimpleDateFormat

data class PersonalGoal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: GoalCategory,
    val goalType: GoalType = GoalType.ONE_TIME,
    val targetDate: Long,
    val createdDate: Long = System.currentTimeMillis(),
    val progress: Float = 0f,
    val notes: String = "",
    val isCompleted: Boolean = false,
    val dailyProgress: DailyProgress = DailyProgress()
)

enum class GoalType(val displayName: String) {
    ONE_TIME("One-time Goal"),
    DAILY("Daily Goal")
}

data class DailyProgress(
    val completedDates: MutableSet<String> = mutableSetOf(), // Format: "yyyy-MM-dd"
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalDaysCompleted: Int = 0
)

enum class GoalCategory(val displayName: String, val emoji: String) {
    HEALTH("Health", "🏃‍♀️"),
    QUIT_HABIT("Quit Habit", "🚫"),
    WEIGHT("Weight", "⚖️"),
    FITNESS("Fitness", "💪"),
    MENTAL_WELLNESS("Mental Wellness", "🧘"),
    LEARNING("Learning", "📚"),
    CAREER("Career", "💼"),
    RELATIONSHIP("Relationships", "❤️"),
    FINANCIAL("Financial", "💰"),
    CREATIVE("Creative", "🎨"),
    OTHER("Other", "⭐")
}

class PersonalGoalsStorage private constructor(context: Context) {
    private val sharedPreferences: SharedPreferences
    private val gson = Gson()

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        sharedPreferences = EncryptedSharedPreferences.create(
            "personal_goals_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveGoal(goal: PersonalGoal) {
        val goals = getAllGoals().toMutableList()
        val existingIndex = goals.indexOfFirst { it.id == goal.id }
        
        if (existingIndex != -1) {
            goals[existingIndex] = goal
        } else {
            goals.add(goal)
        }
        
        val json = gson.toJson(goals)
        sharedPreferences.edit().putString(GOALS_KEY, json).apply()
    }

    fun getAllGoals(): List<PersonalGoal> {
        val json = sharedPreferences.getString(GOALS_KEY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PersonalGoal>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getActiveGoals(): List<PersonalGoal> {
        return getAllGoals().filter { !it.isCompleted }
    }

    fun getGoalById(id: String): PersonalGoal? {
        return getAllGoals().find { it.id == id }
    }

    fun deleteGoal(id: String) {
        val goals = getAllGoals().filter { it.id != id }
        val json = gson.toJson(goals)
        sharedPreferences.edit().putString(GOALS_KEY, json).apply()
    }

    fun updateGoalProgress(id: String, progress: Float) {
        val goal = getGoalById(id) ?: return
        val updatedGoal = goal.copy(
            progress = progress.coerceIn(0f, 1f),
            isCompleted = progress >= 1f
        )
        saveGoal(updatedGoal)
    }

    fun markDailyGoalCompleted(id: String, date: String = getCurrentDateString()): Boolean {
        val goal = getGoalById(id) ?: return false
        if (goal.goalType != GoalType.DAILY) return false

        val updatedDates = goal.dailyProgress.completedDates.toMutableSet()
        if (updatedDates.contains(date)) return false // Already completed today

        updatedDates.add(date)
        val newStreak = calculateCurrentStreak(updatedDates, date)
        val newLongestStreak = maxOf(goal.dailyProgress.longestStreak, newStreak)

        val updatedProgress = goal.copy(
            dailyProgress = goal.dailyProgress.copy(
                completedDates = updatedDates,
                currentStreak = newStreak,
                longestStreak = newLongestStreak,
                totalDaysCompleted = updatedDates.size
            )
        )
        saveGoal(updatedProgress)
        return true
    }

    fun markDailyGoalIncomplete(id: String, date: String = getCurrentDateString()): Boolean {
        val goal = getGoalById(id) ?: return false
        if (goal.goalType != GoalType.DAILY) return false

        val updatedDates = goal.dailyProgress.completedDates.toMutableSet()
        if (!updatedDates.contains(date)) return false // Not completed today

        updatedDates.remove(date)
        val newStreak = calculateCurrentStreak(updatedDates, getCurrentDateString())

        val updatedProgress = goal.copy(
            dailyProgress = goal.dailyProgress.copy(
                completedDates = updatedDates,
                currentStreak = newStreak,
                totalDaysCompleted = updatedDates.size
            )
        )
        saveGoal(updatedProgress)
        return true
    }

    fun isDailyGoalCompletedToday(id: String): Boolean {
        val goal = getGoalById(id) ?: return false
        return goal.goalType == GoalType.DAILY && 
               goal.dailyProgress.completedDates.contains(getCurrentDateString())
    }

    private fun getCurrentDateString(): String {
        val calendar = Calendar.getInstance()
        return String.format("%04d-%02d-%02d", 
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun calculateCurrentStreak(completedDates: Set<String>, fromDate: String): Int {
        val calendar = Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        try {
            calendar.time = dateFormat.parse(fromDate) ?: return 0
        } catch (e: Exception) {
            return 0
        }

        var streak = 0
        while (true) {
            val dateString = String.format("%04d-%02d-%02d", 
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            
            if (completedDates.contains(dateString)) {
                streak++
                calendar.add(Calendar.DAY_OF_MONTH, -1)
            } else {
                break
            }
        }
        
        return streak
    }

    fun clearAllGoals() {
        sharedPreferences.edit().remove(GOALS_KEY).apply()
    }

    companion object {
        private const val GOALS_KEY = "personal_goals"
        
        @Volatile
        private var INSTANCE: PersonalGoalsStorage? = null

        fun getInstance(context: Context): PersonalGoalsStorage {
            return INSTANCE ?: synchronized(this) {
                val instance = PersonalGoalsStorage(context)
                INSTANCE = instance
                instance
            }
        }
    }
}