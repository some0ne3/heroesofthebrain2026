package com.heroesofthebrain.halo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.heroesofthebrain.halo.ui.theme.FreehandColors

/**
 * Freehand Pupil Status Dot — Source of Truth: logo/kit/BRAND.md (§6)
 *
 * "Pupil dot: stan systemu:
 *  pupil pulsuje = nasłuch; pełne = gest rozpoznany; text-tertiary = pauza"
 */
@Composable
fun PupilStatusDot(
    active: Boolean,
    armed: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pupil_dot_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    val dotColor = when {
        !active -> FreehandColors.SandMuted
        armed -> FreehandColors.Pupil
        else -> FreehandColors.Warning.copy(alpha = 0.6f)
    }

    Box(
        modifier = modifier.size(size * 1.6f),
        contentAlignment = Alignment.Center,
    ) {
        if (active && armed) {
            // Glowing aura
            Box(
                Modifier
                    .size(size)
                    .scale(pulseScale)
                    .background(dotColor.copy(alpha = pulseAlpha * 0.45f), CircleShape),
            )
        }
        // Center solid dot
        Box(
            Modifier
                .size(size)
                .background(dotColor, CircleShape),
        )
    }
}
