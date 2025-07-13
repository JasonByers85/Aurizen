package com.aurizen

import android.content.Context
import android.content.SharedPreferences

class TTSSettings private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("talk_tts_settings", Context.MODE_PRIVATE)

    fun getTtsEnabled(): Boolean {
        return prefs.getBoolean("tts_enabled", true)
    }

    fun setTtsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("tts_enabled", enabled).apply()
    }

    fun getSpeechRate(): Float {
        return prefs.getFloat("speech_rate", 1.0f)
    }

    fun setSpeechRate(rate: Float) {
        prefs.edit().putFloat("speech_rate", rate).apply()
    }

    fun getPitch(): Float {
        return prefs.getFloat("pitch", 1.0f)
    }

    fun setPitch(pitch: Float) {
        prefs.edit().putFloat("pitch", pitch).apply()
    }

    fun getVolume(): Float {
        return prefs.getFloat("volume", 1.0f)
    }

    fun setVolume(volume: Float) {
        prefs.edit().putFloat("volume", volume).apply()
    }

    fun getVoice(): String {
        return prefs.getString("voice", "") ?: ""
    }

    fun setVoice(voice: String) {
        prefs.edit().putString("voice", voice).apply()
    }

    fun getGenderPreference(): String {
        return prefs.getString("gender_preference", "Any") ?: "Any"
    }

    fun setGenderPreference(gender: String) {
        prefs.edit().putString("gender_preference", gender).apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: TTSSettings? = null

        fun getInstance(context: Context): TTSSettings {
            return INSTANCE ?: synchronized(this) {
                val instance = TTSSettings(context)
                INSTANCE = instance
                instance
            }
        }
    }
}