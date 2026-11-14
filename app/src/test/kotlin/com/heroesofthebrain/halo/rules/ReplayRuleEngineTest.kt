package com.heroesofthebrain.halo.rules

import com.heroesofthebrain.halo.detect.Detector
import com.heroesofthebrain.halo.detect.GestureEvent
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.eeg.ReplaySource
import com.heroesofthebrain.halo.media.MediaCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayRuleEngineTest {

    private fun openReplay(name: String): InputStream {
        val direct = File("src/main/assets/replay/$name.csv")
        if (direct.exists()) return direct.inputStream()
        val appPrefixed = File("app/src/main/assets/replay/$name.csv")
        if (appPrefixed.exists()) return appPrefixed.inputStream()
        throw IllegalArgumentException("Could not find replay file for $name")
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun runReplay(
        recordingName: String,
        profileJson: String,
    ): List<EngineEffect.ExecuteCommand> {
        val profile = json.decodeFromString(BindingProfile.serializer(), profileJson)
        val engine = RuleEngine(
            profile = profile,
            initialContext = EngineContext(actionsAllowed = true, isArmed = true),
        )
        val executedCommands = mutableListOf<EngineEffect.ExecuteCommand>()

        runTest {
            val source = ReplaySource(recordingName, nanoTime = { testScheduler.currentTime * 1_000_000 }) {
                openReplay(recordingName)
            }
            val detector = Detector()
            val start = testScheduler.currentTime

            source.frames().collect { frame ->
                val timeMs = testScheduler.currentTime - start
                val events = detector.process(frame)
                for (event in events) {
                    val effects = engine.processEvent(event, timeMs)
                    executedCommands += effects.filterIsInstance<EngineEffect.ExecuteCommand>()
                }
                val tickEffects = engine.advanceTime(timeMs)
                executedCommands += tickEffects.filterIsInstance<EngineEffect.ExecuteCommand>()
            }
        }
        return executedCommands
    }

    @Test
    fun `clench replay triggers expected volume actions from json profile`() {
        val profile = BindingProfile(
            id = "clench-profile",
            name = "Clench Media Remote",
            enabled = true,
            bindings = listOf(
                Binding(
                    id = "short-clench-volup",
                    name = "Short Clench -> Vol Up",
                    trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 500L, maxDurationMs = 1250L),
                    action = RuleAction.SingleCommand(MediaCommand.VolumeUp),
                ),
                Binding(
                    id = "long-clench-voldown",
                    name = "Long Clench -> Vol Down",
                    trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 1350L, maxDurationMs = 3000L),
                    action = RuleAction.SingleCommand(MediaCommand.VolumeDown),
                ),
            ),
            globalSettings = GlobalSettings(
                globalCooldownMs = 50L,
                minimumConfidence = 0.3f,
            ),
        )
        val profileJson = json.encodeToString(BindingProfile.serializer(), profile)

        // szczeka-docisk has 5 clenches:
        // 4 holds of ~0.56s to ~1.06s, and 1 longer hold of ~1.54s
        val commands = runReplay("szczeka-docisk", profileJson)

        println("Executed from szczeka-docisk: " + commands.map { it.command })

        // Check that commands were fired
        assertTrue("Expected actions from clenches", commands.isNotEmpty())

        val volUpCount = commands.count { it.command == MediaCommand.VolumeUp }
        val volDownCount = commands.count { it.command == MediaCommand.VolumeDown }

        // The recording has 4 short clenches and 1 long clench
        assertTrue("Expected at least 3 VolumeUp actions from short clenches, got $volUpCount", volUpCount >= 3)
        assertEquals("Expected exactly 1 VolumeDown action from the long clench", 1, volDownCount)
    }

    @Test
    fun `double blink replay triggers play pause from json profile`() {
        val profile = BindingProfile.defaultProfile()
        val profileJson = json.encodeToString(BindingProfile.serializer(), profile)

        // podw-mrug has 4 double blinks
        val commands = runReplay("podw-mrug", profileJson)

        println("Executed from podw-mrug: " + commands.map { it.command })

        val playPauseCount = commands.count { it.command == MediaCommand.PlayPause }
        assertEquals(4, playPauseCount)
    }

    @Test
    fun `head turn replay triggers track navigation from json profile`() {
        val profile = BindingProfile(
            id = "turn-profile",
            name = "Head Turns",
            bindings = listOf(
                Binding(
                    id = "turn-right-next",
                    trigger = Trigger.Single(GestureType.TURN_RIGHT),
                    action = RuleAction.SingleCommand(MediaCommand.Next),
                ),
                Binding(
                    id = "turn-left-prev",
                    trigger = Trigger.Single(GestureType.TURN_LEFT),
                    action = RuleAction.SingleCommand(MediaCommand.Previous),
                ),
            ),
            globalSettings = GlobalSettings(
                globalCooldownMs = 100L,
                minimumConfidence = 0.3f,
            ),
        )
        val profileJson = json.encodeToString(BindingProfile.serializer(), profile)

        // obrot-glowa-poziomo has alternating left and right turns
        val commands = runReplay("obrot-glowa-poziomo", profileJson)

        println("Executed from obrot-glowa-poziomo: " + commands.map { it.command })

        val nextCount = commands.count { it.command == MediaCommand.Next }
        val prevCount = commands.count { it.command == MediaCommand.Previous }

        assertTrue("Expected Next actions from right turns, got $nextCount", nextCount > 0)
        assertTrue("Expected Previous actions from left turns, got $prevCount", prevCount > 0)
    }
}
