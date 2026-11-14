package com.heroesofthebrain.halo.rules

import com.heroesofthebrain.halo.detect.GestureType.CLENCH
import com.heroesofthebrain.halo.detect.GestureType.DOUBLE_BLINK
import com.heroesofthebrain.halo.detect.GestureType.TURN_LEFT
import com.heroesofthebrain.halo.detect.GestureType.TURN_RIGHT
import com.heroesofthebrain.halo.media.MediaCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputMacroAmbiguityTest {

    private fun binding(id: String, trigger: Trigger) = Binding(
        id = id,
        trigger = trigger,
        action = RuleAction.SingleCommand(MediaCommand.PlayPause),
    )

    private val blinkThenRight = binding(
        "blink-right",
        Trigger.InputMacro(listOf(InputStep(DOUBLE_BLINK), InputStep(TURN_RIGHT, withinMs = 3000L))),
    )

    private fun shadowsOf(bindings: List<Binding>) =
        AmbiguityResolver.analyze(bindings).filterIsInstance<AmbiguityWarning.ShadowedByInputMacro>()

    @Test
    fun `a single gesture that opens a macro waits for the macro's second window`() {
        val blink = binding("blink", Trigger.Single(DOUBLE_BLINK))
        val warnings = shadowsOf(listOf(blink, blinkThenRight))

        assertEquals(1, warnings.size)
        assertEquals("blink", warnings.first().shadowedBinding.id)
        assertEquals(3000L, warnings.first().extraWaitMs)
        assertEquals(3000L, AmbiguityResolver.maxShadowWaitMs(blink, listOf(blink, blinkThenRight)))
    }

    @Test
    fun `a hold is shadowed only by a macro whose clench step overlaps its range`() {
        val shortClench = binding("short", Trigger.Hold(CLENCH, minDurationMs = 300L, maxDurationMs = 800L))
        val longClench = binding("long", Trigger.Hold(CLENCH, minDurationMs = 1500L, maxDurationMs = 3000L))
        val clenchThenLeft = binding(
            "clench-left",
            Trigger.InputMacro(
                listOf(InputStep(CLENCH, minHoldMs = 500L, maxHoldMs = 1000L), InputStep(TURN_LEFT, withinMs = 1500L)),
            ),
        )

        val warnings = shadowsOf(listOf(shortClench, longClench, clenchThenLeft))

        assertEquals(listOf("short"), warnings.map { it.shadowedBinding.id })
        assertEquals(1500L, warnings.first().extraWaitMs)
    }

    @Test
    fun `a shorter macro is shadowed by a longer one with the same opening steps`() {
        val longer = binding(
            "longer",
            Trigger.InputMacro(
                listOf(InputStep(DOUBLE_BLINK), InputStep(TURN_RIGHT), InputStep(TURN_LEFT, withinMs = 2500L)),
            ),
        )
        val unrelated = binding("unrelated", Trigger.InputMacro(TURN_LEFT, TURN_RIGHT))

        val warnings = shadowsOf(listOf(blinkThenRight, longer, unrelated))

        assertEquals(1, warnings.size)
        assertEquals("blink-right", warnings.first().shadowedBinding.id)
        assertEquals("longer", warnings.first().macroBinding.id)
        assertEquals(2500L, warnings.first().extraWaitMs)
    }

    @Test
    fun `identical macros are reported as duplicates`() {
        val copy = blinkThenRight.copy(id = "copy")
        val warnings = AmbiguityResolver.analyze(listOf(blinkThenRight, copy))

        assertTrue(warnings.any { it is AmbiguityWarning.DuplicateTrigger })
    }
}
