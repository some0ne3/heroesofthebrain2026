package com.heroesofthebrain.halo.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Freehand Shape System — Source of Truth: logo/kit/BRAND.md (§5)
 *
 * Core motifs: capsule and circle (direct echo of the Wave Eye mark).
 * Touch targets: minimum 56dp for accessibility.
 */
object FreehandDimens {
    val TouchTargetMin = 56.dp
    val ButtonHeight = 56.dp
    val PlayButtonSize = 88.dp
    val CardRadius = 24.dp
    val SheetRadius = 28.dp
    val FieldRadius = 16.dp
    val SpacingSmall = 8.dp
    val SpacingMedium = 16.dp
    val SpacingLarge = 24.dp
    val ScreenMargin = 20.dp
}

val FreehandShapes = Shapes(
    small = RoundedCornerShape(16.dp),       // Text fields, small chips
    medium = RoundedCornerShape(24.dp),      // Cards, dialogs
    large = RoundedCornerShape(28.dp),       // Bottom sheets, hero containers
    extraLarge = CircleShape,                // Buttons, pills, status indicators
)
