package com.medbox.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E4),
    background = Color(0xFFF6F9F8),
    onBackground = Color(0xFF191C1B),
    surface = Color.White,
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFEDF3F1),
    onSurfaceVariant = Color(0xFF47514F),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD9CC),
    onPrimary = Color(0xFF00332E),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFFB1CCC7),
    background = Color(0xFF0F1413),
    onBackground = Color(0xFFE1E3E2),
    surface = Color(0xFF171D1C),
    onSurface = Color(0xFFE1E3E2),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBFC9C7),
    error = Color(0xFFF2B8B5)
)

@Composable
fun MedBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
