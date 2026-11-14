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

    // Input macros part way through. Once a held step has started it waits for the hold to end, not the deadline.
    private data class MacroProgress(
        val binding: Binding,
        val steps: List<InputStep>,
        val nextIndex: Int,
        val deadlineMs: Long,
        val holdStartedMs: Long? = null,
    ) {
        val next: InputStep get() = steps[nextIndex]
    }
    private val inputMacros = mutableListOf<MacroProgress>()

    private data class RepeatState(
        val binding: Binding,
        var countSoFar: Int,
        var deadlineMs: Long,
    )
    private val activeRepeats = mutableListOf<RepeatState>()

    // A trigger that matched while longer triggers using the same input could still complete. It fires once all
    // of those have failed, and is dropped as soon as one of them takes another step or completes.
    private data class PendingAmbiguous(
        val binding: Binding,
        val shadowedBy: Set<String>,
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

        // 4. Progress that ran out of time; a hold can outlast its step's maximum before it ends
        inputMacros.removeAll { p ->
            val heldSince = p.holdStartedMs
            if (heldSince == null) nowMs > p.deadlineMs else p.next.maxHoldMs?.let { nowMs - heldSince > it } == true
        }
        activeRepeats.removeAll { nowMs > it.deadlineMs }

        // 5. Pending ambiguous actions whose longer rivals all failed
        releasePending(effects)

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

        // A cancelled hold never happened, so only a macro step waiting on it fails.
        if (inputMacros.removeAll { it.holdStartedMs != null && it.next.gesture == event.type }) {
            releasePending(effects)
        }

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

        // Other holds are judged when they end, since a hold that gets cancelled never happened.
        var forMacroStep = false
        val iterator = inputMacros.listIterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            if (p.holdStartedMs == null && p.next.gesture == event.type && currentTimeMs <= p.deadlineMs) {
                iterator.set(p.copy(holdStartedMs = currentTimeMs))
                forMacroStep = true
            }
        }
        if (forMacroStep) return

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
        activeHold = null
        activeRepeat = null

        val duration = event.durationMs

        val offer = offerToMacros { p ->
            p.holdStartedMs != null && p.next.gesture == event.type && p.next.acceptsHold(duration)
        }
        if (settle(offer, effects)) return

        val started = mutableSetOf<String>()
        for (binding in profile.bindings) {
            if (!binding.enabled || !matchesConditions(binding)) continue
            val trigger = binding.trigger as? Trigger.InputMacro ?: continue
            val first = trigger.steps.first()
            if (first.gesture == event.type && first.gesture.held && first.acceptsHold(duration)) {
                startMacro(binding, trigger)
                started += binding.id
            }
        }

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
            fireOrDefer(best, started, effects)
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

        // 1. Macros in progress take the gesture or fail; repeats ignore other gestures
        val offer = offerToMacros { p ->
            p.holdStartedMs == null && currentTimeMs <= p.deadlineMs && !p.next.gesture.held && p.next.gesture == gesture
        }
        val repeatIterator = activeRepeats.iterator()
        while (repeatIterator.hasNext()) {
            val rep = repeatIterator.next()
            val repTrigger = rep.binding.trigger as Trigger.Repeat
            if (repTrigger.gesture != gesture) continue
            rep.countSoFar++
            if (rep.countSoFar >= repTrigger.count) {
                repeatIterator.remove()
                offer.completed += rep.binding
            } else {
                rep.deadlineMs = currentTimeMs + repTrigger.maxGapMs
                offer.took += rep.binding.id
            }
        }
        if (settle(offer, effects)) return

        // 2. Nothing in progress wanted it: it may start new macros and repeats
        val started = mutableSetOf<String>()
        for (binding in profile.bindings) {
            if (!binding.enabled || !matchesConditions(binding)) continue
            when (val trigger = binding.trigger) {
                is Trigger.InputMacro -> {
                    val first = trigger.steps.first()
                    if (first.gesture == gesture && !first.gesture.held) {
                        startMacro(binding, trigger)
                        started += binding.id
                    }
                }
                is Trigger.Repeat -> {
                    if (trigger.gesture == gesture) {
                        activeRepeats += RepeatState(binding, countSoFar = 1, deadlineMs = currentTimeMs + trigger.maxGapMs)
                        started += binding.id
                    }
                }
                else -> Unit
            }
        }

        // 3. Single bindings wait while anything that just started could still complete
        val singleBindings = profile.bindings.filter {
            it.enabled && it.trigger is Trigger.Single && it.trigger.gesture == gesture
        }
        for (single in singleBindings) {
            fireOrDefer(single, started, effects)
        }
    }

    private class Offer {
        // Progress that took the input and still needs more of it.
        val took = mutableSetOf<String>()
        val completed = mutableListOf<Binding>()
    }

    // Every macro in progress either takes the input or fails: a procedure has to be done in order.
    private fun offerToMacros(accepts: (MacroProgress) -> Boolean): Offer {
        val offer = Offer()
        val snapshot = inputMacros.toList()
        inputMacros.clear()
        for (p in snapshot) {
            if (!accepts(p)) continue
            val nextIndex = p.nextIndex + 1
            if (nextIndex == p.steps.size) {
                offer.completed += p.binding
            } else {
                inputMacros += p.copy(
                    nextIndex = nextIndex,
                    deadlineMs = currentTimeMs + p.steps[nextIndex].withinMs,
                    holdStartedMs = null,
                )
                offer.took += p.binding.id
            }
        }
        return offer
    }

    // Returns true when the input belonged to something already in progress.
    private fun settle(offer: Offer, effects: MutableList<EngineEffect>): Boolean {
        val progressed = offer.took + offer.completed.map { it.id }
        pendingAmbiguousActions.removeAll { pending -> pending.shadowedBy.any(progressed::contains) }
        // What failed on this input lets its waiting rivals fire first, before anything the input itself triggers.
        releasePending(effects)
        // Several completing on one input are identical triggers; like any duplicate, only the first one runs.
        offer.completed.firstOrNull()?.let { fireOrDefer(it, offer.took, effects) }
        return progressed.isNotEmpty()
    }

    private fun startMacro(binding: Binding, trigger: Trigger.InputMacro) {
        inputMacros += MacroProgress(
            binding = binding,
            steps = trigger.steps,
            nextIndex = 1,
            deadlineMs = currentTimeMs + trigger.steps[1].withinMs,
        )
    }

    private fun fireOrDefer(binding: Binding, rivals: Set<String>, effects: MutableList<EngineEffect>) {
        if (!matchesConditions(binding)) return
        val others = rivals - binding.id
        if (others.isEmpty()) {
            effects += scheduleOrExecute(binding)
        } else {
            pendingAmbiguousActions += PendingAmbiguous(binding, others)
        }
    }

    private fun releasePending(effects: MutableList<EngineEffect>) {
        if (pendingAmbiguousActions.isEmpty()) return
        val live = inputMacros.mapTo(mutableSetOf()) { it.binding.id }
        activeRepeats.mapTo(live) { it.binding.id }
        val ready = pendingAmbiguousActions.filter { pending -> pending.shadowedBy.none(live::contains) }
        if (ready.isEmpty()) return
        pendingAmbiguousActions.removeAll(ready.toSet())
        for (pending in ready) {
            if (matchesConditions(pending.binding)) {
                effects += scheduleOrExecute(pending.binding)
            }
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
        inputMacros.clear()
        activeRepeats.clear()

        for (scheduled in scheduledActions) {
            effects += EngineEffect.ActionCancelled(scheduled.binding, "actionsAllowed is false")
        }
        scheduledActions.clear()
        pendingAmbiguousActions.clear()
    }
}
