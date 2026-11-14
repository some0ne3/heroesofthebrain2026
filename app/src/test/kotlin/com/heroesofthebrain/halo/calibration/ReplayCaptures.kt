package com.heroesofthebrain.halo.calibration

import com.heroesofthebrain.halo.eeg.EegFrame
import com.heroesofthebrain.halo.eeg.ReplayCsv
import com.heroesofthebrain.halo.eeg.ReplayRecording
import java.io.File
import kotlin.math.min
import kotlin.math.roundToLong

// Calibration captures cut from the replay recordings (one subject, ses-001 and ses-002). The trial windows stand in
// for cues: each opens shortly before a gesture that was read off the recording offline.
object ReplayCaptures {
    private const val RATE = 250

    private val cache = HashMap<String, ReplayRecording>()

    fun recording(name: String): ReplayRecording =
        cache.getOrPut(name) { File("src/main/assets/replay/$name.csv").bufferedReader().use { ReplayCsv.parse(it) } }

    fun capture(
        step: CalibrationStep,
        name: String,
        windowsS: List<Pair<Double, Double>>,
        fromS: Double = 0.0,
        toS: Double = Double.MAX_VALUE,
        analysisStartS: Double? = null,
        withGyro: Boolean = true,
    ): StepCapture {
        val rec = recording(name)
        val from = (fromS * RATE).toInt()
        val to = min((toS * RATE).toLong(), rec.size.toLong()).toInt()
        val frames = (from until to step ReplayRecording.DEFAULT_CHUNK).map { rec.frame(it, min(it + ReplayRecording.DEFAULT_CHUNK, to)) }
            .map { if (withGyro) it else it.withoutGyro() }
        fun ms(s: Double) = ((s - fromS) * 1000).roundToLong()
        val trials = windowsS.mapIndexed { i, (a, b) -> TrialWindow(i, ms(a), ms(b), "trial $i") }
        val start = analysisStartS?.let(::ms) ?: trials.firstOrNull()?.startMs ?: 0L
        return StepCapture(step, RATE, frames, trials, start)
    }

    private fun EegFrame.withoutGyro() =
        EegFrame(firstSampleIndex, hostTimeNs, sampleRate, sampleCount, eeg, accel, null, contact, batteryPct, isDummy)

    // untitled-125247 is still until 15 s; the eye closure at 15-16.7 s is left out.
    fun rest(withGyro: Boolean = true) =
        capture(CalibrationStep.Rest, "untitled-125247", emptyList(), toS = 14.5, analysisStartS = 1.5, withGyro = withGyro)

    // Onsets of the 30-100 Hz rise (DetectorReplayTest); each window opens 0.4 s before and runs to the next one.
    val clenchOnsetsS = listOf(1.12, 2.84, 4.56, 6.30, 9.34)

    fun clench(withGyro: Boolean = true): StepCapture {
        val starts = clenchOnsetsS.map { it - 0.4 }
        val windows = starts.mapIndexed { i, s -> s to min(s + 2.4, starts.getOrElse(i + 1) { 11.2 }) }
        return capture(CalibrationStep.Gesture(CalibrationGesture.CLENCH), "szczeka-docisk", windows, withGyro = withGyro)
    }

    // Swings between the gyro's still points. The horizontal ones alternate starting on -y, the vertical ones on +z.
    private val horizontalSwingsS = listOf(
        0.74 to 1.16, 1.18 to 1.92, 1.94 to 2.66, 2.72 to 3.38, 3.42 to 4.14, 4.18 to 4.82, 4.86 to 5.60,
        5.68 to 6.32, 6.40 to 7.08, 7.16 to 7.80, 7.86 to 8.52, 8.64 to 9.24, 9.34 to 10.00, 10.10 to 10.66,
    )
    private val verticalSwingsS = listOf(
        0.66 to 1.10, 1.16 to 1.74, 1.80 to 2.40, 2.46 to 3.00, 3.08 to 3.58, 3.66 to 4.28, 4.40 to 4.88,
        4.98 to 5.50, 5.58 to 6.12, 6.22 to 6.78, 6.86 to 7.58, 7.68 to 8.22, 8.32 to 8.98, 9.08 to 9.60,
    )

