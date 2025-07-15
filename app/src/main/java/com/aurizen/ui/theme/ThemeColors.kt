package com.aurizen.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Nature Theme - Earthy greens and browns
val NatureLightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32), // Forest green
    secondary = Color(0xFF8BC34A), // Light green
    tertiary = Color(0xFFFFC107), // Amber
    background = Color(0xFFE8F5E8), // Light green background
    surface = Color(0xFFF1F8E9),
    onPrimary = Color.White,
    onSecondary = Color(0xFF1B5E20),
    onTertiary = Color(0xFF333333),
    onBackground = Color(0xFF1B5E20),
    onSurface = Color(0xFF1B5E20),
    primaryContainer = Color(0xFFA5D6A7), // More vibrant green
    secondaryContainer = Color(0xFFC8E6C9), // Light green container
    tertiaryContainer = Color(0xFFFFE082),
    surfaceVariant = Color(0xFFE8F5E8),
    outline = Color(0xFF4CAF50),
    outlineVariant = Color(0xFFA5D6A7)
)

val NatureDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CAF50), // Medium green
    secondary = Color(0xFF8BC34A), // Light green
    tertiary = Color(0xFFFFC107), // Amber
    background = Color(0xFF1B3A1E), // Dark forest green
    surface = Color(0xFF2E4F31),
    onPrimary = Color.White,
    onSecondary = Color(0xFF1B5E20),
    onTertiary = Color(0xFF333333),
    onBackground = Color(0xFFE8F5E8),
    onSurface = Color(0xFFE8F5E8),
    primaryContainer = Color(0xFF2E7D32),
    secondaryContainer = Color(0xFF33691E),
    tertiaryContainer = Color(0xFFFF8F00),
    surfaceVariant = Color(0xFF3E5B41),
    outline = Color(0xFF66BB6A),
    outlineVariant = Color(0xFF43A047)
)

// Minimal Theme - Clean grays and whites
val MinimalLightColorScheme = lightColorScheme(
    primary = Color(0xFF424242), // Dark gray
    secondary = Color(0xFF757575), // Medium gray
    tertiary = Color(0xFF9E9E9E), // Light gray
    background = Color(0xFFFFFFFF), // Pure white
    surface = Color(0xFFFAFAFA),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF212121),
    onSurface = Color(0xFF212121),
    primaryContainer = Color(0xFFEEEEEE),
    secondaryContainer = Color(0xFFF5F5F5),
    tertiaryContainer = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFFF8F8F8),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0)
)

val MinimalDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE0E0E0), // Light gray
    secondary = Color(0xFFBDBDBD), // Medium gray
    tertiary = Color(0xFF9E9E9E), // Darker gray
    background = Color(0xFF121212), // Very dark gray
    surface = Color(0xFF1E1E1E),
    onPrimary = Color(0xFF121212),
    onSecondary = Color(0xFF121212),
    onTertiary = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    primaryContainer = Color(0xFF424242),
    secondaryContainer = Color(0xFF2E2E2E),
    tertiaryContainer = Color(0xFF616161),
    surfaceVariant = Color(0xFF2A2A2A),
    outline = Color(0xFF616161),
    outlineVariant = Color(0xFF424242)
)

// Vibrant Theme - Bright and energetic colors
val VibrantLightColorScheme = lightColorScheme(
    primary = Color(0xFFE91E63), // Pink
    secondary = Color(0xFF9C27B0), // Purple
    tertiary = Color(0xFFFF5722), // Deep orange
    background = Color(0xFFFFF8E1), // Light cream
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF880E4F),
    onSurface = Color(0xFF4A148C),
    primaryContainer = Color(0xFFFC99AC),
    secondaryContainer = Color(0xFFE1BEE7),
    tertiaryContainer = Color(0xFFFFAB91),
    surfaceVariant = Color(0xFFFFF3E0),
    outline = Color(0xFFFF4081),
    outlineVariant = Color(0xFFBA68C8)
)

val VibrantDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF4081), // Bright pink
    secondary = Color(0xFFBA68C8), // Light purple
    tertiary = Color(0xFFFF7043), // Orange
    background = Color(0xFF4A148C), // Dark purple
    surface = Color(0xFF6A1B9A),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFFFF8E1),
    onSurface = Color(0xFFFFF8E1),
    primaryContainer = Color(0xFFAD1457),
    secondaryContainer = Color(0xFF7B1FA2),
    tertiaryContainer = Color(0xFFD84315),
    surfaceVariant = Color(0xFF7B1FA2),
    outline = Color(0xFFE91E63),
    outlineVariant = Color(0xFF9C27B0)
)

