package com.aurizen.ui.theme

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.aurizen.data.ThemePreferences

class ThemeManager private constructor(context: Context) {
    
    private val themePreferences = ThemePreferences.getInstance(context)
    private val _currentTheme: MutableState<ThemeMode> = mutableStateOf(themePreferences.getThemeMode())
    
    val currentTheme: ThemeMode get() = _currentTheme.value
    
    companion object {
        @Volatile
        private var INSTANCE: ThemeManager? = null
        
        fun getInstance(context: Context): ThemeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    fun setTheme(themeMode: ThemeMode) {
        _currentTheme.value = themeMode
        themePreferences.setThemeMode(themeMode)
    }
    
    fun getThemeState(): MutableState<ThemeMode> = _currentTheme
    
    fun getAllThemes(): List<ThemeMode> = ThemeMode.values().toList()
    
    fun getThemeDisplayName(themeMode: ThemeMode): String = themeMode.displayName
    
    fun getThemeDescription(themeMode: ThemeMode): String = when (themeMode) {
        ThemeMode.SYSTEM -> "Follows your device's theme setting"
        ThemeMode.LIGHT -> "Clean, bright appearance"
        ThemeMode.DARK -> "Easy on the eyes in low light"
        ThemeMode.PASTEL -> "Soft, calming colors"
        ThemeMode.NATURE -> "Earthy greens and natural tones"
        ThemeMode.MINIMAL -> "Clean, distraction-free design"
        ThemeMode.VIBRANT -> "Bold, energetic colors"
        ThemeMode.OCEAN -> "Cool blues and aquatic tones"
        ThemeMode.SUNSET -> "Warm oranges and golden hues"
        ThemeMode.FOREST_GREEN -> "Deep forest greens and teals"
    }
}