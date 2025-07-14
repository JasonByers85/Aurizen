package com.aurizen.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * AuriZen gradient background used throughout the app - now theme-aware
 */
@Composable
fun AuriZenGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val gradientColors = getThemeGradientColors()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = gradientColors
                )
            )
    ) {
        content()
    }
}

@Composable
private fun getThemeGradientColors(): List<Color> {
    val colorScheme = MaterialTheme.colorScheme
    
    // Create distinctive gradients based on primary colors to differentiate themes
    return listOf(
        colorScheme.background,
        colorScheme.primaryContainer.copy(alpha = 0.6f),
        colorScheme.secondaryContainer.copy(alpha = 0.4f),
        colorScheme.tertiaryContainer.copy(alpha = 0.2f)
    )
}