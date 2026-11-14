package com.heroesofthebrain.halo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.ui.theme.FreehandColors
import kotlinx.coroutines.delay

/**
 * Freehand Gesture Toast Confirmation — Source of Truth: logo/kit/BRAND.md (§6)
 *
 * "Gesture toast: kapsuła u dołu ekranu: ikona gestu + nazwa akcji
 *  ('Podwójne mrugnięcie · Pauza'), czas trwania 1.2 s"
 */
data class GestureToastData(
    val gesture: GestureType,
    val actionText: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

@Composable
fun FreehandGestureToast(
    toast: GestureToastData?,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(toast?.timestamp) {
        if (toast != null) {
            visible = true
            delay(1200) // 1.2s per design brief
            visible = false
            onDismiss()
        } else {
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible && toast != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        if (toast != null) {
            val gestureColor = FreehandColors.forGesture(toast.gesture)
            val gestureLabel = when (toast.gesture) {
                GestureType.DOUBLE_BLINK -> "Double Blink"
                GestureType.LONG_BLINK -> "Long Blink"
                GestureType.CLENCH -> "Jaw Clench"
                GestureType.TURN_LEFT -> "Turn Head ←"
                GestureType.TURN_RIGHT -> "Turn Head →"
                GestureType.TURN_UP -> "Nod Head ↑"
                GestureType.TURN_DOWN -> "Tilt Head ↓"
            }

            Box(
                modifier = Modifier
                    .shadow(16.dp, CircleShape, spotColor = gestureColor.copy(alpha = 0.5f))
                    .clip(CircleShape)
                    .background(FreehandColors.Ink800)
                    .border(1.5.dp, gestureColor.copy(alpha = 0.8f), CircleShape)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Gesture Icon / Dot
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(gestureColor, CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = gestureLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = FreehandColors.Cream,
                    )
                    if (!toast.actionText.isNullOrBlank()) {
                        Text(
                            text = "  ·  ${toast.actionText}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            color = FreehandColors.Sand,
                        )
                    }
                }
            }
        }
    }
}
