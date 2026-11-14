package com.heroesofthebrain.halo.ui.editor

import com.heroesofthebrain.halo.detect.CancelReason
import com.heroesofthebrain.halo.detect.GestureCancel
import com.heroesofthebrain.halo.detect.GestureEnd
import com.heroesofthebrain.halo.detect.GestureInstant
import com.heroesofthebrain.halo.detect.GestureStart
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.media.MediaCommand
import com.heroesofthebrain.halo.rules.Binding
import com.heroesofthebrain.halo.rules.BindingTiming
import com.heroesofthebrain.halo.rules.InputStep
import com.heroesofthebrain.halo.rules.RuleAction
import com.heroesofthebrain.halo.rules.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TryItTesterTest {

    @Test
    fun `single gesture matches on instant event`() {
        val binding = Binding(
            id = "test-single",
            name = "Test Single",
            trigger = Trigger.Single(GestureType.DOUBLE_BLINK),
            action = RuleAction.SingleCommand(MediaCommand.PlayPause),
            timing = BindingTiming(delayBeforeMs = 100L, cooldownAfterMs = 200L),
        )

        val tester = TryItTester(binding, ambiguityWaitMs = 50L)
        assertEquals(TryItStatus.LISTENING, tester.session.status)

        // Event for different gesture: should be ignored
        tester.processEvent(GestureInstant(GestureType.TURN_RIGHT, timestampMs = 1000L, confidence = 0.9f))
        assertEquals(TryItStatus.LISTENING, tester.session.status)

        // Matching event
        val session = tester.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1500L, confidence = 0.95f))
        assertEquals(TryItStatus.MATCHED, session.status)
        assertEquals(1, session.history.size)
        assertEquals(TryItStatus.MATCHED, session.history.first().status)

        val breakdown = session.activeBreakdown
        assertNotNull(breakdown)
        assertEquals(0L, breakdown!!.triggerDurationMs)
        assertEquals(50L, breakdown.ambiguityWaitMs)
        assertEquals(100L, breakdown.delayBeforeMs)
        assertEquals(200L, breakdown.cooldownAfterMs)
        assertEquals(350L, breakdown.totalMs) // 0 + 50 + 100 + 200
    }

    @Test
    fun `hold gesture matches when duration is within min and max`() {
        val binding = Binding(
            id = "test-hold",
            name = "Test Hold",
            trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 500L, maxDurationMs = 1200L),
            action = RuleAction.SingleCommand(MediaCommand.VolumeUp),
        )

        val tester = TryItTester(binding)

        // 1. Start holding
        tester.processEvent(GestureStart(GestureType.CLENCH, timestampMs = 1400L, confidence = 0.8f, onsetMs = 1000L))
        assertEquals(TryItStatus.HOLDING, tester.session.status)

        // 2. Tick progress
        tester.tick(currentTimeMs = 1600L)
        assertEquals(600L, tester.session.currentHoldElapsedMs) // 1600 - 1000

        // 3. End with 800ms hold -> In range!
        val session = tester.processEvent(GestureEnd(GestureType.CLENCH, timestampMs = 1800L, confidence = 0.8f, durationMs = 800L))
        assertEquals(TryItStatus.MATCHED, session.status)
        assertEquals(800L, session.currentHoldElapsedMs)
        assertEquals(1, session.history.size)
        assertEquals(800L, session.history.first().durationMs)
    }

    @Test
    fun `hold gesture fails when duration is too short or too long`() {
        val binding = Binding(
            id = "test-hold",
            trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 500L, maxDurationMs = 1000L),
            action = RuleAction.SingleCommand(MediaCommand.VolumeUp),
        )

        val tester = TryItTester(binding)

        // Too short (< 500ms)
        tester.processEvent(GestureStart(GestureType.CLENCH, timestampMs = 1400L, confidence = 0.8f, onsetMs = 1000L))
        tester.processEvent(GestureEnd(GestureType.CLENCH, timestampMs = 1300L, confidence = 0.8f, durationMs = 300L))
        assertEquals(TryItStatus.TOO_SHORT, tester.session.status)
        assertNull(tester.session.activeBreakdown)

        // Too long (> 1000ms)
        tester.processEvent(GestureStart(GestureType.CLENCH, timestampMs = 2400L, confidence = 0.8f, onsetMs = 2000L))
        tester.processEvent(GestureEnd(GestureType.CLENCH, timestampMs = 3500L, confidence = 0.8f, durationMs = 1500L))
        assertEquals(TryItStatus.TOO_LONG, tester.session.status)
    }

    @Test
    fun `hold gesture reports cancellation on head movement`() {
        val binding = Binding(
            id = "test-hold",
            trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 500L, maxDurationMs = 1500L),
            action = RuleAction.SingleCommand(MediaCommand.VolumeUp),
        )

        val tester = TryItTester(binding)
        tester.processEvent(GestureStart(GestureType.CLENCH, timestampMs = 1400L, confidence = 0.8f, onsetMs = 1000L))
        val session = tester.processEvent(GestureCancel(GestureType.CLENCH, timestampMs = 1600L, confidence = 0.8f, reason = CancelReason.HEAD_MOTION))

        assertEquals(TryItStatus.CANCELLED, session.status)
        assertEquals(CancelReason.HEAD_MOTION, session.history.first().cancelReason)
    }

    @Test
    fun `repeat gesture matches when count reached within maxGap`() {
        val binding = Binding(
            id = "test-repeat",
            trigger = Trigger.Repeat(GestureType.DOUBLE_BLINK, count = 2, maxGapMs = 800L),
            action = RuleAction.SingleCommand(MediaCommand.Next),
        )

        val tester = TryItTester(binding)

        // First blink at t=1000
        tester.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1000L, confidence = 0.9f))
        assertEquals(1, tester.session.repeatCountSoFar)
        assertEquals(TryItStatus.LISTENING, tester.session.status)

        // Second blink at t=1400 (gap 400ms <= 800ms) -> Match!
        val session = tester.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1400L, confidence = 0.9f))
        assertEquals(TryItStatus.MATCHED, session.status)
    }

    @Test
    fun `input macro matches step by step`() {
        val binding = Binding(
            id = "test-seq",
            trigger = Trigger.InputMacro(GestureType.TURN_LEFT, GestureType.TURN_RIGHT, withinMs = 1000L),
            action = RuleAction.SingleCommand(MediaCommand.Previous),
        )

        val tester = TryItTester(binding)

        // Step 1: Turn Left at t=1000
        tester.processEvent(GestureInstant(GestureType.TURN_LEFT, timestampMs = 1000L, confidence = 0.9f))
        assertEquals(1, tester.session.macroStepIndex)
        assertEquals(TryItStatus.LISTENING, tester.session.status)

        // Step 2: Turn Right at t=1600 (gap 600ms <= 1000ms) -> Match!
        val session = tester.processEvent(GestureInstant(GestureType.TURN_RIGHT, timestampMs = 1600L, confidence = 0.9f))
        assertEquals(TryItStatus.MATCHED, session.status)
    }

    private val blinkThenClench = Binding(
        id = "test-macro",
        trigger = Trigger.InputMacro(
            listOf(
                InputStep(GestureType.DOUBLE_BLINK),
                InputStep(GestureType.CLENCH, withinMs = 2000L, minHoldMs = 500L, maxHoldMs = 1500L),
            ),
        ),
        action = RuleAction.SingleCommand(MediaCommand.Mute),
    )

    @Test
    fun `input macro clench step matches when held within its range`() {
        val tester = TryItTester(blinkThenClench)

        tester.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1000L, confidence = 0.9f), timestampMs = 1000L)
        tester.processEvent(GestureStart(GestureType.CLENCH, timestampMs = 1500L, confidence = 0.9f, onsetMs = 1300L), timestampMs = 1500L)
        assertEquals(TryItStatus.HOLDING, tester.session.status)

        val session = tester.processEvent(GestureEnd(GestureType.CLENCH, timestampMs = 2400L, confidence = 0.9f, durationMs = 1100L), timestampMs = 2400L)
        assertEquals(TryItStatus.MATCHED, session.status)
        assertEquals(1400L, session.activeBreakdown!!.triggerDurationMs)
    }

    @Test
    fun `input macro clench step held too briefly starts over`() {
        val tester = TryItTester(blinkThenClench)

        tester.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1000L, confidence = 0.9f), timestampMs = 1000L)
        tester.processEvent(GestureStart(GestureType.CLENCH, timestampMs = 1500L, confidence = 0.9f, onsetMs = 1300L), timestampMs = 1500L)
        val session = tester.processEvent(GestureEnd(GestureType.CLENCH, timestampMs = 1700L, confidence = 0.9f, durationMs = 400L), timestampMs = 1700L)

        assertEquals(TryItStatus.TOO_SHORT, session.status)
        assertEquals(0, session.macroStepIndex)
    }

    @Test
    fun `input macro step window runs out on the tick`() {
        val tester = TryItTester(blinkThenClench)

        tester.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1000L, confidence = 0.9f), timestampMs = 1000L)
        tester.tick(2500L)
        assertEquals(500L, tester.session.macroRemainingMs)

        tester.tick(3001L)
        assertEquals(TryItStatus.TIMED_OUT, tester.session.status)
        assertEquals(0, tester.session.macroStepIndex)
    }

    @Test
    fun `input macro wrong gesture starts over and can open a new attempt`() {
        val binding = Binding(
            id = "blink-right",
            trigger = Trigger.InputMacro(GestureType.DOUBLE_BLINK, GestureType.TURN_RIGHT, withinMs = 2000L),
            action = RuleAction.SingleCommand(MediaCommand.Next),
        )
        val tester = TryItTester(binding)

        tester.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1000L, confidence = 0.9f), timestampMs = 1000L)
        tester.processEvent(GestureInstant(GestureType.TURN_LEFT, timestampMs = 1200L, confidence = 0.9f), timestampMs = 1200L)
        assertEquals(TryItStatus.WRONG_STEP, tester.session.status)
        assertEquals(0, tester.session.macroStepIndex)

        tester.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1400L, confidence = 0.9f), timestampMs = 1400L)
        tester.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1600L, confidence = 0.9f), timestampMs = 1600L)
        assertEquals(TryItStatus.WRONG_STEP, tester.session.history.first().status)
        assertEquals(TryItStatus.LISTENING, tester.session.status)
        assertEquals(1, tester.session.macroStepIndex)

        val session = tester.processEvent(GestureInstant(GestureType.TURN_RIGHT, timestampMs = 2000L, confidence = 0.9f), timestampMs = 2000L)
        assertEquals(TryItStatus.MATCHED, session.status)
    }
}
