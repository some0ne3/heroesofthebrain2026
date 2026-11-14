package com.heroesofthebrain.halo.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.eeg.SourceStatus
import com.heroesofthebrain.halo.service.ActionBlock
import com.heroesofthebrain.halo.ui.calibration.CalibrationRoute
import com.heroesofthebrain.halo.ui.calibration.PracticeRoute
import com.heroesofthebrain.halo.ui.calibration.formatDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Green = Color(0xFF43A047)
private val Amber = Color(0xFFFFB300)
private val Red = Color(0xFFE53935)
private val Grey = Color(0xFF9E9E9E)

fun GestureType?.color(): Color = when (this) {
    GestureType.CLENCH -> Color(0xFFFF8F00)
    GestureType.TURN_LEFT, GestureType.TURN_RIGHT -> Color(0xFF1E88E5)
    GestureType.TURN_UP, GestureType.TURN_DOWN -> Color(0xFF8E24AA)
    GestureType.DOUBLE_BLINK -> Color(0xFF00897B)
    GestureType.LONG_BLINK -> Color(0xFF6D4C41)
    null -> Grey
}

fun GestureType?.title(): String = when (this) {
    GestureType.CLENCH -> "CLENCH"
    GestureType.TURN_LEFT -> "TURN ←"
    GestureType.TURN_RIGHT -> "TURN →"
    GestureType.TURN_UP -> "TURN ↑"
    GestureType.TURN_DOWN -> "TURN ↓"
    GestureType.DOUBLE_BLINK -> "DOUBLE BLINK"
    GestureType.LONG_BLINK -> "LONG BLINK"
    null -> "NONE"
}

private fun SourceStatus.text(): String = when (this) {
    SourceStatus.Idle -> "Not connected"
    SourceStatus.Scanning -> "Scanning for a HALO…"
    is SourceStatus.Connecting -> "Connecting to $device…"
    is SourceStatus.Streaming -> device
    is SourceStatus.Reconnecting -> "Reconnecting: $reason"
    is SourceStatus.Waiting -> reason
    SourceStatus.Finished -> "Replay finished"
    is SourceStatus.Failed -> message
}

private fun SourceStatus.color(): Color = when (this) {
    is SourceStatus.Streaming -> Green
    SourceStatus.Scanning, is SourceStatus.Connecting, is SourceStatus.Reconnecting, is SourceStatus.Waiting -> Amber
    is SourceStatus.Failed -> Red
    SourceStatus.Idle, SourceStatus.Finished -> Grey
}

private fun ActionBlock?.text(): String = when (this) {
    null -> "Actions allowed"
    ActionBlock.NOT_RUNNING -> "Actions off: not running"
    ActionBlock.RECONNECTING -> "Actions paused until the headset is back"
    ActionBlock.NOT_CONNECTED -> "Actions off: waiting for the headset"
    ActionBlock.DISARMED -> "Actions off: disarmed"
}

