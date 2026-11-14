package com.heroesofthebrain.halo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.heroesofthebrain.halo.ui.theme.FreehandColors as C

/**
 * Freehand Dark Theme (Default) — Source of Truth: logo/kit/BRAND.md (§9)
 */
val FreehandDark = darkColorScheme(
    primary = C.Flame,
    onPrimary = C.Ink900,
    primaryContainer = Color(0xFF4A2414),
    onPrimaryContainer = Color(0xFFFFD9C9),
    secondary = C.Pupil,
    onSecondary = C.Ink900,
    secondaryContainer = Color(0xFF423214),
    onSecondaryContainer = Color(0xFFFFE082),
    tertiary = C.Ember,
    onTertiary = C.Cream,
    tertiaryContainer = Color(0xFF4D1713),
    onTertiaryContainer = Color(0xFFFFB4A9),
    background = C.Ink900,
    onBackground = C.Cream,
    surface = C.Ink800,
    onSurface = C.Cream,
    surfaceVariant = C.Ink700,
    onSurfaceVariant = C.Sand,
    surfaceContainer = C.Ink800,
    surfaceContainerHigh = C.Ink700,
    surfaceContainerHighest = C.Ink650,
    outline = C.Ink600,
    outlineVariant = C.Ink700,
    error = C.Error,
    onError = C.Ink900,
)

/**
 * Freehand Light Theme (Secondary)
 */
val FreehandLight = lightColorScheme(
    primary = C.Rust,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCCB),
    onPrimaryContainer = Color(0xFF3A1406),
    secondary = C.PupilLight,
    onSecondary = C.InkText,
    secondaryContainer = Color(0xFFFFF0D4),
    onSecondaryContainer = Color(0xFF402800),
    tertiary = C.EmberLight,
    onTertiary = Color.White,
    background = C.Paper,
    onBackground = C.InkText,
    surface = Color.White,
    onSurface = C.InkText,
    surfaceVariant = C.PaperDim,
    onSurfaceVariant = C.Taupe,
    outline = C.Linen,
    outlineVariant = C.PaperDim,
    error = C.ErrorLight,
    onError = Color.White,
)

@Composable
fun FreehandTheme(
    darkTheme: Boolean = true, // Freehand is dark by default per brand identity (§3.2)
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FreehandDark else FreehandLight

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FreehandTypography,
        shapes = FreehandShapes,
        content = content,
    )
}
