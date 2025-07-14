package com.aurizen.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.aurizen.ui.theme.ThemeMode

class ThemePreferences private constructor(context: Context) {
    
    private val sharedPreferences: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        
        EncryptedSharedPreferences.create(
            "theme_preferences",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    companion object {
        @Volatile
        private var INSTANCE: ThemePreferences? = null
        
        fun getInstance(context: Context): ThemePreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemePreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private const val THEME_MODE_KEY = "theme_mode"
    }
    
    fun getThemeMode(): ThemeMode {
        val themeString = sharedPreferences.getString(THEME_MODE_KEY, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(themeString ?: ThemeMode.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }
    
    fun setThemeMode(themeMode: ThemeMode) {
        sharedPreferences.edit().putString(THEME_MODE_KEY, themeMode.name).apply()
    }
    
    fun clearThemePreferences() {
        sharedPreferences.edit().clear().apply()
    }
}