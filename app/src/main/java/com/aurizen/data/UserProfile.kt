package com.aurizen.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class UserProfile(
    var hobbies: List<String> = emptyList(),
    var previousTopics: MutableList<String> = mutableListOf(),
    var mood: String = "",
    var lastInteractionDate: Long = 0,
    var sex: String = "",
    var age: Int = 0,
    var themeMode: String = "SYSTEM" // SYSTEM, LIGHT, DARK, PASTEL
) {
    companion object {
        private const val PREFS_NAME = "user_profile"
        private const val KEY_PROFILE = "profile_data"
        private val gson = Gson()

        fun getInstance(context: Context): UserProfile {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return loadProfile(prefs)
        }

        private fun loadProfile(prefs: SharedPreferences): UserProfile {
            val json = prefs.getString(KEY_PROFILE, null)
            return if (json != null) {
                gson.fromJson(json, UserProfile::class.java)
            } else {
                UserProfile()
            }
        }
    }

    fun saveProfile(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(this)
        prefs.edit().putString(KEY_PROFILE, json).apply()
    }

    fun addHobby(hobby: String) {
        hobbies = hobbies + hobby
    }

    fun addTopic(topic: String) {
        if (!previousTopics.contains(topic)) {
            previousTopics.add(topic)
        }
    }

    fun updateMood(newMood: String) {
        mood = newMood
        lastInteractionDate = System.currentTimeMillis()
    }

    fun getRecentTopics(limit: Int = 5): List<String> {
        return previousTopics.takeLast(limit)
    }

    fun clearProfile(context: Context) {
        hobbies = emptyList()
        previousTopics.clear()
        mood = ""
        lastInteractionDate = 0
        sex = ""
        age = 0
        themeMode = "SYSTEM"
        saveProfile(context)
    }

    fun updateSexAndAge(newSex: String, newAge: Int) {
        sex = newSex
        age = newAge
    }

    fun updateThemeMode(newTheme: String) {
        themeMode = newTheme
    }
}
