package com.heroesofthebrain.halo.rules

import com.heroesofthebrain.halo.detect.GestureType
import kotlin.math.max
import kotlin.math.min

sealed interface AmbiguityWarning {
    val message: String

    // The shadowed binding can be a single gesture, a hold, or a shorter input macro with the same opening steps.
    data class ShadowedByInputMacro(
        val shadowedBinding: Binding,
        val macroBinding: Binding,
        val extraWaitMs: Long,
    ) : AmbiguityWarning {
        override val message: String
            get() = "'${shadowedBinding.name.ifEmpty { shadowedBinding.id }}' will wait up to ${extraWaitMs}ms to see if '${macroBinding.name.ifEmpty { macroBinding.id }}' continues"
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

        // 1. Bindings that wait for an input macro or repeat starting the same way
        val macroBindings = enabled.filter { it.trigger is Trigger.InputMacro }
        val repeatBindings = enabled.filter { it.trigger is Trigger.Repeat }

        for (binding in enabled) {
            for (macro in macroBindings) {
                if (macro.id == binding.id) continue
                val wait = macroShadowWaitMs(binding.trigger, macro.trigger as Trigger.InputMacro) ?: continue
                warnings += AmbiguityWarning.ShadowedByInputMacro(
                    shadowedBinding = binding,
                    macroBinding = macro,
                    extraWaitMs = wait,
                )
            }

            val singleTrigger = binding.trigger as? Trigger.Single ?: continue
            for (rep in repeatBindings) {
                val repTrigger = rep.trigger as Trigger.Repeat
                if (repTrigger.gesture == singleTrigger.gesture) {
                    warnings += AmbiguityWarning.ShadowedByRepeat(
                        singleBinding = binding,
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
        is Trigger.InputMacro -> trigger.describe()
    }

    /**
     * Returns the longest extra wait (in ms) a binding can add because an input macro or repeat starts the same way.
     */
    fun maxShadowWaitMs(binding: Binding, allBindings: List<Binding>): Long {
        if (!binding.enabled) return 0L
        val single = binding.trigger as? Trigger.Single

        var maxWait = 0L
        for (other in allBindings) {
            if (!other.enabled || other.id == binding.id) continue
            when (val otherTrigger = other.trigger) {
                is Trigger.InputMacro -> {
                    macroShadowWaitMs(binding.trigger, otherTrigger)?.let { maxWait = max(maxWait, it) }
                }
                is Trigger.Repeat -> {
                    if (single != null && otherTrigger.gesture == single.gesture) {
                        maxWait = max(maxWait, otherTrigger.maxGapMs)
                    }
                }
                else -> Unit
            }
        }
        return maxWait
    }

    // When the trigger's input is also the opening of the macro, the engine waits for the macro's next step.
    private fun macroShadowWaitMs(trigger: Trigger, macro: Trigger.InputMacro): Long? {
        val steps = macro.steps
        val opening = when (trigger) {
            is Trigger.Single -> if (trigger.gesture.held) return null else listOf(InputStep(trigger.gesture))
            is Trigger.Hold -> listOf(InputStep(trigger.gesture, minHoldMs = trigger.minDurationMs, maxHoldMs = trigger.maxDurationMs))
            is Trigger.InputMacro -> trigger.steps
            is Trigger.Repeat -> return null
        }
        if (opening.size >= steps.size) return null
        val sameStart = opening.indices.all { i -> stepsOverlap(opening[i], steps[i]) }
        return if (sameStart) steps[opening.size].withinMs else null
    }

    private fun stepsOverlap(a: InputStep, b: InputStep): Boolean =
        a.gesture == b.gesture && (!a.gesture.held || computeOverlap(a.minHoldMs, a.maxHoldMs, b.minHoldMs, b.maxHoldMs) != null)

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
