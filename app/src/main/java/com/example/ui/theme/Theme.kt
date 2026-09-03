package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MinimalBlueAccent,
    onPrimary = MinimalBlueOnContainer,
    primaryContainer = MinimalBlueContainer,
    onPrimaryContainer = MinimalBlueContainerLight,
    secondary = MinimalBlueContainerLight,
    onSecondary = MinimalBlueOnContainer,
    secondaryContainer = MinimalBlueContainer,
    onSecondaryContainer = MinimalBlueContainerLight,
    tertiary = SecurityEmerald,
    onTertiary = SecurityEmeraldDark,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = Color(0xFF36383A),
    error = SecurityRed,
    onError = Color(0xFF601410)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF00639B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC2E7FF),
    onSecondaryContainer = Color(0xFF001D32),
    tertiary = Color(0xFF006D3A),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = Color(0xFFC4C7C5),
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun VaultKeepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}


