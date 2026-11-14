package com.heroesofthebrain.halo.ui.editor

import com.heroesofthebrain.halo.detect.GestureCancel
import com.heroesofthebrain.halo.detect.GestureEnd
import com.heroesofthebrain.halo.detect.GestureEvent
import com.heroesofthebrain.halo.detect.GestureInstant
import com.heroesofthebrain.halo.detect.GestureStart
import com.heroesofthebrain.halo.rules.Binding
import com.heroesofthebrain.halo.rules.InputStep
import com.heroesofthebrain.halo.rules.Trigger
import com.heroesofthebrain.halo.rules.describe
import com.heroesofthebrain.halo.rules.formatWindow
import java.util.concurrent.atomic.AtomicLong

class TryItTester(
    val binding: Binding,
    val ambiguityWaitMs: Long = 0L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val nextResultId = AtomicLong()

    var session: TryItSession = TryItSession(
        binding = binding,
        status = TryItStatus.LISTENING,
        message = "Waiting for gesture: ${binding.triggerDescription()}",
    )
        private set

    fun reset() {
        session = session.copy(
            status = TryItStatus.LISTENING,
            currentHoldElapsedMs = 0L,
            holdOnsetMs = 0L,
            repeatCountSoFar = 0,
            repeatLastEventMs = 0L,
            macroStepIndex = 0,
            macroLastStepMs = 0L,
            macroStartedMs = 0L,
            macroHolding = false,
            macroRemainingMs = null,
            lastEvent = null,
            message = "Waiting for gesture: ${binding.triggerDescription()}",
            activeBreakdown = null,
        )
    }

    /**
     * Updates hold progress as time passes (for live UI gauge rendering during hold).
     */
    fun tick(currentTimeMs: Long = clock()) {
        if (session.status == TryItStatus.HOLDING && session.holdOnsetMs > 0L) {
            val elapsed = currentTimeMs - session.holdOnsetMs
            session = session.copy(currentHoldElapsedMs = elapsed)
        }
        val macro = binding.trigger as? Trigger.InputMacro ?: return
        expireMacroStep(macro, currentTimeMs)
        if (session.macroStepIndex > 0 && !session.macroHolding) {
            val window = macro.steps[session.macroStepIndex].withinMs
            session = session.copy(macroRemainingMs = (window - (currentTimeMs - session.macroLastStepMs)).coerceAtLeast(0L))
        }
    }

    fun processEvent(event: GestureEvent, timestampMs: Long = clock()): TryItSession {
        session = session.copy(lastEvent = event)

        when (val trigger = binding.trigger) {
            is Trigger.Single -> processSingle(trigger, event, timestampMs)
            is Trigger.Hold -> processHold(trigger, event, timestampMs)
            is Trigger.Repeat -> processRepeat(trigger, event, timestampMs)
            is Trigger.InputMacro -> processInputMacro(trigger, event, timestampMs)
        }
        return session
    }

    private fun processSingle(trigger: Trigger.Single, event: GestureEvent, timestampMs: Long) {
        if (event.type == trigger.gesture && (event is GestureInstant || event is GestureStart)) {
            val breakdown = computeBreakdown(triggerDurationMs = 0L)
            val result = TryItResult(
                id = nextResultId.incrementAndGet(),
                status = TryItStatus.MATCHED,
                timestampMs = timestampMs,
                summary = "Matched: ${trigger.gesture.name} single gesture",
                timingBreakdown = breakdown,
            )
            session = session.copy(
                status = TryItStatus.MATCHED,
                message = "Matched ${trigger.gesture.name}! (Simulated action: ${breakdown.executionSummary})",
                history = listOf(result) + session.history.take(9),
                activeBreakdown = breakdown,
            )
        }
    }

    private fun processHold(trigger: Trigger.Hold, event: GestureEvent, timestampMs: Long) {
        if (event.type != trigger.gesture) return

        when (event) {
            is GestureStart -> {
                session = session.copy(
                    status = TryItStatus.HOLDING,
                    holdOnsetMs = event.onsetMs,
                    currentHoldElapsedMs = timestampMs - event.onsetMs,
                    message = "Holding ${trigger.gesture.name}… target: ${trigger.minDurationMs}..${trigger.maxDurationMs?.toString() ?: "∞"} ms",
                )
            }
            is GestureEnd -> {
                val duration = event.durationMs
                val (status, msg) = when {
                    duration < trigger.minDurationMs -> {
                        TryItStatus.TOO_SHORT to "Hold too short: ${duration} ms (minimum required: ${trigger.minDurationMs} ms)"
                    }
                    trigger.maxDurationMs != null && duration > trigger.maxDurationMs -> {
                        TryItStatus.TOO_LONG to "Hold too long: ${duration} ms (maximum allowed: ${trigger.maxDurationMs} ms)"
                    }
                    else -> {
                        TryItStatus.MATCHED to "Matched hold: ${duration} ms (target: ${trigger.minDurationMs}..${trigger.maxDurationMs?.toString() ?: "∞"} ms)"
                    }
                }

                val breakdown = if (status == TryItStatus.MATCHED) computeBreakdown(triggerDurationMs = duration) else null
                val result = TryItResult(
                    id = nextResultId.incrementAndGet(),
                    status = status,
                    timestampMs = timestampMs,
                    summary = msg,
                    durationMs = duration,
                    timingBreakdown = breakdown,
                )

                session = session.copy(
                    status = status,
                    currentHoldElapsedMs = duration,
                    holdOnsetMs = 0L,
                    message = msg,
                    history = listOf(result) + session.history.take(9),
                    activeBreakdown = breakdown,
                )
            }
            is GestureCancel -> {
                val result = TryItResult(
                    id = nextResultId.incrementAndGet(),
                    status = TryItStatus.CANCELLED,
                    timestampMs = timestampMs,
                    summary = "Cancelled: ${event.reason.name.lowercase()}",
                    cancelReason = event.reason,
                )
                session = session.copy(
                    status = TryItStatus.CANCELLED,
                    holdOnsetMs = 0L,
                    currentHoldElapsedMs = 0L,
                    message = "Hold cancelled due to ${event.reason.name.lowercase()}",
                    history = listOf(result) + session.history.take(9),
                    activeBreakdown = null,
                )
            }
            is GestureInstant -> Unit
        }
    }

    private fun processRepeat(trigger: Trigger.Repeat, event: GestureEvent, timestampMs: Long) {
        if (event.type != trigger.gesture) return
        if (event !is GestureInstant && event !is GestureStart) return

        val count = session.repeatCountSoFar
        val lastTime = session.repeatLastEventMs
        val gap = if (count > 0) timestampMs - lastTime else 0L

        if (count == 0 || gap > trigger.maxGapMs) {
            session = session.copy(
                status = TryItStatus.LISTENING,
                repeatCountSoFar = 1,
                repeatLastEventMs = timestampMs,
                message = "Detected 1/${trigger.count}. Repeat within ${trigger.maxGapMs} ms",
            )
        } else {
            val newCount = count + 1
            if (newCount >= trigger.count) {
                val breakdown = computeBreakdown(triggerDurationMs = gap)
                val result = TryItResult(
                    id = nextResultId.incrementAndGet(),
                    status = TryItStatus.MATCHED,
                    timestampMs = timestampMs,
                    summary = "Matched repeat: ${trigger.count} × ${trigger.gesture.name} (gap: ${gap} ms)",
                    timingBreakdown = breakdown,
                )
                session = session.copy(
                    status = TryItStatus.MATCHED,
                    repeatCountSoFar = 0,
                    repeatLastEventMs = 0L,
                    message = "Matched ${trigger.count} repeats! (Last gap: ${gap} ms)",
                    history = listOf(result) + session.history.take(9),
                    activeBreakdown = breakdown,
                )
            } else {
                session = session.copy(
                    status = TryItStatus.LISTENING,
                    repeatCountSoFar = newCount,
                    repeatLastEventMs = timestampMs,
                    message = "Detected $newCount/${trigger.count}. Next within ${trigger.maxGapMs} ms (gap: ${gap} ms)",
                )
            }
        }
    }

    // Mirrors the engine: steps in order, each within its window, and a wrong gesture starts over.
    private fun processInputMacro(trigger: Trigger.InputMacro, event: GestureEvent, timestampMs: Long) {
        val steps = trigger.steps
        expireMacroStep(trigger, timestampMs)
        val index = session.macroStepIndex
        val step = steps[index]
        val holding = session.macroHolding

        when (event) {
            is GestureStart -> {
                if (!holding && step.gesture == event.type) {
                    session = session.copy(
                        status = TryItStatus.HOLDING,
                        macroHolding = true,
                        macroRemainingMs = null,
                        message = "Step ${index + 1}/${steps.size}: holding ${step.gesture.name}… target ${holdRange(step)}",
                    )
                }
            }
            is GestureCancel -> {
                if (holding && step.gesture == event.type) {
                    failMacro(TryItStatus.CANCELLED, "Step ${index + 1} cancelled: ${event.reason.name.lowercase()}", timestampMs)
                }
            }
            is GestureEnd -> {
                if (holding && step.gesture == event.type) {
                    val duration = event.durationMs
                    when {
                        duration < step.minHoldMs ->
                            failMacro(TryItStatus.TOO_SHORT, "Step ${index + 1} hold too short: $duration ms (target ${holdRange(step)})", timestampMs)
                        step.maxHoldMs != null && duration > step.maxHoldMs ->
                            failMacro(TryItStatus.TOO_LONG, "Step ${index + 1} hold too long: $duration ms (target ${holdRange(step)})", timestampMs)
                        else -> advanceMacro(trigger, timestampMs)
                    }
                } else if (index > 0) {
                    failMacro(TryItStatus.WRONG_STEP, "Expected ${step.gesture.name} for step ${index + 1}, got ${event.type.name}", timestampMs)
                }
            }
            is GestureInstant -> {
                if (!holding && step.gesture == event.type && !step.gesture.held) {
                    advanceMacro(trigger, timestampMs)
                } else if (index > 0 || holding) {
                    failMacro(TryItStatus.WRONG_STEP, "Expected ${step.gesture.name} for step ${index + 1}, got ${event.type.name}", timestampMs)
                    val first = steps.first()
                    if (first.gesture == event.type && !first.gesture.held) advanceMacro(trigger, timestampMs)
                }
            }
        }
    }

    private fun expireMacroStep(trigger: Trigger.InputMacro, nowMs: Long) {
        val index = session.macroStepIndex
        if (index == 0 || session.macroHolding) return
        val step = trigger.steps[index]
        val gap = nowMs - session.macroLastStepMs
        if (gap > step.withinMs) {
            failMacro(TryItStatus.TIMED_OUT, "Step ${index + 1} (${step.gesture.name}) came too late: $gap ms > ${step.withinMs} ms", nowMs)
        }
    }

    private fun advanceMacro(trigger: Trigger.InputMacro, nowMs: Long) {
        val steps = trigger.steps
        val done = session.macroStepIndex
        val startedMs = if (done == 0) nowMs else session.macroStartedMs
        val nextIndex = done + 1
        if (nextIndex == steps.size) {
            val breakdown = computeBreakdown(triggerDurationMs = nowMs - startedMs)
            val result = TryItResult(
                id = nextResultId.incrementAndGet(),
                status = TryItStatus.MATCHED,
                timestampMs = nowMs,
                summary = "Input macro matched: ${trigger.describe()}",
                timingBreakdown = breakdown,
            )
            session = session.copy(
                status = TryItStatus.MATCHED,
                macroStepIndex = 0,
                macroLastStepMs = 0L,
                macroStartedMs = 0L,
                macroHolding = false,
                macroRemainingMs = null,
                message = "All ${steps.size} steps matched in ${nowMs - startedMs} ms",
                history = listOf(result) + session.history.take(9),
                activeBreakdown = breakdown,
            )
        } else {
            val next = steps[nextIndex]
            session = session.copy(
                status = TryItStatus.LISTENING,
                macroStepIndex = nextIndex,
                macroLastStepMs = nowMs,
                macroStartedMs = startedMs,
                macroHolding = false,
                macroRemainingMs = next.withinMs,
                message = "Step $nextIndex/${steps.size} ok. Next: ${next.describe(first = true)} within ${formatWindow(next.withinMs)}",
            )
        }
    }

    private fun failMacro(status: TryItStatus, message: String, nowMs: Long) {
        val result = TryItResult(
            id = nextResultId.incrementAndGet(),
            status = status,
            timestampMs = nowMs,
            summary = message,
        )
        session = session.copy(
            status = status,
            macroStepIndex = 0,
            macroLastStepMs = 0L,
            macroStartedMs = 0L,
            macroHolding = false,
            macroRemainingMs = null,
            message = message,
            history = listOf(result) + session.history.take(9),
            activeBreakdown = null,
        )
    }

    private fun holdRange(step: InputStep): String = "${step.minHoldMs}..${step.maxHoldMs?.toString() ?: "∞"} ms"

    private fun computeBreakdown(triggerDurationMs: Long): TimingBreakdown {
        val delayBefore = binding.timing.delayBeforeMs
        val cooldown = binding.timing.cooldownAfterMs
        val summary = binding.actionDescription()
        val total = triggerDurationMs + ambiguityWaitMs + delayBefore + cooldown
        return TimingBreakdown(
            triggerDurationMs = triggerDurationMs,
            ambiguityWaitMs = ambiguityWaitMs,
            delayBeforeMs = delayBefore,
            executionSummary = summary,
            cooldownAfterMs = cooldown,
            totalMs = total,
        )
    }
}

fun Binding.triggerDescription(): String = when (val t = trigger) {
    is Trigger.Single -> "${t.gesture.name} (single)"
    is Trigger.Hold -> "Hold ${t.gesture.name} (${t.minDurationMs}..${t.maxDurationMs?.toString() ?: "∞"} ms)"
    is Trigger.Repeat -> "${t.count} × ${t.gesture.name} (gap ≤ ${t.maxGapMs} ms)"
    is Trigger.InputMacro -> t.describe()
}

fun Binding.actionDescription(): String = when (val a = action) {
    is com.heroesofthebrain.halo.rules.RuleAction.SingleCommand -> a.command.javaClass.simpleName
    is com.heroesofthebrain.halo.rules.RuleAction.RepeatWhileHeld -> "Repeat ${a.command.javaClass.simpleName} every ${a.intervalMs} ms"
    is com.heroesofthebrain.halo.rules.RuleAction.Macro -> "Macro (${a.steps.size} steps)"
}
