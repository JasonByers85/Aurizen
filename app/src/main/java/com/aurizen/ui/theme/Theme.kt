package com.aurizen.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Import all the new color schemes
// (Note: These are imported from ThemeColors.kt and PastelTheme.kt)

// AuriZen Blue Gradient Theme Colors
private val AuriZenDarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),           // Light blue accent
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A1A2E),  // Dark blue from gradient
    onPrimaryContainer = Color.White,
    
    secondary = Color(0xFF16213E),          // Mid blue from gradient  
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0F3460), // Deep blue from gradient
    onSecondaryContainer = Color.White,
    
    tertiary = Color(0xFF64B5F6),
    onTertiary = Color.White,
    
    background = Color(0xFF1A1A2E),         // Dark blue background
    onBackground = Color.White,
    
    surface = Color(0xFF16213E),            // Mid blue surface
    onSurface = Color.White,
    surfaceVariant = Color(0xFF0F3460),
    onSurfaceVariant = Color.White,
    
    outline = Color(0xFF64B5F6).copy(alpha = 0.5f),
    outlineVariant = Color(0xFF64B5F6).copy(alpha = 0.3f)
)

private val AuriZenLightColorScheme = lightColorScheme(
    primary = Color(0xFF0F3460),           // Deep blue for light theme
    onPrimary = Color.White,
    primaryContainer = Color(0xFF64B5F6).copy(alpha = 0.1f),
    onPrimaryContainer = Color(0xFF0F3460),
    
    secondary = Color(0xFF16213E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF64B5F6).copy(alpha = 0.2f),
    onSecondaryContainer = Color(0xFF0F3460),
    
    tertiary = Color(0xFF64B5F6),
    onTertiary = Color.White,
    
    background = Color(0xFFF8FAFF),         // Very light blue background
    onBackground = Color(0xFF0F3460),
    
    surface = Color.White,
    onSurface = Color(0xFF0F3460),
    surfaceVariant = Color(0xFF64B5F6).copy(alpha = 0.05f),
    onSurfaceVariant = Color(0xFF0F3460),
    
    outline = Color(0xFF64B5F6).copy(alpha = 0.7f),
    outlineVariant = Color(0xFF64B5F6).copy(alpha = 0.3f)
)

@Composable
fun AuriZenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.SYSTEM -> if (darkTheme) AuriZenDarkColorScheme else AuriZenLightColorScheme
        ThemeMode.LIGHT -> AuriZenLightColorScheme
        ThemeMode.DARK -> AuriZenDarkColorScheme
        ThemeMode.PASTEL -> if (darkTheme) PastelDarkColorScheme else PastelLightColorScheme
        ThemeMode.NATURE -> if (darkTheme) NatureDarkColorScheme else NatureLightColorScheme
        ThemeMode.MINIMAL -> if (darkTheme) MinimalDarkColorScheme else MinimalLightColorScheme
        ThemeMode.VIBRANT -> if (darkTheme) VibrantDarkColorScheme else VibrantLightColorScheme
        ThemeMode.OCEAN -> if (darkTheme) OceanDarkColorScheme else OceanLightColorScheme
        ThemeMode.SUNSET -> if (darkTheme) SunsetDarkColorScheme else SunsetLightColorScheme
        ThemeMode.FOREST_GREEN -> if (darkTheme) ForestGreenDarkColorScheme else ForestGreenLightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            
            // Set status bar color based on theme
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.surface.toArgb()
            
            val isLightTheme = when (themeMode) {
                ThemeMode.DARK -> false
                ThemeMode.LIGHT -> true
                ThemeMode.PASTEL -> !darkTheme
                ThemeMode.NATURE -> !darkTheme
                ThemeMode.MINIMAL -> !darkTheme
                ThemeMode.VIBRANT -> !darkTheme
                ThemeMode.OCEAN -> !darkTheme
                ThemeMode.SUNSET -> !darkTheme
                ThemeMode.FOREST_GREEN -> !darkTheme
                else -> !darkTheme
            }
            
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Keep the old name for backward compatibility during transition
@Composable
fun LLMInferenceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    AuriZenTheme(darkTheme = darkTheme, themeMode = themeMode, content = content)
}
