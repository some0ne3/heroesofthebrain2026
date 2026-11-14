package com.heroesofthebrain.halo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.heroesofthebrain.halo.detect.DebugTrace
import com.heroesofthebrain.halo.eeg.EEG_CHANNELS
import com.heroesofthebrain.halo.eeg.EEG_LABELS
import kotlin.math.abs
import kotlin.math.max

private val laneColors = listOf(Color(0xFF66BB6A), Color(0xFFFFCA28), Color(0xFF42A5F5), Color(0xFFEF5350), Color(0xFFBDBDBD))

// One lane per series: the 4 EEG channels (symmetric auto-scale) and the gyro magnitude (0 up).
@Composable
fun TracePlot(series: List<FloatArray>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val lanes = DebugTrace.SERIES
        val laneHeight = size.height / lanes
        val dx = size.width / (DebugTrace.CAPACITY - 1)
        for (lane in 0 until lanes) {
            val top = lane * laneHeight
            drawLine(gridColor, Offset(0f, top + laneHeight), Offset(size.width, top + laneHeight))
            val data = series.getOrNull(lane)
            val isGyro = lane == EEG_CHANNELS
            var low: Float
            var high: Float
            if (isGyro) {
                low = 0f
                high = max(100f, data?.maxOrNull() ?: 0f)
            } else {
                val m = max(50f, data?.maxOfOrNull { abs(it) } ?: 0f)
                low = -m
                high = m
            }
            fun y(v: Float) = top + laneHeight * (1 - (v - low) / (high - low))
            if (isGyro) {
                for (line in listOf(20f, 50f)) {
                    drawLine(gridColor, Offset(0f, y(line)), Offset(size.width, y(line)), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                }
            }
            if (data != null && data.size >= 2) {
                val x0 = size.width - (data.size - 1) * dx
                val path = Path()
                data.forEachIndexed { i, v ->
                    val x = x0 + i * dx
                    if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
                }
                drawPath(path, laneColors[lane], style = Stroke(width = 2f))
            }
            val name = if (isGyro) "|gyro| 0-${high.toInt()} °/s" else "${EEG_LABELS[lane]} ±${high.toInt()} µV"
            drawText(measurer, name, topLeft = Offset(4f, top + 2f), style = TextStyle(color = textColor, fontSize = 10.sp))
        }
    }
}
