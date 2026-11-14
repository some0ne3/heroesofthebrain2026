package com.heroesofthebrain.halo.ui.editor

import com.heroesofthebrain.halo.detect.CancelReason
import com.heroesofthebrain.halo.detect.GestureEvent
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.media.MediaCommand
import com.heroesofthebrain.halo.rules.Binding
import com.heroesofthebrain.halo.rules.BindingProfile
import com.heroesofthebrain.halo.rules.RuleAction
import com.heroesofthebrain.halo.rules.Trigger

enum class TryItStatus {
    IDLE,
    LISTENING,
    HOLDING,
    MATCHED,
    TOO_SHORT,
    TOO_LONG,
    TIMED_OUT,
    CANCELLED,
}

data class TimingBreakdown(
    val triggerDurationMs: Long,
    val ambiguityWaitMs: Long,
    val delayBeforeMs: Long,
    val executionSummary: String,
    val cooldownAfterMs: Long,
    val totalMs: Long,
)

data class TryItResult(
    val id: Long,
    val status: TryItStatus,
    val timestampMs: Long,
    val summary: String,
    val durationMs: Long? = null,
    val cancelReason: CancelReason? = null,
    val timingBreakdown: TimingBreakdown? = null,
)

data class TryItSession(
    val binding: Binding,
    val status: TryItStatus = TryItStatus.LISTENING,
    val currentHoldElapsedMs: Long = 0L,
    val holdOnsetMs: Long = 0L,
    val repeatCountSoFar: Int = 0,
    val repeatLastEventMs: Long = 0L,
    val sequenceStepIndex: Int = 0,
    val sequenceLastEventMs: Long = 0L,
    val lastEvent: GestureEvent? = null,
    val message: String = "Perform the gesture to test this binding",
    val history: List<TryItResult> = emptyList(),
    val activeBreakdown: TimingBreakdown? = null,
)

enum class MacroItemType {
    TRIGGER,
    PRE_DELAY,
    COMMAND,
    STEP_DELAY,
    COOLDOWN,
}

data class MacroTimelineItem(
    val id: String,
    val type: MacroItemType,
    val stepIndex: Int? = null,
    val title: String,
    val subtitle: String,
    val durationMs: Long = 0L,
    val command: MediaCommand? = null,
)

/**
 * Builds the visual timeline nodes for a binding, especially useful for macros or timed actions.
 */
fun buildTimelineItems(binding: Binding, ambiguityWaitMs: Long = 0L): List<MacroTimelineItem> {
    val items = mutableListOf<MacroTimelineItem>()

    // 1. Trigger
    val triggerTitle = when (val t = binding.trigger) {
        is Trigger.Single -> "Trigger: ${t.gesture.name}"
        is Trigger.Hold -> "Hold: ${t.gesture.name} (${t.minDurationMs}..${t.maxDurationMs?.toString() ?: "∞"} ms)"
        is Trigger.Repeat -> "Repeat: ${t.gesture.name} × ${t.count} (gap ≤ ${t.maxGapMs} ms)"
        is Trigger.Sequence -> "Sequence: ${t.gestures.joinToString(" → ") { it.name }} (gap ≤ ${t.maxGapMs} ms)"
    }
    items += MacroTimelineItem(
        id = "trigger",
        type = MacroItemType.TRIGGER,
        title = triggerTitle,
        subtitle = "Gesture detection window",
        durationMs = when (val t = binding.trigger) {
            is Trigger.Hold -> t.minDurationMs
            is Trigger.Repeat -> t.maxGapMs * (t.count - 1)
            is Trigger.Sequence -> t.maxGapMs * (t.gestures.size - 1)
            is Trigger.Single -> 0L
        },
    )

    // Ambiguity wait if any
    if (ambiguityWaitMs > 0L) {
        items += MacroTimelineItem(
            id = "ambiguity-wait",
            type = MacroItemType.PRE_DELAY,
            title = "Ambiguity Wait",
            subtitle = "Waiting to see if shadowed rule matches",
            durationMs = ambiguityWaitMs,
        )
    }

    // Pre-delay
    if (binding.timing.delayBeforeMs > 0L) {
        items += MacroTimelineItem(
            id = "pre-delay",
            type = MacroItemType.PRE_DELAY,
            title = "Delay Before",
            subtitle = "Configured pre-execution delay",
            durationMs = binding.timing.delayBeforeMs,
        )
    }

    // Action execution
    when (val action = binding.action) {
        is RuleAction.SingleCommand -> {
            items += MacroTimelineItem(
                id = "cmd-single",
                type = MacroItemType.COMMAND,
                title = action.command.javaClass.simpleName,
                subtitle = "Single command dispatch",
                command = action.command,
            )
        }
        is RuleAction.RepeatWhileHeld -> {
            items += MacroTimelineItem(
                id = "cmd-repeat",
                type = MacroItemType.COMMAND,
                title = "Repeat ${action.command.javaClass.simpleName}",
                subtitle = "Every ${action.intervalMs} ms while held",
                durationMs = action.intervalMs,
                command = action.command,
            )
        }
        is RuleAction.Macro -> {
            action.steps.forEachIndexed { idx, step ->
                items += MacroTimelineItem(
                    id = "macro-cmd-$idx",
                    type = MacroItemType.COMMAND,
                    stepIndex = idx + 1,
                    title = "Step ${idx + 1}: ${step.command.javaClass.simpleName}",
                    subtitle = "Command dispatch",
                    command = step.command,
                )
                if (step.delayAfterMs > 0L) {
                    items += MacroTimelineItem(
                        id = "macro-delay-$idx",
                        type = MacroItemType.STEP_DELAY,
                        title = "Delay",
                        subtitle = "Wait before next step",
                        durationMs = step.delayAfterMs,
                    )
                }
            }
        }
    }

    // Cooldown
    if (binding.timing.cooldownAfterMs > 0L) {
        items += MacroTimelineItem(
            id = "cooldown",
            type = MacroItemType.COOLDOWN,
            title = "Cooldown",
            subtitle = "Ignores re-triggers during cooldown",
            durationMs = binding.timing.cooldownAfterMs,
        )
    }

    return items
}
