package com.heroesofthebrain.halo.calibration

import com.heroesofthebrain.halo.detect.Biquad
import com.heroesofthebrain.halo.detect.Detector
import com.heroesofthebrain.halo.detect.DetectorConfig
import com.heroesofthebrain.halo.eeg.CH_FP1
import com.heroesofthebrain.halo.eeg.CH_FP2
import com.heroesofthebrain.halo.eeg.EEG_CHANNELS
import com.heroesofthebrain.halo.eeg.EegFrame
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Per-sample features of one capture, indexed by sample offset from its first sample. NaN where no sample arrived.
internal class CaptureSeries(
    val sampleRate: Int,
    // the detector's own 30-100 Hz ratio to baseline
    val ratio: DoubleArray,
    val gyroMag: DoubleArray?,
    val gy: DoubleArray?,
    val gz: DoubleArray?,
    // most negative 1-8 Hz value of the two Fp channels, like the detector's EEG blink fallback
    val frontal: DoubleArray,
) {
    val size: Int get() = ratio.size

    fun index(ms: Long): Int = (ms * sampleRate / 1000).toInt().coerceIn(0, size)

    fun range(fromMs: Long, toMs: Long): IntRange = index(fromMs) until index(toMs)

    fun range(window: TrialWindow): IntRange = range(window.startMs, window.endMs)

    fun samples(seconds: Double): Int = max(1, (seconds * sampleRate).roundToInt())

    fun ms(index: Int): Long = index * 1000L / sampleRate
}

internal fun StepCapture.series(base: DetectorConfig): CaptureSeries {
    val n = sampleSpan
    val ratio = DoubleArray(n) { Double.NaN }
    val gyroMag = if (hasGyro) DoubleArray(n) { Double.NaN } else null
    val gy = if (hasGyro) DoubleArray(n) { Double.NaN } else null
    val gz = if (hasGyro) DoubleArray(n) { Double.NaN } else null
    val frontal = DoubleArray(n) { Double.NaN }

    val fs = sampleRate.toDouble()
    val fp = intArrayOf(CH_FP2, CH_FP1)
    val hp = Array(fp.size) { Biquad.highPass(fs, 1.0) }
    val lp = Array(fp.size) { Biquad.lowPass(fs, 8.0) }
    val offset = DoubleArray(fp.size)
    var filtersStarted = false

    // One sample per call, so lastRatio is read at every sample and not just at chunk ends.
    val detector = Detector(sampleRate, base)
    for (frame in frames) {
        for (s in 0 until frame.sampleCount) {
            detector.process(frame.sample(s))
            val pos = (frame.firstSampleIndex + s - firstSampleIndex).toInt()
            if (pos !in 0 until n || frame.isDummy[s]) continue
            ratio[pos] = detector.lastRatio
            val g = frame.gyro
            if (g != null && gyroMag != null && gy != null && gz != null) {
                val x = g[s * 3].toDouble()
                val y = g[s * 3 + 1].toDouble()
                val z = g[s * 3 + 2].toDouble()
                gyroMag[pos] = sqrt(x * x + y * y + z * z)
                gy[pos] = y
                gz[pos] = z
            }
            if (!filtersStarted) {
                for (i in fp.indices) offset[i] = frame.eeg(s, fp[i]).toDouble()
                filtersStarted = true
            }
            var lowest = Double.POSITIVE_INFINITY
            for (i in fp.indices) {
                if (!frame.contact[fp[i]]) continue
                lowest = min(lowest, lp[i].step(hp[i].step(frame.eeg(s, fp[i]) - offset[i])))
            }
            if (lowest.isFinite()) frontal[pos] = lowest
        }
    }
    return CaptureSeries(sampleRate, ratio, gyroMag, gy, gz, frontal)
}

// Sign of the first excursion of the integrated rate past minDeg, so the way out decides and not the way back.
internal fun DoubleArray.firstExcursionSign(range: IntRange, sampleRate: Int, bias: Double, minDeg: Double): Int {
    var angle = 0.0
    for (i in range) {
        val v = this[i]
        if (v.isNaN()) continue
        angle += (v - bias) / sampleRate
        if (angle >= minDeg) return 1
        if (angle <= -minDeg) return -1
    }
    return 0
}

internal fun DoubleArray.meanIn(range: IntRange): Double {
    var sum = 0.0
    var n = 0
    for (i in range) {
        val v = this[i]
        if (v.isNaN()) continue
        sum += v
        n++
    }
    return if (n == 0) Double.NaN else sum / n
}

