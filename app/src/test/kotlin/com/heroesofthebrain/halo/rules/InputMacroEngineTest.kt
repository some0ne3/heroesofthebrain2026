package com.heroesofthebrain.halo.rules

import com.heroesofthebrain.halo.detect.CancelReason
import com.heroesofthebrain.halo.detect.GestureCancel
import com.heroesofthebrain.halo.detect.GestureEnd
import com.heroesofthebrain.halo.detect.GestureInstant
import com.heroesofthebrain.halo.detect.GestureStart
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.detect.GestureType.CLENCH
import com.heroesofthebrain.halo.detect.GestureType.DOUBLE_BLINK
import com.heroesofthebrain.halo.detect.GestureType.TURN_LEFT
import com.heroesofthebrain.halo.detect.GestureType.TURN_RIGHT
import com.heroesofthebrain.halo.media.MediaCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputMacroEngineTest {

    private val playPause = single("blink", DOUBLE_BLINK, MediaCommand.PlayPause)
    private val next = single("right", TURN_RIGHT, MediaCommand.Next)
    private val previous = single("left", TURN_LEFT, MediaCommand.Previous)
    private val blinkThenRight = macro(
        "blink-right",
        MediaCommand.VolumeUp,
        InputStep(DOUBLE_BLINK),
        InputStep(TURN_RIGHT, withinMs = 3000L),
    )

    private fun single(id: String, gesture: GestureType, command: MediaCommand) = Binding(
        id = id,
        trigger = Trigger.Single(gesture),
        action = RuleAction.SingleCommand(command),
    )

    private fun macro(id: String, command: MediaCommand, vararg steps: InputStep) = Binding(
        id = id,
        trigger = Trigger.InputMacro(steps.toList()),
        action = RuleAction.SingleCommand(command),
    )

    private fun hold(id: String, minMs: Long, maxMs: Long?, command: MediaCommand) = Binding(
        id = id,
        trigger = Trigger.Hold(CLENCH, minDurationMs = minMs, maxDurationMs = maxMs),
        action = RuleAction.SingleCommand(command),
    )

    private fun engine(vararg bindings: Binding, armed: Boolean = true) = RuleEngine(
        profile = BindingProfile(
            id = "test",
            name = "Test",
            createdAtMs = 0L,
            bindings = bindings.toList(),
            globalSettings = GlobalSettings(
                armGesture = null,
                autoDisarmTimeoutMs = null,
                globalCooldownMs = 0L,
                minimumConfidence = 0.3f,
            ),
        ),
        initialContext = EngineContext(actionsAllowed = true, isArmed = armed),
        clock = { 0L },
    )

    private fun RuleEngine.instant(gesture: GestureType, atMs: Long) =
        commands(processEvent(GestureInstant(gesture, timestampMs = atMs, confidence = 0.9f)))

    private fun RuleEngine.clenchStart(atMs: Long) =
        commands(processEvent(GestureStart(CLENCH, timestampMs = atMs, confidence = 0.9f, onsetMs = atMs - 200L)))

    private fun RuleEngine.clenchEnd(atMs: Long, durationMs: Long) =
        commands(processEvent(GestureEnd(CLENCH, timestampMs = atMs, confidence = 0.9f, durationMs = durationMs)))

    private fun RuleEngine.at(nowMs: Long) = commands(advanceTime(nowMs))

    private fun commands(effects: List<EngineEffect>) =
        effects.filterIsInstance<EngineEffect.ExecuteCommand>().map { it.command }

    @Test
    fun `head turn inside the window after a double blink runs the macro instead of either single`() {
        val engine = engine(playPause, next, blinkThenRight)

        assertEquals(emptyList<MediaCommand>(), engine.instant(DOUBLE_BLINK, 1000L))
        assertEquals(listOf(MediaCommand.VolumeUp), engine.instant(TURN_RIGHT, 3500L))
        assertEquals(emptyList<MediaCommand>(), engine.at(10_000L))
    }

    @Test
    fun `once the window closes the blink runs its own binding and the turn is a plain turn`() {
        val engine = engine(playPause, next, blinkThenRight)

        engine.instant(DOUBLE_BLINK, 1000L)
        assertEquals(emptyList<MediaCommand>(), engine.at(4000L))
        assertEquals(listOf(MediaCommand.PlayPause), engine.at(4001L))
        assertEquals(listOf(MediaCommand.Next), engine.instant(TURN_RIGHT, 5000L))
    }

    @Test
    fun `every step has its own window`() {
        val threeSteps = macro(
            "three",
            MediaCommand.Mute,
            InputStep(DOUBLE_BLINK),
            InputStep(TURN_RIGHT, withinMs = 500L),
            InputStep(TURN_LEFT, withinMs = 4000L),
        )

        val inTime = engine(threeSteps)
        inTime.instant(DOUBLE_BLINK, 1000L)
        inTime.instant(TURN_RIGHT, 1400L)
        assertEquals(listOf(MediaCommand.Mute), inTime.instant(TURN_LEFT, 5300L))

        val secondStepLate = engine(threeSteps)
        secondStepLate.instant(DOUBLE_BLINK, 1000L)
        secondStepLate.instant(TURN_RIGHT, 1600L)
        assertEquals(emptyList<MediaCommand>(), secondStepLate.instant(TURN_LEFT, 2000L))
    }

    @Test
    fun `a wrong gesture aborts the macro and the waiting single fires right away`() {
        val engine = engine(playPause, previous, blinkThenRight)

        engine.instant(DOUBLE_BLINK, 1000L)
        assertEquals(listOf(MediaCommand.PlayPause, MediaCommand.Previous), engine.instant(TURN_LEFT, 1500L))
        assertEquals(emptyList<MediaCommand>(), engine.at(10_000L))
    }

    @Test
    fun `macros sharing a first step are told apart by the next one`() {
        val blinkThenLeft = macro("blink-left", MediaCommand.Mute, InputStep(DOUBLE_BLINK), InputStep(TURN_LEFT))
        val engine = engine(blinkThenRight, blinkThenLeft)

        engine.instant(DOUBLE_BLINK, 1000L)
        assertEquals(listOf(MediaCommand.Mute), engine.instant(TURN_LEFT, 1500L))
    }

    @Test
    fun `a shorter macro waits while a longer one with the same opening can still go on`() {
        val short = macro("short", MediaCommand.Next, InputStep(DOUBLE_BLINK), InputStep(TURN_RIGHT))
        val long = macro(
            "long",
            MediaCommand.Previous,
            InputStep(DOUBLE_BLINK),
            InputStep(TURN_RIGHT),
            InputStep(TURN_LEFT, withinMs = 2000L),
        )

        val completesLong = engine(short, long)
        completesLong.instant(DOUBLE_BLINK, 1000L)
        assertEquals(emptyList<MediaCommand>(), completesLong.instant(TURN_RIGHT, 1500L))
        assertEquals(listOf(MediaCommand.Previous), completesLong.instant(TURN_LEFT, 2000L))
        assertEquals(emptyList<MediaCommand>(), completesLong.at(10_000L))

        val stopsAtShort = engine(short, long)
        stopsAtShort.instant(DOUBLE_BLINK, 1000L)
        stopsAtShort.instant(TURN_RIGHT, 1500L)
        assertEquals(emptyList<MediaCommand>(), stopsAtShort.at(3500L))
        assertEquals(listOf(MediaCommand.Next), stopsAtShort.at(3501L))

        val brokenOff = engine(short, long)
        brokenOff.instant(DOUBLE_BLINK, 1000L)
        brokenOff.instant(TURN_RIGHT, 1500L)
        assertEquals(listOf(MediaCommand.Next), brokenOff.instant(DOUBLE_BLINK, 2000L))
    }

    @Test
    fun `a clench step counts when held within its range and keeps the clench bindings quiet`() {
        val blinkThenClench = macro(
            "blink-clench",
            MediaCommand.VolumeDown,
            InputStep(DOUBLE_BLINK),
            InputStep(CLENCH, withinMs = 3000L, minHoldMs = 500L, maxHoldMs = 1500L),
        )
        val clenchVolume = hold("clench", 500L, 1200L, MediaCommand.VolumeUp)
        val clenchRepeat = Binding(
            id = "clench-repeat",
            trigger = Trigger.Hold(CLENCH, minDurationMs = 300L),
            action = RuleAction.RepeatWhileHeld(MediaCommand.Next, intervalMs = 200L),
        )
        val engine = engine(blinkThenClench, clenchVolume, clenchRepeat)

        engine.instant(DOUBLE_BLINK, 1000L)
        engine.clenchStart(2000L)
        assertEquals(emptyList<MediaCommand>(), engine.at(2600L))
        assertEquals(listOf(MediaCommand.VolumeDown), engine.clenchEnd(2800L, durationMs = 900L))
    }

    @Test
    fun `a clench step held too briefly fails the macro`() {
        val blinkThenClench = macro(
            "blink-clench",
            MediaCommand.VolumeDown,
            InputStep(DOUBLE_BLINK),
            InputStep(CLENCH, minHoldMs = 500L, maxHoldMs = 1500L),
        )
        val engine = engine(playPause, blinkThenClench)

        engine.instant(DOUBLE_BLINK, 1000L)
        engine.clenchStart(2000L)
        assertEquals(listOf(MediaCommand.PlayPause), engine.clenchEnd(2300L, durationMs = 300L))
    }

    @Test
    fun `a clench past the step maximum fails before the clench ends`() {
        val blinkThenClench = macro(
            "blink-clench",
            MediaCommand.VolumeDown,
            InputStep(DOUBLE_BLINK),
            InputStep(CLENCH, minHoldMs = 500L, maxHoldMs = 1500L),
        )
        val engine = engine(playPause, blinkThenClench)

        engine.instant(DOUBLE_BLINK, 1000L)
        engine.clenchStart(2000L)
        assertEquals(emptyList<MediaCommand>(), engine.at(3400L))
        assertEquals(listOf(MediaCommand.PlayPause), engine.at(3600L))
    }

    @Test
    fun `a cancelled clench fails the step`() {
        val blinkThenClench = macro(
            "blink-clench",
            MediaCommand.VolumeDown,
            InputStep(DOUBLE_BLINK),
            InputStep(CLENCH, minHoldMs = 500L),
        )
        val engine = engine(playPause, blinkThenClench)

        engine.instant(DOUBLE_BLINK, 1000L)
        engine.clenchStart(2000L)
        val effects = engine.processEvent(
            GestureCancel(CLENCH, timestampMs = 2200L, confidence = 0.9f, reason = CancelReason.HEAD_MOTION),
        )
        assertEquals(listOf(MediaCommand.PlayPause), commands(effects))
    }

    @Test
    fun `a clench that opens a macro holds back the matching hold binding`() {
        val clenchThenRight = macro(
            "clench-right",
            MediaCommand.Next,
            InputStep(CLENCH, minHoldMs = 500L, maxHoldMs = 1200L),
            InputStep(TURN_RIGHT, withinMs = 2000L),
        )
        val clenchVolume = hold("clench", 500L, 1200L, MediaCommand.VolumeUp)

        val completed = engine(clenchThenRight, clenchVolume)
        completed.clenchStart(1000L)
        assertEquals(emptyList<MediaCommand>(), completed.clenchEnd(1800L, durationMs = 900L))
        assertEquals(listOf(MediaCommand.Next), completed.instant(TURN_RIGHT, 2500L))

        val abandoned = engine(clenchThenRight, clenchVolume)
        abandoned.clenchStart(1000L)
        abandoned.clenchEnd(1800L, durationMs = 900L)
        assertEquals(listOf(MediaCommand.VolumeUp), abandoned.at(3801L))
    }

    @Test
    fun `a macro only starts while its conditions hold`() {
        val engine = engine(blinkThenRight, armed = false)

        engine.instant(DOUBLE_BLINK, 1000L)
        assertTrue(engine.instant(TURN_RIGHT, 1500L).isEmpty())
    }
}
