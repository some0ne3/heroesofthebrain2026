package com.heroesofthebrain.halo.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.eeg.SourceStatus
import com.heroesofthebrain.halo.service.ActionBlock
import com.heroesofthebrain.halo.ui.calibration.CalibrationRoute
import com.heroesofthebrain.halo.ui.calibration.PracticeRoute
import com.heroesofthebrain.halo.ui.calibration.formatDate
import com.heroesofthebrain.halo.ui.components.FreehandGestureToast
import com.heroesofthebrain.halo.ui.components.FreehandHeaderLockup
import com.heroesofthebrain.halo.ui.components.FreehandSignalCard
import com.heroesofthebrain.halo.ui.components.GestureToastData
import com.heroesofthebrain.halo.ui.components.PupilStatusDot
import com.heroesofthebrain.halo.ui.editor.EditorRoute
import com.heroesofthebrain.halo.ui.theme.FreehandColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun GestureType?.color(): Color = FreehandColors.forGesture(this)

fun GestureType?.title(): String = when (this) {
    GestureType.CLENCH -> "JAW CLENCH"
    GestureType.TURN_LEFT -> "HEAD TURN ←"
    GestureType.TURN_RIGHT -> "HEAD TURN →"
    GestureType.TURN_UP -> "HEAD NOD ↑"
    GestureType.TURN_DOWN -> "HEAD TILT ↓"
    GestureType.DOUBLE_BLINK -> "DOUBLE BLINK"
    GestureType.LONG_BLINK -> "LONG EYE CLOSURE"
    null -> "READY FOR GESTURE"
}

private fun SourceStatus.text(): String = when (this) {
    SourceStatus.Idle -> "Not connected"
    SourceStatus.Scanning -> "Scanning for HALO…"
    is SourceStatus.Connecting -> "Connecting to $device…"
    is SourceStatus.Streaming -> device
    is SourceStatus.Reconnecting -> "Reconnecting: $reason"
    is SourceStatus.Waiting -> reason
    SourceStatus.Finished -> "Replay complete"
    is SourceStatus.Failed -> message
}

private fun ActionBlock?.text(): String = when (this) {
    null -> "Media actions active"
    ActionBlock.NOT_RUNNING -> "Actions inactive: remote stopped"
    ActionBlock.RECONNECTING -> "Actions paused: reconnecting to headset"
    ActionBlock.NOT_CONNECTED -> "Actions inactive: headset not connected"
    ActionBlock.DISARMED -> "Actions disarmed by user"
}

