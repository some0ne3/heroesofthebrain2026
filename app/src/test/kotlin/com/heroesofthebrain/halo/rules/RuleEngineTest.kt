package com.heroesofthebrain.halo.rules

import com.heroesofthebrain.halo.detect.CancelReason
import com.heroesofthebrain.halo.detect.GestureCancel
import com.heroesofthebrain.halo.detect.GestureEnd
import com.heroesofthebrain.halo.detect.GestureInstant
import com.heroesofthebrain.halo.detect.GestureStart
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.media.MediaCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private fun testProfile(
        bindings: List<Binding>,
        globalSettings: GlobalSettings = GlobalSettings(
            armGesture = null,
            disarmGesture = null,
            autoDisarmTimeoutMs = null,
            globalCooldownMs = 0L,
            minimumConfidence = 0.3f,
        ),
    ): BindingProfile = BindingProfile(
        id = "test-profile",
        name = "Test Profile",
        enabled = true,
        createdAtMs = 0L,
        bindings = bindings,
        globalSettings = globalSettings,
    )

    @Test
    fun `single instant gesture executes media command`() {
        val binding = Binding(
            id = "blink-play",
            trigger = Trigger.Single(GestureType.DOUBLE_BLINK),
            action = RuleAction.SingleCommand(MediaCommand.PlayPause),
        )
        val engine = RuleEngine(
            profile = testProfile(listOf(binding)),
            initialContext = EngineContext(actionsAllowed = true, isArmed = true),
        )

        val effects = engine.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1000L, confidence = 0.8f))

        assertEquals(1, effects.size)
        val cmd = effects.first() as EngineEffect.ExecuteCommand
        assertEquals(MediaCommand.PlayPause, cmd.command)
        assertEquals("blink-play", cmd.binding?.id)
    }

    @Test
    fun `hold duration discriminates between short and long hold`() {
        val shortHold = Binding(
            id = "short-clench",
            trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 500L, maxDurationMs = 1200L),
            action = RuleAction.SingleCommand(MediaCommand.VolumeUp()),
        )
        val longHold = Binding(
            id = "long-clench",
            trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 1500L, maxDurationMs = 3000L),
            action = RuleAction.SingleCommand(MediaCommand.VolumeDown()),
        )
        val engine = RuleEngine(
            profile = testProfile(listOf(shortHold, longHold)),
            initialContext = EngineContext(actionsAllowed = true, isArmed = true),
        )

        // Short clench (800ms)
        engine.processEvent(GestureStart(GestureType.CLENCH, timestampMs = 1000L, confidence = 0.9f, onsetMs = 600L))
        val shortEffects = engine.processEvent(GestureEnd(GestureType.CLENCH, timestampMs = 1800L, confidence = 0.9f, durationMs = 800L))
        assertEquals(1, shortEffects.size)
        assertEquals(MediaCommand.VolumeUp(), (shortEffects.first() as EngineEffect.ExecuteCommand).command)

        // Long clench (2000ms)
        engine.processEvent(GestureStart(GestureType.CLENCH, timestampMs = 3000L, confidence = 0.9f, onsetMs = 2600L))
        val longEffects = engine.processEvent(GestureEnd(GestureType.CLENCH, timestampMs = 5000L, confidence = 0.9f, durationMs = 2000L))
        assertEquals(1, longEffects.size)
        assertEquals(MediaCommand.VolumeDown(), (longEffects.first() as EngineEffect.ExecuteCommand).command)
    }

    @Test
    fun `repeat while held fires periodically and stops on release`() {
        val repeatBinding = Binding(
            id = "hold-volup",
            trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 400L),
            action = RuleAction.RepeatWhileHeld(
                command = MediaCommand.VolumeUp(),
                intervalMs = 200L,
                initialDelayMs = 0L,
            ),
        )
        val engine = RuleEngine(
            profile = testProfile(listOf(repeatBinding)),
            initialContext = EngineContext(actionsAllowed = true, isArmed = true),
        )

        // Start clench at t=1000ms, onset=600ms. minDuration is 400ms.
        // First fire is scheduled at 1000 + 400 = 1400ms.
        engine.processEvent(GestureStart(GestureType.CLENCH, timestampMs = 1000L, confidence = 0.9f, onsetMs = 600L))

        // Advance to 1300ms: no fire yet
        val at1300 = engine.advanceTime(1300L)
        assertTrue(at1300.none { it is EngineEffect.ExecuteCommand })

        // Advance to 1400ms: first fire
        val at1400 = engine.advanceTime(1400L)
        assertEquals(1, at1400.filterIsInstance<EngineEffect.ExecuteCommand>().size)
        assertEquals(MediaCommand.VolumeUp(), at1400.filterIsInstance<EngineEffect.ExecuteCommand>().first().command)

        // Advance to 1600ms (+200ms interval): second fire
        val at1600 = engine.advanceTime(1600L)
        assertEquals(1, at1600.filterIsInstance<EngineEffect.ExecuteCommand>().size)

        // End clench at 1700ms
        engine.processEvent(GestureEnd(GestureType.CLENCH, timestampMs = 1700L, confidence = 0.9f, durationMs = 1100L))

        // Advance to 1800ms: should NOT fire anymore because hold ended
        val at1800 = engine.advanceTime(1800L)
        assertTrue(at1800.none { it is EngineEffect.ExecuteCommand })
    }

    @Test
    fun `repeat while held drops when actionsAllowed becomes false`() {
        val repeatBinding = Binding(
            id = "hold-volup",
            trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 400L),
            action = RuleAction.RepeatWhileHeld(command = MediaCommand.VolumeUp(), intervalMs = 200L),
        )
        val engine = RuleEngine(
            profile = testProfile(listOf(repeatBinding)),
            initialContext = EngineContext(actionsAllowed = true, isArmed = true),
        )

        engine.processEvent(GestureStart(GestureType.CLENCH, timestampMs = 1000L, confidence = 0.9f, onsetMs = 600L))
        engine.advanceTime(1400L) // first fire

        // Connection lost or disarmed: actionsAllowed becomes false
        engine.updateContext(EngineContext(actionsAllowed = false, isArmed = false))

        // Time advances to 1600ms: should not fire
        val at1600 = engine.advanceTime(1600L)
        assertTrue(at1600.none { it is EngineEffect.ExecuteCommand })
    }

    @Test
    fun `gesture cancel drops hold and repeats`() {
        val repeatBinding = Binding(
            id = "hold-volup",
            trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 400L),
            action = RuleAction.RepeatWhileHeld(command = MediaCommand.VolumeUp(), intervalMs = 200L),
        )
        val engine = RuleEngine(
            profile = testProfile(listOf(repeatBinding)),
            initialContext = EngineContext(actionsAllowed = true, isArmed = true),
        )

        engine.processEvent(GestureStart(GestureType.CLENCH, timestampMs = 1000L, confidence = 0.9f, onsetMs = 600L))
        engine.processEvent(GestureCancel(GestureType.CLENCH, timestampMs = 1200L, confidence = 0.9f, reason = CancelReason.HEAD_MOTION))

        val at1400 = engine.advanceTime(1400L)
        assertTrue(at1400.none { it is EngineEffect.ExecuteCommand })
    }

    @Test
    fun `repeat trigger matches on repeated gesture within max gap`() {
        val repeatTrigger = Binding(
            id = "double-turn-right",
            trigger = Trigger.Repeat(GestureType.TURN_RIGHT, count = 2, maxGapMs = 600L),
            action = RuleAction.SingleCommand(MediaCommand.Next),
        )
        val engine = RuleEngine(
            profile = testProfile(listOf(repeatTrigger)),
            initialContext = EngineContext(actionsAllowed = true, isArmed = true),
        )

        // First turn right at t=1000
        val eff1 = engine.processEvent(GestureInstant(GestureType.TURN_RIGHT, timestampMs = 1000L, confidence = 0.8f))
        assertTrue(eff1.none { it is EngineEffect.ExecuteCommand })

        // Second turn right at t=1400 (within 600ms gap)
        val eff2 = engine.processEvent(GestureInstant(GestureType.TURN_RIGHT, timestampMs = 1400L, confidence = 0.8f))
        assertEquals(1, eff2.filterIsInstance<EngineEffect.ExecuteCommand>().size)
        assertEquals(MediaCommand.Next, eff2.filterIsInstance<EngineEffect.ExecuteCommand>().first().command)
    }

    @Test
    fun `repeat trigger resets if gap exceeds maxGap`() {
        val repeatTrigger = Binding(
            id = "double-turn-right",
            trigger = Trigger.Repeat(GestureType.TURN_RIGHT, count = 2, maxGapMs = 600L),
            action = RuleAction.SingleCommand(MediaCommand.Next),
        )
        val engine = RuleEngine(
            profile = testProfile(listOf(repeatTrigger)),
            initialContext = EngineContext(actionsAllowed = true, isArmed = true),
        )

        // First turn right at t=1000
        engine.processEvent(GestureInstant(GestureType.TURN_RIGHT, timestampMs = 1000L, confidence = 0.8f))
        // Gap of 700ms (exceeds 600ms)
        engine.advanceTime(1700L)

        // Second turn right at t=1750: acts as first gesture of a new repeat
        val eff2 = engine.processEvent(GestureInstant(GestureType.TURN_RIGHT, timestampMs = 1750L, confidence = 0.8f))
        assertTrue(eff2.none { it is EngineEffect.ExecuteCommand })
    }

    @Test
    fun `input macro matches gestures in order`() {
        val macroBinding = Binding(
            id = "right-then-left",
            trigger = Trigger.InputMacro(GestureType.TURN_RIGHT, GestureType.TURN_LEFT, withinMs = 800L),
            action = RuleAction.SingleCommand(MediaCommand.Previous),
        )
        val engine = RuleEngine(
            profile = testProfile(listOf(macroBinding)),
            initialContext = EngineContext(actionsAllowed = true, isArmed = true),
        )

        engine.processEvent(GestureInstant(GestureType.TURN_RIGHT, timestampMs = 1000L, confidence = 0.8f))
        val eff2 = engine.processEvent(GestureInstant(GestureType.TURN_LEFT, timestampMs = 1500L, confidence = 0.8f))

        assertEquals(1, eff2.filterIsInstance<EngineEffect.ExecuteCommand>().size)
        assertEquals(MediaCommand.Previous, eff2.filterIsInstance<EngineEffect.ExecuteCommand>().first().command)
    }

    @Test
    fun `single gesture waits for an input macro starting with it and fires when the macro times out`() {
        val singleBinding = Binding(
            id = "single-right",
            name = "Single Right",
            trigger = Trigger.Single(GestureType.TURN_RIGHT),
            action = RuleAction.SingleCommand(MediaCommand.Next),
        )
        val macroBinding = Binding(
            id = "seq-right-left",
            name = "Seq Right Left",
            trigger = Trigger.InputMacro(GestureType.TURN_RIGHT, GestureType.TURN_LEFT, withinMs = 1000L),
            action = RuleAction.SingleCommand(MediaCommand.Previous),
        )
        val engine = RuleEngine(
            profile = testProfile(listOf(singleBinding, macroBinding)),
            initialContext = EngineContext(actionsAllowed = true, isArmed = true),
        )

        // Turn right arrives at t=1000. Single right is shadowed by the macro, whose next step has a 1000 ms window.
        val eff1 = engine.processEvent(GestureInstant(GestureType.TURN_RIGHT, timestampMs = 1000L, confidence = 0.8f))
        assertTrue("Single action must not fire immediately due to ambiguity", eff1.none { it is EngineEffect.ExecuteCommand })

        // At t=1800: still waiting
        val effAt1800 = engine.advanceTime(1800L)
        assertTrue(effAt1800.none { it is EngineEffect.ExecuteCommand })

        // At t=2001 (1001ms after first turn): macro timed out, single binding fires!
        val effAt2001 = engine.advanceTime(2001L)
        assertEquals(1, effAt2001.filterIsInstance<EngineEffect.ExecuteCommand>().size)
        assertEquals(MediaCommand.Next, effAt2001.filterIsInstance<EngineEffect.ExecuteCommand>().first().command)
    }

    @Test
    fun `single gesture is dropped when the input macro starting with it completes`() {
        val singleBinding = Binding(
            id = "single-right",
            trigger = Trigger.Single(GestureType.TURN_RIGHT),
            action = RuleAction.SingleCommand(MediaCommand.Next),
        )
        val macroBinding = Binding(
            id = "seq-right-left",
            trigger = Trigger.InputMacro(GestureType.TURN_RIGHT, GestureType.TURN_LEFT, withinMs = 1000L),
            action = RuleAction.SingleCommand(MediaCommand.Previous),
        )
        val engine = RuleEngine(
            profile = testProfile(listOf(singleBinding, macroBinding)),
            initialContext = EngineContext(actionsAllowed = true, isArmed = true),
        )

        // Turn right at t=1000
        engine.processEvent(GestureInstant(GestureType.TURN_RIGHT, timestampMs = 1000L, confidence = 0.8f))

        // Turn left at t=1400 (macro completes!)
        val eff2 = engine.processEvent(GestureInstant(GestureType.TURN_LEFT, timestampMs = 1400L, confidence = 0.8f))
        assertEquals(1, eff2.filterIsInstance<EngineEffect.ExecuteCommand>().size)
        assertEquals(MediaCommand.Previous, eff2.filterIsInstance<EngineEffect.ExecuteCommand>().first().command)

        // Later time advances: single action must NEVER fire
        val later = engine.advanceTime(3000L)
        assertTrue(later.none { it is EngineEffect.ExecuteCommand })
    }

    @Test
    fun `macro executes steps with delays`() {
        val macroBinding = Binding(
            id = "macro-test",
            trigger = Trigger.Single(GestureType.DOUBLE_BLINK),
            action = RuleAction.Macro(
                MacroStep(MediaCommand.Next, delayAfterMs = 200L),
                MacroStep(MediaCommand.Play, delayAfterMs = 0L),
            ),
        )
        val engine = RuleEngine(
            profile = testProfile(listOf(macroBinding)),
            initialContext = EngineContext(actionsAllowed = true, isArmed = true),
        )

        // Trigger macro at t=1000
        val eff1 = engine.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1000L, confidence = 0.8f))
        assertEquals(1, eff1.filterIsInstance<EngineEffect.ExecuteCommand>().size)
        assertEquals(MediaCommand.Next, eff1.filterIsInstance<EngineEffect.ExecuteCommand>().first().command)

        // At t=1100: step 2 delayAfterMs is 200ms, not ready yet
        val at1100 = engine.advanceTime(1100L)
        assertTrue(at1100.none { it is EngineEffect.ExecuteCommand })

        // At t=1200: step 2 executes
        val at1200 = engine.advanceTime(1200L)
        assertEquals(1, at1200.filterIsInstance<EngineEffect.ExecuteCommand>().size)
        assertEquals(MediaCommand.Play, at1200.filterIsInstance<EngineEffect.ExecuteCommand>().first().command)
    }

    @Test
    fun `delayBefore and cancelWindow cancel action if cancel gesture occurs`() {
        val binding = Binding(
            id = "delayed-action",
            trigger = Trigger.Single(GestureType.TURN_RIGHT),
            action = RuleAction.SingleCommand(MediaCommand.Next),
            timing = BindingTiming(
                delayBeforeMs = 500L,
                cancelWindowMs = 500L,
                cancelGesture = GestureType.TURN_LEFT,
            ),
        )
        val engine = RuleEngine(
            profile = testProfile(listOf(binding)),
            initialContext = EngineContext(actionsAllowed = true, isArmed = true),
        )

        // Trigger at t=1000
        val eff1 = engine.processEvent(GestureInstant(GestureType.TURN_RIGHT, timestampMs = 1000L, confidence = 0.8f))
        assertEquals(1, eff1.filterIsInstance<EngineEffect.ActionScheduled>().size)

        // Cancel gesture at t=1200 (within cancel window)
        val effCancel = engine.processEvent(GestureInstant(GestureType.TURN_LEFT, timestampMs = 1200L, confidence = 0.8f))
        assertEquals(1, effCancel.filterIsInstance<EngineEffect.ActionCancelled>().size)

        // Advance to t=1600: action must not execute
        val at1600 = engine.advanceTime(1600L)
        assertTrue(at1600.none { it is EngineEffect.ExecuteCommand })
    }

    @Test
    fun `conditions check armed status, playback state, and required channels`() {
        val armedOnlyBinding = Binding(
            id = "armed-only",
            trigger = Trigger.Single(GestureType.DOUBLE_BLINK),
            action = RuleAction.SingleCommand(MediaCommand.PlayPause),
            conditions = BindingConditions(onlyWhenArmed = true),
        )
        val playingOnlyBinding = Binding(
            id = "playing-only",
            trigger = Trigger.Single(GestureType.TURN_RIGHT),
            action = RuleAction.SingleCommand(MediaCommand.Next),
            conditions = BindingConditions(onlyWhenArmed = false, mediaStatus = MediaCondition.PLAYING_ONLY),
        )
        val channelBinding = Binding(
            id = "frontal-only",
            trigger = Trigger.Single(GestureType.TURN_LEFT),
            action = RuleAction.SingleCommand(MediaCommand.Previous),
            conditions = BindingConditions(
                onlyWhenArmed = false,
                requiredChannels = listOf("FP1", "FP2"),
            ),
        )

        val engine = RuleEngine(
            profile = testProfile(listOf(armedOnlyBinding, playingOnlyBinding, channelBinding)),
            initialContext = EngineContext(
                actionsAllowed = true,
                isArmed = false,
                isPlaying = false,
                goodChannels = setOf("T3", "T4"), // FP1/FP2 missing
            ),
        )

        // 1. Armed only binding fails when disarmed
        val eff1 = engine.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1000L, confidence = 0.8f))
        assertTrue(eff1.none { it is EngineEffect.ExecuteCommand })

        // 2. Playing only binding fails when paused
        val eff2 = engine.processEvent(GestureInstant(GestureType.TURN_RIGHT, timestampMs = 1100L, confidence = 0.8f))
        assertTrue(eff2.none { it is EngineEffect.ExecuteCommand })

        // 3. Channel binding fails when FP1/FP2 are not good
        val eff3 = engine.processEvent(GestureInstant(GestureType.TURN_LEFT, timestampMs = 1200L, confidence = 0.8f))
        assertTrue(eff3.none { it is EngineEffect.ExecuteCommand })

        // Now satisfy all conditions
        engine.updateContext(
            EngineContext(
                actionsAllowed = true,
                isArmed = true,
                isPlaying = true,
                goodChannels = setOf("FP1", "FP2", "T3", "T4"),
            )
        )

        val effArmed = engine.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 2000L, confidence = 0.8f))
        assertEquals(MediaCommand.PlayPause, (effArmed.first() as EngineEffect.ExecuteCommand).command)

        val effPlaying = engine.processEvent(GestureInstant(GestureType.TURN_RIGHT, timestampMs = 2100L, confidence = 0.8f))
        assertEquals(MediaCommand.Next, (effPlaying.first() as EngineEffect.ExecuteCommand).command)

        val effChannel = engine.processEvent(GestureInstant(GestureType.TURN_LEFT, timestampMs = 2200L, confidence = 0.8f))
        assertEquals(MediaCommand.Previous, (effChannel.first() as EngineEffect.ExecuteCommand).command)
    }

    @Test
    fun `global arm and disarm gestures change armed state`() {
        val settings = GlobalSettings(
            armGesture = GestureType.LONG_BLINK,
            disarmGesture = GestureType.TURN_UP,
            autoDisarmTimeoutMs = 5000L,
            globalCooldownMs = 0L,
            minimumConfidence = 0.3f,
        )
        val engine = RuleEngine(
            profile = testProfile(emptyList(), globalSettings = settings),
            initialContext = EngineContext(actionsAllowed = true, isArmed = false),
        )

        assertFalse(engine.armed)

        // Arm gesture
        val armEffects = engine.processEvent(GestureInstant(GestureType.LONG_BLINK, timestampMs = 1000L, confidence = 0.8f))
        assertTrue(engine.armed)
        assertEquals(1, armEffects.filterIsInstance<EngineEffect.ArmChanged>().size)
        assertTrue(armEffects.filterIsInstance<EngineEffect.ArmChanged>().first().armed)

        // Disarm gesture
        val disarmEffects = engine.processEvent(GestureInstant(GestureType.TURN_UP, timestampMs = 2000L, confidence = 0.8f))
        assertFalse(engine.armed)
        assertEquals(1, disarmEffects.filterIsInstance<EngineEffect.ArmChanged>().size)
        assertFalse(disarmEffects.filterIsInstance<EngineEffect.ArmChanged>().first().armed)

        // Re-arm
        engine.processEvent(GestureInstant(GestureType.LONG_BLINK, timestampMs = 3000L, confidence = 0.8f))
        assertTrue(engine.armed)

        // Auto-disarm after 5000ms inactivity (t=3000 + 5000 = 8000)
        val timeoutEffects = engine.advanceTime(8001L)
        assertFalse(engine.armed)
        assertEquals(1, timeoutEffects.filterIsInstance<EngineEffect.ArmChanged>().size)
    }

    @Test
    fun `play pause spacing prevents fast double tap from firing`() {
        val binding = Binding(
            id = "blink-play",
            trigger = Trigger.Single(GestureType.DOUBLE_BLINK),
            action = RuleAction.SingleCommand(MediaCommand.PlayPause),
        )
        val engine = RuleEngine(
            profile = testProfile(listOf(binding)),
            initialContext = EngineContext(actionsAllowed = true, isArmed = true),
        )

        // First Play/Pause at t=1000
        val eff1 = engine.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1000L, confidence = 0.8f))
        assertEquals(1, eff1.filterIsInstance<EngineEffect.ExecuteCommand>().size)

        // Second Play/Pause at t=1200 (200ms gap, less than 350ms Android double-tap guard)
        val eff2 = engine.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1200L, confidence = 0.8f))
        assertTrue("Play/Pause within 350ms must be dropped to prevent Android next-track trigger", eff2.none { it is EngineEffect.ExecuteCommand })

        // Third Play/Pause at t=1400 (400ms after first, >350ms)
        val eff3 = engine.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, timestampMs = 1400L, confidence = 0.8f))
        assertEquals(1, eff3.filterIsInstance<EngineEffect.ExecuteCommand>().size)
    }

    @Test
    fun `ambiguity analyzer accurately detects shadowed bindings and overlaps`() {
        val single = Binding(
            id = "single-r",
            name = "Right",
            trigger = Trigger.Single(GestureType.TURN_RIGHT),
            action = RuleAction.SingleCommand(MediaCommand.Next),
        )
        val seq = Binding(
            id = "seq-rl",
            name = "Right Left",
            trigger = Trigger.InputMacro(GestureType.TURN_RIGHT, GestureType.TURN_LEFT, withinMs = 900L),
            action = RuleAction.SingleCommand(MediaCommand.Previous),
        )
        val rep = Binding(
            id = "rep-r",
            name = "Double Right",
            trigger = Trigger.Repeat(GestureType.TURN_RIGHT, count = 2, maxGapMs = 500L),
            action = RuleAction.SingleCommand(MediaCommand.Play),
        )
        val holdA = Binding(
            id = "hold-a",
            name = "Short Clench",
            trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 500L, maxDurationMs = 1500L),
            action = RuleAction.SingleCommand(MediaCommand.VolumeUp()),
        )
        val holdB = Binding(
            id = "hold-b",
            name = "Long Clench",
            trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 1200L, maxDurationMs = 2500L),
            action = RuleAction.SingleCommand(MediaCommand.VolumeDown()),
        )

        val warnings = AmbiguityResolver.analyze(listOf(single, seq, rep, holdA, holdB))

        assertEquals(3, warnings.size)
        assertTrue(warnings.any { it is AmbiguityWarning.ShadowedByInputMacro && it.extraWaitMs == 900L })
        assertTrue(warnings.any { it is AmbiguityWarning.ShadowedByRepeat && it.extraWaitMs == 500L })
        assertTrue(warnings.any { it is AmbiguityWarning.OverlappingHoldRanges && it.overlapMinMs == 1200L && it.overlapMaxMs == 1500L })

        val maxWait = AmbiguityResolver.maxShadowWaitMs(single, listOf(single, seq, rep))
        assertEquals(900L, maxWait)
    }
}
