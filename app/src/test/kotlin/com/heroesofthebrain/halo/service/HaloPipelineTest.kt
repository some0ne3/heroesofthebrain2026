package com.heroesofthebrain.halo.service

import com.heroesofthebrain.halo.detect.Detector
import com.heroesofthebrain.halo.detect.DetectorConfig
import com.heroesofthebrain.halo.eeg.EEG_CHANNELS
import com.heroesofthebrain.halo.eeg.EegFrame
import com.heroesofthebrain.halo.eeg.EegSource
import com.heroesofthebrain.halo.eeg.ReplaySource
import com.heroesofthebrain.halo.eeg.SourceStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HaloPipelineTest {

    // Behaves like BrainAccessSource: the test drives status by hand, and cancelling the collector ends at Idle.
    private class FakeSource : EegSource {
        override val label = "fake"
        val statusFlow = MutableStateFlow<SourceStatus>(SourceStatus.Idle)
        override val status: StateFlow<SourceStatus> = statusFlow
        val input = Channel<EegFrame>(Channel.UNLIMITED)
        var failWith: Exception? = null
        var opened = 0

        override fun frames(): Flow<EegFrame> = flow {
            opened++
            try {
                for (f in input) {
                    failWith?.let { throw it }
                    emit(f)
                }
            } finally {
                if (statusFlow.value !is SourceStatus.Failed) statusFlow.value = SourceStatus.Idle
            }
        }
    }

    private fun frame(first: Long, n: Int = 25, dummy: Boolean = false) = EegFrame(
        firstSampleIndex = first,
        hostTimeNs = 0L,
        sampleRate = 250,
        sampleCount = n,
        eeg = FloatArray(n * EEG_CHANNELS),
        accel = null,
        gyro = FloatArray(n * 3),
        contact = BooleanArray(EEG_CHANNELS) { it != 1 },
        batteryPct = 81f,
        isDummy = BooleanArray(n) { dummy },
    )

    private fun <T> TestScope.record(flow: Flow<T>): MutableList<T> {
        val out = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(out) }
        return out
    }

    private fun TestScope.pipelineWith(source: FakeSource) = HaloPipeline(backgroundScope) { source }

    @Test
    fun `replay runs through the same path and every tap sees it`() = runTest {
        val pipeline = HaloPipeline(backgroundScope) { request ->
            val name = (request as SourceRequest.Replay).file
            ReplaySource(name, nanoTime = { testScheduler.currentTime * 1_000_000 }) {
                File("src/main/assets/replay/$name.csv").inputStream()
            }
        }
        val frames = record(pipeline.frames)
        val events = record(pipeline.events)

        pipeline.start(SourceRequest.Replay("szczeka-docisk"))
        assertTrue(pipeline.state.value.running)
        val end = pipeline.state.first { !it.running }

        assertEquals(SourceStatus.Finished, end.status)
        assertEquals(112, frames.size)
        // The pipeline must hand on exactly what the detector produces for these frames, nothing dropped or added.
        val detector = Detector()
        val expected = frames.flatMap { detector.process(it) }
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, events)
        assertTrue(end.batteryPct != null)
        assertEquals(true, end.hasGyro)
        assertEquals(SourceRequest.Replay("szczeka-docisk"), end.source)
    }

    @Test
    fun `actions are blocked while reconnecting and resume when the stream is back`() = runTest {
        val source = FakeSource()
        val pipeline = pipelineWith(source)
        val alerts = record(pipeline.linkAlerts)

        pipeline.start(SourceRequest.Headset)
        runCurrent()
        source.statusFlow.value = SourceStatus.Connecting("HALO 1")
        runCurrent()
        assertEquals(ActionBlock.NOT_CONNECTED, pipeline.state.value.actionBlock)

        source.statusFlow.value = SourceStatus.Streaming("HALO 1")
        runCurrent()
        assertEquals(ActionBlock.DISARMED, pipeline.state.value.actionBlock)
        pipeline.setArmed(true)
        assertTrue(pipeline.state.value.actionsAllowed)

        source.statusFlow.value = SourceStatus.Reconnecting("Headset disconnected")
        runCurrent()
        assertEquals(ActionBlock.RECONNECTING, pipeline.state.value.actionBlock)
        assertTrue("a link loss does not disarm", pipeline.state.value.armed)
        source.statusFlow.value = SourceStatus.Scanning
        runCurrent()
        source.statusFlow.value = SourceStatus.Connecting("HALO 1")
        runCurrent()
        assertEquals(ActionBlock.RECONNECTING, pipeline.state.value.actionBlock)

        source.statusFlow.value = SourceStatus.Streaming("HALO 1")
        runCurrent()
        assertTrue(pipeline.state.value.actionsAllowed)
        assertEquals(listOf(LinkAlert.LOST, LinkAlert.RECOVERED), alerts)

        pipeline.stop()
        runCurrent()
        val end = pipeline.state.value
        assertFalse(end.running)
        assertFalse(end.armed)
        assertEquals(SourceStatus.Idle, end.status)
        assertEquals("a user stop stays silent", listOf(LinkAlert.LOST, LinkAlert.RECOVERED), alerts)
    }

    @Test
    fun `frames update battery, contact and missed samples`() = runTest {
        val source = FakeSource()
        val pipeline = pipelineWith(source)
        val frames = record(pipeline.frames)

        pipeline.start(SourceRequest.Headset)
        source.statusFlow.value = SourceStatus.Streaming("HALO 1")
        source.input.send(frame(0))
        source.input.send(frame(25, dummy = true))
        runCurrent()

        val s = pipeline.state.value
        assertEquals(2, frames.size)
        assertEquals(81f, s.batteryPct)
        assertEquals(listOf(true, false, true, true), s.channels.map { it.contact })
        assertEquals(25L, s.missedSamples)
        assertEquals(true, s.hasGyro)
    }

    @Test
    fun `every run starts disarmed and arming is ignored while stopped`() = runTest {
        val source = FakeSource()
        val pipeline = pipelineWith(source)

        pipeline.setArmed(true)
        assertFalse(pipeline.state.value.armed)

        pipeline.start(SourceRequest.Headset)
        runCurrent()
        pipeline.toggleArmed()
        assertTrue(pipeline.state.value.armed)
        pipeline.stop()
        runCurrent()
        assertFalse(pipeline.state.value.armed)

        pipeline.start(SourceRequest.Headset)
        runCurrent()
        assertTrue(pipeline.state.value.running)
        assertFalse(pipeline.state.value.armed)
    }

    @Test
    fun `start while running is ignored`() = runTest {
        val source = FakeSource()
        var opened = 0
        val pipeline = HaloPipeline(backgroundScope) {
            opened++
            source
        }
        pipeline.start(SourceRequest.Headset)
        pipeline.start(SourceRequest.Replay("szczeka-docisk"))
        runCurrent()
        assertEquals(1, opened)
        assertEquals(1, source.opened)
        assertEquals(SourceRequest.Headset, pipeline.state.value.source)
    }

    @Test
    fun `a source crash ends the run as failed and counts as a lost link`() = runTest {
        val source = FakeSource()
        val pipeline = pipelineWith(source)
        val alerts = record(pipeline.linkAlerts)

        pipeline.start(SourceRequest.Headset)
        runCurrent()
        source.statusFlow.value = SourceStatus.Streaming("HALO 1")
        runCurrent()
        source.failWith = IllegalStateException("Unexpected channel layout")
        source.input.send(frame(0))
        runCurrent()

        val end = pipeline.state.value
        assertFalse(end.running)
        assertEquals(SourceStatus.Failed("Unexpected channel layout"), end.status)
        assertEquals(ActionBlock.NOT_RUNNING, end.actionBlock)
        assertEquals(listOf(LinkAlert.LOST), alerts)
    }

    @Test
    fun `fail only reports when nothing is running`() = runTest {
        val source = FakeSource()
        val pipeline = pipelineWith(source)
        pipeline.fail("no service")
        assertEquals(SourceStatus.Failed("no service"), pipeline.state.value.status)

        pipeline.start(SourceRequest.Headset)
        runCurrent()
        source.statusFlow.value = SourceStatus.Streaming("HALO 1")
        runCurrent()
        pipeline.fail("late")
        assertEquals(SourceStatus.Streaming("HALO 1"), pipeline.state.value.status)
    }

    @Test
    fun `pipeline queries configProvider when creating detector`() = runTest {
        val source = FakeSource()
        var queried = 0
        val customConfig = DetectorConfig(clenchOnRatio = 5.0)
        val pipeline = HaloPipeline(
            scope = backgroundScope,
            configProvider = {
                queried++
                customConfig
            },
        ) { source }
        pipeline.start(SourceRequest.Headset)
        runCurrent()
        source.statusFlow.value = SourceStatus.Streaming("HALO 1")
        source.input.send(frame(0))
        runCurrent()
        assertEquals(1, queried)
    }
}
