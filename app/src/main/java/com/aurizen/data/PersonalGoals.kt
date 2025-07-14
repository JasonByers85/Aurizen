package com.aurizen.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

data class PersonalGoal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: GoalCategory,
    val targetDate: Long,
    val createdDate: Long = System.currentTimeMillis(),
    val progress: Float = 0f,
    val notes: String = "",
    val isCompleted: Boolean = false
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