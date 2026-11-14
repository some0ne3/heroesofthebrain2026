package com.heroesofthebrain.halo.rules

import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.media.MediaCommand
import com.heroesofthebrain.halo.media.needsInputService
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
enum class MediaCondition {
    ANY,
    PLAYING_ONLY,
    PAUSED_ONLY,
}

@Serializable
sealed interface Trigger {
    @Serializable
    data class Single(val gesture: GestureType) : Trigger

    @Serializable
    data class Hold(
        val gesture: GestureType = GestureType.CLENCH,
        val minDurationMs: Long,
        val maxDurationMs: Long? = null,
    ) : Trigger {
        init {
            require(minDurationMs >= 0) { "minDurationMs must be non-negative, got $minDurationMs" }
            if (maxDurationMs != null) {
                require(maxDurationMs >= minDurationMs) {
                    "maxDurationMs ($maxDurationMs) must be >= minDurationMs ($minDurationMs)"
                }
            }
        }
    }

    @Serializable
    data class Repeat(
        val gesture: GestureType,
        val count: Int = 2,
        val maxGapMs: Long = 800L,
    ) : Trigger {
        init {
            require(count >= 2) { "repeat count must be at least 2, got $count" }
            require(maxGapMs > 0) { "maxGapMs must be positive, got $maxGapMs" }
        }
    }

    // Gestures done in order, each step within its own window after the previous one. Profiles saved with the
    // older Sequence trigger are converted on load (ProfileMigration).
    @Serializable
    data class InputMacro(val steps: List<InputStep>) : Trigger {
        constructor(vararg gestures: GestureType, withinMs: Long = InputStep.DEFAULT_WITHIN_MS) :
            this(gestures.map { InputStep(it, withinMs) })

        init {
            require(steps.size >= 2) { "input macro needs at least 2 steps, got ${steps.size}" }
        }
    }
}

@Serializable
data class InputStep(
    val gesture: GestureType,
    // How long after the previous step finished this one may start; the first step ignores it.
    val withinMs: Long = DEFAULT_WITHIN_MS,
    // Held gestures only.
    val minHoldMs: Long = 0L,
    val maxHoldMs: Long? = null,
) {
    init {
        require(withinMs > 0) { "withinMs must be positive, got $withinMs" }
        require(minHoldMs >= 0) { "minHoldMs must be non-negative, got $minHoldMs" }
        if (maxHoldMs != null) {
            require(maxHoldMs >= minHoldMs) { "maxHoldMs ($maxHoldMs) must be >= minHoldMs ($minHoldMs)" }
        }
    }

    fun acceptsHold(durationMs: Long): Boolean =
        durationMs >= minHoldMs && (maxHoldMs == null || durationMs <= maxHoldMs)

    companion object {
        const val DEFAULT_WITHIN_MS = 2000L
    }
}

fun InputStep.describe(first: Boolean = false): String {
    val hold = if (gesture.held) " ${minHoldMs}..${maxHoldMs?.toString() ?: "∞"} ms" else ""
    val window = if (first) "" else " (≤ ${formatWindow(withinMs)})"
    return "${gesture.name}$hold$window"
}

fun Trigger.InputMacro.describe(): String =
    steps.mapIndexed { index, step -> step.describe(first = index == 0) }.joinToString(" → ")

fun formatWindow(ms: Long): String =
    if (ms % 1000 == 0L) "${ms / 1000} s" else String.format(Locale.US, "%.1f s", ms / 1000.0)

@Serializable
data class MacroStep(
    val command: MediaCommand,
    val delayAfterMs: Long = 0L,
) {
    init {
        require(delayAfterMs >= 0) { "delayAfterMs must be non-negative, got $delayAfterMs" }
    }
}

@Serializable
sealed interface RuleAction {
    @Serializable
    data class SingleCommand(val command: MediaCommand) : RuleAction

    @Serializable
    data class RepeatWhileHeld(
        val command: MediaCommand,
        val intervalMs: Long = 350L,
        val initialDelayMs: Long = 0L,
    ) : RuleAction {
        init {
            require(intervalMs > 0) { "intervalMs must be positive, got $intervalMs" }
            require(initialDelayMs >= 0) { "initialDelayMs must be non-negative, got $initialDelayMs" }
        }
    }

    @Serializable
    data class Macro(val steps: List<MacroStep>) : RuleAction {
        constructor(vararg steps: MacroStep) : this(steps.toList())
        constructor(vararg commands: MediaCommand, stepDelayMs: Long = 200L) : this(
            commands.mapIndexed { index, cmd ->
                MacroStep(cmd, if (index == commands.lastIndex) 0L else stepDelayMs)
            }
        )