// Ocean Theme - Blues and teals
val OceanLightColorScheme = lightColorScheme(
    primary = Color(0xFF0277BD), // Deep blue
    secondary = Color(0xFF00ACC1), // Cyan
    tertiary = Color(0xFF00695C), // Teal
    background = Color(0xFFE1F5FE), // Light blue background
    surface = Color(0xFFE3F2FD),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF01579B),
    onSurface = Color(0xFF01579B),
    primaryContainer = Color(0xFF81D4FA), // Bright blue
    secondaryContainer = Color(0xFF80DEEA), // Bright cyan
    tertiaryContainer = Color(0xFF80CBC4), // Bright teal
    surfaceVariant = Color(0xFFE1F5FE),
    outline = Color(0xFF29B6F6),
    outlineVariant = Color(0xFF4DD0E1)
)

val OceanDarkColorScheme = darkColorScheme(
    primary = Color(0xFF29B6F6), // Light blue
    secondary = Color(0xFF4DD0E1), // Light cyan
    tertiary = Color(0xFF26A69A), // Teal
    background = Color(0xFF0D47A1), // Dark blue
    surface = Color(0xFF1565C0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFE3F2FD),
    onSurface = Color(0xFFE3F2FD),
    primaryContainer = Color(0xFF0288D1),
    secondaryContainer = Color(0xFF00838F),
    tertiaryContainer = Color(0xFF00695C),
    surfaceVariant = Color(0xFF1976D2),
    outline = Color(0xFF03A9F4),
    outlineVariant = Color(0xFF00BCD4)
)

// Forest Green Theme - Deep greens and teals
val ForestGreenLightColorScheme = lightColorScheme(
    primary = Color(0xFF375846), // Forest green
    secondary = Color(0xFF355645), // Earthy green
    tertiary = Color(0xFF385a47), // Deep green
    background = Color(0xFF283f3f), // Dark teal/green-gray
    surface = Color(0xFF27403f), // Muted teal
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFE8F5E8),
    onSurface = Color(0xFFE8F5E8),
    primaryContainer = Color(0xFF2c453a), // Greenish slate
    secondaryContainer = Color(0xFF263e3e), // Cool dark teal
    tertiaryContainer = Color(0xFF3d4f4f), // Slate grey-blue
    surfaceVariant = Color(0xFF172623), // Very dark green-black
    outline = Color(0xFF385a47),
    outlineVariant = Color(0xFF355645)
)

val ForestGreenDarkColorScheme = darkColorScheme(
    primary = Color(0xFF375846), // Forest green
    secondary = Color(0xFF355645), // Earthy green
    tertiary = Color(0xFF385a47), // Deep green
    background = Color(0xFF172623), // Very dark green-black
    surface = Color(0xFF162422), // Near black with green hint
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFE8F5E8),
    onSurface = Color(0xFFE8F5E8),
    primaryContainer = Color(0xFF2c453a), // Greenish slate
    secondaryContainer = Color(0xFF263e3e), // Cool dark teal
    tertiaryContainer = Color(0xFF3d4f4f), // Slate grey-blue
    surfaceVariant = Color(0xFF283f3f), // Dark teal/green-gray
    outline = Color(0xFF27403f),
    outlineVariant = Color(0xFF355645)
)

// Sunset Theme - Warm oranges and reds
val SunsetLightColorScheme = lightColorScheme(
    primary = Color(0xFFFF5722), // Deep orange
    secondary = Color(0xFFFFC107), // Amber
    tertiary = Color(0xFFE91E63), // Pink
    background = Color(0xFFFFF3E0), // Light orange background
    surface = Color(0xFFFFF8E1),
    onPrimary = Color.White,
    onSecondary = Color(0xFF333333),
    onTertiary = Color.White,
    onBackground = Color(0xFFBF360C),
    onSurface = Color(0xFFBF360C),
    primaryContainer = Color(0xFFFFCC80), // Bright orange
    secondaryContainer = Color(0xFFFFE082), // Bright yellow
    tertiaryContainer = Color(0xFFFFAB91), // Bright pink-orange
    surfaceVariant = Color(0xFFFFF3E0),
    outline = Color(0xFFFF7043),
    outlineVariant = Color(0xFFFFB74D)
)

val SunsetDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF7043), // Orange
    secondary = Color(0xFFFFB74D), // Light amber
    tertiary = Color(0xFFFF4081), // Pink
    background = Color(0xFFBF360C), // Dark orange
    surface = Color(0xFFD84315),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFFFF8E1),
    onSurface = Color(0xFFFFF8E1),
    primaryContainer = Color(0xFFE64A19),
    secondaryContainer = Color(0xFFFF8F00),
    tertiaryContainer = Color(0xFFC2185B),
    surfaceVariant = Color(0xFFE64A19),
    outline = Color(0xFFFF5722),
    outlineVariant = Color(0xFFFFC107)
)