package com.heroesofthebrain.halo.rules

import com.heroesofthebrain.halo.detect.DetectorConfig
import com.heroesofthebrain.halo.detect.GestureInstant
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.eeg.EegFrame
import com.heroesofthebrain.halo.eeg.EegSource
import com.heroesofthebrain.halo.eeg.SourceStatus
import com.heroesofthebrain.halo.media.MediaCommand
import com.heroesofthebrain.halo.media.MediaOutput
import com.heroesofthebrain.halo.media.MediaResult
import com.heroesofthebrain.halo.service.ChannelState
import com.heroesofthebrain.halo.service.HaloPipeline
import com.heroesofthebrain.halo.service.PipelineState
import com.heroesofthebrain.halo.service.SourceRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuleRunnerTest {

    private class FakeSource : EegSource {
        override val label = "fake"
        override val status: StateFlow<SourceStatus> = MutableStateFlow(SourceStatus.Streaming("fake"))
        override fun frames(): Flow<EegFrame> = emptyFlow()
    }

    @Test
    fun `rule runner reloads profile in real time when activeProfileFlow emits`() = runTest {
        val initialProfile = BindingProfile.defaultProfile()
        val activeFlow = MutableStateFlow(initialProfile)

        val pipeline = HaloPipeline(
            scope = backgroundScope,
            configProvider = { DetectorConfig() },
            openSource = { FakeSource() },
        )

        var currentTime = 1000L
        val runner = RuleRunner(
            scope = backgroundScope,
            pipeline = pipeline,
            mediaOutput = { MediaResult.KeyDispatched(com.heroesofthebrain.halo.media.MediaKey.PLAY_PAUSE, likelyTarget = null) },
            profileProvider = { activeFlow.value },
            activeProfileFlow = activeFlow,
            clock = { currentTime },
        )

        runner.start()
        runCurrent()

        assertEquals(initialProfile, runner.engine.profile)

        // User rebinds specific action in active profile (Double Blink -> Next)
        val reboundProfile = initialProfile.copy(
            name = "Rebound Profile",
            bindings = listOf(
                Binding(
                    id = "double-blink-next",
                    name = "Next on double blink",
                    trigger = Trigger.Single(GestureType.DOUBLE_BLINK),
                    action = RuleAction.SingleCommand(MediaCommand.Next),
                    conditions = BindingConditions(onlyWhenArmed = false),
                )
            ),
            globalSettings = GlobalSettings(globalCooldownMs = 50L),
        )

        // Update active profile flow while runner is running
        activeFlow.value = reboundProfile
        runCurrent()

        assertEquals("Rebound Profile", runner.engine.profile.name)
        assertEquals(reboundProfile, runner.engine.profile)

        // Enable actions and verify gesture arrives after rebind fires Next
        runner.engine.updateContext(EngineContext(actionsAllowed = true))
        currentTime += 500L
        val effects = runner.engine.processEvent(GestureInstant(GestureType.DOUBLE_BLINK, confidence = 0.9f, timestampMs = currentTime))
        val commands = effects.filterIsInstance<EngineEffect.ExecuteCommand>().map { it.command }
        assertEquals(listOf(MediaCommand.Next), commands)
    }

    @Test
    fun `setProfile directly updates engine profile on active runner`() = runTest {
        val initialProfile = BindingProfile.defaultProfile()
        val pipeline = HaloPipeline(
            scope = backgroundScope,
            configProvider = { DetectorConfig() },
            openSource = { FakeSource() },
        )
        val runner = RuleRunner(
            scope = backgroundScope,
            pipeline = pipeline,
            mediaOutput = { MediaResult.Unsupported("test") },
            profileProvider = { initialProfile },
        )

        assertEquals(initialProfile, runner.engine.profile)

        val updated = initialProfile.copy(name = "Updated")
        runner.setProfile(updated)
        assertEquals("Updated", runner.engine.profile.name)
    }
}
