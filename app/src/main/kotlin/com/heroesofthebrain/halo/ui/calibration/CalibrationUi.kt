package com.heroesofthebrain.halo.ui.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.heroesofthebrain.halo.calibration.CalibrationGesture
import com.heroesofthebrain.halo.ui.theme.FreehandColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val Green = FreehandColors.Success
internal val Amber = FreehandColors.Warning
internal val Red = FreehandColors.Error
internal val Grey = FreehandColors.SandMuted

internal fun CalibrationGesture.color(): Color = when (this) {
    CalibrationGesture.CLENCH -> FreehandColors.Flame
    CalibrationGesture.TURN_LEFT, CalibrationGesture.TURN_RIGHT,
    CalibrationGesture.TURN_UP, CalibrationGesture.TURN_DOWN -> FreehandColors.Ember
    CalibrationGesture.DOUBLE_BLINK, CalibrationGesture.LONG_BLINK -> FreehandColors.Pupil
}

internal fun rateColor(rate: Float): Color = when {
    rate >= 0.8f -> FreehandColors.Success
    rate >= 0.5f -> FreehandColors.Warning
    else -> FreehandColors.Error
}

@Composable
internal fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .background(color, CircleShape),
    )
}

fun formatDate(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))

internal fun formatSeconds(ms: Long): String = "%.2f s".format(Locale.US, ms / 1000.0)

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(Locale.US, bytes / 1_000_000.0)
    else -> "${(bytes + 999) / 1000} kB"
}