@Composable
fun MainScreen(vm: MainViewModel, onConnect: () -> Unit, onAllowBackground: () -> Unit, onPickHeadset: () -> Unit) {
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
        ActiveScreen.EDITOR -> {
            BackHandler { vm.closeChildScreen() }
            EditorRoute(
                profileStore = vm.profileStore,
                calibrationStore = vm.store,
                events = vm.events,
                onClose = vm::closeChildScreen,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        ActiveScreen.MAIN -> Unit
    }

    // Keep screen on if debug trace is expanded
    val view = LocalView.current
    val watchingTrace = running && state.debugExpanded
    DisposableEffect(watchingTrace) {
        view.keepScreenOn = watchingTrace
        onDispose { view.keepScreenOn = false }
    }

    // Gesture Toast state
    var currentToast by remember { mutableStateOf<GestureToastData?>(null) }
    val latestLog = state.log.firstOrNull()
    LaunchedEffect(latestLog?.id) {
        if (latestLog != null) {
            currentToast = GestureToastData(
                gesture = latestLog.type,
                actionText = latestLog.text,
                timestamp = latestLog.wallTimeMs,
            )
        }
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

    state.picker?.let { picker ->
        HeadsetPickerDialog(
            picker = picker,
            chosen = state.chosenHeadset,
            onChoose = vm::chooseHeadset,
            onRescan = vm::scanHeadsets,
            onDismiss = vm::closeHeadsetPicker,
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

    Scaffold(
        containerColor = FreehandColors.Ink900,
        contentColor = FreehandColors.Cream,
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(16.dp))

                // Top Header Lockup: "freehand" + Wave Eye mark
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FreehandHeaderLockup(symbolHeight = 26.dp)

                    // Source pill badge; for the headset it opens the picker
                    val headsetSource = state.sourceKind == SourceKind.DEVICE
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f, fill = false)
                            .clip(CircleShape)
                            .background(FreehandColors.Ink800)
                            .border(1.dp, FreehandColors.Ink600, CircleShape)
                            .clickable(enabled = headsetSource, onClick = onPickHeadset)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = if (headsetSource) headsetLabel(running, state.deviceName, state.chosenHeadset) else "REPLAY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = FreehandColors.Sand,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (headsetSource) {
                            Text(" ▾", style = MaterialTheme.typography.labelSmall, color = FreehandColors.Sand)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Wave Eye Signal Quality & Connection Card
                FreehandSignalCard(pipeline = state.pipeline)

                Spacer(Modifier.height(16.dp))

                // Hero Gesture Recognition Panel
                HeroGesturePanel(label = state.label, confidence = state.confidence)

                Spacer(Modifier.height(16.dp))

                // Remote Arming Card (Tactile Switch)
                ArmCard(state = state, onArmed = vm::setArmed)

                if (!state.batteryExempt) {
                    Spacer(Modifier.height(12.dp))
                    BatteryWarningCard(onFix = onAllowBackground)
                }

                if (state.permissionDenied) {
                    Spacer(Modifier.height(12.dp))
                    PermissionCard(onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    })
                }

                Spacer(Modifier.height(20.dp))

                // Primary Connection Action Button (Capsule 56dp height)
                if (running) {
                    OutlinedButton(
                        onClick = vm::stop,
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = FreehandColors.Ember,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, FreehandColors.Ember),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text(
                            text = "Disconnect Remote",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FreehandColors.Flame,
                            contentColor = FreehandColors.Ink900,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text(
                            text = if (state.sourceKind == SourceKind.DEVICE) "Connect ${state.chosenHeadset?.name ?: "HALO Headset"}" else "Play Replay Session",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Quick Navigation Pills (Calibrate · Practice · Editor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!running) onConnect()
                            vm.openCalibration()
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FreehandColors.Cream),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text("Calibrate", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            if (!running) onConnect()
                            vm.openPractice()
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FreehandColors.Cream),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text("Practice", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = vm::openEditor,
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FreehandColors.Cream),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text("Editor", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Gesture Event Stream
                EventStreamCard(log = state.log)

                Spacer(Modifier.height(16.dp))

                // Collapsible Debug Panel
                DebugPanel(state = state, trace = trace, vm = vm)

                Spacer(Modifier.height(40.dp))
            }

            // Floating Gesture Toast Overlay at Bottom
            FreehandGestureToast(
                toast = currentToast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
                onDismiss = { currentToast = null },
            )
        }
    }
}

/**
 * Modern Hero Gesture Panel with brand accent glow and confidence bar.
 */
@Composable
private fun HeroGesturePanel(label: GestureType?, confidence: Float) {
    val activeColor = FreehandColors.forGesture(label)
    val hasActive = label != null && confidence > 0f

    val cardBorderColor by animateColorAsState(
        targetValue = if (hasActive) activeColor.copy(alpha = 0.6f) else FreehandColors.Ink600,
        label = "hero_border",
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasActive) FreehandColors.Ink800 else FreehandColors.Ink850,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Category pill
                val gestureCategory = when (label) {
                    GestureType.DOUBLE_BLINK, GestureType.LONG_BLINK -> "EYE GESTURE (EOG)"
                    GestureType.CLENCH -> "MUSCLE CLENCH (EMG)"
                    GestureType.TURN_LEFT, GestureType.TURN_RIGHT,
                    GestureType.TURN_UP, GestureType.TURN_DOWN -> "MOTION GESTURE (GYRO)"
                    null -> "GESTURE DETECTOR"
                }

                Box(
                    modifier = Modifier
                        .background(activeColor.copy(alpha = 0.15f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = gestureCategory,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeColor,
                    )
                }

                if (hasActive) {
                    Text(
                        text = "${(confidence * 100).roundToInt()}% CONFIDENCE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = activeColor,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Large Hero Gesture Title
            Text(
                text = label.title(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (hasActive) activeColor else FreehandColors.Cream,
                letterSpacing = (-0.2).sp,
            )

            Spacer(Modifier.height(14.dp))

            // Capsule Confidence Meter
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(FreehandColors.Ink700),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = confidence.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(activeColor),
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = when (label) {
                    GestureType.DOUBLE_BLINK -> "Double blink detected — triggers instant media action."
                    GestureType.LONG_BLINK -> "Long eye closure detected — used for hold triggers or disarming."
                    GestureType.CLENCH -> "Jaw clench detected — ideal for hold actions like volume adjustment."
                    GestureType.TURN_LEFT, GestureType.TURN_RIGHT -> "Head turn detected — ideal for track skip navigation."
                    GestureType.TURN_UP, GestureType.TURN_DOWN -> "Vertical head tilt detected."
                    null -> "Perform a calibrated facial gesture (blink, clench, or head movement) to trigger media control."
                },
                style = MaterialTheme.typography.bodySmall,
                color = FreehandColors.Sand,
                lineHeight = 19.sp,
            )
        }
    }
}

/**
 * Modern Remote Arming Card with Tactile Switch
 */
@Composable
private fun ArmCard(state: UiState, onArmed: (Boolean) -> Unit) {
    val p = state.pipeline
    val isArmed = p.armed

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Ink800),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PupilStatusDot(active = p.running, armed = isArmed)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isArmed) "Remote Armed" else "Remote Disarmed",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = FreehandColors.Cream,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = p.actionBlock.text(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (p.actionsAllowed) FreehandColors.Success else FreehandColors.SandMuted,
                    )
                }
            }

            Switch(
                checked = isArmed,
                onCheckedChange = onArmed,
                enabled = p.running,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = FreehandColors.Ink900,
                    checkedTrackColor = FreehandColors.Flame,
                    uncheckedThumbColor = FreehandColors.SandMuted,
                    uncheckedTrackColor = FreehandColors.Ink700,
                    uncheckedBorderColor = FreehandColors.Ink600,
                ),
            )
        }
    }
}

