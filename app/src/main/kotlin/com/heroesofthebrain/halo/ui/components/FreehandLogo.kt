package com.heroesofthebrain.halo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heroesofthebrain.halo.ui.theme.FreehandColors

/**
 * Freehand "Wave Eye" Logo Component.
 * Faithfully implements geometry and colors from logo/kit/BRAND.md (§2).
 *
 * Słupki (EEG / Sound Wave) + Źrenica (Signal / Brain Access HALO).
 */
@Composable
fun FreehandSymbol(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    animated: Boolean = false,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_eye_anim")
    val pulseScale by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pupil_pulse",
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1.0f) }
    }

    // Aspect ratio of the symbol viewBox: 228 wide, 104 high ≈ 2.192
    val width = size * 2.192f
    val height = size

    Canvas(modifier = modifier.width(width).height(height)) {
        // Base viewBox mapping: x from 14 to 242 (width 228), y from 76 to 180 (height 104)
        val scaleX = size.toPx() * 2.192f / 228f
        val scaleY = size.toPx() / 104f

        fun mapX(origX: Float) = (origX - 14f) * scaleX
        fun mapY(origY: Float) = (origY - 76f) * scaleY
        fun mapW(origW: Float) = origW * scaleX
        fun mapH(origH: Float) = origH * scaleY

        val barRx = mapW(13f)

        // bar1 (left outer): 26 x 36 at x=14, y=110
        drawRoundRect(
            color = FreehandColors.Ember,
            topLeft = Offset(mapX(14f), mapY(110f)),
            size = Size(mapW(26f), mapH(36f)),
            cornerRadius = CornerRadius(barRx, barRx),
        )

        // bar2 (left inner): 26 x 104 at x=54, y=76
        drawRoundRect(
            color = FreehandColors.Flame,
            topLeft = Offset(mapX(54f), mapY(76f)),
            size = Size(mapW(26f), mapH(104f)),
            cornerRadius = CornerRadius(barRx, barRx),
        )

        // pupil (center circle): cx=128, cy=128, r=34
        val pupilRadius = mapW(34f) * pulseScale
        drawCircle(
            color = FreehandColors.Pupil,
            radius = pupilRadius,
            center = Offset(mapX(128f), mapY(128f)),
        )

        // bar4 (right inner): 26 x 104 at x=176, y=76
        drawRoundRect(
            color = FreehandColors.Flame,
            topLeft = Offset(mapX(176f), mapY(76f)),
            size = Size(mapW(26f), mapH(104f)),
            cornerRadius = CornerRadius(barRx, barRx),
        )

        // bar5 (right outer): 26 x 36 at x=216, y=110
        drawRoundRect(
            color = FreehandColors.Ember,
            topLeft = Offset(mapX(216f), mapY(110f)),
            size = Size(mapW(26f), mapH(36f)),
            cornerRadius = CornerRadius(barRx, barRx),
        )
    }
}

/**
 * Freehand Lockup: Symbol + Wordmark "freehand"
 */
@Composable
fun FreehandHeaderLockup(
    modifier: Modifier = Modifier,
    symbolHeight: Dp = 26.dp,
    textColor: Color = FreehandColors.Cream,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FreehandSymbol(size = symbolHeight, animated = true)
        Text(
            text = "freehand",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            letterSpacing = (-0.5).sp,
            color = textColor,
        )
    }
}
