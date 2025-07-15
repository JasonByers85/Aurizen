package com.aurizen.settings

import android.content.Context
import android.content.SharedPreferences
import com.aurizen.data.BackgroundSound
import com.aurizen.data.BinauralTone
import com.aurizen.data.MeditationStatistics
import com.aurizen.data.MeditationPacingPreferences
import com.aurizen.data.CueFrequency
import com.aurizen.data.PauseLength
import com.aurizen.data.PersonalizationLevel
import com.aurizen.data.CueStyle

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
        
        // Pacing preferences keys
        private const val KEY_GENTLE_CUE_FREQUENCY = "gentle_cue_frequency"
        private const val KEY_PAUSE_LENGTH = "pause_length"
        private const val KEY_BREATHING_SYNC = "breathing_sync"
        private const val KEY_PERSONALIZATION_LEVEL = "personalization_level"
        private const val KEY_INSTRUCTION_TO_SILENCE_RATIO = "instruction_to_silence_ratio"
        private const val KEY_ENABLE_GENTLE_CUES = "enable_gentle_cues"
        private const val KEY_FADE_IN_OUT = "fade_in_out"
        private const val KEY_PREFERRED_CUE_STYLE = "preferred_cue_style"

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

    // Meditation Pacing Preferences
    fun getPacingPreferences(): MeditationPacingPreferences {
        return MeditationPacingPreferences(
            gentleCueFrequency = getCueFrequency(),
            pauseLength = getPauseLength(),
            breathingSync = getBreathingSync(),
            personalizationLevel = getPersonalizationLevel(),
            instructionToSilenceRatio = getInstructionToSilenceRatio(),
            enableGentleCues = getEnableGentleCues(),
            fadeInOut = getFadeInOut(),
            preferredCueStyle = getPreferredCueStyle()
        )
    }
    
    fun setPacingPreferences(preferences: MeditationPacingPreferences) {
        prefs.edit().apply {
            putString(KEY_GENTLE_CUE_FREQUENCY, preferences.gentleCueFrequency.name)
            putString(KEY_PAUSE_LENGTH, preferences.pauseLength.name)
            putBoolean(KEY_BREATHING_SYNC, preferences.breathingSync)
            putString(KEY_PERSONALIZATION_LEVEL, preferences.personalizationLevel.name)
            putFloat(KEY_INSTRUCTION_TO_SILENCE_RATIO, preferences.instructionToSilenceRatio)
            putBoolean(KEY_ENABLE_GENTLE_CUES, preferences.enableGentleCues)
            putBoolean(KEY_FADE_IN_OUT, preferences.fadeInOut)
            putString(KEY_PREFERRED_CUE_STYLE, preferences.preferredCueStyle.name)
        }.apply()
    }
    
    // Individual pacing preference getters
    fun getCueFrequency(): CueFrequency {
        val frequency = prefs.getString(KEY_GENTLE_CUE_FREQUENCY, CueFrequency.MEDIUM.name)
        return try {
            CueFrequency.valueOf(frequency ?: CueFrequency.MEDIUM.name)
        } catch (e: IllegalArgumentException) {
            CueFrequency.MEDIUM
        }
    }
    
    fun setCueFrequency(frequency: CueFrequency) {
        prefs.edit().putString(KEY_GENTLE_CUE_FREQUENCY, frequency.name).apply()
    }
    
    fun getPauseLength(): PauseLength {
        val length = prefs.getString(KEY_PAUSE_LENGTH, PauseLength.MEDIUM.name)
        return try {
            PauseLength.valueOf(length ?: PauseLength.MEDIUM.name)
        } catch (e: IllegalArgumentException) {
            PauseLength.MEDIUM
        }
    }
    
    fun setPauseLength(length: PauseLength) {
        prefs.edit().putString(KEY_PAUSE_LENGTH, length.name).apply()
    }
    
    fun getBreathingSync(): Boolean {
        return prefs.getBoolean(KEY_BREATHING_SYNC, false)
    }
    
    fun setBreathingSync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BREATHING_SYNC, enabled).apply()
    }
    
    fun getPersonalizationLevel(): PersonalizationLevel {
        val level = prefs.getString(KEY_PERSONALIZATION_LEVEL, PersonalizationLevel.ADAPTIVE.name)
        return try {
            PersonalizationLevel.valueOf(level ?: PersonalizationLevel.ADAPTIVE.name)
        } catch (e: IllegalArgumentException) {
            PersonalizationLevel.ADAPTIVE
        }
    }
    
    fun setPersonalizationLevel(level: PersonalizationLevel) {
        prefs.edit().putString(KEY_PERSONALIZATION_LEVEL, level.name).apply()
    }
    
    fun getInstructionToSilenceRatio(): Float {
        return prefs.getFloat(KEY_INSTRUCTION_TO_SILENCE_RATIO, 0.3f)
    }
    
    fun setInstructionToSilenceRatio(ratio: Float) {
        prefs.edit().putFloat(KEY_INSTRUCTION_TO_SILENCE_RATIO, ratio).apply()
    }
    
    fun getEnableGentleCues(): Boolean {
        return prefs.getBoolean(KEY_ENABLE_GENTLE_CUES, true)
    }
    
    fun setEnableGentleCues(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_GENTLE_CUES, enabled).apply()
    }
    
    fun getFadeInOut(): Boolean {
        return prefs.getBoolean(KEY_FADE_IN_OUT, true)
    }
    
    fun setFadeInOut(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FADE_IN_OUT, enabled).apply()
    }
    
    fun getPreferredCueStyle(): CueStyle {
        val style = prefs.getString(KEY_PREFERRED_CUE_STYLE, CueStyle.BREATHING_FOCUSED.name)
        return try {
            CueStyle.valueOf(style ?: CueStyle.BREATHING_FOCUSED.name)
        } catch (e: IllegalArgumentException) {
            CueStyle.BREATHING_FOCUSED
        }
    }
    
    fun setPreferredCueStyle(style: CueStyle) {
        prefs.edit().putString(KEY_PREFERRED_CUE_STYLE, style.name).apply()
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
            "reminderTime" to getReminderTime(),
            // Pacing preferences
            "gentleCueFrequency" to getCueFrequency().name,
            "pauseLength" to getPauseLength().name,
            "breathingSync" to getBreathingSync(),
            "personalizationLevel" to getPersonalizationLevel().name,
            "instructionToSilenceRatio" to getInstructionToSilenceRatio(),
            "enableGentleCues" to getEnableGentleCues(),
            "fadeInOut" to getFadeInOut(),
            "preferredCueStyle" to getPreferredCueStyle().name
        )
    }
}