// Permission requests and the battery settings screen are launched by MainActivity, which owns their order.
@Composable
fun MainScreen(vm: MainViewModel, onConnect: () -> Unit, onAllowBackground: () -> Unit) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val trace by vm.trace.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val running = state.pipeline.running

    when (state.activeScreen) {
        ActiveScreen.CALIBRATION -> {
            BackHandler { vm.closeChildScreen() }
            CalibrationRoute(
                frames = vm.frames,
                store = vm.store,
                profile = vm.activeProfile(),
                onFinished = vm::onCalibrationFinished,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        ActiveScreen.PRACTICE -> {
            BackHandler { vm.closeChildScreen() }
            PracticeRoute(
                events = vm.events,
                onClose = vm::closeChildScreen,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        ActiveScreen.MAIN -> Unit
    }

    // The service keeps the remote alive with the screen off; keeping it on is only for watching the trace.
    val view = LocalView.current
    val watchingTrace = running && state.debugExpanded
    DisposableEffect(watchingTrace) {
        view.keepScreenOn = watchingTrace
        onDispose { view.keepScreenOn = false }
    }

    state.recalibrationOffer?.let { offer ->
        RecalibrationDialog(
            lastCalibratedAtMs = offer.lastCalibratedAtMs,
            onCalibrate = {
                if (!running) onConnect()
                vm.openCalibration()
            },
            onDismiss = vm::dismissRecalibration,
        )
    }

    if (state.showBatteryDialog) {
        BatteryDialog(
            onContinue = {
                vm.dismissBatteryDialog()
                onAllowBackground()
            },
            onDismiss = vm::dismissBatteryDialog,
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
        ) {
            Spacer(Modifier.height(8.dp))
            StatusRow(state)
            Spacer(Modifier.height(16.dp))
            LabelPanel(state.label, state.confidence)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (running) {
                    OutlinedButton(onClick = vm::stop) { Text("Stop") }
                } else {
                    Button(onClick = onConnect) { Text(if (state.sourceKind == SourceKind.DEVICE) "Connect" else "Play replay") }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (state.sourceKind == SourceKind.DEVICE) "Source: HALO headset" else "Source: replay ${state.replayFile ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    if (!running) onConnect()
                    vm.openCalibration()
                }) {
                    Text("Calibrate")
                }
                OutlinedButton(onClick = {
                    if (!running) onConnect()
                    vm.openPractice()
                }) {
                    Text("Practice")
                }
            }
            Spacer(Modifier.height(8.dp))
            ArmRow(state, onArmed = vm::setArmed)
            if (!state.batteryExempt) BatteryRow(onFix = onAllowBackground)
            if (state.permissionDenied) {
                Spacer(Modifier.height(8.dp))
                PermissionCard(onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                })
            }
            Spacer(Modifier.height(12.dp))
            Text("Detected events", style = MaterialTheme.typography.titleSmall)
            EventLog(state.log, Modifier.weight(1f))
            HorizontalDivider()
            DebugPanel(state, trace, vm)
        }
    }
}

