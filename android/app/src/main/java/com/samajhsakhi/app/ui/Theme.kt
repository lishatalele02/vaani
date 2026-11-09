package com.samajhsakhi.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF0B6E75),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2F1F2),
    onPrimaryContainer = Color(0xFF06383C),
    secondary = Color(0xFF506062),
    secondaryContainer = Color(0xFFEEF2F2),
    onSecondaryContainer = Color(0xFF1B2728),
    background = Color(0xFFFAFBFB),
    onBackground = Color(0xFF151B1C),
    surface = Color.White,
    onSurface = Color(0xFF151B1C),
    surfaceVariant = Color(0xFFF0F3F3),
    onSurfaceVariant = Color(0xFF4A5859),
    outline = Color(0xFF8A9899),
    outlineVariant = Color(0xFFDCE2E2),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFBE9E7),
    onErrorContainer = Color(0xFF410E0B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD3D8),
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF12393C),
    onPrimaryContainer = Color(0xFFCDEBED),
    secondary = Color(0xFFB1CBCD),
    secondaryContainer = Color(0xFF223031),
    onSecondaryContainer = Color(0xFFDDE8E9),
    background = Color(0xFF0F1415),
    onBackground = Color(0xFFE2E5E5),
    surface = Color(0xFF161D1E),
    onSurface = Color(0xFFE2E5E5),
    surfaceVariant = Color(0xFF1F2829),
    onSurfaceVariant = Color(0xFFB9C3C4),
    outline = Color(0xFF7A8687),
    outlineVariant = Color(0xFF2E3A3B),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF4A1612),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Maximum-contrast palette (black on white, strong teal/red) for low vision. */
private val HighContrast = lightColorScheme(
    primary = Color(0xFF00363A),
    onPrimary = Color.White,
    primaryContainer = Color.White,
    onPrimaryContainer = Color.Black,
    secondary = Color.Black,
    secondaryContainer = Color(0xFFFFF3B0),
    onSecondaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color.Black,
    outline = Color.Black,
    outlineVariant = Color.Black,
    error = Color(0xFF8C0000),
    errorContainer = Color.White,
    onErrorContainer = Color(0xFF5C0000),
)

@Composable
fun SamajhTheme(highContrast: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = when {
            highContrast -> HighContrast
            isSystemInDarkTheme() -> Dark
            else -> Light
        },
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(18.dp),
        ),
        content = content,
    )
}
