package com.heroesofthebrain.halo.ui.calibration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import com.heroesofthebrain.halo.calibration.CalibrationGesture
import com.heroesofthebrain.halo.calibration.gesture
import com.heroesofthebrain.halo.calibration.isOnset
import com.heroesofthebrain.halo.detect.GestureEnd
import com.heroesofthebrain.halo.detect.GestureEvent
import com.heroesofthebrain.halo.detect.GestureStart
import com.heroesofthebrain.halo.detect.describe
import com.heroesofthebrain.halo.ui.theme.FreehandColors
import kotlinx.coroutines.flow.Flow
import java.util.Locale

// Practice mode: the user performs gestures and sees detections live, with no actions fired.
// events: a Flow of GestureEvents from a detector running with the given thresholds.
@Composable
fun PracticeRoute(
    events: Flow<GestureEvent>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Practice mode",
    subtitle: String = "Perform gestures to see if the detector picks them up. Nothing is sent to your media player.",
) {
    val log = remember { mutableStateListOf<PracticeEntry>() }
    var current by remember { mutableStateOf<GestureEvent?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(events) {
        events.collect { event ->
            when {
                event.isOnset -> {
                    current = event
                    log.add(0, PracticeEntry(event))
                }
                event is GestureEnd -> {
                    current = null
                    val existing = log.indexOfFirst { it.event.type == event.type && it.event is GestureStart && it.endEvent == null }
                    if (existing >= 0) log[existing] = log[existing].copy(endEvent = event)
                }
                else -> {
                    current = null
                    log.add(0, PracticeEntry(event))
                }
            }
        }
    }

    // Auto-scroll to top when a new entry arrives
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Scaffold(
        modifier = modifier,
        containerColor = FreehandColors.Ink900,
        contentColor = FreehandColors.Cream,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .fillMaxSize(),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = FreehandColors.Cream)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FreehandColors.Sand)
            Spacer(Modifier.height(16.dp))

            // Live detection banner
            AnimatedVisibility(
                visible = current != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                val event = current
                if (event != null) {
                    val gColor = event.gesture.color()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(FreehandColors.Ink800, RoundedCornerShape(20.dp))
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(gColor)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            event.gesture.title,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = gColor,
                        )
                        if (event is GestureStart) {
                            Spacer(Modifier.width(10.dp))
                            Text("holding…", style = MaterialTheme.typography.bodyMedium, color = FreehandColors.SandMuted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Detection log
            if (log.isEmpty()) {
                Text(
                    "Waiting for gestures…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FreehandColors.SandMuted,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Text("Recent detections", style = MaterialTheme.typography.titleSmall, color = FreehandColors.Cream)
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(log, key = { it.id }) { entry ->
                        PracticeLogRow(entry)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { log.clear() },
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FreehandColors.Cream),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
                    modifier = Modifier.height(48.dp),
                ) { Text("Clear") }

                OutlinedButton(
                    onClick = onClose,
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FreehandColors.Cream),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
                    modifier = Modifier.height(48.dp),
                ) { Text("Close") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private data class PracticeEntry(
    val event: GestureEvent,
    val endEvent: GestureEnd? = null,
    val id: Long = idCounter++,
) {
    companion object {
        private var idCounter = 0L
    }
}

@Composable
private fun PracticeLogRow(entry: PracticeEntry) {
    val gesture = entry.event.gesture
    val color = gesture.color()
    Row(
        Modifier
            .fillMaxWidth()
            .background(FreehandColors.Ink800, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(gesture.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = FreehandColors.Cream)
            val detail = buildString {
                append(entry.event.describe())
                entry.endEvent?.let { end ->
                    append(" → held %.2f s".format(Locale.US, end.durationMs / 1000.0))
                }
            }
            Text(detail, style = MaterialTheme.typography.labelSmall, color = FreehandColors.Sand)
        }
        Text(
            "%.0f %%".format(Locale.US, entry.event.confidence * 100),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (entry.event.confidence >= 0.7f) Green else if (entry.event.confidence >= 0.4f) Amber else Red,
        )
    }
}

// Standalone entry point for practice from an existing calibration, not from the results screen.
@Composable
fun PracticeRoute(
    events: Flow<GestureEvent>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    calibrationGesture: CalibrationGesture? = null,
) {
    PracticeRoute(
        events = events,
        onClose = onClose,
        modifier = modifier,
        title = if (calibrationGesture != null) "Practice: ${calibrationGesture.title}" else "Practice mode",
        subtitle = "Perform gestures to see if the detector picks them up. Nothing is sent to your media player.",
    )
}
