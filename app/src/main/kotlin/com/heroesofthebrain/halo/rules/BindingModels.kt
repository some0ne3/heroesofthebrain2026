package com.heroesofthebrain.halo.rules

import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.media.MediaCommand
import kotlinx.serialization.Serializable

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

    @Serializable
    data class Sequence(
        val gestures: List<GestureType>,
        val maxGapMs: Long = 1000L,
    ) : Trigger {
        constructor(first: GestureType, second: GestureType, maxGapMs: Long = 1000L) : this(listOf(first, second), maxGapMs)

        init {
            require(gestures.size >= 2) { "sequence must have at least 2 gestures, got ${gestures.size}" }
            require(maxGapMs > 0) { "maxGapMs must be positive, got $maxGapMs" }
        }
    }
}

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
                    action = RuleAction.SingleCommand(MediaCommand.VolumeUp),
                ),
                Binding(
                    id = "clench-volume-down",
                    name = "Volume Down",
                    trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 1500L, maxDurationMs = 3000L),
                    action = RuleAction.SingleCommand(MediaCommand.VolumeDown),
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
