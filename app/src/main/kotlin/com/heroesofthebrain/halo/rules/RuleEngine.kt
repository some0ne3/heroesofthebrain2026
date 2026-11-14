package com.heroesofthebrain.halo.rules

import com.heroesofthebrain.halo.detect.CancelReason
import com.heroesofthebrain.halo.detect.GestureCancel
import com.heroesofthebrain.halo.detect.GestureEnd
import com.heroesofthebrain.halo.detect.GestureEvent
import com.heroesofthebrain.halo.detect.GestureInstant
import com.heroesofthebrain.halo.detect.GestureStart
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.media.MediaCommand
import kotlin.math.max

data class EngineContext(
    val actionsAllowed: Boolean = true,
    val isArmed: Boolean = false,
    val isPlaying: Boolean? = null,
    val goodChannels: Set<String> = emptySet(),
)

sealed interface EngineEffect {
    data class ExecuteCommand(val command: MediaCommand, val binding: Binding? = null) : EngineEffect
    data class ArmChanged(val armed: Boolean, val reason: String) : EngineEffect
    data class HoldProgress(val gesture: GestureType, val durationMs: Long) : EngineEffect
    data class ActionScheduled(val binding: Binding, val executeAtMs: Long) : EngineEffect
    data class ActionCancelled(val binding: Binding, val reason: String) : EngineEffect
}

/**
 * Pure Kotlin state machine that transforms GestureEvents + clock ticks into media actions.
 */
