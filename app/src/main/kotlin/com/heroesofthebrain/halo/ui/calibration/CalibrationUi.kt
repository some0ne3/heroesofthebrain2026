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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val Green = Color(0xFF43A047)
internal val Amber = Color(0xFFFFB300)
internal val Red = Color(0xFFE53935)
internal val Grey = Color(0xFF9E9E9E)

internal fun CalibrationGesture.color(): Color = when (this) {
    CalibrationGesture.CLENCH -> Color(0xFFFF8F00)
    CalibrationGesture.TURN_LEFT, CalibrationGesture.TURN_RIGHT -> Color(0xFF1E88E5)
    CalibrationGesture.TURN_UP, CalibrationGesture.TURN_DOWN -> Color(0xFF8E24AA)
    CalibrationGesture.DOUBLE_BLINK -> Color(0xFF00897B)
    CalibrationGesture.LONG_BLINK -> Color(0xFF6D4C41)
}

internal fun rateColor(rate: Float): Color = when {
    rate >= 0.8f -> Green
    rate >= 0.5f -> Amber
    else -> Red
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