internal fun EegFrame.sample(s: Int): EegFrame = EegFrame(
    firstSampleIndex = firstSampleIndex + s,
    hostTimeNs = hostTimeNs,
    sampleRate = sampleRate,
    sampleCount = 1,
    eeg = eeg.copyOfRange(s * EEG_CHANNELS, (s + 1) * EEG_CHANNELS),
    accel = accel?.takeIf { it.size >= (s + 1) * 3 }?.copyOfRange(s * 3, (s + 1) * 3),
    gyro = gyro?.takeIf { it.size >= (s + 1) * 3 }?.copyOfRange(s * 3, (s + 1) * 3),
    contact = contact,
    batteryPct = batteryPct,
    isDummy = booleanArrayOf(isDummy[s]),
)

// The highest level the series stays above for w samples in a row, i.e. what a "held for w" rule can still fire on.
internal fun DoubleArray.sustainedPeak(range: IntRange, w: Int): Double {
    if (w <= 0 || range.isEmpty()) return Double.NaN
    val window = ArrayDeque<Int>()
    fun at(i: Int) = this[i].let { if (it.isNaN()) Double.NEGATIVE_INFINITY else it }
    var best = Double.NEGATIVE_INFINITY
    for (i in range) {
        val v = at(i)
        while (window.isNotEmpty() && at(window.last()) >= v) window.removeLast()
        window.addLast(i)
        if (window.first() <= i - w) window.removeFirst()
        if (i - range.first + 1 >= w) best = max(best, at(window.first()))
    }
    return if (best.isFinite()) best else Double.NaN
}

internal fun DoubleArray.percentile(ranges: List<IntRange>, p: Double): Double =
    percentileOf(ranges.flatMap { r -> r.map { this[it] } }.filter { !it.isNaN() }, p)

internal fun DoubleArray.maxIn(range: IntRange, transform: (Double) -> Double = { it }): Double {
    var best = Double.NaN
    for (i in range) {
        val v = this[i]
        if (v.isNaN()) continue
        val t = transform(v)
        if (best.isNaN() || t > best) best = t
    }
    return best
}

internal class LidBlink(val closeIndex: Int?, val openIndex: Int, val closePeak: Double) {
    var openPeak = 0.0
}

// Same kick logic as Detector.updateLids (arm, fire, re-arm at half the threshold), without pairing or the motion veto.
internal fun lidBlinks(gz: DoubleArray, sampleRate: Int, kick: Double, maxClosedS: Double): List<LidBlink> {
    val maxClosed = (maxClosedS * sampleRate).toInt()
    val out = ArrayList<LidBlink>()
    var closeArmed = true
    var openArmed = true
    var closeAt = -1
    var closePeak = 0.0
    var current: LidBlink? = null
    for (i in gz.indices) {
        val v = gz[i]
        if (v.isNaN()) continue
        if (v < -kick) {
            if (closeArmed) {
                closeArmed = false
                closeAt = i
                closePeak = 0.0
            }
            closePeak = max(closePeak, -v)
        } else if (v > -kick / 2) {
            closeArmed = true
        }
        if (v > kick) {
            if (openArmed) {
                openArmed = false
                val closed = closeAt >= 0 && i - closeAt <= maxClosed
                current = LidBlink(if (closed) closeAt else null, i, if (closed) closePeak else 0.0).also { out += it }
                closeAt = -1
            }
            current?.let { it.openPeak = max(it.openPeak, v) }
        } else if (v < kick / 2) {
            openArmed = true
            current = null
        }
    }
    return out
}

internal class Lobe(val peakIndex: Int, val peakUv: Double)

// Runs where the frontal signal dips below -level, split wherever it comes back above it.
internal fun lobes(frontal: DoubleArray, range: IntRange, level: Double): List<Lobe> {
    val out = ArrayList<Lobe>()
    var peak = 0.0
    var peakAt = -1
    for (i in range) {
        val v = frontal[i]
        if (!v.isNaN() && -v > level) {
            if (peakAt < 0 || -v > peak) {
                peak = -v
                peakAt = i
            }
        } else if (peakAt >= 0) {
            out += Lobe(peakAt, peak)
            peakAt = -1
        }
    }
    if (peakAt >= 0) out += Lobe(peakAt, peak)
    return out
}
