package com.heroesofthebrain.halo.detect

import com.heroesofthebrain.halo.detect.GestureType.TURN_DOWN
import com.heroesofthebrain.halo.detect.GestureType.TURN_LEFT
import com.heroesofthebrain.halo.detect.GestureType.TURN_RIGHT
import com.heroesofthebrain.halo.detect.GestureType.TURN_UP
import com.heroesofthebrain.halo.eeg.EEG_CHANNELS
import com.heroesofthebrain.halo.eeg.EegFrame
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

// The recordings only have continuous swings, so turning and coming back is checked on made-up gyro traces.
class DetectorTurnTest {

    private val fs = 250

    private class Move(val startS: Double, val axis: Int, val deg: Double, val seconds: Double)

    // Each move is a sin² velocity bump that rotates the head by `deg` about `axis` (1 = y, 2 = z).
    private fun frames(totalS: Double, vararg moves: Move): List<EegFrame> {
        val n = (totalS * fs).toInt()
        val gyro = FloatArray(n * 3)
        for (m in moves) {
            val peak = 2 * m.deg / m.seconds
            val from = (m.startS * fs).toInt()
            val len = (m.seconds * fs).toInt()
            for (i in 0 until len) {
                val x = sin(PI * (i + 0.5) / len)
                gyro[(from + i) * 3 + m.axis] += (peak * x * x).toFloat()
            }
        }
        return (0 until n step 25).map { first ->
            val count = minOf(25, n - first)
            val eeg = FloatArray(count * EEG_CHANNELS) { 10f * sin(2 * PI * 10 * (first + it / EEG_CHANNELS) / fs).toFloat() }
            EegFrame(
                first.toLong(), 0L, fs, count, eeg,
                FloatArray(count * 3) { if (it % 3 == 1) 9.8f else 0f },
                gyro.copyOfRange(first * 3, (first + count) * 3),
                BooleanArray(EEG_CHANNELS) { true }, 80f, BooleanArray(count),
            )
        }
    }

    private fun turns(frames: List<EegFrame>, cfg: DetectorConfig = DetectorConfig()): List<GestureType> {
        val detector = Detector(fs, cfg)
        return frames.flatMap { detector.process(it) }.map { it.type }
    }

    @Test
    fun `turn and come back is one turn`() {
        assertEquals(listOf(TURN_LEFT), turns(frames(5.0, Move(1.0, 1, 45.0, 0.4), Move(2.4, 1, -45.0, 0.4))))
    }

    @Test
    fun `coming back slowly is still the way back`() {
        // The return peaks at 30 deg/s, below the turn threshold, and the second turn comes before turnReturnMaxS runs out,
        // so only the integrated angle can tell that the head is back.
        val trace = frames(7.0, Move(1.0, 1, 45.0, 0.4), Move(1.6, 1, -45.0, 3.0), Move(5.0, 1, 45.0, 0.4))
        assertEquals(listOf(TURN_LEFT, TURN_LEFT), turns(trace))
    }

    @Test
    fun `swinging past the start pose is a turn the other way`() {
        val trace = frames(6.0, Move(1.0, 1, 45.0, 0.4), Move(2.0, 1, -90.0, 0.6), Move(3.5, 1, 45.0, 0.4))
        assertEquals(listOf(TURN_LEFT, TURN_RIGHT), turns(trace))
    }

    @Test
    fun `coming back after a long hold counts as a turn of its own`() {
        val hold = DetectorConfig().turnReturnMaxS
        val trace = frames(hold + 5, Move(1.0, 1, 45.0, 0.4), Move(1.4 + hold + 1, 1, -45.0, 0.4))
        assertEquals(listOf(TURN_LEFT, TURN_RIGHT), turns(trace))
    }

    @Test
    fun `nod up and back then down and back`() {
        val trace = frames(7.0, Move(1.0, 2, 25.0, 0.4), Move(2.0, 2, -25.0, 0.4), Move(4.0, 2, -25.0, 0.4), Move(5.0, 2, 25.0, 0.4))
        assertEquals(listOf(TURN_UP, TURN_DOWN), turns(trace))
    }

    @Test
    fun `a quick small jerk is not a turn`() {
        assertEquals(emptyList<GestureType>(), turns(frames(3.0, Move(1.0, 1, 8.0, 0.15), Move(1.5, 1, -8.0, 0.15))))
    }

    @Test
    fun `direction comes from the config`() {
        val trace = frames(7.0, Move(1.0, 1, -45.0, 0.4), Move(2.0, 1, 45.0, 0.4), Move(4.0, 2, -25.0, 0.4), Move(5.0, 2, 25.0, 0.4))
        assertEquals(listOf(TURN_RIGHT, TURN_DOWN), turns(trace))
        val flipped = DetectorConfig(turnLeftIsPositiveY = false, turnUpIsPositiveZ = false)
        assertEquals(listOf(TURN_LEFT, TURN_UP), turns(trace, flipped))
    }
}
