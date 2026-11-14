package com.heroesofthebrain.halo.detect

import com.heroesofthebrain.halo.detect.ControlType.CLENCH
import com.heroesofthebrain.halo.detect.ControlType.DOUBLE_BLINK
import com.heroesofthebrain.halo.detect.ControlType.HEAD_TURN_HORIZONTAL
import com.heroesofthebrain.halo.detect.ControlType.HEAD_TURN_VERTICAL
import com.heroesofthebrain.halo.detect.ControlType.LONG_BLINK
import com.heroesofthebrain.halo.eeg.EegFrame
import com.heroesofthebrain.halo.eeg.ReplayCsv
import com.heroesofthebrain.halo.eeg.ReplayRecording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DetectorReplayTest {

    private val headTurns = setOf(HEAD_TURN_HORIZONTAL, HEAD_TURN_VERTICAL)

    private fun recording(name: String): ReplayRecording =
        File("src/main/assets/replay/$name.csv").bufferedReader().use { ReplayCsv.parse(it) }

    private fun detect(frames: List<EegFrame>, sampleRate: Int = 250): List<ControlEvent> {
        val detector = Detector(sampleRate)
        return frames.flatMap { detector.process(it) }
    }

    private fun detect(name: String): List<ControlEvent> {
        val events = detect(recording(name).frames())
        println("$name -> " + events.joinToString { "${it.type}@${it.timestampMs}ms(${"%.2f".format(it.confidence)})" })
        return events
    }

    private fun List<ControlEvent>.count(type: ControlType) = count { it.type == type }

    @Test
    fun `jaw clench recording yields exactly 5 clenches and no head turns`() {
        val events = detect("szczeka-docisk")
        assertEquals(5, events.count(CLENCH))
        assertTrue(events.none { it.type in headTurns })
    }

    @Test
    fun `clench is reported within 0_8 s of its onset`() {
        // Onsets of the 30-100 Hz rise, read off the recording offline.
        val onsetsMs = listOf(1120L, 2840L, 4560L, 6300L, 9340L)
        val clenches = detect("szczeka-docisk").filter { it.type == CLENCH }.map { it.timestampMs }
        assertEquals(onsetsMs.size, clenches.size)
        onsetsMs.zip(clenches).forEach { (onset, at) ->
            assertTrue("clench at $at ms, onset $onset ms", at - onset in 0..800)
        }
    }

    @Test
    fun `horizontal head turns yield only HEAD_TURN_HORIZONTAL`() {
        val events = detect("obrot-glowa-poziomo")
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.type == HEAD_TURN_HORIZONTAL })
    }

    @Test
    fun `vertical head turns yield only HEAD_TURN_VERTICAL`() {
        val events = detect("obrot-glowa-pion")
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.type == HEAD_TURN_VERTICAL })
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
        assertEquals(4, events.count(DOUBLE_BLINK))
        assertTrue(events.none { it.type == CLENCH || it.type == LONG_BLINK || it.type in headTurns })
    }

    @Test
    fun `session 2 double blink recording yields 9 double blinks`() {
        // Pairs read off the gyro z kicks offline; the EEG here saturates on several of them.
        val events = detect("podw-mrug-s2")
        assertEquals(9, events.count(DOUBLE_BLINK))
        assertTrue(events.all { it.type == DOUBLE_BLINK })
    }

    @Test
    fun `long blink recording yields 3 long blinks and no double blink`() {
        // Closures of 1.44, 1.48 and 1.52 s, ending at 3.78, 9.46 and 22.60 s.
        val events = detect("dlugie-mrug-s2")
        val longMs = events.filter { it.type == LONG_BLINK }.map { it.timestampMs }
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
        assertEquals(4, events.count(DOUBLE_BLINK))
        assertTrue(events.none { it.type == LONG_BLINK })
    }

    // Not a claim that eyes-closed is detected: only that the one closure we have does not fire other rules.
    @Test
    fun `closing the eyes does not fire double blink or clench`() {
        val events = detect("zaamkn-oczy")
        assertTrue(events.none { it.type == DOUBLE_BLINK || it.type == CLENCH })
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
        assertEquals(5, events.count(CLENCH))
        assertTrue(events.none { it.timestampMs in 8000..8500 })
    }

    @Test
    fun `missing chunk in sample index is treated as a gap`() {
        val frames = recording("szczeka-docisk").frames().filterIndexed { i, _ -> i != 80 }
        val events = detect(frames)
        assertEquals(5, events.count(CLENCH))
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
        assertEquals(5, events.count(CLENCH))
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
        val all = listOf("szczeka-docisk", "obrot-glowa-poziomo", "obrot-glowa-pion", "podw-mrug").flatMap { detect(it) }
        assertTrue(all.all { it.confidence in 0f..1f })
    }
}
