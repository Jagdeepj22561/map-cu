package com.example.maps123.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF1744),       // vivid red accents
    background = Color(0xFF000000),    // pure black background
    surface = Color(0xFF000000),       // black surfaces to keep high contrast
    onPrimary = Color.White,           // white text/icons on red
    onBackground = Color.White,        // white content on black
    onSurface = Color.White            // white content on surfaces
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFD50000),       // deep red for light mode
    background = Color(0xFFFFFFFF),    // pure white background
    surface = Color(0xFFFFFFFF),       // white surfaces
    onPrimary = Color.White,           // white text/icons on red
    onBackground = Color.Black,        // black content on white
    onSurface = Color.Black            // black content on surfaces
)

@Composable
fun CampusTheme(
    darkMode: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkMode) DarkColors else LightColors,
        content = content
    )
}