class RuleEngine(
    var profile: BindingProfile = BindingProfile.defaultProfile(),
    initialContext: EngineContext = EngineContext(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var context: EngineContext = initialContext
        private set

    var armed: Boolean = initialContext.isArmed
        private set

    private var currentTimeMs: Long = clock()
    private var lastActivityMs: Long = currentTimeMs
    private var lastGlobalActionMs: Long = 0L
    private var lastPlayPauseMs: Long = 0L
    private val lastBindingFiredMs = mutableMapOf<String, Long>()

    // Hold state
    private data class ActiveHold(
        val gesture: GestureType,
        val onsetMs: Long,
        val startTimestampMs: Long,
    )
    private var activeHold: ActiveHold? = null

    // Repeat while held
    private data class ActiveRepeat(
        val binding: Binding,
        val command: MediaCommand,
        val intervalMs: Long,
        var nextFireMs: Long,
    )
    private var activeRepeat: ActiveRepeat? = null

    // Sequences & Repeats in progress
    private data class SequenceState(
        val binding: Binding,
        val nextIndex: Int,
        var deadlineMs: Long,
    )
    private val activeSequences = mutableListOf<SequenceState>()

    private data class RepeatState(
        val binding: Binding,
        var countSoFar: Int,
        var deadlineMs: Long,
    )
    private val activeRepeats = mutableListOf<RepeatState>()

    // Pending ambiguous actions (single gesture waiting to see if sequence/repeat completes)
    private data class PendingAmbiguous(
        val binding: Binding,
        val fireDeadlineMs: Long,
    )
    private val pendingAmbiguousActions = mutableListOf<PendingAmbiguous>()

    // Scheduled actions (delayBeforeMs, cancelWindowMs)
    private data class ScheduledAction(
        val binding: Binding,
        val executeAtMs: Long,
        val cancelWindowUntilMs: Long,
        val cancelGesture: GestureType?,
    )
    private val scheduledActions = mutableListOf<ScheduledAction>()

    // Macro execution
    private data class ActiveMacro(
        val binding: Binding?,
        val steps: List<MacroStep>,
        var currentStepIndex: Int,
        var nextStepAtMs: Long,
    )
    private var activeMacro: ActiveMacro? = null

    /**
     * Process an incoming gesture event at the given [timestampMs].
     */
    fun processEvent(event: GestureEvent, timestampMs: Long = event.timestampMs): List<EngineEffect> {
        currentTimeMs = timestampMs
        val effects = mutableListOf<EngineEffect>()

        // Update auto-disarm activity timer
        lastActivityMs = timestampMs

        // Confidence filter (except cancellations which must always be handled)
        if (event !is GestureCancel && event.confidence < profile.globalSettings.minimumConfidence) {
            return effects
        }

        // 1. Check Global Arm / Disarm gestures first
        if (handleArmDisarmGesture(event, effects)) {
            return effects
        }

        // 2. Handle cancellation
        if (event is GestureCancel) {
            handleGestureCancel(event, effects)
            return effects
        }

        // 3. Handle gesture start (hold start)
        if (event is GestureStart) {
            handleGestureStart(event, effects)
            return effects
        }

        // 4. Handle gesture end (hold release)
        if (event is GestureEnd) {
            handleGestureEnd(event, effects)
            return effects
        }

        // 5. Handle instant gestures
        if (event is GestureInstant) {
            handleGestureInstant(event, effects)
            return effects
        }

        return effects
    }

    /**
     * Advance engine clock to [nowMs], triggering timeouts, scheduled actions, repeats and macros.
     */
    fun advanceTime(nowMs: Long): List<EngineEffect> {
        currentTimeMs = nowMs
        val effects = mutableListOf<EngineEffect>()

        // 1. Check auto-disarm timeout
        val autoDisarmMs = profile.globalSettings.autoDisarmTimeoutMs
        if (armed && autoDisarmMs != null && (nowMs - lastActivityMs >= autoDisarmMs)) {
            armed = false
            effects += EngineEffect.ArmChanged(armed = false, reason = "Inactivity timeout")
        }

        // If actions not allowed, abort pending active executions
        if (!context.actionsAllowed) {
            dropActiveHoldsAndRepeats(effects)
            return effects
        }

        // 2. Active repeats while held
        val repeat = activeRepeat
        val hold = activeHold
        if (repeat != null && hold != null) {
            if (nowMs >= repeat.nextFireMs) {
                if (canFireActionNow(repeat.command)) {
                    effects += executeCommand(repeat.command, repeat.binding)
                    repeat.nextFireMs = nowMs + repeat.intervalMs
                    effects += EngineEffect.HoldProgress(hold.gesture, nowMs - hold.onsetMs)
                }
            }
        }

        // 3. In-flight Macro steps
        val macro = activeMacro
        if (macro != null && nowMs >= macro.nextStepAtMs) {
            val step = macro.steps[macro.currentStepIndex]
            if (canFireActionNow(step.command)) {
                effects += executeCommand(step.command, macro.binding)
                macro.currentStepIndex++
                if (macro.currentStepIndex < macro.steps.size) {
                    macro.nextStepAtMs = nowMs + step.delayAfterMs
                } else {
                    activeMacro = null
                }
            }
        }

        // 4. Sequence & Repeat deadlines (cleanup expired progress)
        activeSequences.removeAll { nowMs > it.deadlineMs }
        activeRepeats.removeAll { nowMs > it.deadlineMs }

        // 5. Pending ambiguous actions whose wait time has elapsed
        val readyAmbiguous = pendingAmbiguousActions.filter { nowMs >= it.fireDeadlineMs }
        if (readyAmbiguous.isNotEmpty()) {
            pendingAmbiguousActions.removeAll(readyAmbiguous.toSet())
            for (pending in readyAmbiguous) {
                if (matchesConditions(pending.binding)) {
                    effects += scheduleOrExecute(pending.binding)
                }
            }
        }

        // 6. Scheduled actions whose delay / cancel window elapsed
        val readyScheduled = scheduledActions.filter { nowMs >= it.executeAtMs && nowMs >= it.cancelWindowUntilMs }
        if (readyScheduled.isNotEmpty()) {
            scheduledActions.removeAll(readyScheduled.toSet())
            for (scheduled in readyScheduled) {
                if (matchesConditions(scheduled.binding)) {
                    effects += executeAction(scheduled.binding, scheduled.binding.action)
                }
            }
        }

        return effects
    }

    /**
     * Notify engine of changes to external context (permissions, connection, armed state, playing state).
     */
    fun updateContext(newContext: EngineContext): List<EngineEffect> {
        val oldContext = context
        context = newContext
        val effects = mutableListOf<EngineEffect>()

        if (newContext.isArmed != armed) {
            armed = newContext.isArmed
        }

        // If actions allowed turned false, immediately abort holds, repeats and pending actions
        if (oldContext.actionsAllowed && !newContext.actionsAllowed) {
            dropActiveHoldsAndRepeats(effects)
        }

        return effects
    }

    private fun handleArmDisarmGesture(event: GestureEvent, effects: MutableList<EngineEffect>): Boolean {
        val settings = profile.globalSettings

        // Toggle arm gesture
        if (settings.toggleArmGesture != null && event.type == settings.toggleArmGesture && isActivationEvent(event)) {
            armed = !armed
            effects += EngineEffect.ArmChanged(armed = armed, reason = "Toggle arm gesture ${event.type}")
            return true
        }

        // Arm gesture
        if (settings.armGesture != null && event.type == settings.armGesture && isActivationEvent(event)) {
            if (!armed) {
                armed = true
                effects += EngineEffect.ArmChanged(armed = true, reason = "Arm gesture ${event.type}")
            }
            return true
        }

        // Disarm gesture
        if (settings.disarmGesture != null && event.type == settings.disarmGesture && isActivationEvent(event)) {
            if (armed) {
                armed = false
                effects += EngineEffect.ArmChanged(armed = false, reason = "Disarm gesture ${event.type}")
            }
            return true
        }

        return false
    }

    private fun isActivationEvent(event: GestureEvent): Boolean =
        event is GestureInstant || event is GestureEnd || event is GestureStart

    private fun handleGestureCancel(event: GestureCancel, effects: MutableList<EngineEffect>) {
        // Cancel active hold and repeat
        activeHold = null
        activeRepeat = null

        // Check scheduled actions in cancel window
        val cancelled = scheduledActions.filter {
            currentTimeMs <= it.cancelWindowUntilMs && (it.cancelGesture == null || it.cancelGesture == event.type)
        }
        if (cancelled.isNotEmpty()) {
            scheduledActions.removeAll(cancelled.toSet())
            for (c in cancelled) {
                effects += EngineEffect.ActionCancelled(c.binding, "GestureCancel ${event.reason}")
            }
        }
    }

    private fun handleGestureStart(event: GestureStart, effects: MutableList<EngineEffect>) {
        activeHold = ActiveHold(event.type, event.onsetMs, event.timestampMs)

        // Check if any enabled Hold binding has RepeatWhileHeld
        for (binding in profile.bindings) {
            if (!binding.enabled || !matchesConditions(binding)) continue
            val trigger = binding.trigger as? Trigger.Hold ?: continue
            if (trigger.gesture == event.type) {
                val action = binding.action as? RuleAction.RepeatWhileHeld ?: continue
                val firstFire = currentTimeMs + trigger.minDurationMs + action.initialDelayMs
                activeRepeat = ActiveRepeat(binding, action.command, action.intervalMs, firstFire)
                break
            }
        }
    }

    private fun handleGestureEnd(event: GestureEnd, effects: MutableList<EngineEffect>) {
        val hold = activeHold
        activeHold = null
        activeRepeat = null

        val duration = event.durationMs

        // Find candidate hold bindings
        val candidates = profile.bindings.filter { binding ->
            if (!binding.enabled || !matchesConditions(binding)) return@filter false
            val trigger = binding.trigger as? Trigger.Hold ?: return@filter false
            if (trigger.gesture != event.type) return@filter false
            if (duration < trigger.minDurationMs) return@filter false
            if (trigger.maxDurationMs != null && duration > trigger.maxDurationMs) return@filter false
            // Don't trigger RepeatWhileHeld on end
            binding.action !is RuleAction.RepeatWhileHeld
        }

        if (candidates.isNotEmpty()) {
            // Pick most specific (largest minDurationMs)
            val best = candidates.maxByOrNull { (it.trigger as Trigger.Hold).minDurationMs } ?: candidates.first()
            effects += scheduleOrExecute(best)
        }
    }

    private fun handleGestureInstant(event: GestureInstant, effects: MutableList<EngineEffect>) {
        val gesture = event.type

        // Cancel scheduled actions if gesture matches cancelGesture
        val toCancel = scheduledActions.filter {
            currentTimeMs <= it.cancelWindowUntilMs && it.cancelGesture == gesture
        }
        if (toCancel.isNotEmpty()) {
            scheduledActions.removeAll(toCancel.toSet())
            for (c in toCancel) {
                effects += EngineEffect.ActionCancelled(c.binding, "Cancelled by $gesture")
            }
        }

        // 1. Check if gesture continues an active sequence
        val matchedSequence = activeSequences.firstOrNull { seq ->
            val seqTrigger = seq.binding.trigger as Trigger.Sequence
            seqTrigger.gestures.getOrNull(seq.nextIndex) == gesture
        }

        if (matchedSequence != null) {
            val seqTrigger = matchedSequence.binding.trigger as Trigger.Sequence
            if (matchedSequence.nextIndex == seqTrigger.gestures.lastIndex) {
                // Completed sequence!
                activeSequences.remove(matchedSequence)
                // Cancel pending ambiguous actions shadowed by this sequence
                dropAmbiguousShadowedBy(matchedSequence.binding)
                if (matchesConditions(matchedSequence.binding)) {
                    effects += scheduleOrExecute(matchedSequence.binding)
                }
                return
            } else {
                // Advance sequence
                val updatedIndex = matchedSequence.nextIndex + 1
                matchedSequence.deadlineMs = currentTimeMs + seqTrigger.maxGapMs
                activeSequences.remove(matchedSequence)
                activeSequences += matchedSequence.copy(nextIndex = updatedIndex)
                return
            }
        }

        // 2. Check if gesture continues an active repeat
        val matchedRepeat = activeRepeats.firstOrNull { rep ->
            val repTrigger = rep.binding.trigger as Trigger.Repeat
            repTrigger.gesture == gesture
        }

        if (matchedRepeat != null) {
            val repTrigger = matchedRepeat.binding.trigger as Trigger.Repeat
            matchedRepeat.countSoFar++
            if (matchedRepeat.countSoFar >= repTrigger.count) {
                // Completed repeat!
                activeRepeats.remove(matchedRepeat)
                dropAmbiguousShadowedBy(matchedRepeat.binding)
                if (matchesConditions(matchedRepeat.binding)) {
                    effects += scheduleOrExecute(matchedRepeat.binding)
                }
                return
            } else {
                matchedRepeat.deadlineMs = currentTimeMs + repTrigger.maxGapMs
                return
            }
        }

        // 3. New Sequence or Repeat starts with this gesture?
        var startedNewMultiGesture = false
        for (binding in profile.bindings) {
            if (!binding.enabled || !matchesConditions(binding)) continue
            when (val trigger = binding.trigger) {
                is Trigger.Sequence -> {
                    if (trigger.gestures.first() == gesture) {
                        activeSequences += SequenceState(binding, nextIndex = 1, deadlineMs = currentTimeMs + trigger.maxGapMs)
                        startedNewMultiGesture = true
                    }
                }
                is Trigger.Repeat -> {
                    if (trigger.gesture == gesture) {
                        activeRepeats += RepeatState(binding, countSoFar = 1, deadlineMs = currentTimeMs + trigger.maxGapMs)
                        startedNewMultiGesture = true
                    }
                }
                else -> Unit
            }
        }

        // 4. Check Single bindings for this gesture
        val singleBindings = profile.bindings.filter {
            it.enabled && matchesConditions(it) && it.trigger is Trigger.Single && it.trigger.gesture == gesture
        }

        for (single in singleBindings) {
            val shadowWait = AmbiguityResolver.maxShadowWaitMs(single, profile.bindings)
            if (shadowWait > 0L) {
                // Ambiguous: wait until deadline before firing
                pendingAmbiguousActions += PendingAmbiguous(single, fireDeadlineMs = currentTimeMs + shadowWait)
            } else {
                // Unambiguous: schedule or execute immediately
                effects += scheduleOrExecute(single)
            }
        }
    }

    private fun dropAmbiguousShadowedBy(completedBinding: Binding) {
        val trigger = completedBinding.trigger
        val initialGesture = when (trigger) {
            is Trigger.Sequence -> trigger.gestures.first()
            is Trigger.Repeat -> trigger.gesture
            else -> null
        } ?: return

        pendingAmbiguousActions.removeAll { pending ->
            val t = pending.binding.trigger
            t is Trigger.Single && t.gesture == initialGesture
        }
    }

    private fun scheduleOrExecute(binding: Binding): List<EngineEffect> {
        val effects = mutableListOf<EngineEffect>()
        val timing = binding.timing

        if (timing.delayBeforeMs > 0L || timing.cancelWindowMs > 0L) {
            val execAt = currentTimeMs + timing.delayBeforeMs
            val cancelUntil = currentTimeMs + timing.cancelWindowMs
            scheduledActions += ScheduledAction(binding, execAt, cancelUntil, timing.cancelGesture)
            effects += EngineEffect.ActionScheduled(binding, execAt)
        } else {
            effects += executeAction(binding, binding.action)
        }

        return effects
    }

    private fun executeAction(binding: Binding, action: RuleAction): List<EngineEffect> {
        val effects = mutableListOf<EngineEffect>()
        if (!context.actionsAllowed) return effects

        when (action) {
            is RuleAction.SingleCommand -> {
                if (canFireActionNow(action.command)) {
                    effects += executeCommand(action.command, binding)
                }
            }
            is RuleAction.Macro -> {
                if (action.steps.isNotEmpty()) {
                    val first = action.steps.first()
                    if (canFireActionNow(first.command)) {
                        effects += executeCommand(first.command, binding)
                        if (action.steps.size > 1) {
                            activeMacro = ActiveMacro(
                                binding = binding,
                                steps = action.steps,
                                currentStepIndex = 1,
                                nextStepAtMs = currentTimeMs + first.delayAfterMs,
                            )
                        }
                    }
                }
            }
            is RuleAction.RepeatWhileHeld -> {
                // Repeats are handled during activeHold
            }
        }

        return effects
    }

    private fun executeCommand(command: MediaCommand, binding: Binding?): EngineEffect.ExecuteCommand {
        lastGlobalActionMs = currentTimeMs
        if (command == MediaCommand.PlayPause) {
            lastPlayPauseMs = currentTimeMs
        }
        if (binding != null) {
            lastBindingFiredMs[binding.id] = currentTimeMs
        }
        return EngineEffect.ExecuteCommand(command, binding)
    }

    private fun canFireActionNow(command: MediaCommand): Boolean {
        if (!context.actionsAllowed) return false

        // Global cooldown
        val globalCooldown = profile.globalSettings.globalCooldownMs
        if (currentTimeMs < lastGlobalActionMs + globalCooldown) {
            return false
        }

        // Android Play/Pause 300ms double-tap spacing guard
        if (command == MediaCommand.PlayPause) {
            if (currentTimeMs - lastPlayPauseMs < 350L) {
                return false
            }
        }

        return true
    }

    private fun matchesConditions(binding: Binding): Boolean {
        if (!binding.enabled) return false

        // Armed condition
        if (binding.conditions.onlyWhenArmed && !armed) {
            return false
        }

        // Media status condition
        when (binding.conditions.mediaStatus) {
            MediaCondition.ANY -> Unit
            MediaCondition.PLAYING_ONLY -> if (context.isPlaying != true) return false
            MediaCondition.PAUSED_ONLY -> if (context.isPlaying == true) return false
        }

        // Required channels condition
        for (ch in binding.conditions.requiredChannels) {
            if (ch !in context.goodChannels) return false
        }

        // Per-binding cooldown
        val lastFired = lastBindingFiredMs[binding.id] ?: 0L
        if (currentTimeMs < lastFired + binding.timing.cooldownAfterMs) {
            return false
        }

        return true
    }

    private fun dropActiveHoldsAndRepeats(effects: MutableList<EngineEffect>) {
        activeHold = null
        activeRepeat = null
        activeMacro = null
        activeSequences.clear()
        activeRepeats.clear()

        for (scheduled in scheduledActions) {
            effects += EngineEffect.ActionCancelled(scheduled.binding, "actionsAllowed is false")
        }
        scheduledActions.clear()
        pendingAmbiguousActions.clear()
    }
}
