package com.heroesofthebrain.halo.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heroesofthebrain.halo.calibration.HoldStats
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.media.MediaCommand
import com.heroesofthebrain.halo.media.describe
import com.heroesofthebrain.halo.rules.AmbiguityResolver
import com.heroesofthebrain.halo.rules.Binding
import com.heroesofthebrain.halo.rules.BindingConditions
import com.heroesofthebrain.halo.rules.BindingTiming
import com.heroesofthebrain.halo.rules.MacroStep
import com.heroesofthebrain.halo.rules.MediaCondition
import com.heroesofthebrain.halo.rules.RuleAction
import com.heroesofthebrain.halo.rules.Trigger
import com.heroesofthebrain.halo.ui.color
import com.heroesofthebrain.halo.ui.title
import com.heroesofthebrain.halo.ui.theme.FreehandColors
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SliderDefaults
import kotlin.math.max
import kotlin.math.roundToLong

private val Green = FreehandColors.Success
private val Amber = FreehandColors.Warning
private val Red = FreehandColors.Error
private val Grey = FreehandColors.SandMuted

private enum class TriggerKind { SINGLE, HOLD, REPEAT, SEQUENCE }
private enum class ActionKind { SINGLE_COMMAND, REPEAT_WHILE_HELD, MACRO }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BindingEditorScreen(
    initialBinding: Binding,
    allBindings: List<Binding>,
    calibrationHolds: HoldStats?,
    onSave: (Binding) -> Unit,
    onCancel: () -> Unit,
    onTryIt: (Binding) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(initialBinding.id) { mutableStateOf(initialBinding.name) }
    var enabled by remember(initialBinding.id) { mutableStateOf(initialBinding.enabled) }

    // Trigger state
    var triggerKind by remember(initialBinding.id) {
        mutableStateOf(
            when (initialBinding.trigger) {
                is Trigger.Single -> TriggerKind.SINGLE
                is Trigger.Hold -> TriggerKind.HOLD
                is Trigger.Repeat -> TriggerKind.REPEAT
                is Trigger.Sequence -> TriggerKind.SEQUENCE
            },
        )
    }

    var singleGesture by remember(initialBinding.id) {
        mutableStateOf((initialBinding.trigger as? Trigger.Single)?.gesture ?: GestureType.DOUBLE_BLINK)
    }

    var holdGesture by remember(initialBinding.id) {
        mutableStateOf((initialBinding.trigger as? Trigger.Hold)?.gesture ?: GestureType.CLENCH)
    }
    var holdMinMs by remember(initialBinding.id) {
        mutableLongStateOf((initialBinding.trigger as? Trigger.Hold)?.minDurationMs ?: 500L)
    }
    var holdHasMax by remember(initialBinding.id) {
        mutableStateOf((initialBinding.trigger as? Trigger.Hold)?.maxDurationMs != null)
    }
    var holdMaxMs by remember(initialBinding.id) {
        mutableLongStateOf((initialBinding.trigger as? Trigger.Hold)?.maxDurationMs ?: 1500L)
    }

    var repeatGesture by remember(initialBinding.id) {
        mutableStateOf((initialBinding.trigger as? Trigger.Repeat)?.gesture ?: GestureType.DOUBLE_BLINK)
    }
    var repeatCount by remember(initialBinding.id) {
        mutableIntStateOf((initialBinding.trigger as? Trigger.Repeat)?.count ?: 2)
    }
    var repeatMaxGapMs by remember(initialBinding.id) {
        mutableLongStateOf((initialBinding.trigger as? Trigger.Repeat)?.maxGapMs ?: 800L)
    }

    val sequenceGestures = remember(initialBinding.id) {
        mutableStateListOf<GestureType>().apply {
            val seq = (initialBinding.trigger as? Trigger.Sequence)?.gestures
            if (!seq.isNullOrEmpty()) addAll(seq) else addAll(listOf(GestureType.TURN_LEFT, GestureType.TURN_RIGHT))
        }
    }
    var sequenceMaxGapMs by remember(initialBinding.id) {
        mutableLongStateOf((initialBinding.trigger as? Trigger.Sequence)?.maxGapMs ?: 1000L)
    }

    // Action state
    var actionKind by remember(initialBinding.id) {
        mutableStateOf(
            when (initialBinding.action) {
                is RuleAction.SingleCommand -> ActionKind.SINGLE_COMMAND
                is RuleAction.RepeatWhileHeld -> ActionKind.REPEAT_WHILE_HELD
                is RuleAction.Macro -> ActionKind.MACRO
            },
        )
    }

    var singleCommand by remember(initialBinding.id) {
        mutableStateOf((initialBinding.action as? RuleAction.SingleCommand)?.command ?: MediaCommand.PlayPause)
    }

    var repeatCommand by remember(initialBinding.id) {
        mutableStateOf((initialBinding.action as? RuleAction.RepeatWhileHeld)?.command ?: MediaCommand.VolumeUp)
    }
    var repeatIntervalMs by remember(initialBinding.id) {
        mutableLongStateOf((initialBinding.action as? RuleAction.RepeatWhileHeld)?.intervalMs ?: 350L)
    }
    var repeatInitialDelayMs by remember(initialBinding.id) {
        mutableLongStateOf((initialBinding.action as? RuleAction.RepeatWhileHeld)?.initialDelayMs ?: 0L)
    }

    val macroSteps = remember(initialBinding.id) {
        mutableStateListOf<MacroStep>().apply {
            val steps = (initialBinding.action as? RuleAction.Macro)?.steps
            if (!steps.isNullOrEmpty()) addAll(steps) else addAll(
                listOf(
                    MacroStep(MediaCommand.Next, 200L),
                    MacroStep(MediaCommand.VolumeUp, 0L),
                ),
            )
        }
    }

    // Timing & Conditions
    var delayBeforeMs by remember(initialBinding.id) { mutableLongStateOf(initialBinding.timing.delayBeforeMs) }
    var cooldownAfterMs by remember(initialBinding.id) { mutableLongStateOf(initialBinding.timing.cooldownAfterMs) }
    var cancelWindowMs by remember(initialBinding.id) { mutableLongStateOf(initialBinding.timing.cancelWindowMs) }
    var cancelGesture by remember(initialBinding.id) { mutableStateOf(initialBinding.timing.cancelGesture) }

    var onlyWhenArmed by remember(initialBinding.id) { mutableStateOf(initialBinding.conditions.onlyWhenArmed) }
    var mediaCondition by remember(initialBinding.id) { mutableStateOf(initialBinding.conditions.mediaStatus) }

    // Assemble preview binding
    fun buildCurrentBinding(): Binding {
        val trigger: Trigger = when (triggerKind) {
            TriggerKind.SINGLE -> Trigger.Single(singleGesture)
            TriggerKind.HOLD -> Trigger.Hold(
                gesture = holdGesture,
                minDurationMs = holdMinMs,
                maxDurationMs = if (holdHasMax) max(holdMinMs, holdMaxMs) else null,
            )
            TriggerKind.REPEAT -> Trigger.Repeat(
                gesture = repeatGesture,
                count = max(2, repeatCount),
                maxGapMs = max(100L, repeatMaxGapMs),
            )
            TriggerKind.SEQUENCE -> Trigger.Sequence(
                gestures = if (sequenceGestures.size >= 2) sequenceGestures.toList() else listOf(GestureType.TURN_LEFT, GestureType.TURN_RIGHT),
                maxGapMs = max(100L, sequenceMaxGapMs),
            )
        }

        val action: RuleAction = when (actionKind) {
            ActionKind.SINGLE_COMMAND -> RuleAction.SingleCommand(singleCommand)
            ActionKind.REPEAT_WHILE_HELD -> RuleAction.RepeatWhileHeld(
                command = repeatCommand,
                intervalMs = max(50L, repeatIntervalMs),
                initialDelayMs = max(0L, repeatInitialDelayMs),
            )
            ActionKind.MACRO -> RuleAction.Macro(
                steps = if (macroSteps.isNotEmpty()) macroSteps.toList() else listOf(MacroStep(MediaCommand.PlayPause, 0L)),
            )
        }

        return Binding(
            id = initialBinding.id,
            name = name.trim().ifEmpty { initialBinding.id },
            enabled = enabled,
            trigger = trigger,
            action = action,
            timing = BindingTiming(
                delayBeforeMs = max(0L, delayBeforeMs),
                cooldownAfterMs = max(0L, cooldownAfterMs),
                cancelWindowMs = max(0L, cancelWindowMs),
                cancelGesture = cancelGesture,
            ),
            conditions = BindingConditions(
                onlyWhenArmed = onlyWhenArmed,
                mediaStatus = mediaCondition,
            ),
        )
    }

    val currentBinding = buildCurrentBinding()
    val otherBindings = allBindings.filter { it.id != currentBinding.id }
    val shadowWaitMs = AmbiguityResolver.maxShadowWaitMs(currentBinding, allBindings)
    val warnings = AmbiguityResolver.analyze(listOf(currentBinding) + otherBindings).filter {
        when (it) {
            is com.heroesofthebrain.halo.rules.AmbiguityWarning.ShadowedBySequence -> it.singleBinding.id == currentBinding.id || it.sequenceBinding.id == currentBinding.id
            is com.heroesofthebrain.halo.rules.AmbiguityWarning.ShadowedByRepeat -> it.singleBinding.id == currentBinding.id || it.repeatBinding.id == currentBinding.id
            is com.heroesofthebrain.halo.rules.AmbiguityWarning.OverlappingHoldRanges -> it.bindingA.id == currentBinding.id || it.bindingB.id == currentBinding.id
            is com.heroesofthebrain.halo.rules.AmbiguityWarning.DuplicateTrigger -> it.bindingA.id == currentBinding.id || it.bindingB.id == currentBinding.id
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FreehandColors.Ink900,
        contentColor = FreehandColors.Cream,
        topBar = {
            Surface(
                color = FreehandColors.Ink800,
                border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.textButtonColors(contentColor = FreehandColors.Sand),
                    ) { Text("Cancel") }
                    Text(
                        text = if (initialBinding.name.isEmpty()) "New Binding" else "Edit Binding",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FreehandColors.Cream,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onTryIt(buildCurrentBinding()) },
                            shape = CircleShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FreehandColors.Pupil),
                            border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Pupil.copy(alpha = 0.5f)),
                        ) {
                            Text("Try it")
                        }
                        Button(
                            onClick = { onSave(buildCurrentBinding()) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FreehandColors.Flame,
                                contentColor = FreehandColors.Ink900,
                            ),
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))

            // 1. Name & Active Toggle
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = FreehandColors.Ink800),
                border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Binding Name") },
                            placeholder = { Text("e.g. Next Track, Volume Up") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Enabled", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = enabled, onCheckedChange = { enabled = it })
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Suggestions:", style = MaterialTheme.typography.labelSmall, color = Grey)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        listOf("Play / Pause", "Next Track", "Previous Track", "Volume Up", "Volume Down", "Mute", "Seek +10s").forEach { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.clickable { name = suggestion },
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Text(
                                    text = suggestion,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 2. Trigger Section
            Text(
                text = "1. TRIGGER CONFIGURATION",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))

            TriggerTypeSelector(
                selected = triggerKind,
                onSelect = { triggerKind = it },
            )

            Spacer(Modifier.height(10.dp))

            when (triggerKind) {
                TriggerKind.SINGLE -> {
                    GesturePicker(
                        selected = singleGesture,
                        onSelect = { singleGesture = it },
                        title = "Select Gesture",
                    )
                }
                TriggerKind.HOLD -> {
                    HoldTriggerEditor(
                        gesture = holdGesture,
                        onGestureChange = { holdGesture = it },
                        minDurationMs = holdMinMs,
                        onMinDurationChange = { holdMinMs = it },
                        hasMax = holdHasMax,
                        onHasMaxChange = { holdHasMax = it },
                        maxDurationMs = holdMaxMs,
                        onMaxDurationChange = { holdMaxMs = it },
                        calibrationHolds = calibrationHolds,
                    )
                }
                TriggerKind.REPEAT -> {
                    RepeatTriggerEditor(
                        gesture = repeatGesture,
                        onGestureChange = { repeatGesture = it },
                        count = repeatCount,
                        onCountChange = { repeatCount = it },
                        maxGapMs = repeatMaxGapMs,
                        onMaxGapChange = { repeatMaxGapMs = it },
                    )
                }
                TriggerKind.SEQUENCE -> {
                    SequenceTriggerEditor(
                        gestures = sequenceGestures,
                        maxGapMs = sequenceMaxGapMs,
                        onMaxGapChange = { sequenceMaxGapMs = it },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 3. Action Section
            Text(
                text = "2. ACTION CONFIGURATION",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))

            ActionTypeSelector(
                selected = actionKind,
                onSelect = { actionKind = it },
            )

            Spacer(Modifier.height(10.dp))

            when (actionKind) {
                ActionKind.SINGLE_COMMAND -> {
                    SingleCommandEditor(
                        command = singleCommand,
                        onCommandChange = { singleCommand = it },
                    )
                }
                ActionKind.REPEAT_WHILE_HELD -> {
                    RepeatActionEditor(
                        command = repeatCommand,
                        onCommandChange = { repeatCommand = it },
                        intervalMs = repeatIntervalMs,
                        onIntervalChange = { repeatIntervalMs = it },
                        initialDelayMs = repeatInitialDelayMs,
                        onInitialDelayChange = { repeatInitialDelayMs = it },
                    )
                }
                ActionKind.MACRO -> {
                    MacroTimelineView(binding = currentBinding, ambiguityWaitMs = shadowWaitMs)
                    Spacer(Modifier.height(12.dp))
                    MacroActionEditor(
                        steps = macroSteps,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 4. Timing & Delays
            Text(
                text = "3. TIMING & CANCELLATION",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))

            TimingEditorCard(
                delayBeforeMs = delayBeforeMs,
                onDelayBeforeChange = { delayBeforeMs = it },
                cooldownAfterMs = cooldownAfterMs,
                onCooldownAfterChange = { cooldownAfterMs = it },
                cancelWindowMs = cancelWindowMs,
                onCancelWindowChange = { cancelWindowMs = it },
                cancelGesture = cancelGesture,
                onCancelGestureChange = { cancelGesture = it },
            )

            Spacer(Modifier.height(16.dp))

            // 5. Conditions
            Text(
                text = "4. CONDITIONS",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))

            ConditionsCard(
                onlyWhenArmed = onlyWhenArmed,
                onOnlyWhenArmedChange = { onlyWhenArmed = it },
                mediaCondition = mediaCondition,
                onMediaConditionChange = { mediaCondition = it },
            )

            Spacer(Modifier.height(16.dp))

            // 6. Ambiguity & Conflict Analysis
            ConflictAnalysisCard(
                binding = currentBinding,
                warnings = warnings,
                shadowWaitMs = shadowWaitMs,
            )

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun TriggerTypeSelector(
    selected: TriggerKind,
    onSelect: (TriggerKind) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TriggerKind.values().forEach { kind ->
            val isSelected = kind == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(kind) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    text = kind.name.replace('_', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ActionTypeSelector(
    selected: ActionKind,
    onSelect: (ActionKind) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ActionKind.values().forEach { kind ->
            val isSelected = kind == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(kind) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    text = when (kind) {
                        ActionKind.SINGLE_COMMAND -> "Single"
                        ActionKind.REPEAT_WHILE_HELD -> "Repeat Held"
                        ActionKind.MACRO -> "Macro"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GesturePicker(
    selected: GestureType?,
    onSelect: (GestureType) -> Unit,
    title: String = "Select Gesture",
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Grey)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            GestureType.values().forEach { gesture ->
                val isSelected = gesture == selected
                val gColor = gesture.color()
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) gColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) gColor else Color.Transparent),
                    modifier = Modifier.clickable { onSelect(gesture) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(gColor, CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = gesture.title(),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldTriggerEditor(
    gesture: GestureType,
    onGestureChange: (GestureType) -> Unit,
    minDurationMs: Long,
    onMinDurationChange: (Long) -> Unit,
    hasMax: Boolean,
    onHasMaxChange: (Boolean) -> Unit,
    maxDurationMs: Long,
    onMaxDurationChange: (Long) -> Unit,
    calibrationHolds: HoldStats?,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Ink800),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            GesturePicker(
                selected = gesture,
                onSelect = onGestureChange,
                title = "Held Gesture (Clench recommended)",
            )

            Spacer(Modifier.height(14.dp))

            // Calibration Prefill Section
            if (calibrationHolds != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Green.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Green.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "CALIBRATED CLENCH STATS",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Green,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Median: ${calibrationHolds.medianMs} ms",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = "Measured holds: ${calibrationHolds.holdsMs.joinToString(", ") { "${it}ms" }} (Range: ${calibrationHolds.minMs}..${calibrationHolds.maxMs} ms)",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onMinDurationChange(max(300L, calibrationHolds.minMs - 200L))
                                    onHasMaxChange(true)
                                    onMaxDurationChange(calibrationHolds.medianMs)
                                },
                            ) {
                                Text("Use Short Hold (< ${calibrationHolds.medianMs}ms)", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    onMinDurationChange(calibrationHolds.medianMs)
                                    onHasMaxChange(true)
                                    onMaxDurationChange((calibrationHolds.maxMs * 1.3).roundToLong())
                                },
                            ) {
                                Text("Use Long Hold (> ${calibrationHolds.medianMs}ms)", fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // Duration Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Min Hold Duration: ${minDurationMs} ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = minDurationMs.toFloat(),
                onValueChange = { onMinDurationChange(it.roundToLong()) },
                valueRange = 200f..3000f,
                steps = 27,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hasMax, onCheckedChange = onHasMaxChange)
                Text("Enable maximum hold duration limit", style = MaterialTheme.typography.bodySmall)
            }

            if (hasMax) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Max Hold Duration: ${maxDurationMs} ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = max(minDurationMs.toFloat(), maxDurationMs.toFloat()),
                    onValueChange = { onMaxDurationChange(it.roundToLong()) },
                    valueRange = minDurationMs.toFloat()..5000f,
                )
            }
        }
    }
}

@Composable
private fun RepeatTriggerEditor(
    gesture: GestureType,
    onGestureChange: (GestureType) -> Unit,
    count: Int,
    onCountChange: (Int) -> Unit,
    maxGapMs: Long,
    onMaxGapChange: (Long) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Ink800),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            GesturePicker(
                selected = gesture,
                onSelect = onGestureChange,
                title = "Repeat Gesture",
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Repeat Count: $count times", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 3, 4, 5).forEach { c ->
                        Surface(
                            shape = CircleShape,
                            color = if (c == count) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { onCountChange(c) },
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$c",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (c == count) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Max Gap Between Repeats: ${maxGapMs} ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Slider(
                value = maxGapMs.toFloat(),
                onValueChange = { onMaxGapChange(it.roundToLong()) },
                valueRange = 300f..2000f,
                steps = 16,
            )
        }
    }
}

@Composable
private fun SequenceTriggerEditor(
    gestures: MutableList<GestureType>,
    maxGapMs: Long,
    onMaxGapChange: (Long) -> Unit,
) {
    var showAddMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Ink800),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Gestures in Sequence (${gestures.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Box {
                    AddPillButton("Add Step", onClick = { showAddMenu = true })
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        GestureType.values().forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g.title()) },
                                onClick = {
                                    showAddMenu = false
                                    gestures.add(g)
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            gestures.forEachIndexed { index, gesture ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(gesture.color(), CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(gesture.title(), style = MaterialTheme.typography.bodyMedium)
                    }

                    Row {
                        if (gestures.size > 2) {
                            TextButton(onClick = { gestures.removeAt(index) }) {
                                Text("Remove", color = Red)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Max Gap Between Steps: ${maxGapMs} ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Slider(
                value = maxGapMs.toFloat(),
                onValueChange = { onMaxGapChange(it.roundToLong()) },
                valueRange = 400f..2500f,
                steps = 20,
            )
        }
    }
}

@Composable
private fun SingleCommandEditor(
    command: MediaCommand,
    onCommandChange: (MediaCommand) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Ink800),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Select Media Command", style = MaterialTheme.typography.labelSmall, color = Grey)
            Spacer(Modifier.height(8.dp))
            CommandDropdown(selected = command, onSelect = onCommandChange)
        }
    }
}

@Composable
private fun CommandDropdown(
    selected: MediaCommand,
    onSelect: (MediaCommand) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selected.describe(), modifier = Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            MediaCommand.all.forEach { cmd ->
                DropdownMenuItem(
                    text = { Text(cmd.describe()) },
                    onClick = {
                        expanded = false
                        onSelect(cmd)
                    },
                )
            }
        }
    }
}

