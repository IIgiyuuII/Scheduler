package com.example.schedule.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val Brand1 = Color(0xFF4F46E5)   // индиго
val Brand2 = Color(0xFF9333EA)   // фиолетовый

private val LightColors = lightColorScheme(
    primary = Brand1, onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF), onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Brand2, onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF), onSecondaryContainer = Color(0xFF3B0764),
    background = Color(0xFFF6F5FF), onBackground = Color(0xFF1B1A2E),
    surface = Color.White, onSurface = Color(0xFF1B1A2E),
    surfaceVariant = Color(0xFFEAE8FA), onSurfaceVariant = Color(0xFF4B4A63),
    outline = Color(0xFFB9B6D6),
    error = Color(0xFFDC2626), onError = Color.White,
    errorContainer = Color(0xFFFEE2E2), onErrorContainer = Color(0xFF7F1D1D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC), onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81), onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFD8B4FE), onSecondary = Color(0xFF3B0764),
    secondaryContainer = Color(0xFF4C1D95), onSecondaryContainer = Color(0xFFF3E8FF),
    background = Color(0xFF0F0E1A), onBackground = Color(0xFFE8E6FA),
    surface = Color(0xFF181730), onSurface = Color(0xFFE8E6FA),
    surfaceVariant = Color(0xFF27254A), onSurfaceVariant = Color(0xFFC9C7E3),
    outline = Color(0xFF5E5C85),
    error = Color(0xFFFCA5A5), onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D), onErrorContainer = Color(0xFFFEE2E2),
)

@Composable
fun ScheduleTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = base.copy(
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