    private fun swings(all: List<Pair<Double, Double>>, odd: Boolean) =
        all.filterIndexed { i, _ -> (i % 2 == 1) == odd }.map { (a, b) -> a - 0.05 to b + 0.05 }

    private fun turn(gesture: CalibrationGesture, name: String, windows: List<Pair<Double, Double>>) =
        capture(CalibrationStep.Gesture(gesture), name, windows)

    // The recordings say nothing about which way is left, so the cue labels are a choice: by default they follow the
    // detector's provisional guess (left = +y, up = +z), flipped they say the opposite. Both captures of an axis
    // share one recording, so the other direction's swings show up as other detections.
    fun turns(flipped: Boolean = false): List<StepCapture> {
        val plusY = swings(horizontalSwingsS, odd = true)
        val minusY = swings(horizontalSwingsS, odd = false)
        val plusZ = swings(verticalSwingsS, odd = false)
        val minusZ = swings(verticalSwingsS, odd = true)
        return listOf(
            turn(CalibrationGesture.TURN_LEFT, "obrot-glowa-poziomo", if (flipped) minusY else plusY),
            turn(CalibrationGesture.TURN_RIGHT, "obrot-glowa-poziomo", if (flipped) plusY else minusY),
            turn(CalibrationGesture.TURN_UP, "obrot-glowa-pion", if (flipped) minusZ else plusZ),
            turn(CalibrationGesture.TURN_DOWN, "obrot-glowa-pion", if (flipped) plusZ else minusZ),
        )
    }

    // First lid close and second lid open of each pair, from the gyro z kicks.
    private val doubleBlinksS2 = listOf(
        2.88 to 3.90, 4.76 to 5.36, 7.04 to 7.76, 9.66 to 10.26, 11.74 to 12.34,
        14.18 to 14.74, 16.80 to 17.36, 19.24 to 19.84, 21.52 to 22.10,
    )
    private val doubleBlinksS1 = listOf(3.04 to 3.40, 4.06 to 4.46, 6.86 to 7.26, 7.66 to 8.10)

    private fun blinkWindows(pairs: List<Pair<Double, Double>>, before: Double, after: Double) =
        pairs.mapIndexed { i, (close, open) ->
            val next = pairs.getOrNull(i + 1)?.first?.minus(before) ?: Double.MAX_VALUE
            close - before to min(open + after, maxOf(next, open + 0.3))
        }

    fun doubleBlinkS2() = capture(CalibrationStep.Gesture(CalibrationGesture.DOUBLE_BLINK), "podw-mrug-s2", blinkWindows(doubleBlinksS2, 0.4, 0.6))

    // Pairs 3 and 4 are only 0.4 s apart, so their windows overlap a little.
    fun doubleBlinkS1(withGyro: Boolean = true) = capture(
        CalibrationStep.Gesture(CalibrationGesture.DOUBLE_BLINK),
        "podw-mrug",
        doubleBlinksS1.map { (close, open) -> close - 0.3 to open + 0.5 },
        withGyro = withGyro,
    )

    // Closures of 1.44, 1.48 and 1.52 s.
    private val longBlinksS = listOf(2.34 to 3.78, 7.98 to 9.46, 21.08 to 22.60)

    fun longBlink() = capture(CalibrationStep.Gesture(CalibrationGesture.LONG_BLINK), "dlugie-mrug-s2", longBlinksS.map { (c, o) -> c - 0.5 to o + 0.8 })

    fun full(flippedTurns: Boolean = false): CalibrationCaptures =
        (listOf(rest(), clench()) + turns(flippedTurns) + listOf(doubleBlinkS2(), longBlink()))
            .fold(CalibrationCaptures()) { acc, c -> acc.with(c) }
}
