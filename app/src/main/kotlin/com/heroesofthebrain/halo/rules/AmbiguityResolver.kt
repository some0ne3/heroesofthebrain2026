package com.heroesofthebrain.halo.rules

import com.heroesofthebrain.halo.detect.GestureType
import kotlin.math.max
import kotlin.math.min

sealed interface AmbiguityWarning {
    val message: String

    data class ShadowedBySequence(
        val singleBinding: Binding,
        val sequenceBinding: Binding,
        val extraWaitMs: Long,
    ) : AmbiguityWarning {
        override val message: String
            get() = "'${singleBinding.name.ifEmpty { singleBinding.id }}' will wait ${extraWaitMs}ms to see if '${sequenceBinding.name.ifEmpty { sequenceBinding.id }}' completes"
    }

    data class ShadowedByRepeat(
        val singleBinding: Binding,
        val repeatBinding: Binding,
        val extraWaitMs: Long,
    ) : AmbiguityWarning {
        override val message: String
            get() = "'${singleBinding.name.ifEmpty { singleBinding.id }}' will wait ${extraWaitMs}ms to see if '${repeatBinding.name.ifEmpty { repeatBinding.id }}' completes"
    }

    data class OverlappingHoldRanges(
        val bindingA: Binding,
        val bindingB: Binding,
        val gesture: GestureType,
        val overlapMinMs: Long,
        val overlapMaxMs: Long?,
    ) : AmbiguityWarning {
        override val message: String
            get() = "'${bindingA.name.ifEmpty { bindingA.id }}' and '${bindingB.name.ifEmpty { bindingB.id }}' have overlapping hold ranges on $gesture ($overlapMinMs..${overlapMaxMs?.toString() ?: "∞"} ms)"
    }

    data class DuplicateTrigger(
        val bindingA: Binding,
        val bindingB: Binding,
        val triggerDescription: String,
    ) : AmbiguityWarning {
        override val message: String
            get() = "'${bindingA.name.ifEmpty { bindingA.id }}' and '${bindingB.name.ifEmpty { bindingB.id }}' share the identical trigger ($triggerDescription). Only the first one will execute."
    }
}

object AmbiguityResolver {

    /**
     * Analyzes enabled bindings in a profile for potential ambiguity and conflicts.
     */
    fun analyze(bindings: List<Binding>): List<AmbiguityWarning> {
        val enabled = bindings.filter { it.enabled }
        val warnings = mutableListOf<AmbiguityWarning>()

        // 1. Single gesture shadowed by Sequence or Repeat
        val singleBindings = enabled.filter { it.trigger is Trigger.Single }
        val sequenceBindings = enabled.filter { it.trigger is Trigger.Sequence }
        val repeatBindings = enabled.filter { it.trigger is Trigger.Repeat }

        for (single in singleBindings) {
            val singleTrigger = single.trigger as Trigger.Single
            val gesture = singleTrigger.gesture

            for (seq in sequenceBindings) {
                val seqTrigger = seq.trigger as Trigger.Sequence
                if (seqTrigger.gestures.firstOrNull() == gesture) {
                    warnings += AmbiguityWarning.ShadowedBySequence(
                        singleBinding = single,
                        sequenceBinding = seq,
                        extraWaitMs = seqTrigger.maxGapMs,
                    )
                }
            }

            for (rep in repeatBindings) {
                val repTrigger = rep.trigger as Trigger.Repeat
                if (repTrigger.gesture == gesture) {
                    warnings += AmbiguityWarning.ShadowedByRepeat(
                        singleBinding = single,
                        repeatBinding = rep,
                        extraWaitMs = repTrigger.maxGapMs,
                    )
                }
            }
        }

        // 2. Overlapping Hold ranges for the same gesture
        val holdBindings = enabled.filter { it.trigger is Trigger.Hold }
        for (i in 0 until holdBindings.size) {
            val bA = holdBindings[i]
            val tA = bA.trigger as Trigger.Hold
            for (j in i + 1 until holdBindings.size) {
                val bB = holdBindings[j]
                val tB = bB.trigger as Trigger.Hold
                if (tA.gesture == tB.gesture) {
                    val overlap = computeOverlap(tA.minDurationMs, tA.maxDurationMs, tB.minDurationMs, tB.maxDurationMs)
                    if (overlap != null) {
                        warnings += AmbiguityWarning.OverlappingHoldRanges(
                            bindingA = bA,
                            bindingB = bB,
                            gesture = tA.gesture,
                            overlapMinMs = overlap.first,
                            overlapMaxMs = overlap.second,
                        )
                    }
                }
            }
        }

        // 3. Duplicate identical triggers
        for (i in 0 until enabled.size) {
            val bA = enabled[i]
            for (j in i + 1 until enabled.size) {
                val bB = enabled[j]
                if (bA.trigger == bB.trigger) {
                    warnings += AmbiguityWarning.DuplicateTrigger(
                        bindingA = bA,
                        bindingB = bB,
                        triggerDescription = formatTrigger(bA.trigger),
                    )
                }
            }
        }

        return warnings
    }

    private fun formatTrigger(trigger: Trigger): String = when (trigger) {
        is Trigger.Single -> trigger.gesture.name.replace('_', ' ')
        is Trigger.Hold -> "Hold ${trigger.gesture.name.replace('_', ' ')} (${trigger.minDurationMs}..${trigger.maxDurationMs?.toString() ?: "∞"} ms)"
        is Trigger.Repeat -> "${trigger.count}x ${trigger.gesture.name.replace('_', ' ')}"
        is Trigger.Sequence -> trigger.gestures.joinToString(" → ") { it.name.replace('_', ' ') }
    }

    /**
     * Returns the extra wait time (in ms) that a single gesture binding must wait
     * due to shadowing sequence or repeat bindings.
     */
    fun maxShadowWaitMs(binding: Binding, allBindings: List<Binding>): Long {
        if (!binding.enabled) return 0L
        val trigger = binding.trigger as? Trigger.Single ?: return 0L
        val gesture = trigger.gesture

        var maxWait = 0L
        for (other in allBindings) {
            if (!other.enabled || other.id == binding.id) continue
            when (val otherTrigger = other.trigger) {
                is Trigger.Sequence -> {
                    if (otherTrigger.gestures.firstOrNull() == gesture) {
                        maxWait = max(maxWait, otherTrigger.maxGapMs)
                    }
                }
                is Trigger.Repeat -> {
                    if (otherTrigger.gesture == gesture) {
                        maxWait = max(maxWait, otherTrigger.maxGapMs)
                    }
                }
                else -> Unit
            }
        }
        return maxWait
    }

    private fun computeOverlap(
        minA: Long,
        maxA: Long?,
        minB: Long,
        maxB: Long?,
    ): Pair<Long, Long?>? {
        val start = max(minA, minB)
        val effectiveMaxA = maxA ?: Long.MAX_VALUE
        val effectiveMaxB = maxB ?: Long.MAX_VALUE
        val end = min(effectiveMaxA, effectiveMaxB)

        return if (start <= end) {
            val resolvedEnd = if (end == Long.MAX_VALUE) null else end
            Pair(start, resolvedEnd)
        } else {
            null
        }
    }
}
