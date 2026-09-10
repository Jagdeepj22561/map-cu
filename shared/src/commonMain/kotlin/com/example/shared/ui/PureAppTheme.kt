package com.example.shared.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PreviewDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF1744),
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF3A0A16),
    secondaryContainer = Color(0xFF202020),
    tertiaryContainer = Color(0xFF221B10),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFD0D0D0),
    onPrimaryContainer = Color.White,
    onSecondaryContainer = Color.White,
    onTertiaryContainer = Color.White
)

private val PreviewLightColorScheme = lightColorScheme(
    primary = Color(0xFFD50000),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3EDF7),
    primaryContainer = Color(0xFFFFDAD6),
    secondaryContainer = Color(0xFFE8DEF8),
    tertiaryContainer = Color(0xFFFFE08A),
    onPrimary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF49454F),
    onPrimaryContainer = Color(0xFF410002),
    onSecondaryContainer = Color(0xFF1D192B),
    onTertiaryContainer = Color(0xFF2B2000)
)

@Composable
fun PureAppTheme(
    isDarkMode: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isDarkMode) PreviewDarkColorScheme else PreviewLightColorScheme,
        content = content
    )
}
