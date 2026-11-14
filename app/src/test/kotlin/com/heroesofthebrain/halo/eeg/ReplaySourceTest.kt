package com.heroesofthebrain.halo.eeg

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ReplaySourceTest {

    private val csv = File("src/main/assets/replay/szczeka-docisk.csv")

    @Test
    fun `csv parses into the expected shape`() {
        val rec = csv.bufferedReader().use { ReplayCsv.parse(it) }
        assertEquals(2800, rec.size)
        assertEquals(42900L, rec.sampleIndex.first())
        assertEquals(rec.size * EEG_CHANNELS, rec.eeg.size)
        assertTrue(rec.streaming.all { it })
    }

    @Test
    fun `replay paces chunks at 250 Hz`() = runTest {
        val source = ReplaySource("szczeka-docisk", nanoTime = { testScheduler.currentTime * 1_000_000 }) {
            csv.inputStream()
        }
        val frames = source.frames().toList()
        val emittedMs = frames.map { it.hostTimeNs / 1_000_000 }
        assertEquals(112, frames.size)
        assertEquals(100L, emittedMs.first())
        assertEquals(11_200L, emittedMs.last())
        assertTrue(emittedMs.zipWithNext().all { (a, b) -> b - a == 100L })
        assertEquals(SourceStatus.Finished, source.status.value)
    }
}
