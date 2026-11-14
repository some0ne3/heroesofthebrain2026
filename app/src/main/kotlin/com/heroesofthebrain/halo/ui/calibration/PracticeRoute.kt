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
import com.heroesofthebrain.halo.calibration.CalibrationGesture
import com.heroesofthebrain.halo.calibration.gesture
import com.heroesofthebrain.halo.calibration.isOnset
import com.heroesofthebrain.halo.detect.GestureEnd
import com.heroesofthebrain.halo.detect.GestureEvent
import com.heroesofthebrain.halo.detect.GestureStart
import com.heroesofthebrain.halo.detect.describe
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

    Scaffold(modifier) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Grey)
            Spacer(Modifier.height(12.dp))

            // Live detection banner
            AnimatedVisibility(
                visible = current != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                val event = current
                if (event != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(event.gesture.color().copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(event.gesture.color())
                        Spacer(Modifier.width(8.dp))
                        Text(
                            event.gesture.title,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = event.gesture.color(),
                        )
                        if (event is GestureStart) {
                            Spacer(Modifier.width(8.dp))
                            Text("holding…", style = MaterialTheme.typography.bodyMedium, color = Grey)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Detection log
            if (log.isEmpty()) {
                Text(
                    "Waiting for gestures…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grey,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Text("Recent detections", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(log, key = { it.id }) { entry ->
                        PracticeLogRow(entry)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { log.clear() }) { Text("Clear") }
                OutlinedButton(onClick = onClose) { Text("Close") }
            }
            Spacer(Modifier.height(16.dp))
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
            .background(
                color.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(color)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(gesture.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            val detail = buildString {
                append(entry.event.describe())
                entry.endEvent?.let { end ->
                    append(" → held %.2f s".format(Locale.US, end.durationMs / 1000.0))
                }
            }
            Text(detail, style = MaterialTheme.typography.labelSmall, color = Grey)
        }
        Text(
            "%.0f %%".format(Locale.US, entry.event.confidence * 100),
            style = MaterialTheme.typography.labelMedium,
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
