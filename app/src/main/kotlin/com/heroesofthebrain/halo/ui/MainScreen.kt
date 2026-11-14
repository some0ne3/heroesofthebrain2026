package com.heroesofthebrain.halo.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.heroesofthebrain.halo.detect.ControlType
import com.heroesofthebrain.halo.eeg.SourceStatus
import com.neurotec.brainaccess.transport.BlePermissions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Green = Color(0xFF43A047)
private val Amber = Color(0xFFFFB300)
private val Red = Color(0xFFE53935)
private val Grey = Color(0xFF9E9E9E)

fun ControlType.color(): Color = when (this) {
    ControlType.CLENCH -> Color(0xFFFF8F00)
    ControlType.HEAD_TURN_HORIZONTAL -> Color(0xFF1E88E5)
    ControlType.HEAD_TURN_VERTICAL -> Color(0xFF8E24AA)
    ControlType.DOUBLE_BLINK -> Color(0xFF00897B)
    ControlType.NONE -> Grey
}

fun ControlType.title(): String = when (this) {
    ControlType.CLENCH -> "CLENCH"
    ControlType.HEAD_TURN_HORIZONTAL -> "HEAD TURN ↔"
    ControlType.HEAD_TURN_VERTICAL -> "HEAD TURN ↕"
    ControlType.DOUBLE_BLINK -> "DOUBLE BLINK"
    ControlType.NONE -> "NONE"
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

@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val trace by vm.trace.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.onPermissionResult(result.values.all { it })
    }
    val onStart = {
        if (state.sourceKind == SourceKind.DEVICE) {
            // BlePermissions.required is the SDK's list for the running OS: SCAN+CONNECT on 31+, fine location on 30-.
            val missing = BlePermissions.required.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) vm.start() else permissionLauncher.launch(missing.toTypedArray())
        } else {
            vm.start()
        }
    }

    val view = LocalView.current
    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
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
                if (state.running) {
                    OutlinedButton(onClick = vm::stop) { Text("Stop") }
                } else {
                    Button(onClick = onStart) { Text(if (state.sourceKind == SourceKind.DEVICE) "Connect" else "Play replay") }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (state.sourceKind == SourceKind.DEVICE) "Source: HALO headset" else "Source: replay ${state.replayFile ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(state.status.color())
            Spacer(Modifier.width(6.dp))
            Text(state.status.text(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.weight(1f))
            Text(
                text = "Battery " + (state.batteryPct?.let { "${it.roundToInt()}%" } ?: "–"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            state.channels.forEach { ch ->
                val color = when {
                    !state.running -> Grey
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
private fun LabelPanel(label: ControlType, confidence: Float) {
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
            if (label == ControlType.DOUBLE_BLINK) {
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
                Text(e.type.title(), fontSize = 13.sp, modifier = Modifier.weight(1f))
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
            Text(
                "EEG 1-20 Hz per channel (auto-scaled, last 5 s), gyro magnitude with 20 and 50 °/s lines. " +
                    "30-100 Hz ratio to baseline: %.1f (clench above 3.0). ".format(Locale.US, state.clenchRatio) +
                    "Missed samples: ${state.missedSamples}." +
                    if (state.hasGyro == false) " This headset has no gyroscope, so head turns are off." else "",
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
