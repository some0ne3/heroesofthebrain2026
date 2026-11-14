package com.heroesofthebrain.halo.detect

import com.heroesofthebrain.halo.detect.GestureType.CLENCH
import com.heroesofthebrain.halo.detect.GestureType.DOUBLE_BLINK
import com.heroesofthebrain.halo.detect.GestureType.LONG_BLINK
import com.heroesofthebrain.halo.detect.GestureType.TURN_DOWN
import com.heroesofthebrain.halo.detect.GestureType.TURN_LEFT
import com.heroesofthebrain.halo.detect.GestureType.TURN_RIGHT
import com.heroesofthebrain.halo.detect.GestureType.TURN_UP
import com.heroesofthebrain.halo.eeg.EegFrame
import com.heroesofthebrain.halo.eeg.ReplayCsv
import com.heroesofthebrain.halo.eeg.ReplayRecording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DetectorReplayTest {

    private val headTurns = setOf(TURN_LEFT, TURN_RIGHT, TURN_UP, TURN_DOWN)
    private val allNames = listOf(
        "szczeka-docisk", "szczeka-otwierani", "obrot-glowa-poziomo", "obrot-glowa-pion", "poj-mrug", "podw-mrug",
        "podw-mrug-s2", "dlugie-mrug-s2", "zaamkn-oczy", "untitled-125247",
    )

    private fun recording(name: String): ReplayRecording =
        File("src/main/assets/replay/$name.csv").bufferedReader().use { ReplayCsv.parse(it) }

    private fun detect(frames: List<EegFrame>, sampleRate: Int = 250, cfg: DetectorConfig = DetectorConfig()): List<GestureEvent> {
        val detector = Detector(sampleRate, cfg)
        return frames.flatMap { detector.process(it) }
    }

    private fun detect(name: String): List<GestureEvent> {
        val events = detect(recording(name).frames())
        println("$name -> " + events.joinToString { "${it.describe()}@${it.timestampMs}ms(${"%.2f".format(it.confidence)})" })
        return events
    }

    private fun List<GestureEvent>.starts(type: GestureType) = filterIsInstance<GestureStart>().filter { it.type == type }
    private fun List<GestureEvent>.ends(type: GestureType) = filterIsInstance<GestureEnd>().filter { it.type == type }
    private fun List<GestureEvent>.cancels(type: GestureType) = filterIsInstance<GestureCancel>().filter { it.type == type }
    private fun List<GestureEvent>.instants(type: GestureType) = filterIsInstance<GestureInstant>().filter { it.type == type }
    private fun List<GestureEvent>.turns() = filter { it.type in headTurns }

    // Offline 30-100 Hz holds above 2.5x baseline, from tools/gesture_ground_truth.py (zero-phase filter, centred window).
    private val clenchOnsetsMs = listOf(1040L, 2770L, 4480L, 6200L, 9260L)
    private val clenchHoldsMs = listOf(930L, 980L, 1060L, 1540L, 560L)

    @Test
    fun `jaw clench recording yields exactly 5 clench holds and no head turns`() {
        val events = detect("szczeka-docisk")
        assertEquals(5, events.starts(CLENCH).size)
        assertEquals(5, events.ends(CLENCH).size)
        assertTrue(events.cancels(CLENCH).isEmpty())
        assertTrue(events.turns().isEmpty())
    }

    @Test
    fun `clench start is reported within 0_8 s of its onset`() {
        // Onsets of the causal 30-100 Hz rise, read off the recording offline.
        val onsetsMs = listOf(1120L, 2840L, 4560L, 6300L, 9340L)
        val starts = detect("szczeka-docisk").starts(CLENCH)
        assertEquals(onsetsMs.size, starts.size)
        onsetsMs.zip(starts).forEach { (onset, start) ->
            assertTrue("clench at ${start.timestampMs} ms, onset $onset ms", start.timestampMs - onset in 0..800)
            assertTrue("onsetMs ${start.onsetMs} is after the start", start.onsetMs <= start.timestampMs)
        }
    }

    @Test
    fun `clench hold durations match the offline holds`() {
        val events = detect("szczeka-docisk")
        val starts = events.starts(CLENCH)
        val ends = events.ends(CLENCH)
        assertEquals(clenchHoldsMs.size, ends.size)
        // The causal filters push both edges back by a similar amount, so the length survives better than the edges.
        clenchOnsetsMs.indices.forEach { i ->
            assertTrue("onset ${starts[i].onsetMs} vs ${clenchOnsetsMs[i]}", starts[i].onsetMs - clenchOnsetsMs[i] in 0..150)
            assertTrue("hold ${ends[i].durationMs} vs ${clenchHoldsMs[i]}", ends[i].durationMs - clenchHoldsMs[i] in -150..150)
            assertEquals(ends[i].timestampMs - starts[i].onsetMs, ends[i].durationMs)
        }
        // The 1.54 s hold is clearly the long one, the 0.56 s hold the short one.
        assertEquals(3, ends.indexOf(ends.maxBy { it.durationMs }))
        assertEquals(4, ends.indexOf(ends.minBy { it.durationMs }))
    }

    @Test
    fun `head moving during a held clench cancels it instead of ending it`() {
        val rec = recording("szczeka-docisk")
        // 40 deg/s about x from 7.0 to 7.3 s, inside the 4th hold (6.2-7.74 s) and after its start; x is a tilt, not a turn.
        val frames = rec.frames().map { f ->
            val first = (f.firstSampleIndex - rec.sampleIndex[0]).toInt()
            val gyro = f.gyro!!.copyOf()
            for (s in 0 until f.sampleCount) if (first + s in 1750 until 1825) gyro[s * 3] = 40f
            EegFrame(f.firstSampleIndex, f.hostTimeNs, f.sampleRate, f.sampleCount, f.eeg, f.accel, gyro, f.contact, f.batteryPct, f.isDummy)
        }
        val events = detect(frames)
        val cancels = events.cancels(CLENCH)
        assertEquals(1, cancels.size)
        assertEquals(CancelReason.HEAD_MOTION, cancels[0].reason)
        assertTrue(cancels[0].timestampMs in 7000..7300)
        assertEquals(5, events.starts(CLENCH).size)
        assertEquals(4, events.ends(CLENCH).size)
        assertTrue(events.ends(CLENCH).none { it.timestampMs in 7000..8000 })
    }

    @Test
    fun `gap during a held clench cancels it with signal lost`() {
        val rec = recording("szczeka-docisk")
        // 7.0-7.2 s zeroed like the SDK's gap filling, inside the 4th hold.
        val frames = rec.frames().map { f ->
            val first = (f.firstSampleIndex - rec.sampleIndex[0]).toInt()
            if (first !in 1750 until 1800) return@map f
            EegFrame(
                f.firstSampleIndex, f.hostTimeNs, f.sampleRate, f.sampleCount,
                FloatArray(f.eeg.size), FloatArray(f.sampleCount * 3), FloatArray(f.sampleCount * 3),
                f.contact, f.batteryPct, BooleanArray(f.sampleCount) { true },
            )
        }
        val events = detect(frames)
        val cancels = events.cancels(CLENCH)
        assertEquals(1, cancels.size)
        assertEquals(CancelReason.SIGNAL_LOST, cancels[0].reason)
        assertEquals(7000L, cancels[0].timestampMs)
        assertEquals(events.starts(CLENCH).size, events.ends(CLENCH).size + 1)
    }

    @Test
    fun `horizontal swings yield 14 alternating left and right turns`() {
        // tools/gesture_ground_truth.py: the integrated gyro y reverses 14 times, 33-61 deg either side of the start pose,
        // starting on the -y side; the 15th lobe only brings the head back to the start pose.
        val events = detect("obrot-glowa-poziomo")
        val turns = events.turns()
        assertEquals(events, turns)
        assertEquals(14, turns.size)
        assertTrue(turns.all { it is GestureInstant && (it.type == TURN_LEFT || it.type == TURN_RIGHT) })
        assertTrue(turns.zipWithNext().all { (a, b) -> a.type != b.type })
        val minusY = if (DetectorConfig().turnLeftIsPositiveY) TURN_RIGHT else TURN_LEFT
        assertEquals(minusY, turns.first().type)
        assertEquals(7, turns.count { it.type == TURN_LEFT })
    }

    @Test
    fun `vertical nods yield 13 alternating up and down turns`() {
        // tools/gesture_ground_truth.py: the integrated gyro z reverses 13 times, starting on the +z side. The recording
        // stops during one more downward swing, which may or may not count, hence 13..14.
        val events = detect("obrot-glowa-pion")
        val turns = events.turns()
        assertEquals(events, turns)
        assertTrue("${turns.size} turns", turns.size in 13..14)
        assertTrue(turns.all { it is GestureInstant && (it.type == TURN_UP || it.type == TURN_DOWN) })
        assertTrue(turns.zipWithNext().all { (a, b) -> a.type != b.type })
        val plusZ = if (DetectorConfig().turnUpIsPositiveZ) TURN_UP else TURN_DOWN
        assertEquals(plusZ, turns.first().type)
        assertEquals(7, turns.count { it.type == plusZ })
    }

    @Test
    fun `flipping the direction config swaps the reported sides`() {
        val frames = recording("obrot-glowa-poziomo").frames()
        val normal = detect(frames).map { it.type }
        val flipped = detect(frames, cfg = DetectorConfig(turnLeftIsPositiveY = false)).map { it.type }
        assertEquals(normal.map { if (it == TURN_LEFT) TURN_RIGHT else TURN_LEFT }, flipped)
    }

    @Test
    fun `jaw opening yields no clench or head turn`() {
        val events = detect("szczeka-otwierani")
        assertTrue(events.none { it.type == CLENCH || it.type in headTurns })
    }

    @Test
    fun `single blinks yield no clench or head turn`() {
        val events = detect("poj-mrug")
        assertTrue(events.none { it.type == CLENCH || it.type in headTurns })
    }

    @Test
    fun `double blink recording yields 4 double blinks`() {
        val events = detect("podw-mrug")
        assertEquals(4, events.instants(DOUBLE_BLINK).size)
        assertTrue(events.none { it.type == CLENCH || it.type == LONG_BLINK || it.type in headTurns })
    }

    @Test
    fun `session 2 double blink recording yields 9 double blinks`() {
        // Pairs read off the gyro z kicks offline; the EEG here saturates on several of them.
        val events = detect("podw-mrug-s2")
        assertEquals(9, events.instants(DOUBLE_BLINK).size)
        assertTrue(events.all { it.type == DOUBLE_BLINK })
    }

    @Test
    fun `long blink recording yields 3 long blinks and no double blink`() {
        // Closures of 1.44, 1.48 and 1.52 s, ending at 3.78, 9.46 and 22.60 s.
        val events = detect("dlugie-mrug-s2")
        val longMs = events.instants(LONG_BLINK).map { it.timestampMs }
        assertEquals(3, longMs.size)
        listOf(3780L, 9460L, 22600L).zip(longMs).forEach { (open, at) -> assertTrue("long blink at $at ms", at - open in 0..500) }
        assertTrue(events.all { it.type == LONG_BLINK })
    }

    @Test
    fun `recordings without deliberate blinks yield no blink events`() {
        val names = listOf("poj-mrug", "szczeka-docisk", "szczeka-otwierani", "obrot-glowa-pion", "obrot-glowa-poziomo", "untitled-125247", "zaamkn-oczy")
        names.forEach { name ->
            assertTrue(name, detect(name).none { it.type == DOUBLE_BLINK || it.type == LONG_BLINK })
        }
    }

    @Test
    fun `without gyro the EEG fallback still finds the session 1 double blinks`() {
        val frames = recording("podw-mrug").frames().map { f ->
            EegFrame(f.firstSampleIndex, f.hostTimeNs, f.sampleRate, f.sampleCount, f.eeg, f.accel, null, f.contact, f.batteryPct, f.isDummy)
        }
        val events = detect(frames)
        assertEquals(4, events.instants(DOUBLE_BLINK).size)
        assertTrue(events.none { it.type == LONG_BLINK })
    }

    // Not a claim that eyes-closed is detected: only that the one closure we have does not fire other rules.
    @Test
    fun `closing the eyes does not fire double blink or clench`() {
        val events = detect("zaamkn-oczy")
        assertTrue(events.none { it.type == DOUBLE_BLINK || it.type == CLENCH })
    }

    @Test
    fun `every start is closed by one end or cancel and only held gestures start`() {
        allNames.forEach { name ->
            var open: GestureStart? = null
            detect(name).forEach { e ->
                assertTrue("$name: ${e.describe()}", (e is GestureInstant) != e.type.held)
                when (e) {
                    is GestureStart -> {
                        assertEquals("$name: start while ${open?.describe()} is open", null, open)
                        open = e
                    }
                    is GestureEnd, is GestureCancel -> {
                        assertEquals("$name: ${e.describe()} without a start", open?.type, e.type)
                        open = null
                    }
                    is GestureInstant -> Unit
                }
            }
            assertEquals("$name: left open", null, open)
        }
    }

    @Test
    fun `gap-filled samples are skipped and detection recovers`() {
        val rec = recording("szczeka-docisk")
        // 8.0-8.2 s lies between the 4th and 5th clench; zeroed rows like the SDK's gap filling.
        val frames = rec.frames().map { f ->
            val first = (f.firstSampleIndex - rec.sampleIndex[0]).toInt()
            if (first !in 2000 until 2050) return@map f
            EegFrame(
                f.firstSampleIndex, f.hostTimeNs, f.sampleRate, f.sampleCount,
                FloatArray(f.eeg.size), FloatArray(f.sampleCount * 3), FloatArray(f.sampleCount * 3),
                f.contact, f.batteryPct, BooleanArray(f.sampleCount) { true },
            )
        }
        val events = detect(frames)
        assertEquals(5, events.starts(CLENCH).size)
        assertEquals(5, events.ends(CLENCH).size)
        assertTrue(events.none { it.timestampMs in 8000..8500 })
    }

    @Test
    fun `missing chunk in sample index is treated as a gap`() {
        val frames = recording("szczeka-docisk").frames().filterIndexed { i, _ -> i != 80 }
        val events = detect(frames)
        assertEquals(5, events.starts(CLENCH).size)
        assertEquals(5, events.ends(CLENCH).size)
    }

    private fun withChannel(rec: ReplayRecording, channel: Int, value: (sample: Int, original: Float) -> Float): List<EegFrame> =
        rec.frames().map { f ->
            val eeg = f.eeg.copyOf()
            val first = (f.firstSampleIndex - rec.sampleIndex[0]).toInt()
            for (s in 0 until f.sampleCount) eeg[s * 4 + channel] = value(first + s, eeg[s * 4 + channel])
            EegFrame(f.firstSampleIndex, f.hostTimeNs, f.sampleRate, f.sampleCount, eeg, f.accel, f.gyro, f.contact, f.batteryPct, f.isDummy)
        }

    @Test
    fun `quality gate drops a flat channel and clenches are still found`() {
        val frames = withChannel(recording("szczeka-docisk"), channel = 0) { _, _ -> 1234f }
        val detector = Detector()
        val events = frames.flatMap { detector.process(it) }
        assertEquals(false, detector.channelOk[0])
        assertEquals(5, events.starts(CLENCH).size)
        assertEquals(5, events.ends(CLENCH).size)
    }

    @Test
    fun `quality gate drops a very noisy channel even though its contact flag is good`() {
        val rng = java.util.Random(7)
        val frames = withChannel(recording("szczeka-docisk"), channel = 1) { _, v -> v + 2000f * rng.nextGaussian().toFloat() }
        val detector = Detector()
        val okO2 = frames.map { detector.process(it); detector.channelOk[1] }
        assertTrue("O2 should be gated most of the time", okO2.drop(10).count { !it } > okO2.size * 0.8)
    }

    @Test
    fun `clean recording keeps all channels through the gate`() {
        val detector = Detector()
        val okAll = recording("szczeka-docisk").frames().map { detector.process(it); detector.channelOk.all { ok -> ok } }
        assertTrue(okAll.all { it })
    }

    @Test
    fun `confidence stays within 0 and 1`() {
        val all = allNames.flatMap { detect(it) }
        assertTrue(all.all { it.confidence in 0f..1f })
    }
}
