package com.heroesofthebrain.halo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heroesofthebrain.halo.eeg.SourceStatus
import com.heroesofthebrain.halo.service.ChannelState
import com.heroesofthebrain.halo.service.PipelineState
import com.heroesofthebrain.halo.ui.theme.FreehandColors

/**
 * Freehand Signal Meter — Source of Truth: logo/kit/BRAND.md (§6)
 *
 * "Signal meter (5 elementów jak w logo): jakość sygnału z opaski.
 *  Wysokości słupków = poziom sygnału; szare (outline) = brak kontaktu elektrod"
 *
 * Displays real-time EEG electrode contact & connection quality using the 5-element
 * Freehand Wave Eye motif.
 */
@Composable
fun FreehandSignalCard(
    pipeline: PipelineState,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Ink800),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: Status title and Battery pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PupilStatusDot(
                        active = pipeline.running && pipeline.status is SourceStatus.Streaming,
                        armed = pipeline.armed,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when (val s = pipeline.status) {
                            SourceStatus.Idle -> "Headset disconnected"
                            SourceStatus.Scanning -> "Searching for HALO…"
                            is SourceStatus.Connecting -> "Connecting…"
                            is SourceStatus.Streaming -> "HALO Connected"
                            is SourceStatus.Reconnecting -> "Reconnecting…"
                            is SourceStatus.Waiting -> s.reason
                            SourceStatus.Finished -> "Replay complete"
                            is SourceStatus.Failed -> "Connection failed"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = FreehandColors.Cream,
                    )
                }

                // Battery pill
                pipeline.batteryPct?.let { pct ->
                    Box(
                        modifier = Modifier
                            .background(FreehandColors.Ink700, RoundedCornerShape(12.dp))
                            .border(1.dp, FreehandColors.Ink600, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "⚡ ${pct.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (pct <= 20f) FreehandColors.Warning else FreehandColors.Cream,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Wave Eye Signal Meter (5 Bars)
            FreehandWaveEyeMeter(
                running = pipeline.running,
                channels = pipeline.channels,
                status = pipeline.status,
                height = 54.dp,
            )

            Spacer(Modifier.height(14.dp))

            // Channel Badges (Ch1 - Ch4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val channels = pipeline.channels
                val channelLabels = if (channels.isNotEmpty()) {
                    channels
                } else {
                    listOf(
                        ChannelState("Ch 1", contact = false, signalOk = false),
                        ChannelState("Ch 2", contact = false, signalOk = false),
                        ChannelState("Ch 3", contact = false, signalOk = false),
                        ChannelState("Ch 4", contact = false, signalOk = false),
                    )
                }

                channelLabels.forEach { ch ->
                    val (dotColor, labelColor) = when {
                        !pipeline.running -> FreehandColors.SandMuted to FreehandColors.SandMuted
                        !ch.contact -> FreehandColors.Error to FreehandColors.Sand
                        !ch.signalOk -> FreehandColors.Warning to FreehandColors.Cream
                        else -> FreehandColors.Success to FreehandColors.Cream
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(FreehandColors.Ink700, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(dotColor, CircleShape),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = ch.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = labelColor,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 5-Bar Wave Eye Visualizer for Signal Quality
 */
@Composable
fun FreehandWaveEyeMeter(
    running: Boolean,
    channels: List<ChannelState>,
    status: SourceStatus,
    height: Dp = 50.dp,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "signal_meter_breathe")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "meter_breathe",
    )

    val isStreaming = running && status is SourceStatus.Streaming
    val ch0 = channels.getOrNull(0)
    val ch1 = channels.getOrNull(1)
    val ch2 = channels.getOrNull(2)
    val ch3 = channels.getOrNull(3)

    // Channel color resolver
    fun colorFor(ch: ChannelState?, defaultBrand: Color): Color = when {
        !running -> FreehandColors.Ink600
        ch == null || !ch.contact -> FreehandColors.Ink600
        !ch.signalOk -> FreehandColors.Warning
        else -> defaultBrand
    }

    val bar1Color by animateColorAsState(colorFor(ch0, FreehandColors.Ember), label = "c_bar1")
    val bar2Color by animateColorAsState(colorFor(ch1, FreehandColors.Flame), label = "c_bar2")
    val pupilColor by animateColorAsState(
        when {
            !running -> FreehandColors.Ink600
            status is SourceStatus.Streaming -> FreehandColors.Pupil
            status is SourceStatus.Connecting || status is SourceStatus.Scanning -> FreehandColors.Warning
            status is SourceStatus.Failed -> FreehandColors.Error
            else -> FreehandColors.Ink600
        },
        label = "c_pupil",
    )
    val bar4Color by animateColorAsState(colorFor(ch2, FreehandColors.Flame), label = "c_bar4")
    val bar5Color by animateColorAsState(colorFor(ch3, FreehandColors.Ember), label = "c_bar5")

    val width = height * 2.192f

    Canvas(modifier = modifier.width(width).height(height)) {
        val scaleX = width.toPx() / 228f
        val scaleY = height.toPx() / 104f

        fun mapX(x: Float) = (x - 14f) * scaleX
        fun mapY(y: Float) = (y - 76f) * scaleY
        fun mapW(w: Float) = w * scaleX
        fun mapH(h: Float) = h * scaleY

        val rx = mapW(13f)
        val breathe = if (isStreaming) breatheScale else 1.0f

        // bar1 (left outer)
        drawRoundRect(
            color = bar1Color,
            topLeft = Offset(mapX(14f), mapY(110f)),
            size = Size(mapW(26f), mapH(36f)),
            cornerRadius = CornerRadius(rx, rx),
        )

        // bar2 (left inner)
        drawRoundRect(
            color = bar2Color,
            topLeft = Offset(mapX(54f), mapY(76f)),
            size = Size(mapW(26f), mapH(104f) * breathe),
            cornerRadius = CornerRadius(rx, rx),
        )

        // pupil (center)
        val pr = mapW(34f) * (if (isStreaming) breathe else 1f)
        drawCircle(
            color = pupilColor,
            radius = pr,
            center = Offset(mapX(128f), mapY(128f)),
        )

        // bar4 (right inner)
        drawRoundRect(
            color = bar4Color,
            topLeft = Offset(mapX(176f), mapY(76f)),
            size = Size(mapW(26f), mapH(104f) * breathe),
            cornerRadius = CornerRadius(rx, rx),
        )

        // bar5 (right outer)
        drawRoundRect(
            color = bar5Color,
            topLeft = Offset(mapX(216f), mapY(110f)),
            size = Size(mapW(26f), mapH(36f)),
            cornerRadius = CornerRadius(rx, rx),
        )
    }
}
