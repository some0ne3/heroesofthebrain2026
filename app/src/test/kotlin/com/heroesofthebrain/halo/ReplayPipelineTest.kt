package com.heroesofthebrain.halo

import com.heroesofthebrain.halo.detect.Detector
import com.heroesofthebrain.halo.detect.GestureEnd
import com.heroesofthebrain.halo.detect.GestureEvent
import com.heroesofthebrain.halo.detect.GestureInstant
import com.heroesofthebrain.halo.detect.GestureStart
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.detect.describe
import com.heroesofthebrain.halo.eeg.ReplaySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Source and detector wired like the ViewModel, on a virtual clock, to measure gesture-to-label latency.
@OptIn(ExperimentalCoroutinesApi::class)
class ReplayPipelineTest {

    private data class Arrival(val event: GestureEvent, val arrivedMs: Long) {
        val streamMs get() = event.timestampMs
    }

    private fun arrivals(name: String): List<Arrival> {
        val out = mutableListOf<Arrival>()
        runTest {
            val source = ReplaySource(name, nanoTime = { testScheduler.currentTime * 1_000_000 }) {
                File("src/main/assets/replay/$name.csv").inputStream()
            }
            val start = testScheduler.currentTime
            val detector = Detector()
            source.frames().collect { frame ->
                detector.process(frame).forEach { out += Arrival(it, testScheduler.currentTime - start) }
            }
        }
        println("$name arrivals: " + out.joinToString { "${it.event.describe()}@${it.arrivedMs}ms" })
        return out
    }

    @Test
    fun `clench starts reach the app within 1 s of the clench onset`() {
        val onsetsMs = listOf(1120L, 2840L, 4560L, 6300L, 9340L)
        val clenches = arrivals("szczeka-docisk").filter { it.event is GestureStart && it.event.type == GestureType.CLENCH }
        assertEquals(onsetsMs.size, clenches.size)
        onsetsMs.zip(clenches).forEach { (onset, a) ->
            assertTrue("arrived ${a.arrivedMs - onset} ms after onset", a.arrivedMs - onset in 0..1000)
        }
    }

    @Test
    fun `clench ends reach the app within 0_3 s of the release`() {
        // Ends of the offline 30-100 Hz holds, from tools/gesture_ground_truth.py.
        val releasesMs = listOf(1970L, 3740L, 5540L, 7740L, 9830L)
        val ends = arrivals("szczeka-docisk").filter { it.event is GestureEnd && it.event.type == GestureType.CLENCH }
        assertEquals(releasesMs.size, ends.size)
        releasesMs.zip(ends).forEach { (release, a) ->
            assertTrue("arrived ${a.arrivedMs - release} ms after release", a.arrivedMs - release in 0..300)
        }
    }

    @Test
    fun `chunking adds at most one chunk of delay`() {
        val all = listOf("szczeka-docisk", "podw-mrug", "obrot-glowa-pion").flatMap { arrivals(it) }
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.arrivedMs - it.streamMs in 0..100 })
    }

    @Test
    fun `double blinks reach the app within 1 s of the second blink`() {
        // Second-blink lobe peaks in the causal 1-8 Hz signal, read off the recording offline.
        val secondBlinkMs = listOf(3410L, 4460L, 7270L, 8100L)
        val blinks = arrivals("podw-mrug").filter { it.event is GestureInstant && it.event.type == GestureType.DOUBLE_BLINK }
        assertEquals(secondBlinkMs.size, blinks.size)
        secondBlinkMs.zip(blinks).forEach { (second, a) ->
            assertTrue("arrived ${a.arrivedMs - second} ms after the second blink", a.arrivedMs - second in 0..1000)
        }
    }
}
