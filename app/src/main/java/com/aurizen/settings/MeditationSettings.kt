package com.aurizen.settings

import android.content.Context
import android.content.SharedPreferences
import com.aurizen.data.BackgroundSound
import com.aurizen.data.BinauralTone
import com.aurizen.data.MeditationStatistics

class MeditationSettings private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "meditation_settings"
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
        private const val KEY_SESSIONS_COMPLETED = "sessions_completed"
        private const val KEY_TOTAL_MEDITATION_TIME = "total_meditation_time"
        private const val KEY_LAST_SESSION_DATE = "last_session_date"
        private const val KEY_STREAK_COUNT = "streak_count"
        private const val KEY_PREFERRED_DURATION = "preferred_duration"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_TIME = "reminder_time"

        @Volatile
        private var instance: MeditationSettings? = null

        fun getInstance(context: Context): MeditationSettings {
            return instance ?: synchronized(this) {
                instance ?: MeditationSettings(context).also { instance = it }
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
        return prefs.getFloat("binaural_volume", 0.1f)
    }

    fun setBinauralVolume(volume: Float) {
        prefs.edit().putFloat("binaural_volume", volume).apply()
    }

    // TTS Volume Settings (note: TTS volume is handled differently via speed/pitch)
    fun getTtsVolume(): Float {
        return prefs.getFloat("tts_volume", 0.8f)
    }

    fun setTtsVolume(volume: Float) {
        prefs.edit().putFloat("tts_volume", volume).apply()
    }

    // Binaural Tone Settings
    fun getBinauralTone(): BinauralTone {
        val toneName = prefs.getString(KEY_BINAURAL_TONE, BinauralTone.NONE.name)
        return try {
            BinauralTone.valueOf(toneName ?: BinauralTone.NONE.name)
        } catch (e: IllegalArgumentException) {
            BinauralTone.NONE
        }
    }

    fun setBinauralTone(tone: BinauralTone) {
        prefs.edit().putString(KEY_BINAURAL_TONE, tone.name).apply()
    }

    fun isBinauralEnabled(): Boolean {
        return prefs.getBoolean(KEY_BINAURAL_ENABLED, false)
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
        return prefs.getFloat(KEY_TTS_SPEED, 0.8f) // Slower for meditation
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

    // Session Tracking
    fun recordSessionCompletion(meditationType: String, durationMinutes: Int = 0) {
        val currentCount = getSessionsCompleted()
        val currentTime = getTotalMeditationTime()
        val today = System.currentTimeMillis()
        val newStreak = calculateStreak(today)
        val currentLongestStreak = getLongestStreak()

        prefs.edit().apply {
            putInt(KEY_SESSIONS_COMPLETED, currentCount + 1)
            putInt(KEY_TOTAL_MEDITATION_TIME, currentTime + durationMinutes)
            putLong(KEY_LAST_SESSION_DATE, today)
            putInt(KEY_STREAK_COUNT, newStreak)
            // Update longest streak if current streak is longer
            if (newStreak > currentLongestStreak) {
                putInt("longest_streak", newStreak)
            }
            apply()
        }
    }

    fun getSessionsCompleted(): Int {
        return prefs.getInt(KEY_SESSIONS_COMPLETED, 0)
    }

    fun getTotalMeditationTime(): Int {
        return prefs.getInt(KEY_TOTAL_MEDITATION_TIME, 0)
    }

    fun getLastSessionDate(): Long {
        return prefs.getLong(KEY_LAST_SESSION_DATE, 0)
    }

    fun getStreakCount(): Int {
        return prefs.getInt(KEY_STREAK_COUNT, 0)
    }

    private fun calculateStreak(today: Long): Int {
        val lastSession = getLastSessionDate()
        if (lastSession == 0L) return 1

        // Convert timestamps to calendar days to avoid time-of-day issues
        val todayCalendar = java.util.Calendar.getInstance().apply {
            timeInMillis = today
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        
        val lastSessionCalendar = java.util.Calendar.getInstance().apply {
            timeInMillis = lastSession
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        
        val daysDiff = (todayCalendar.timeInMillis - lastSessionCalendar.timeInMillis) / (24 * 60 * 60 * 1000)
        
        return when {
            daysDiff == 0L -> getStreakCount()  // Same day - don't increment streak
            daysDiff == 1L -> getStreakCount() + 1  // Next day - increment streak
            else -> 1  // More than one day gap - reset streak
        }
    }

    // Preferences
    fun getPreferredDuration(): Int {
        return prefs.getInt(KEY_PREFERRED_DURATION, 10) // Default 10 minutes
    }

    fun setPreferredDuration(minutes: Int) {
        prefs.edit().putInt(KEY_PREFERRED_DURATION, minutes).apply()
    }

    // Reminders
    fun isReminderEnabled(): Boolean {
        return prefs.getBoolean(KEY_REMINDER_ENABLED, false)
    }

    fun setReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    }

    fun getReminderTime(): String {
        return prefs.getString(KEY_REMINDER_TIME, "19:00") ?: "19:00"
    }

    fun setReminderTime(time: String) {
        prefs.edit().putString(KEY_REMINDER_TIME, time).apply()
    }

    // Statistics
    fun getMeditationStatistics(): MeditationStatistics {
        return MeditationStatistics(
            totalSessions = getSessionsCompleted(),
            totalMinutes = getTotalMeditationTime(),
            currentStreak = getStreakCount(),
            longestStreak = getLongestStreak(),
            averageSessionLength = getAverageSessionLength(),
            lastSessionDate = getLastSessionDate()
        )
    }

    private fun getLongestStreak(): Int {
        // This would be stored separately in a real implementation
        return prefs.getInt("longest_streak", getStreakCount())
    }

    private fun getAverageSessionLength(): Float {
        val totalSessions = getSessionsCompleted()
        val totalMinutes = getTotalMeditationTime()
        return if (totalSessions > 0) totalMinutes.toFloat() / totalSessions else 0f
    }

    // Reset all settings
    fun resetSettings() {
        prefs.edit().clear().apply()
    }

    // Export settings for backup
    fun exportSettings(): Map<String, Any> {
        return mapOf(
            "soundEnabled" to isSoundEnabled(),
            "backgroundSound" to getBackgroundSound().name,
            "ttsEnabled" to isTtsEnabled(),
            "ttsSpeed" to getTtsSpeed(),
            "ttsPitch" to getTtsPitch(),
            "volume" to getVolume(),
            "sessionsCompleted" to getSessionsCompleted(),
            "totalMeditationTime" to getTotalMeditationTime(),
            "preferredDuration" to getPreferredDuration(),
            "reminderEnabled" to isReminderEnabled(),
            "reminderTime" to getReminderTime()
        )
    }
}