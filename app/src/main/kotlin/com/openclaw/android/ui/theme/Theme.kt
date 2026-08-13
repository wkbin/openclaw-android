package com.openclaw.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density

@Composable
fun OpenClawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    uiScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF8FA36A),
            onPrimary = Color(0xFF141412),
            primaryContainer = Color(0x338FA36A),
            onPrimaryContainer = Color(0xFFE8E6DC),
            secondary = Color(0xFFD9A85B),
            onSecondary = Color(0xFF141412),
            error = Color(0xFFC77469),
            background = Color(0xFF141412),
            onBackground = Color(0xFFE8E6DC),
            surface = Color(0xFF1C1C1A),
            onSurface = Color(0xFFE8E6DC),
            surfaceVariant = Color(0x338FA36A),
            onSurfaceVariant = Color(0xFF8F8E85),
            outline = Color(0xFF2A2A26),
            outlineVariant = Color(0x22E8E6DC),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6E8050),
            onPrimary = Color(0xFFFDFDFB),
            primaryContainer = Color(0x1A6E8050),
            onPrimaryContainer = Color(0xFF1A1A17),
            secondary = Color(0xFFB88B3A),
            onSecondary = Color(0xFFFDFDFB),
            error = Color(0xFFA8514A),
            background = Color(0xFFF9F9F6),
            onBackground = Color(0xFF1A1A17),
            surface = Color(0xFFFDFDFB),
            onSurface = Color(0xFF1A1A17),
            surfaceVariant = Color(0x1A6E8050),
            onSurfaceVariant = Color(0xFF86857B),
            outline = Color(0xFFE6E4DC),
            outlineVariant = Color(0x141A1A17),
        )
    }
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density * uiScale.coerceIn(0.7f, 1.4f),
        fontScale = baseDensity.fontScale * uiScale.coerceIn(0.7f, 1.4f),
    )
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}
