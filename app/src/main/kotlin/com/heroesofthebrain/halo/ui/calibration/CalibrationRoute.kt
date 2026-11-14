package com.heroesofthebrain.halo.ui.calibration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import com.heroesofthebrain.halo.ui.theme.FreehandColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heroesofthebrain.halo.calibration.CalibrationGesture
import com.heroesofthebrain.halo.calibration.CalibrationPlan
import com.heroesofthebrain.halo.calibration.CalibrationState
import com.heroesofthebrain.halo.calibration.CalibrationStep
import com.heroesofthebrain.halo.calibration.CalibrationStore
import com.heroesofthebrain.halo.calibration.GestureScore
import com.heroesofthebrain.halo.calibration.ParameterReport
import com.heroesofthebrain.halo.calibration.ParameterStatus
import com.heroesofthebrain.halo.calibration.RestScore
import com.heroesofthebrain.halo.calibration.StepPhase
import com.heroesofthebrain.halo.calibration.StoredCalibration
import com.heroesofthebrain.halo.calibration.UserProfile
import com.heroesofthebrain.halo.calibration.detectWith
import com.heroesofthebrain.halo.eeg.EegFrame
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

// frames: the raw frame stream from the headset (the service's SharedFlow). Collected only while steps run.
// onFinished gets the saved calibration, or null when the user cancelled or discarded it.
@Composable
fun CalibrationRoute(
    frames: Flow<EegFrame>,
    store: CalibrationStore,
    profile: UserProfile,
    onFinished: (StoredCalibration?) -> Unit,
    modifier: Modifier = Modifier,
    plan: CalibrationPlan = CalibrationPlan(),
) {
    // A fresh ViewModel per visit, kept across configuration changes.
    val key = rememberSaveable(profile.id) { "calibration-${profile.id}-${System.nanoTime()}" }
    val vm = viewModel(key = key) { CalibrationViewModel(frames, store, profile, plan) }
    val screen by vm.screen.collectAsStateWithLifecycle()

    val tone = rememberCueTone()
    LaunchedEffect(vm) { vm.cues.collect { tone.play(it.kind) } }

    val view = LocalView.current
    val running = screen is CalibrationScreen.Running
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(screen) {
        when (val s = screen) {
            is CalibrationScreen.Saved -> onFinished(s.calibration)
            CalibrationScreen.Cancelled -> onFinished(null)
            else -> Unit
        }
    }

    var practicing by rememberSaveable { mutableStateOf(false) }
    val current = screen
    if (practicing && current is CalibrationScreen.Results) {
        BackHandler { practicing = false }
        val events = remember(current.result) { frames.detectWith(current.result.config) }
        PracticeRoute(
            events = events,
            onClose = { practicing = false },
            modifier = modifier,
            title = "Try your new thresholds",
            subtitle = "These thresholds are not saved yet. Nothing is sent to your media player.",
        )
        return
    }
    BackHandler(enabled = running) { vm.cancel() }

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
            when (current) {
                CalibrationScreen.Intro -> Intro(profile, plan, onStart = vm::start, onCancel = vm::discard)
                is CalibrationScreen.Running -> when (val s = current.state) {
                    is CalibrationState.CheckingContact -> ContactCheck(s, onSkip = vm::skipContactCheck, onCancel = vm::cancel)
                    is CalibrationState.Running -> StepView(s, onCancel = vm::cancel)
                    is CalibrationState.Stalled -> Waiting(s.message, onCancel = vm::cancel)
                    else -> Waiting("Starting…", onCancel = vm::cancel)
                }
                CalibrationScreen.Analyzing -> Waiting("Working out your thresholds…", onCancel = null)
                CalibrationScreen.Saving -> Waiting("Saving…", onCancel = null)
                is CalibrationScreen.Results -> Results(
                    current,
                    onRetry = vm::retry,
                    onPractice = { practicing = true },
                    onDiscard = vm::discard,
                    onSave = vm::accept,
                )
                is CalibrationScreen.Failed -> Failed(current.message, onClose = vm::discard)
                is CalibrationScreen.Saved, CalibrationScreen.Cancelled -> Unit
            }
        }
    }
}

