package com.heroesofthebrain.halo

import com.heroesofthebrain.halo.detect.ControlType
import com.heroesofthebrain.halo.detect.Detector
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

    private data class Arrival(val type: ControlType, val streamMs: Long, val arrivedMs: Long)

    private fun arrivals(name: String): List<Arrival> {
        val out = mutableListOf<Arrival>()
        runTest {
            val source = ReplaySource(name, nanoTime = { testScheduler.currentTime * 1_000_000 }) {
                File("src/main/assets/replay/$name.csv").inputStream()
            }
            val start = testScheduler.currentTime
            val detector = Detector()
            source.frames().collect { frame ->
                detector.process(frame).forEach { out += Arrival(it.type, it.timestampMs, testScheduler.currentTime - start) }
            }
        }
        println("$name arrivals: " + out.joinToString { "${it.type}@${it.arrivedMs}ms" })
        return out
    }

    @Test
    fun `clench labels reach the app within 1 s of the clench onset`() {
        val onsetsMs = listOf(1120L, 2840L, 4560L, 6300L, 9340L)
        val clenches = arrivals("szczeka-docisk").filter { it.type == ControlType.CLENCH }
        assertEquals(onsetsMs.size, clenches.size)
        onsetsMs.zip(clenches).forEach { (onset, a) ->
            assertTrue("arrived ${a.arrivedMs - onset} ms after onset", a.arrivedMs - onset in 0..1000)
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
        val blinks = arrivals("podw-mrug").filter { it.type == ControlType.DOUBLE_BLINK }
        assertEquals(secondBlinkMs.size, blinks.size)
        secondBlinkMs.zip(blinks).forEach { (second, a) ->
            assertTrue("arrived ${a.arrivedMs - second} ms after the second blink", a.arrivedMs - second in 0..1000)
        }
    }
}