        init {
            require(steps.isNotEmpty()) { "macro must contain at least one step" }
        }
    }
}

@Serializable
data class BindingTiming(
    val delayBeforeMs: Long = 0L,
    val cooldownAfterMs: Long = 0L,
    val cancelWindowMs: Long = 0L,
    val cancelGesture: GestureType? = null,
) {
    init {
        require(delayBeforeMs >= 0) { "delayBeforeMs must be >= 0" }
        require(cooldownAfterMs >= 0) { "cooldownAfterMs must be >= 0" }
        require(cancelWindowMs >= 0) { "cancelWindowMs must be >= 0" }
    }
}

@Serializable
data class BindingConditions(
    val onlyWhenArmed: Boolean = true,
    val mediaStatus: MediaCondition = MediaCondition.ANY,
    val requiredChannels: List<String> = emptyList(),
)

@Serializable
data class Binding(
    val id: String,
    val name: String = "",
    val enabled: Boolean = true,
    val trigger: Trigger,
    val action: RuleAction,
    val timing: BindingTiming = BindingTiming(),
    val conditions: BindingConditions = BindingConditions(),
)

@Serializable
data class GlobalSettings(
    val armGesture: GestureType? = null,
    val disarmGesture: GestureType? = null,
    val toggleArmGesture: GestureType? = null,
    val autoDisarmTimeoutMs: Long? = 300_000L,
    val globalCooldownMs: Long = 100L,
    val minimumConfidence: Float = 0.5f,
) {
    init {
        if (autoDisarmTimeoutMs != null) {
            require(autoDisarmTimeoutMs > 0) { "autoDisarmTimeoutMs must be positive" }
        }
        require(globalCooldownMs >= 0) { "globalCooldownMs must be >= 0" }
        require(minimumConfidence in 0f..1f) { "minimumConfidence must be between 0 and 1" }
    }
}

@Serializable
data class BindingProfile(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val createdAtMs: Long = 0L,
    val bindings: List<Binding> = emptyList(),
    val globalSettings: GlobalSettings = GlobalSettings(),
) {
    companion object {
        fun defaultProfile(
            id: String = "default",
            name: String = "Default Profile",
            createdAtMs: Long = System.currentTimeMillis(),
        ): BindingProfile = BindingProfile(
            id = id,
            name = name,
            enabled = true,
            createdAtMs = createdAtMs,
            bindings = listOf(
                Binding(
                    id = "double-blink-play-pause",
                    name = "Play / Pause",
                    trigger = Trigger.Single(GestureType.DOUBLE_BLINK),
                    action = RuleAction.SingleCommand(MediaCommand.PlayPause),
                ),
                Binding(
                    id = "turn-right-next",
                    name = "Next Track",
                    trigger = Trigger.Single(GestureType.TURN_RIGHT),
                    action = RuleAction.SingleCommand(MediaCommand.Next),
                ),
                Binding(
                    id = "turn-left-prev",
                    name = "Previous Track",
                    trigger = Trigger.Single(GestureType.TURN_LEFT),
                    action = RuleAction.SingleCommand(MediaCommand.Previous),
                ),
                Binding(
                    id = "clench-volume-up",
                    name = "Volume Up",
                    trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 500L, maxDurationMs = 1200L),
                    action = RuleAction.SingleCommand(MediaCommand.VolumeUp()),
                ),
                Binding(
                    id = "clench-volume-down",
                    name = "Volume Down",
                    trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 1500L, maxDurationMs = 3000L),
                    action = RuleAction.SingleCommand(MediaCommand.VolumeDown()),
                ),
            ),
            globalSettings = GlobalSettings(
                armGesture = GestureType.LONG_BLINK,
                disarmGesture = null,
                toggleArmGesture = null,
                autoDisarmTimeoutMs = 300_000L,
                globalCooldownMs = 150L,
                minimumConfidence = 0.4f,
            ),
        )
    }
}

typealias Profile = BindingProfile

val RuleAction.commands: List<MediaCommand>
    get() = when (this) {
        is RuleAction.SingleCommand -> listOf(command)
        is RuleAction.RepeatWhileHeld -> listOf(command)
        is RuleAction.Macro -> steps.map { it.command }
    }

// Disabled bindings don't count: they can't fire, so they shouldn't ask for the accessibility service.
fun BindingProfile.usesInputService(): Boolean =
    bindings.any { binding -> binding.enabled && binding.action.commands.any { it.needsInputService } }