@Composable
private fun Intro(profile: UserProfile, plan: CalibrationPlan, onStart: (List<CalibrationGesture>) -> Unit, onCancel: () -> Unit) {
    var selected by remember { mutableStateOf(plan.gestures.toSet()) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Calibrate: ${profile.name}", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "First a contact check, then a still recording while you rest, then each gesture ${plan.trialsPerGesture} times. " +
                "A tone and a prompt tell you when to do it. Turn the media volume up. At the end you see how often each " +
                "gesture was recognised and can redo any of them.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text("Gestures", style = MaterialTheme.typography.titleSmall)
        CalibrationGesture.entries.forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = g in selected, onCheckedChange = { on -> selected = if (on) selected + g else selected - g })
                Dot(g.color())
                Spacer(Modifier.width(6.dp))
                Text(g.title)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCancel,
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FreehandColors.Cream),
                border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
                modifier = Modifier.height(48.dp),
            ) { Text("Cancel") }
            Button(
                onClick = { onStart(CalibrationGesture.entries.filter { it in selected }) },
                enabled = selected.isNotEmpty(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FreehandColors.Flame,
                    contentColor = FreehandColors.Ink900,
                ),
                modifier = Modifier.height(48.dp),
            ) { Text("Start Calibration", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ContactCheck(state: CalibrationState.CheckingContact, onSkip: () -> Unit, onCancel: () -> Unit) {
    Text("Contact check", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        "Adjust the headset until the channels the gestures need are green. Calibration starts once they stay good for a moment.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        state.channels.forEach { ch ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(
                    when {
                        !ch.contact -> Red
                        !ch.signalOk -> Amber
                        else -> Green
                    },
                )
                Spacer(Modifier.width(4.dp))
                Text(ch.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Text("Green ok, amber contact but the signal is flat or noisy, red no contact", style = MaterialTheme.typography.labelSmall, color = Grey)
    Spacer(Modifier.height(8.dp))
    Text(
        when (state.hasGyro) {
            null -> "Gyroscope: waiting for data"
            true -> "Gyroscope: ok"
            false -> "Gyroscope: none, so head turns and long blinks are skipped"
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    state.problems.forEach { Text("• $it", color = Red, style = MaterialTheme.typography.bodyMedium) }
    if (state.problems.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (state.neededMs <= 0) 1f else (state.heldMs.toFloat() / state.neededMs).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Signal good, keep still", style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(8.dp))
    Text("Next: " + state.upcoming.joinToString { it.title }, style = MaterialTheme.typography.bodySmall, color = Grey)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
        TextButton(onClick = onSkip) { Text("Start anyway") }
    }
}

@Composable
private fun StepView(state: CalibrationState.Running, onCancel: () -> Unit) {
    val gesture = (state.step as? CalibrationStep.Gesture)?.gesture
    val accent = gesture?.color() ?: MaterialTheme.colorScheme.primary
    val now = state.phase == StepPhase.TRIAL || state.phase == StepPhase.RECORDING
    Text(
        "Step ${state.stepNumber} of ${state.stepCount}: ${state.step.title}" + if (state.retry) " (again)" else "",
        style = MaterialTheme.typography.titleMedium,
    )
    state.trial?.let { Text("Trial ${it + 1} of ${state.trials}", style = MaterialTheme.typography.bodyMedium) }
    Spacer(Modifier.height(12.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = if (now) accent.copy(alpha = 0.2f) else FreehandColors.Ink800),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (now) accent.copy(alpha = 0.6f) else FreehandColors.Ink600),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                when (state.phase) {
                    StepPhase.GET_READY -> "Get ready"
                    StepPhase.RECORDING -> "Recording"
                    StepPhase.TRIAL -> "Now"
                    StepPhase.PAUSE -> "Relax"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (now) accent else Grey,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                state.prompt,
                fontSize = if (state.phase == StepPhase.TRIAL) 34.sp else 20.sp,
                fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
                color = if (state.phase == StepPhase.TRIAL) accent else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { if (state.phaseMs <= 0) 1f else (state.phaseElapsedMs.toFloat() / state.phaseMs).coerceIn(0f, 1f) },
        color = accent,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!state.signalOk) {
        Spacer(Modifier.height(8.dp))
        Text("The signal this step needs is poor right now, so this step may score low.", color = Amber, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(16.dp))
    OutlinedButton(onClick = onCancel) { Text("Cancel calibration") }
}

@Composable
private fun Waiting(message: String, onCancel: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.padding(end = 12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
    if (onCancel != null) {
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onCancel) { Text("Cancel calibration") }
    }
}

@Composable
private fun Failed(message: String, onClose: () -> Unit) {
    Text(message, color = Red, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onClose) { Text("Close") }
}

@Composable
private fun Results(
    screen: CalibrationScreen.Results,
    onRetry: (CalibrationStep) -> Unit,
    onPractice: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (keepRaw: Boolean) -> Unit,
) {
    val result = screen.result
    var keepRaw by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Results", style = MaterialTheme.typography.titleLarge)
        Text(
            "Hits: how often the detector, running with your new thresholds, found the cued gesture after its tone.",
            style = MaterialTheme.typography.bodySmall,
            color = Grey,
        )
        Spacer(Modifier.height(8.dp))
        result.scores.forEach { GestureRow(it, onRetry) }
        result.skipped.forEach { (g, why) -> Text("${g.title}: skipped. $why", style = MaterialTheme.typography.bodySmall, color = Grey) }
        result.rest?.let { RestRow(it, onRetry) }
        result.clenchHolds?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                "Your clench holds: ${formatSeconds(it.minMs)} to ${formatSeconds(it.maxMs)}, typically ${formatSeconds(it.medianMs)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text("Thresholds", style = MaterialTheme.typography.titleMedium)
        result.analysis.parameters.filter { it.status != ParameterStatus.NOT_APPLICABLE }.forEach { ParamRow(it) }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = keepRaw, onCheckedChange = { keepRaw = it })
            Spacer(Modifier.width(8.dp))
            Text("Keep the raw recording", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Off by default. When on, saving also stores the raw EEG (O1, O2, Fp2, Fp1 in µV), electrode contact flags, " +
                "accelerometer and gyroscope samples of every calibration step, one replay CSV per step, plus the cue times " +
                "in trials.json, about ${formatBytes(screen.rawBytes)}, in this folder:",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(screen.rawDir, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Text(
            "The folder is private to this app and is deleted with this calibration, its profile, or the app. " +
                "It is only for replaying the recording when tuning the detector.",
            style = MaterialTheme.typography.bodySmall,
            color = Grey,
        )

        screen.saveError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Red, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onDiscard,
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FreehandColors.Cream),
                border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
                modifier = Modifier.height(48.dp),
            ) { Text("Discard") }
            OutlinedButton(
                onClick = onPractice,
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FreehandColors.Cream),
                border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
                modifier = Modifier.height(48.dp),
            ) { Text("Try it") }
            Button(
                onClick = { onSave(keepRaw) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FreehandColors.Flame,
                    contentColor = FreehandColors.Ink900,
                ),
                modifier = Modifier.height(48.dp),
            ) { Text("Save Calibration", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GestureRow(score: GestureScore, onRetry: (CalibrationStep) -> Unit) {
    val rate = score.hitRate
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(score.gesture.color())
            Spacer(Modifier.width(6.dp))
            Text(score.gesture.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${score.hits} / ${score.trials}", color = rateColor(rate), fontWeight = FontWeight.Bold)
            TextButton(onClick = { onRetry(CalibrationStep.Gesture(score.gesture)) }) { Text("Retry") }
        }
        LinearProgressIndicator(progress = { rate }, color = rateColor(rate), modifier = Modifier.fillMaxWidth())
        val details = buildList {
            add("Default thresholds: ${score.defaultHits} / ${score.trials}")
            if (score.otherDetections > 0) add("${score.otherDetections} other detections")
            if (score.cancelled > 0) add("${score.cancelled} holds cancelled")
            if (score.signalOkFraction < 0.9f) add("signal poor ${((1 - score.signalOkFraction) * 100).roundToInt()} % of the time")
        }
        Text(details.joinToString(". "), style = MaterialTheme.typography.labelSmall, color = Grey)
    }
}

@Composable
private fun RestRow(rest: RestScore, onRetry: (CalibrationStep) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("At rest", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            val n = rest.falseDetections.size
            Text(if (n == 0) "no false detections" else "$n false", color = if (n == 0) Green else Red, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onRetry(CalibrationStep.Rest) }) { Text("Retry") }
        }
        val list = rest.falseDetections.joinToString { "${it.gesture.title} at ${formatSeconds(it.atMs)}" }
        Text(
            "Over ${formatSeconds(rest.durationMs)} of rest. Default thresholds: ${rest.defaultFalseDetections}." + if (list.isNotEmpty()) " $list." else "",
            style = MaterialTheme.typography.labelSmall,
            color = Grey,
        )
    }
}

@Composable
private fun ParamRow(p: ParameterReport) {
    Column(Modifier.padding(vertical = 3.dp)) {
        val unit = if (p.param.unit.isEmpty()) "" else " ${p.param.unit}"
        Text("${p.param.label}: ${p.param.format(p.value)}$unit", style = MaterialTheme.typography.bodyMedium)
        val status = when (p.status) {
            ParameterStatus.DERIVED -> "From your data"
            ParameterStatus.CLAMPED -> "From your data, held inside the safe range"
            ParameterStatus.TOO_FEW_TRIALS -> "Default kept, too few usable trials"
            ParameterStatus.NOT_DETECTED -> "Default kept, the gesture was not found"
            ParameterStatus.INCONSISTENT -> "Default kept, the trials disagree"
            ParameterStatus.NO_REFERENCE -> "Default kept, nothing to compare with"
            ParameterStatus.NOT_CALIBRATED -> "Default kept, not calibrated"
            ParameterStatus.NOT_APPLICABLE -> "Not used"
        }
        val default = if (p.status.applied) " (default ${p.param.format(p.defaultValue)})" else ""
        Text("$status$default. ${p.note}".trimEnd(' ', '.') + ".", style = MaterialTheme.typography.labelSmall, color = Grey)
    }
}

internal fun hitSummary(calibration: StoredCalibration): String {
    val hits = calibration.scores.sumOf { it.hits }
    val trials = calibration.scores.sumOf { it.trials }
    val rest = calibration.rest?.falseDetections?.size
    return buildString {
        append("$hits / $trials hits")
        if (rest != null) append(", $rest false at rest")
    }
}
