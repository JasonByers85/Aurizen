package com.aurizen

import android.content.Context
import android.content.SharedPreferences

class BreathingSettings private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "breathing_settings"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_BACKGROUND_SOUND = "background_sound"
        private const val KEY_BINAURAL_TONE = "binaural_tone"
        private const val KEY_BINAURAL_ENABLED = "binaural_enabled"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_TTS_GENDER = "tts_gender"
        private const val KEY_VOLUME = "volume"
        private const val KEY_BINAURAL_VOLUME = "binaural_volume"
        private const val KEY_TTS_VOLUME = "tts_volume"

        @Volatile
        private var instance: BreathingSettings? = null

        fun getInstance(context: Context): BreathingSettings {
            return instance ?: synchronized(this) {
                instance ?: BreathingSettings(context).also { instance = it }
            }
        }
    }

    // Sound Settings
    fun isSoundEnabled(): Boolean {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    fun getBackgroundSound(): BackgroundSound {
        val soundName = prefs.getString(KEY_BACKGROUND_SOUND, BackgroundSound.NONE.name)
        return try {
            BackgroundSound.valueOf(soundName ?: BackgroundSound.NONE.name)
        } catch (e: IllegalArgumentException) {
            BackgroundSound.NONE
        }
    }

    fun setBackgroundSound(sound: BackgroundSound) {
        prefs.edit().putString(KEY_BACKGROUND_SOUND, sound.name).apply()
    }

    fun getVolume(): Float {
        return prefs.getFloat(KEY_VOLUME, 0.3f)
    }

    fun setVolume(volume: Float) {
        prefs.edit().putFloat(KEY_VOLUME, volume).apply()
    }

    // Binaural Volume Settings
    fun getBinauralVolume(): Float {
        return prefs.getFloat(KEY_BINAURAL_VOLUME, 0.1f)
    }

    fun setBinauralVolume(volume: Float) {
        prefs.edit().putFloat(KEY_BINAURAL_VOLUME, volume).apply()
    }

    // TTS Volume Settings
    fun getTtsVolume(): Float {
        return prefs.getFloat(KEY_TTS_VOLUME, 0.8f)
    }

    fun setTtsVolume(volume: Float) {
        prefs.edit().putFloat(KEY_TTS_VOLUME, volume).apply()
    }

    // Binaural Tone Settings
    fun getBinauralTone(): BinauralTone {
        val toneName = prefs.getString(KEY_BINAURAL_TONE, BinauralTone.ANXIETY_RELIEF.name)
        return try {
            BinauralTone.valueOf(toneName ?: BinauralTone.ANXIETY_RELIEF.name)
        } catch (e: IllegalArgumentException) {
            BinauralTone.ANXIETY_RELIEF
        }
    }

    fun setBinauralTone(tone: BinauralTone) {
        prefs.edit().putString(KEY_BINAURAL_TONE, tone.name).apply()
    }

    fun isBinauralEnabled(): Boolean {
        return prefs.getBoolean(KEY_BINAURAL_ENABLED, true)
    }

    fun setBinauralEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BINAURAL_ENABLED, enabled).apply()
    }

    // Text-to-Speech Settings
    fun isTtsEnabled(): Boolean {
        return prefs.getBoolean(KEY_TTS_ENABLED, true)
    }

    fun setTtsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply()
    }

    fun getTtsSpeed(): Float {
        return prefs.getFloat(KEY_TTS_SPEED, 0.8f) // Slower for breathing guidance
    }

    fun setTtsSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_TTS_SPEED, speed).apply()
    }

    fun getTtsPitch(): Float {
        return prefs.getFloat(KEY_TTS_PITCH, 0.9f) // Slightly lower for calm voice
    }

    fun setTtsPitch(pitch: Float) {
        prefs.edit().putFloat(KEY_TTS_PITCH, pitch).apply()
    }

    fun getTtsVoice(): String {
        return prefs.getString(KEY_TTS_VOICE, "") ?: ""
    }

    fun setTtsVoice(voiceName: String) {
        prefs.edit().putString(KEY_TTS_VOICE, voiceName).apply()
    }

    fun getTtsGender(): String {
        return prefs.getString(KEY_TTS_GENDER, "Any") ?: "Any"
    }

    fun setTtsGender(gender: String) {
        prefs.edit().putString(KEY_TTS_GENDER, gender).apply()
    }

    // Reset all settings
    fun resetSettings() {
        prefs.edit().clear().apply()
    }
}