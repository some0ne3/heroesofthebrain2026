package com.heroesofthebrain.halo.ui.editor

import com.heroesofthebrain.halo.calibration.HoldStats
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.media.MediaCommand
import com.heroesofthebrain.halo.rules.AmbiguityResolver
import com.heroesofthebrain.halo.rules.AmbiguityWarning
import com.heroesofthebrain.halo.rules.Binding
import com.heroesofthebrain.halo.rules.BindingProfile
import com.heroesofthebrain.halo.rules.BindingTiming
import com.heroesofthebrain.halo.rules.MacroStep
import com.heroesofthebrain.halo.rules.ProfileStore
import com.heroesofthebrain.halo.rules.RuleAction
import com.heroesofthebrain.halo.rules.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EditorLogicTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `building default profile from scratch matches shipped profile behavior`() {
        val shipped = BindingProfile.defaultProfile()

        // Build from scratch
        val builtFromScratch = BindingProfile(
            id = "default",
            name = "Default Profile",
            enabled = true,
            bindings = listOf(
                Binding(
                    id = "double-blink-play-pause",
                    name = "Play / Pause",
                    trigger = Trigger.Single(GestureType.DOUBLE_BLINK),
                    action = RuleAction.SingleCommand(MediaCommand.PlayPause),
                ),
                Binding(
                    id = "turn-right-next",
                    name = "Next Track",
                    trigger = Trigger.Single(GestureType.TURN_RIGHT),
                    action = RuleAction.SingleCommand(MediaCommand.Next),
                ),
                Binding(
                    id = "turn-left-prev",
                    name = "Previous Track",
                    trigger = Trigger.Single(GestureType.TURN_LEFT),
                    action = RuleAction.SingleCommand(MediaCommand.Previous),
                ),
                Binding(
                    id = "clench-volume-up",
                    name = "Volume Up",
                    trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 500L, maxDurationMs = 1200L),
                    action = RuleAction.SingleCommand(MediaCommand.VolumeUp),
                ),
                Binding(
                    id = "clench-volume-down",
                    name = "Volume Down",
                    trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 1500L, maxDurationMs = 3000L),
                    action = RuleAction.SingleCommand(MediaCommand.VolumeDown),
                ),
            ),
        )

        assertEquals(shipped.bindings.size, builtFromScratch.bindings.size)
        for (i in shipped.bindings.indices) {
            val s = shipped.bindings[i]
            val b = builtFromScratch.bindings[i]
            assertEquals(s.id, b.id)
            assertEquals(s.trigger, b.trigger)
            assertEquals(s.action, b.action)
        }

        // Shipped profile should have no ambiguity warnings
        val warnings = AmbiguityResolver.analyze(builtFromScratch.bindings)
        assertTrue("Shipped profile must not have conflicting ambiguity warnings", warnings.isEmpty())
    }

    @Test
    fun `conflict warnings detected for overlapping holds and shadowed bindings`() {
        val bindings = listOf(
            Binding(
                id = "single-blink",
                name = "Single Blink",
                trigger = Trigger.Single(GestureType.DOUBLE_BLINK),
                action = RuleAction.SingleCommand(MediaCommand.PlayPause),
            ),
            Binding(
                id = "repeat-blink",
                name = "Double Blink Repeat",
                trigger = Trigger.Repeat(GestureType.DOUBLE_BLINK, count = 2, maxGapMs = 700L),
                action = RuleAction.SingleCommand(MediaCommand.Next),
            ),
            Binding(
                id = "clench-1",
                name = "Clench Short",
                trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 400L, maxDurationMs = 1000L),
                action = RuleAction.SingleCommand(MediaCommand.VolumeUp),
            ),
            Binding(
                id = "clench-2",
                name = "Clench Medium Overlap",
                trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 800L, maxDurationMs = 1600L),
                action = RuleAction.SingleCommand(MediaCommand.VolumeDown),
            ),
        )

        val warnings = AmbiguityResolver.analyze(bindings)
        assertEquals(2, warnings.size)

        // Shadow warning
        val shadowWarning = warnings.filterIsInstance<AmbiguityWarning.ShadowedByRepeat>().firstOrNull()
        assertNotNull(shadowWarning)
        assertEquals(700L, shadowWarning!!.extraWaitMs)

        // Overlap warning
        val overlapWarning = warnings.filterIsInstance<AmbiguityWarning.OverlappingHoldRanges>().firstOrNull()
        assertNotNull(overlapWarning)
        assertEquals(800L, overlapWarning!!.overlapMinMs)
        assertEquals(1000L, overlapWarning.overlapMaxMs)
    }

    @Test
    fun `macro timeline builds sequential items accurately`() {
        val macroBinding = Binding(
            id = "skip-and-boost",
            name = "Skip & Boost Volume",
            trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 600L, maxDurationMs = 1500L),
            timing = BindingTiming(delayBeforeMs = 100L, cooldownAfterMs = 250L),
            action = RuleAction.Macro(
                MacroStep(MediaCommand.Next, delayAfterMs = 200L),
                MacroStep(MediaCommand.VolumeUp, delayAfterMs = 0L),
            ),
        )

        val timeline = buildTimelineItems(macroBinding, ambiguityWaitMs = 50L)

        // Expected items: Trigger, Ambiguity Wait, Pre-delay, Step 1, Delay, Step 2, Cooldown
        assertEquals(7, timeline.size)
        assertEquals(MacroItemType.TRIGGER, timeline[0].type)
        assertEquals(600L, timeline[0].durationMs)

        assertEquals(MacroItemType.PRE_DELAY, timeline[1].type)
        assertEquals(50L, timeline[1].durationMs) // Ambiguity wait

        assertEquals(MacroItemType.PRE_DELAY, timeline[2].type)
        assertEquals(100L, timeline[2].durationMs) // Pre-delay

        assertEquals(MacroItemType.COMMAND, timeline[3].type)
        assertEquals(MediaCommand.Next, timeline[3].command)

        assertEquals(MacroItemType.STEP_DELAY, timeline[4].type)
        assertEquals(200L, timeline[4].durationMs)

        assertEquals(MacroItemType.COMMAND, timeline[5].type)
        assertEquals(MediaCommand.VolumeUp, timeline[5].command)

        assertEquals(MacroItemType.COOLDOWN, timeline[6].type)
        assertEquals(250L, timeline[6].durationMs)
    }

    @Test
    fun `hold calibration stats provide valid duration ranges`() {
        val holds = listOf(930L, 980L, 1060L, 1540L, 560L)
        val stats = HoldStats.of(holds)
        assertNotNull(stats)
        assertEquals(560L, stats!!.minMs)
        assertEquals(980L, stats.medianMs)
        assertEquals(1540L, stats.maxMs)
    }

    @Test
    fun `profile store CRUD operations and active profile toggle work seamlessly`() {
        val store = ProfileStore(tempDir.newFolder("profiles"))

        val initial = store.profiles()
        assertEquals(1, initial.size)
        assertEquals("default", initial.first().id)

        // Duplicate
        val dup = store.duplicate("default", "Gym Workout")
        assertEquals("Gym Workout", dup.name)
        assertEquals(2, store.profiles().size)

        // Set active
        store.activeProfileId = dup.id
        assertEquals(dup.id, store.activeProfileId)
        assertEquals("Gym Workout", store.activeProfile().name)

        // Rename
        store.rename(dup.id, "Running Mode")
        assertEquals("Running Mode", store.profile(dup.id)?.name)

        // Export and Import
        val renamedProfile = store.profile(dup.id)!!
        val json = store.exportToJson(renamedProfile)
        assertTrue(json.contains("Running Mode"))

        val imported = store.importFromJson(json)
        assertNotNull(imported)
        assertTrue(store.profiles().size >= 3)

        // Delete
        store.delete(dup.id)
        assertEquals(null, store.profile(dup.id))
    }
}
