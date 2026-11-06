package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisColorScheme = darkColorScheme(
    primary = ThemeElectricBlue,
    onPrimary = DeepSpaceBlack,
    primaryContainer = ThemeElectricBlue.copy(alpha = 0.25f),
    onPrimaryContainer = HologramTextBright,
    secondary = ThemeElectricBlue,
    onSecondary = DeepSpaceBlack,
    secondaryContainer = Color(0xFF003644),
    onSecondaryContainer = HologramTextBright,
    tertiary = ThemeSolarOrange,
    onTertiary = DeepSpaceBlack,
    background = DeepSpaceBlack,
    onBackground = HologramTextBright,
    surface = DarkHologramSurface,
    onSurface = HologramTextBright,
    surfaceVariant = HudCardSurface,
    onSurfaceVariant = HologramTextMuted,
    error = AlertCrimson,
    onError = Color.White
)

@Composable
fun JarvisTheme(
    accentColor: Color = ThemeElectricBlue,
    content: @Composable () -> Unit
) {
    val dynamicScheme = JarvisColorScheme.copy(
        primary = accentColor,
        primaryContainer = accentColor.copy(alpha = 0.25f),
        secondary = accentColor
    )
    MaterialTheme(
        colorScheme = dynamicScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    JarvisTheme(content = content)
}
