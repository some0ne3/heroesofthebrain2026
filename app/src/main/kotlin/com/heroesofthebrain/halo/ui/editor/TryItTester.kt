package com.heroesofthebrain.halo.ui.editor

import com.heroesofthebrain.halo.detect.GestureCancel
import com.heroesofthebrain.halo.detect.GestureEnd
import com.heroesofthebrain.halo.detect.GestureEvent
import com.heroesofthebrain.halo.detect.GestureInstant
import com.heroesofthebrain.halo.detect.GestureStart
import com.heroesofthebrain.halo.rules.Binding
import com.heroesofthebrain.halo.rules.Trigger
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
            sequenceStepIndex = 0,
            sequenceLastEventMs = 0L,
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
    }

    fun processEvent(event: GestureEvent, timestampMs: Long = clock()): TryItSession {
        session = session.copy(lastEvent = event)

        when (val trigger = binding.trigger) {
            is Trigger.Single -> processSingle(trigger, event, timestampMs)
            is Trigger.Hold -> processHold(trigger, event, timestampMs)
            is Trigger.Repeat -> processRepeat(trigger, event, timestampMs)
            is Trigger.Sequence -> processSequence(trigger, event, timestampMs)
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

    private fun processSequence(trigger: Trigger.Sequence, event: GestureEvent, timestampMs: Long) {
        if (event !is GestureInstant && event !is GestureStart) return

        val step = session.sequenceStepIndex
        val expected = trigger.gestures.getOrNull(step) ?: return
        val lastTime = session.sequenceLastEventMs
        val gap = if (step > 0) timestampMs - lastTime else 0L

        if (step > 0 && gap > trigger.maxGapMs) {
            // Timed out: check if this gesture is the first gesture of the sequence
            if (event.type == trigger.gestures.first()) {
                session = session.copy(
                    sequenceStepIndex = 1,
                    sequenceLastEventMs = timestampMs,
                    message = "Sequence restart: 1/${trigger.gestures.size} (${event.type.name})",
                )
                return
            } else {
                session = session.copy(
                    status = TryItStatus.TIMED_OUT,
                    sequenceStepIndex = 0,
                    sequenceLastEventMs = 0L,
                    message = "Sequence timed out (gap was ${gap} ms > max ${trigger.maxGapMs} ms)",
                )
                return
            }
        }

        if (event.type == expected) {
            val nextStep = step + 1
            if (nextStep >= trigger.gestures.size) {
                val breakdown = computeBreakdown(triggerDurationMs = gap)
                val result = TryItResult(
                    id = nextResultId.incrementAndGet(),
                    status = TryItStatus.MATCHED,
                    timestampMs = timestampMs,
                    summary = "Sequence matched: ${trigger.gestures.joinToString(" → ") { it.name }}",
                    timingBreakdown = breakdown,
                )
                session = session.copy(
                    status = TryItStatus.MATCHED,
                    sequenceStepIndex = 0,
                    sequenceLastEventMs = 0L,
                    message = "Sequence fully matched!",
                    history = listOf(result) + session.history.take(9),
                    activeBreakdown = breakdown,
                )
            } else {
                val nextExpected = trigger.gestures[nextStep]
                session = session.copy(
                    status = TryItStatus.LISTENING,
                    sequenceStepIndex = nextStep,
                    sequenceLastEventMs = timestampMs,
                    message = "Step $nextStep/${trigger.gestures.size} (${event.type.name}) ok! Next: ${nextExpected.name} within ${trigger.maxGapMs} ms",
                )
            }
        } else if (event.type == trigger.gestures.first()) {
            // Restarted sequence
            session = session.copy(
                sequenceStepIndex = 1,
                sequenceLastEventMs = timestampMs,
                message = "Restarted sequence: 1/${trigger.gestures.size} (${event.type.name})",
            )
        }
    }

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
    is Trigger.Sequence -> t.gestures.joinToString(" → ") { it.name } + " (gap ≤ ${t.maxGapMs} ms)"
}

fun Binding.actionDescription(): String = when (val a = action) {
    is com.heroesofthebrain.halo.rules.RuleAction.SingleCommand -> a.command.javaClass.simpleName
    is com.heroesofthebrain.halo.rules.RuleAction.RepeatWhileHeld -> "Repeat ${a.command.javaClass.simpleName} every ${a.intervalMs} ms"
    is com.heroesofthebrain.halo.rules.RuleAction.Macro -> "Macro (${a.steps.size} steps)"
}