@Composable
private fun StatusRow(state: UiState) {
    val p = state.pipeline
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(p.status.color())
            Spacer(Modifier.width(6.dp))
            Text(p.status.text(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.weight(1f))
            Text(
                text = "Battery " + (p.batteryPct?.let { "${it.roundToInt()}%" } ?: "–"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            p.channels.forEach { ch ->
                val color = when {
                    !p.running -> Grey
                    !ch.contact -> Red
                    !ch.signalOk -> Amber
                    else -> Green
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(color)
                    Spacer(Modifier.width(4.dp))
                    Text(ch.label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(
            "Contact: green ok, amber = contact reported but signal rejected as flat/noisy, red = no contact",
            style = MaterialTheme.typography.labelSmall,
            color = Grey,
        )
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .background(color, CircleShape),
    )
}

@Composable
private fun LabelPanel(label: GestureType?, confidence: Float) {
    Card(
        colors = CardDefaults.cardColors(containerColor = label.color().copy(alpha = 0.15f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = label.title(),
                color = label.color(),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (label == GestureType.DOUBLE_BLINK || label == GestureType.LONG_BLINK) {
                Text("experimental", color = label.color(), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { confidence },
                    color = label.color(),
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text("${(confidence * 100).roundToInt()} %", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Confidence is a heuristic score of how far the signal went past the rule's threshold. " +
                    "It is not a calibrated probability.",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                "Not detected: single blink, jaw opening, eyes closed. Closing the eyes can still look like a blink or clench.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ArmRow(state: UiState, onArmed: (Boolean) -> Unit) {
    val p = state.pipeline
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = p.armed, onCheckedChange = onArmed, enabled = p.running)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (p.armed) "Armed" else "Disarmed", style = MaterialTheme.typography.bodyMedium)
            Text(p.actionBlock.text(), style = MaterialTheme.typography.labelSmall, color = if (p.actionsAllowed) Green else Grey)
        }
    }
}

@Composable
private fun BatteryRow(onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(Amber)
        Spacer(Modifier.width(6.dp))
        Text(
            "Battery optimisation is on, so Android may stop the remote while the screen is off.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onFix) { Text("Fix") }
    }
}

@Composable
private fun RecalibrationDialog(
    lastCalibratedAtMs: Long?,
    onCalibrate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recalibrate headset?") },
        text = {
            val last = if (lastCalibratedAtMs != null) " Last calibrated: ${formatDate(lastCalibratedAtMs)}." else ""
            Text(
                "The headset was just put on or reconnected. Electrode contact changes with every fitting.$last " +
                    "Would you like to recalibrate your gestures now?",
            )
        },
        confirmButton = { Button(onClick = onCalibrate) { Text("Recalibrate") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun BatteryDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep the remote running") },
        text = {
            Text(
                "HALO Control reads the headset all the time so your gestures keep working with the screen off. " +
                    "Battery optimisation can pause the app or drop the Bluetooth link once the phone is in your pocket. " +
                    "On the next screen, allow HALO Control to ignore battery optimisation. " +
                    "The app only stays active while its notification is showing, and Stop ends it.",
            )
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun PermissionCard(onOpenSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Red.copy(alpha = 0.12f))) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Bluetooth permission was denied, so the app cannot find the headset. " +
                    "Allow \"Nearby devices\" (Android 12 and newer) or \"Location\" (Android 11 and older). " +
                    "Replay mode in the debug panel works without it.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onOpenSettings) { Text("Open app settings") }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

@Composable
private fun EventLog(log: List<LogEntry>, modifier: Modifier) {
    if (log.isEmpty()) {
        Box(modifier.fillMaxWidth()) {
            Text("No events yet", style = MaterialTheme.typography.bodySmall, color = Grey, modifier = Modifier.padding(top = 8.dp))
        }
        return
    }
    LazyColumn(modifier.fillMaxWidth()) {
        items(log, key = { it.id }) { e ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(timeFormat.format(Date(e.wallTimeMs)), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text("t=%.2f s".format(Locale.US, e.streamTimeMs / 1000.0), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Grey)
                Spacer(Modifier.width(8.dp))
                Dot(e.type.color())
                Spacer(Modifier.width(4.dp))
                Text(e.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${(e.confidence * 100).roundToInt()} %", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DebugPanel(state: UiState, trace: List<FloatArray>, vm: MainViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { vm.setDebugExpanded(!state.debugExpanded) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Debug", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(if (state.debugExpanded) "▾" else "▸")
        }
        if (!state.debugExpanded) return@Column
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = state.sourceKind == SourceKind.DEVICE, onClick = { vm.selectSource(SourceKind.DEVICE) })
                Text("Device")
                Spacer(Modifier.width(16.dp))
                RadioButton(selected = state.sourceKind == SourceKind.REPLAY, onClick = { vm.selectSource(SourceKind.REPLAY) })
                Text("Replay")
            }
            if (state.sourceKind == SourceKind.REPLAY) ReplayPicker(state, vm)
            Spacer(Modifier.height(8.dp))
            TracePlot(
                series = trace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
            )
            val p = state.pipeline
            Text(
                "EEG 1-20 Hz per channel (auto-scaled, last 5 s), gyro magnitude with 20 and 50 °/s lines. " +
                    "30-100 Hz ratio to baseline: %.1f (clench above 3.0). ".format(Locale.US, p.clenchRatio) +
                    "Missed samples: ${p.missedSamples}." +
                    if (p.hasGyro == false) " This headset has no gyroscope, so head turns are off." else "",
                style = MaterialTheme.typography.labelSmall,
                color = Grey,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReplayPicker(state: UiState, vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(state.replayFile ?: "Choose a recording") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.replayFiles.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    open = false
                    vm.selectReplay(name)
                })
            }
        }
    }
}
