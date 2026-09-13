package com.example.shared.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BrandRed = Color(0xFFC62828)
private val BrandRedLight = Color(0xFFFFE4E1)
private val Ink = Color(0xFF17181C)
private val Muted = Color(0xFF686B73)
private val Canvas = Color(0xFFF7F7F9)
private val Card = Color(0xFFFFFFFF)
private val DarkCanvas = Color(0xFF101114)
private val DarkCard = Color(0xFF191A1F)

private val LightColors = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = BrandRedLight,
    onPrimaryContainer = Color(0xFF5A1010),
    secondary = Color(0xFF4F5D75),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8ECF4),
    onSecondaryContainer = Color(0xFF172033),
    tertiary = Color(0xFF7A4E00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8B0),
    onTertiaryContainer = Color(0xFF382500),
    background = Canvas,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDEDF1),
    onSurfaceVariant = Muted,
    outline = Color(0xFFD5D6DB),
    outlineVariant = Color(0xFFE3E4E8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF6B67),
    onPrimary = Color(0xFF4A0505),
    primaryContainer = Color(0xFF6D1717),
    onPrimaryContainer = Color(0xFFFFDAD7),
    secondary = Color(0xFFB9C3D8),
    onSecondary = Color(0xFF202938),
    secondaryContainer = Color(0xFF303847),
    onSecondaryContainer = Color(0xFFDCE3F4),
    tertiary = Color(0xFFFFC96B),
    onTertiary = Color(0xFF422900),
    tertiaryContainer = Color(0xFF5A4008),
    onTertiaryContainer = Color(0xFFFFDEA2),
    background = DarkCanvas,
    onBackground = Color(0xFFF2F2F4),
    surface = DarkCard,
    onSurface = Color(0xFFF2F2F4),
    surfaceVariant = Color(0xFF24262C),
    onSurfaceVariant = Color(0xFFC1C3CA),
    outline = Color(0xFF41434A),
    outlineVariant = Color(0xFF303238)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun PureAppTheme(
    isDarkMode: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isDarkMode) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