/**
 * Battery Warning Card
 */
@Composable
private fun BatteryWarningCard(onFix: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Warning.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Warning.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Background Execution Needed",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = FreehandColors.Warning,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Battery optimization may stop EEG listening when the screen is off. Exempt Freehand to keep gestures active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FreehandColors.Cream,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onFix,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FreehandColors.Warning,
                    contentColor = FreehandColors.Ink900,
                ),
            ) {
                Text("Fix", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Permission Required Card
 */
@Composable
private fun PermissionCard(onOpenSettings: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Error.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Error.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Bluetooth Permissions Required",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = FreehandColors.Error,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Nearby devices or Location permission is required to scan and connect to the BrainAccess HALO headband.",
                style = MaterialTheme.typography.bodySmall,
                color = FreehandColors.Cream,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onOpenSettings,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FreehandColors.Error,
                    contentColor = FreehandColors.Ink900,
                ),
            ) {
                Text("Open Settings", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

/**
 * Live Gesture Event Stream Card
 */
@Composable
private fun EventStreamCard(log: List<LogEntry>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Ink800),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = 280.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Activity Stream",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = FreehandColors.Cream,
                )
                Text(
                    text = "${log.size} events",
                    style = MaterialTheme.typography.labelSmall,
                    color = FreehandColors.SandMuted,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = FreehandColors.Ink600)
            Spacer(Modifier.height(8.dp))

            if (log.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No gestures detected yet. Connect headset to start.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FreehandColors.SandMuted,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(log, key = { it.id }) { e ->
                        val gColor = FreehandColors.forGesture(e.type)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = timeFormat.format(Date(e.wallTimeMs)),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = FreehandColors.SandMuted,
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(gColor, CircleShape),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = e.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 13.sp,
                                color = FreehandColors.Cream,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(gColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "${(e.confidence * 100).roundToInt()}%",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = gColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Collapsible Debug Panel with Dark Waveform Trace
 */
@Composable
private fun DebugPanel(state: UiState, trace: List<FloatArray>, vm: MainViewModel) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Ink800),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.setDebugExpanded(!state.debugExpanded) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Signal Diagnostics & Trace",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = FreehandColors.Cream,
                )
                Text(
                    text = if (state.debugExpanded) "▲" else "▼",
                    color = FreehandColors.Sand,
                )
            }

            if (state.debugExpanded) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = FreehandColors.Ink600)
                Spacer(Modifier.height(14.dp))

                // Source selector pills
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.sourceKind == SourceKind.DEVICE,
                        onClick = { vm.selectSource(SourceKind.DEVICE) },
                        colors = RadioButtonDefaults.colors(selectedColor = FreehandColors.Flame),
                    )
                    Text("HALO BLE Headset", style = MaterialTheme.typography.bodyMedium)

                    Spacer(Modifier.width(16.dp))

                    RadioButton(
                        selected = state.sourceKind == SourceKind.REPLAY,
                        onClick = { vm.selectSource(SourceKind.REPLAY) },
                        colors = RadioButtonDefaults.colors(selectedColor = FreehandColors.Flame),
                    )
                    Text("Replay Data", style = MaterialTheme.typography.bodyMedium)
                }

                if (state.sourceKind == SourceKind.REPLAY) {
                    Spacer(Modifier.height(8.dp))
                    ReplayPicker(state, vm)
                }

                Spacer(Modifier.height(14.dp))

                // EEG Waveform Trace Plot
                TracePlot(
                    series = trace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(FreehandColors.Ink900, RoundedCornerShape(16.dp))
                        .border(1.dp, FreehandColors.Ink600, RoundedCornerShape(16.dp))
                        .padding(8.dp),
                )

                val p = state.pipeline
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "EEG 1-20 Hz per channel (auto-scaled, last 5 s), gyro magnitude (0-100 °/s). " +
                        "30-100 Hz baseline clench ratio: %.1f (clench above 3.0). ".format(Locale.US, p.clenchRatio) +
                        "Dropped packets: ${p.missedSamples}." +
                        if (p.hasGyro == false) " Headset has no gyroscope — head motion detection disabled." else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = FreehandColors.Sand,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun ReplayPicker(state: UiState, vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            shape = CircleShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = FreehandColors.Cream),
            border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
        ) {
            Text(state.replayFile ?: "Select recording file…")
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(FreehandColors.Ink800),
        ) {
            state.replayFiles.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name, color = FreehandColors.Cream) },
                    onClick = {
                        open = false
                        vm.selectReplay(name)
                    },
                )
            }
        }
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
        containerColor = FreehandColors.Ink800,
        titleContentColor = FreehandColors.Cream,
        textContentColor = FreehandColors.Sand,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Recalibrate Headset?", fontWeight = FontWeight.SemiBold) },
        text = {
            val last = if (lastCalibratedAtMs != null) " Last calibrated: ${formatDate(lastCalibratedAtMs)}." else ""
            Text(
                "The HALO headband was just put on or reconnected. Electrode contact impedance changes with every fitting.$last " +
                    "Would you like to recalibrate your gesture thresholds now?",
                lineHeight = 22.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onCalibrate,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FreehandColors.Flame,
                    contentColor = FreehandColors.Ink900,
                ),
            ) {
                Text("Recalibrate", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = FreehandColors.Sand),
            ) {
                Text("Later")
            }
        },
    )
}

@Composable
private fun BatteryDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FreehandColors.Ink800,
        titleContentColor = FreehandColors.Cream,
        textContentColor = FreehandColors.Sand,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Unrestricted Background Activity", fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                "Freehand continuously analyzes EEG frames so your gestures keep controlling media when the screen is turned off. " +
                    "Android battery optimization may kill background services once your phone is in your pocket. " +
                    "Please allow Freehand to run without battery restrictions.",
                lineHeight = 22.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FreehandColors.Flame,
                    contentColor = FreehandColors.Ink900,
                ),
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = FreehandColors.Sand),
            ) {
                Text("Not Now")
            }
        },
    )
}