@Composable
private fun RepeatActionEditor(
    command: MediaCommand,
    onCommandChange: (MediaCommand) -> Unit,
    intervalMs: Long,
    onIntervalChange: (Long) -> Unit,
    initialDelayMs: Long,
    onInitialDelayChange: (Long) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Ink800),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Repeated Command", style = MaterialTheme.typography.labelSmall, color = Grey)
            Spacer(Modifier.height(6.dp))
            CommandDropdown(selected = command, onSelect = onCommandChange)

            Spacer(Modifier.height(12.dp))
            Text("Repeat Interval: ${intervalMs} ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Slider(
                value = intervalMs.toFloat(),
                onValueChange = { onIntervalChange(it.roundToLong()) },
                valueRange = 100f..1000f,
                steps = 17,
            )

            if (command is MediaCommand.PlayPause && intervalMs < 350L) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Amber.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Android delays play/pause by ~300ms and turns quick double presses into Next Track. Recommended interval is >= 350ms.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Initial Delay: ${initialDelayMs} ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Slider(
                value = initialDelayMs.toFloat(),
                onValueChange = { onInitialDelayChange(it.roundToLong()) },
                valueRange = 0f..1000f,
                steps = 10,
            )
        }
    }
}

@Composable
private fun MacroActionEditor(
    steps: MutableList<MacroStep>,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Ink800),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Macro Steps (${steps.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                AddPillButton("Add Step", onClick = { steps.add(MacroStep(MediaCommand.VolumeUp, 150L)) })
            }

            Spacer(Modifier.height(10.dp))

            steps.forEachIndexed { index, step ->
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Step #${index + 1}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Row {
                                if (index > 0) {
                                    TextButton(onClick = {
                                        val prev = steps[index - 1]
                                        steps[index - 1] = step
                                        steps[index] = prev
                                    }) { Text("▲") }
                                }
                                if (index < steps.lastIndex) {
                                    TextButton(onClick = {
                                        val next = steps[index + 1]
                                        steps[index + 1] = step
                                        steps[index] = next
                                    }) { Text("▼") }
                                }
                                if (steps.size > 1) {
                                    TextButton(onClick = { steps.removeAt(index) }) { Text("✕", color = Red) }
                                }
                            }
                        }

                        CommandDropdown(
                            selected = step.command,
                            onSelect = { steps[index] = step.copy(command = it) },
                        )

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Delay after: ${step.delayAfterMs} ms", style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = step.delayAfterMs.toFloat(),
                            onValueChange = { steps[index] = step.copy(delayAfterMs = it.roundToLong()) },
                            valueRange = 0f..1000f,
                            steps = 10,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimingEditorCard(
    delayBeforeMs: Long,
    onDelayBeforeChange: (Long) -> Unit,
    cooldownAfterMs: Long,
    onCooldownAfterChange: (Long) -> Unit,
    cancelWindowMs: Long,
    onCancelWindowChange: (Long) -> Unit,
    cancelGesture: GestureType?,
    onCancelGestureChange: (GestureType?) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Ink800),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Delay Before Firing: ${delayBeforeMs} ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Slider(
                value = delayBeforeMs.toFloat(),
                onValueChange = { onDelayBeforeChange(it.roundToLong()) },
                valueRange = 0f..2000f,
                steps = 19,
            )

            Spacer(Modifier.height(8.dp))
            Text("Cooldown After: ${cooldownAfterMs} ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Slider(
                value = cooldownAfterMs.toFloat(),
                onValueChange = { onCooldownAfterChange(it.roundToLong()) },
                valueRange = 0f..2000f,
                steps = 19,
            )

            Spacer(Modifier.height(8.dp))
            Text("Cancel Window: ${cancelWindowMs} ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Slider(
                value = cancelWindowMs.toFloat(),
                onValueChange = { onCancelWindowChange(it.roundToLong()) },
                valueRange = 0f..2000f,
                steps = 19,
            )

            if (cancelWindowMs > 0L) {
                Spacer(Modifier.height(6.dp))
                Text("Cancel Gesture (optional):", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    val cancelOptions = listOf<GestureType?>(null, GestureType.DOUBLE_BLINK, GestureType.CLENCH)
                    cancelOptions.forEach { opt ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (opt == cancelGesture) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.clickable { onCancelGestureChange(opt) },
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Text(
                                text = opt?.title() ?: "Any/None",
                                fontSize = 11.sp,
                                color = if (opt == cancelGesture) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionsCard(
    onlyWhenArmed: Boolean,
    onOnlyWhenArmedChange: (Boolean) -> Unit,
    mediaCondition: MediaCondition,
    onMediaConditionChange: (MediaCondition) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Ink800),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Only When Armed", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Requires remote to be armed to trigger action", style = MaterialTheme.typography.bodySmall, color = Grey)
                }
                Switch(checked = onlyWhenArmed, onCheckedChange = onOnlyWhenArmedChange)
            }

            Spacer(Modifier.height(10.dp))
            Text("Playback State Condition", style = MaterialTheme.typography.labelSmall, color = Grey)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MediaCondition.values().forEach { cond ->
                    val isSelected = cond == mediaCondition
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onMediaConditionChange(cond) },
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = when (cond) {
                                MediaCondition.ANY -> "Any State"
                                MediaCondition.PLAYING_ONLY -> "Playing Only"
                                MediaCondition.PAUSED_ONLY -> "Paused Only"
                            },
                            fontSize = 11.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictAnalysisCard(
    binding: Binding,
    warnings: List<com.heroesofthebrain.halo.rules.AmbiguityWarning>,
    shadowWaitMs: Long,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (warnings.isNotEmpty() || shadowWaitMs > 0) Amber.copy(alpha = 0.12f) else Green.copy(alpha = 0.12f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (warnings.isNotEmpty() || shadowWaitMs > 0) Amber.copy(alpha = 0.5f) else Green.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (warnings.isNotEmpty() || shadowWaitMs > 0) Amber else Green, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (warnings.isNotEmpty() || shadowWaitMs > 0) "AMBIGUITY & CONFLICT WARNINGS" else "NO CONFLICTS DETECTED",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (warnings.isNotEmpty() || shadowWaitMs > 0) Amber else Green,
                )
            }

            Spacer(Modifier.height(6.dp))

            if (warnings.isEmpty() && shadowWaitMs == 0L) {
                Text(
                    text = "This binding operates with zero ambiguity delay and has no conflicting rules.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                if (shadowWaitMs > 0) {
                    Text(
                        text = "• Shadow delay: This binding will wait an extra ${shadowWaitMs} ms before firing because a sequence or repeat shares this initial gesture.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                warnings.forEach { warning ->
                    Text(
                        text = "• ${warning.message}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
