package com.heroesofthebrain.halo.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heroesofthebrain.halo.media.describe
import com.heroesofthebrain.halo.rules.Binding
import com.heroesofthebrain.halo.rules.Trigger
import com.heroesofthebrain.halo.rules.formatWindow
import com.heroesofthebrain.halo.ui.color
import com.heroesofthebrain.halo.ui.title
import com.heroesofthebrain.halo.ui.theme.FreehandColors
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Green = FreehandColors.Success
private val Amber = FreehandColors.Warning
private val Red = FreehandColors.Error
private val Grey = FreehandColors.SandMuted
private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

@Composable
fun TryItSheet(
    session: TryItSession,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onTick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep ticking while holding so elapsed ms increments smoothly, and while a macro waits for its next step
    val macroInProgress = session.macroStepIndex > 0
    LaunchedEffect(session.status, macroInProgress) {
        if (session.status == TryItStatus.HOLDING || macroInProgress) {
            while (true) {
                delay(30)
                onTick()
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = FreehandColors.Ink800,
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "TRY IT BENCH",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = session.binding.name.ifEmpty { session.binding.id },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedButton(onClick = onClose) {
                    Text("Done")
                }
            }

            Text(
                text = "Live gesture testing. Zero media actions are sent to your phone player.",
                style = MaterialTheme.typography.bodySmall,
                color = Grey,
            )

            Spacer(Modifier.height(14.dp))

            // Criteria card
            TargetCriteriaCard(session.binding)

            Spacer(Modifier.height(14.dp))

            // Main status gauge
            TestStatusGauge(session)

            Spacer(Modifier.height(12.dp))

            // Timeline breakdown if matched
            session.activeBreakdown?.let { breakdown ->
                TimingBreakdownCard(breakdown)
                Spacer(Modifier.height(12.dp))
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onReset) {
                    Text("Reset Gauge")
                }
            }

            // Recent attempts log
            if (session.history.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    text = "RECENT ATTEMPTS",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Grey,
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(modifier = Modifier.height(140.dp)) {
                    items(session.history, key = { it.id }) { result ->
                        ResultRow(result)
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetCriteriaCard(binding: Binding) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "TARGET TRIGGER",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Grey,
                )
                Text(
                    text = binding.triggerDescription(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "SIMULATED ACTION",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Grey,
                )
                Text(
                    text = binding.actionDescription(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestStatusGauge(session: TryItSession) {
    val statusColor by animateColorAsState(
        when (session.status) {
            TryItStatus.MATCHED -> Green
            TryItStatus.HOLDING -> Amber
            TryItStatus.TOO_SHORT, TryItStatus.TOO_LONG, TryItStatus.TIMED_OUT, TryItStatus.WRONG_STEP -> Amber
            TryItStatus.CANCELLED -> Red
            TryItStatus.IDLE, TryItStatus.LISTENING -> Grey
        },
        label = "statusColor",
    )

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(statusColor, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = session.status.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                }

                if (session.status == TryItStatus.HOLDING || session.currentHoldElapsedMs > 0) {
                    Text(
                        text = "${session.currentHoldElapsedMs} ms",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = statusColor,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = session.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )

            // Hold progress gauge
            if (session.binding.trigger is Trigger.Hold) {
                val hold = session.binding.trigger as Trigger.Hold
                val minMs = hold.minDurationMs
                val maxMs = hold.maxDurationMs ?: (minMs * 3)
                val current = session.currentHoldElapsedMs.toFloat()
                val progress = (current / maxMs).coerceIn(0f, 1f)

                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        current < minMs -> Amber
                        current <= (hold.maxDurationMs ?: Long.MAX_VALUE) -> Green
                        else -> Red
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("0 ms", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Grey)
                    Text("Min: ${minMs} ms", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Green)
                    Text(
                        if (hold.maxDurationMs != null) "Max: ${hold.maxDurationMs} ms" else "∞",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = if (hold.maxDurationMs != null) Amber else Grey,
                    )
                }
            }

            // Repeat indicator
            if (session.binding.trigger is Trigger.Repeat) {
                val repeat = session.binding.trigger as Trigger.Repeat
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 1..repeat.count) {
                        val active = i <= session.repeatCountSoFar
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (active) Green else Grey.copy(alpha = 0.2f))
                                .border(1.dp, if (active) Green else Grey, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$i",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else Grey,
                            )
                        }
                    }
                }
            }

            // Input macro steps
            if (session.binding.trigger is Trigger.InputMacro) {
                val macro = session.binding.trigger as Trigger.InputMacro
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    macro.steps.forEachIndexed { idx, step ->
                        val completed = idx < session.macroStepIndex
                        val isCurrent = idx == session.macroStepIndex
                        if (idx > 0) {
                            Text(
                                text = "≤ ${formatWindow(step.withinMs)} →",
                                style = MaterialTheme.typography.labelSmall,
                                color = Grey,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                completed -> Green.copy(alpha = 0.2f)
                                isCurrent -> Amber.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when {
                                    completed -> Green
                                    isCurrent -> Amber
                                    else -> Color.Transparent
                                },
                            ),
                        ) {
                            val hold = if (step.gesture.held) " ≥ ${formatWindow(step.minHoldMs.coerceAtLeast(100L))}" else ""
                            Text(
                                text = "${idx + 1}. ${step.gesture.title()}$hold",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                val remaining = session.macroRemainingMs
                if (remaining != null && session.macroStepIndex > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Step ${session.macroStepIndex + 1} window: ${formatWindow((remaining / 100) * 100)} left",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Amber,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimingBreakdownCard(breakdown: TimingBreakdown) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "SIMULATED TIMING SEQUENCE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))

            TimingRow("Gesture detection", "${breakdown.triggerDurationMs} ms")
            if (breakdown.ambiguityWaitMs > 0) {
                TimingRow("Ambiguity wait", "+${breakdown.ambiguityWaitMs} ms", isWarning = true)
            }
            if (breakdown.delayBeforeMs > 0) {
                TimingRow("Delay before execution", "+${breakdown.delayBeforeMs} ms")
            }
            TimingRow("Command", breakdown.executionSummary)
            if (breakdown.cooldownAfterMs > 0) {
                TimingRow("Cooldown period", "${breakdown.cooldownAfterMs} ms")
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            TimingRow("Total execution cycle", "~${breakdown.totalMs} ms", isBold = true)
        }
    }
}

@Composable
private fun TimingRow(label: String, value: String, isWarning: Boolean = false, isBold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isWarning) Amber else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isWarning) Amber else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ResultRow(result: TryItResult) {
    val color = when (result.status) {
        TryItStatus.MATCHED -> Green
        TryItStatus.HOLDING -> Amber
        TryItStatus.TOO_SHORT, TryItStatus.TOO_LONG, TryItStatus.TIMED_OUT, TryItStatus.WRONG_STEP -> Amber
        TryItStatus.CANCELLED -> Red
        TryItStatus.IDLE, TryItStatus.LISTENING -> Grey
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeFormat.format(Date(result.timestampMs)),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Grey,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = result.summary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (result.durationMs != null) {
            Text(
                text = "${result.durationMs} ms",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
