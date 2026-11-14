package com.heroesofthebrain.halo.detect

import com.heroesofthebrain.halo.eeg.CH_FP1
import com.heroesofthebrain.halo.eeg.CH_FP2
import com.heroesofthebrain.halo.eeg.EEG_CHANNELS
import com.heroesofthebrain.halo.eeg.EegFrame
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Values tuned with causal filters on recordings/ses-001 and ses-002 (see tools/convert_recordings.py and DetectorReplayTest).
data class DetectorConfig(
    val warmupS: Double = 0.5,
    val clenchLowHz: Double = 30.0,
    val clenchHighHz: Double = 100.0,
    val rmsWindowS: Double = 0.1,
    val baselineWindowS: Double = 6.0,
    val baselinePercentile: Double = 0.2,
    val clenchOnRatio: Double = 3.0,
    val clenchOffRatio: Double = 2.0,
    val clenchMinS: Double = 0.4,
    val clenchMaxGyroDps: Double = 20.0,
    val gyroSmoothS: Double = 0.2,
    val frontalVetoUv: Double = 350.0,
    val frontalVetoS: Double = 0.5,
    val turnGyroDps: Double = 50.0,
    val turnMinS: Double = 0.1,
    val turnReleaseFraction: Double = 0.6,
    // Nods in obrot-glowa-pion reach 16-34 deg from the start pose, horizontal swings 33-61 (tools/gesture_ground_truth.py).
    val turnMinDeg: Double = 12.0,
    // Coming back later than this counts as a turn of its own, since gyro drift makes the old pose unreliable.
    val turnReturnMaxS: Double = 6.0,
    // PROVISIONAL until checked on a device. The accelerometer puts +y up in every recording, so by the right-hand
    // rule +y should be a left turn. A lid-closing kick (band pulled down at the front) reads -z, so +z should be up.
    val turnLeftIsPositiveY: Boolean = true,
    val turnUpIsPositiveZ: Boolean = true,
    val blinkLobeUv: Double = 300.0,
    val blinkMinGapS: Double = 0.15,
    val blinkMaxGapS: Double = 0.6,
    val blinkLobeMaxWidthS: Double = 0.35,
    val blinkQuietBeforeS: Double = 0.4,
    val blinkThirdLobeFraction: Double = 0.4,
    val blinkMaxEpisodeS: Double = 1.3,
    // Gyro z kicks of a forceful blink: ses-001/002 blinks reach 25-45 deg/s, jaw and rest stay under 8.
    val lidKickDps: Double = 15.0,
    // Blinks peak at ~52 deg/s total, head turns at 100+.
    val lidMotionDps: Double = 75.0,
    val lidMotionGuardS: Double = 0.3,
    val lidConfirmS: Double = 0.2,
    // Short closures measured 0.1-0.58 s, deliberate long ones 1.44-1.52 s.
    val lidLongMinS: Double = 0.8,
    val lidLongMaxS: Double = 5.0,
    val lidDoubleMinGapS: Double = 0.15,
    val lidDoubleMaxGapS: Double = 0.8,
)

class Detector(val sampleRate: Int = 250, private val cfg: DetectorConfig = DetectorConfig()) {

    private val fs = sampleRate.toDouble()

    private val offset = DoubleArray(EEG_CHANNELS)
    private val hp = Array(EEG_CHANNELS) { Biquad.highPass(fs, 1.0) }
    private val hf = Array(EEG_CHANNELS) {
        arrayOf(
            Biquad.highPass(fs, cfg.clenchLowHz, Biquad.BUTTERWORTH4_Q1),
            Biquad.highPass(fs, cfg.clenchLowHz, Biquad.BUTTERWORTH4_Q2),
            Biquad.lowPass(fs, min(cfg.clenchHighHz, 0.45 * fs)),
        )
    }
    private val lf = Array(EEG_CHANNELS) { Biquad.lowPass(fs, 8.0) }
    private val display = Array(EEG_CHANNELS) { Biquad.lowPass(fs, 20.0) }

    private val hpVal = DoubleArray(EEG_CHANNELS)
    private val hfVal = DoubleArray(EEG_CHANNELS)
    private val lfVal = DoubleArray(EEG_CHANNELS)
    private val displayVal = FloatArray(DebugTrace.SERIES)

    private val rmsLen = samples(cfg.rmsWindowS)
    private val squares = Array(EEG_CHANNELS) { DoubleArray(rmsLen) }
    private val squareSum = DoubleArray(EEG_CHANNELS)
    private val rms = DoubleArray(EEG_CHANNELS)
    private var squarePos = 0

    private val decimation = max(1, (fs / DebugTrace.RATE_HZ).roundToInt())
    private val baselines = Array(EEG_CHANNELS) {
        RunningPercentile(max(5, (cfg.baselineWindowS * fs / decimation).roundToInt()), cfg.baselinePercentile)
    }
    private val baseline = DoubleArray(EEG_CHANNELS)
    private val quality = ChannelQuality(fs, cfg.warmupS)

    private val gyroWindow = DoubleArray(samples(cfg.gyroSmoothS))
    private var gyroSum = 0.0
    private var gyroPos = 0
    private var gyroFilled = 0

    private var clock = 0L
    private var lastIndex = Long.MIN_VALUE
    private var resetPending = true
    private var sinceReset = 0L
    private var warmUntilS = 0.0
    private var contact = BooleanArray(EEG_CHANNELS) { true }

    var missedSamples = 0L
        private set
    var lastRatio = 0.0
        private set

    val trace = DebugTrace()

    val channelOk: BooleanArray get() = quality.ok.copyOf()

    fun process(frame: EegFrame): List<GestureEvent> {
        require(frame.sampleRate == sampleRate) { "Detector built for $sampleRate Hz got ${frame.sampleRate} Hz" }
        val out = ArrayList<GestureEvent>(2)
        contact = frame.contact
        for (s in 0 until frame.sampleCount) {
            val index = frame.firstSampleIndex + s
            if (lastIndex != Long.MIN_VALUE && index != lastIndex + 1) {
                if (index > lastIndex + 1) clock += index - lastIndex - 1
                interrupt(out)
            }
            lastIndex = index
            // Gap-filled samples are all zeros; feeding them would look like a huge artefact.
            if (frame.isDummy[s]) {
                missedSamples++
                interrupt(out)
                clock++
                continue
            }
            if (resetPending) {
                resetAt(frame, s)
                resetPending = false
            }
            step(frame, s, out)
            clock++
            sinceReset++
        }
        return out
    }

    // A hold that spans a gap cannot be measured, so it is cancelled right away rather than when samples return.
    private fun interrupt(out: MutableList<GestureEvent>) {
        resetPending = true
        clenchStart?.let { out += GestureCancel(GestureType.CLENCH, ms(clock / fs), it.confidence, CancelReason.SIGNAL_LOST) }
        clenchStart = null
    }

    private fun resetAt(frame: EegFrame, s: Int) {
        val nowS = clock / fs
        for (c in 0 until EEG_CHANNELS) {
            // Starting from the first value keeps the multi-thousand-µV DC offset out of the filters.
            offset[c] = frame.eeg(s, c).toDouble()
            hp[c].reset()
            hf[c].forEach { it.reset() }
            lf[c].reset()
            display[c].reset()
            squares[c].fill(0.0)
            squareSum[c] = 0.0
            rms[c] = 0.0
        }
        squarePos = 0
        gyroWindow.fill(0.0)
        gyroSum = 0.0
        gyroPos = 0
        gyroFilled = 0
        sinceReset = 0
        warmUntilS = nowS + cfg.warmupS
        quality.reset(nowS)
        clenchSinceS = -1.0
        clenchStart = null
        clenchTaint = null
        turnSinceS = -1.0
        headAngle.fill(0.0)
        awaySign.fill(0)
        lobes.clear()
        lobe = null
        halfStartS = -1.0
        envAboveSinceS = -1.0
        pendingA = null
        pendingB = null
        closeArmed = true
        openArmed = true
        closeS = -1.0
        openKick = null
        shortBlink = null
        lidPending = null
        lastMotionS = -1e9
    }

    private fun step(frame: EegFrame, s: Int, out: MutableList<GestureEvent>) {
        val nowS = clock / fs

        for (c in 0 until EEG_CHANNELS) {
            val x = frame.eeg(s, c) - offset[c]
            val h = hp[c].step(x)
            hpVal[c] = h
            var y = x
            for (b in hf[c]) y = b.step(y)
            hfVal[c] = y
            lfVal[c] = lf[c].step(h)
            displayVal[c] = display[c].step(h).toFloat()

            val sq = y * y
            squareSum[c] += sq - squares[c][squarePos]
            squares[c][squarePos] = sq
        }
        squarePos = (squarePos + 1) % rmsLen
        val filled = min(sinceReset + 1, rmsLen.toLong()).toInt()
        for (c in 0 until EEG_CHANNELS) {
            // Re-sum once per window so the running sum cannot drift.
            if (squarePos == 0) squareSum[c] = squares[c].sum()
            rms[c] = sqrt(max(squareSum[c], 0.0) / filled)
        }

        quality.update(nowS, hpVal, hfVal, contact)
        val ok = quality.ok

        if (sinceReset % decimation == 0L && sinceReset >= rmsLen) {
            for (c in 0 until EEG_CHANNELS) {
                baselines[c].add(rms[c])
                if (baselines[c].count >= MIN_BASELINE_POINTS) baseline[c] = baselines[c].value()
            }
        }

        var okCount = 0
        var ratioSum = 0.0
        for (c in 0 until EEG_CHANNELS) {
            if (!ok[c] || baselines[c].count < MIN_BASELINE_POINTS) continue
            // Each channel against its own baseline, so dropping a noisy channel does not make the mean jump.
            ratioSum += rms[c] / max(baseline[c], MIN_BASELINE_UV)
            okCount++
        }
        val ratio = if (okCount > 0) ratioSum / okCount else 0.0
        lastRatio = ratio

        var frontal = 0.0
        var envelope = 0.0
        var frontalOk = 0
        for (c in FRONTAL) {
            if (!ok[c]) continue
            frontalOk++
            frontal = min(frontal, lfVal[c])
            envelope = max(envelope, abs(lfVal[c]))
        }
        if (envelope > cfg.frontalVetoUv) lastBigFrontalS = nowS

        var gyroSmooth: Double? = null
        var gyroMag = 0.0
        val gyro = frame.gyro
        if (gyro != null && gyro.size >= (s + 1) * 3) {
            val gx = gyro[s * 3].toDouble()
            val gy = gyro[s * 3 + 1].toDouble()
            val gz = gyro[s * 3 + 2].toDouble()
            gyroMag = sqrt(gx * gx + gy * gy + gz * gz)
            gyroSum += gyroMag - gyroWindow[gyroPos]
            gyroWindow[gyroPos] = gyroMag
            gyroPos = (gyroPos + 1) % gyroWindow.size
            gyroFilled = min(gyroFilled + 1, gyroWindow.size)
            gyroSmooth = gyroSum / gyroFilled
            updateTurn(nowS, gx, gy, gz, gyroMag, out)
            updateLids(nowS, gz, gyroMag, out)
        }

        updateClench(nowS, ratio, okCount, gyroSmooth, out)
        // Blink EEG is mostly electrode shifts that saturate (ses-002), so EEG blinks are only the no-gyro fallback.
        if (gyroSmooth == null) updateDoubleBlink(nowS, frontal, envelope, frontalOk, out)

        if (sinceReset % decimation == 0L) {
            displayVal[EEG_CHANNELS] = gyroMag.toFloat()
            trace.push(displayVal)
        }
    }

    // ---- CLENCH: broadband 30-100 Hz power on all channels, held for a while, head still, no blink nearby

    private var clenchSinceS = -1.0
    private var clenchStart: GestureStart? = null
    private var clenchTaint: CancelReason? = null
    private var lastBigFrontalS = -1e9

    // A taint before the start means no events at all. After the start it cancels the hold: the 30-100 Hz power
    // from the movement or blink hides when the jaw actually let go, so any duration reported would be made up.
    private fun updateClench(nowS: Double, ratio: Double, okCount: Int, gyroSmooth: Double?, out: MutableList<GestureEvent>) {
        val usable = nowS >= warmUntilS && okCount >= MIN_CLENCH_CHANNELS
        if (usable && ratio > cfg.clenchOnRatio) {
            if (clenchSinceS < 0) {
                clenchSinceS = nowS
                clenchTaint = null
            }
            checkClenchTaint(nowS, gyroSmooth, out)
            if (clenchStart == null && clenchTaint == null && nowS - clenchSinceS >= cfg.clenchMinS) {
                val conf = scaled(ratio, cfg.clenchOnRatio, 4 * cfg.clenchOnRatio) * okCount / EEG_CHANNELS
                val start = GestureStart(GestureType.CLENCH, ms(nowS), confidence(conf), ms(clenchSinceS))
                clenchStart = start
                out += start
            }
        } else if (!usable || ratio < cfg.clenchOffRatio) {
            clenchStart?.let {
                out += if (usable) {
                    GestureEnd(GestureType.CLENCH, ms(nowS), it.confidence, ms(nowS) - it.onsetMs)
                } else {
                    GestureCancel(GestureType.CLENCH, ms(nowS), it.confidence, CancelReason.SIGNAL_LOST)
                }
            }
            clenchSinceS = -1.0
            clenchStart = null
        } else if (clenchStart != null) {
            // A held clench stays watched while it dips between the two thresholds.
            checkClenchTaint(nowS, gyroSmooth, out)
        }
    }

    private fun checkClenchTaint(nowS: Double, gyroSmooth: Double?, out: MutableList<GestureEvent>) {
        if (clenchTaint != null) return
        // Blinks and eye closure leak into 30-100 Hz too; the big frontal low-frequency swing gives them away.
        val reason = when {
            gyroSmooth != null && gyroSmooth > cfg.clenchMaxGyroDps -> CancelReason.HEAD_MOTION
            nowS - lastBigFrontalS < cfg.frontalVetoS -> CancelReason.BLINK
            else -> return
        }
        clenchTaint = reason
        clenchStart?.let { out += GestureCancel(GestureType.CLENCH, ms(nowS), it.confidence, reason) }
        clenchStart = null
    }

    // ---- HEAD TURN: gyro magnitude well above what facial movements produce; the dominant axis gives direction.
    // The integrated angle says which side of the rest pose the head is on, so the way back is not a new turn,
    // while a swing that carries on past the rest pose is (obrot-glowa-* swing side to side without stopping).

    private var turnSinceS = -1.0
    private var turnPeak = 0.0
    private var lastTurnS = -1e9
    private val axisEnergy = DoubleArray(3)
    private val axisSum = DoubleArray(3)
    private val headAngle = DoubleArray(3)
    private val awaySign = IntArray(3)
    private val awayPeak = DoubleArray(3)
    private val awaySinceS = DoubleArray(3)

    private fun updateTurn(nowS: Double, gx: Double, gy: Double, gz: Double, g: Double, out: MutableList<GestureEvent>) {
        headAngle[0] += gx / fs
        headAngle[1] += gy / fs
        headAngle[2] += gz / fs
        if (g > cfg.turnGyroDps) {
            lastTurnS = nowS
            if (turnSinceS < 0) {
                turnSinceS = nowS
                axisEnergy.fill(0.0)
                axisSum.fill(0.0)
                turnPeak = 0.0
                // Where the head rested when it started moving is the pose a turn is measured from.
                for (a in TURN_AXES) if (awaySign[a] == 0) headAngle[a] = 0.0
            }
            axisEnergy[0] += gx * gx
            axisEnergy[1] += gy * gy
            axisEnergy[2] += gz * gz
            axisSum[0] += gx
            axisSum[1] += gy
            axisSum[2] += gz
            turnPeak = max(turnPeak, g)
            val dominant = when {
                axisEnergy[0] >= axisEnergy[1] && axisEnergy[0] >= axisEnergy[2] -> 0
                axisEnergy[1] >= axisEnergy[2] -> AXIS_YAW
                else -> AXIS_PITCH
            }
            // x dominant is a sideways tilt, which is not reported
            if (dominant in TURN_AXES && nowS - turnSinceS >= cfg.turnMinS) {
                val sign = if (axisSum[dominant] > 0) 1 else -1
                if (sign != awaySign[dominant] && headAngle[dominant] * sign >= cfg.turnMinDeg) {
                    awaySign[dominant] = sign
                    awayPeak[dominant] = headAngle[dominant] * sign
                    awaySinceS[dominant] = nowS
                    val share = axisEnergy[dominant] / axisEnergy.sum()
                    val purity = ((share - 1.0 / 3) / (2.0 / 3)).coerceIn(0.0, 1.0)
                    val conf = scaled(turnPeak, cfg.turnGyroDps, 4 * cfg.turnGyroDps) * (0.5 + 0.5 * purity)
                    out += instant(turnType(dominant, sign), conf, nowS)
                }
            }
        } else if (g < cfg.turnGyroDps * cfg.turnReleaseFraction) {
            turnSinceS = -1.0
        }
        for (a in TURN_AXES) {
            val sign = awaySign[a]
            if (sign == 0) continue
            val away = headAngle[a] * sign
            awayPeak[a] = max(awayPeak[a], away)
            // Half way back is enough: returns rarely land exactly on the old pose.
            if (away < max(cfg.turnMinDeg / 2, awayPeak[a] / 2) || nowS - awaySinceS[a] > cfg.turnReturnMaxS) awaySign[a] = 0
        }
    }

    private fun turnType(axis: Int, sign: Int): GestureType =
        if (axis == AXIS_YAW) {
            if ((sign > 0) == cfg.turnLeftIsPositiveY) GestureType.TURN_LEFT else GestureType.TURN_RIGHT
        } else {
            if ((sign > 0) == cfg.turnUpIsPositiveZ) GestureType.TURN_UP else GestureType.TURN_DOWN
        }

    // ---- EYELIDS (gyro): a forceful blink jerks the headband about z, down when the lids close and up when they open.
    // Closed time tells a blink from a long blink; two short blinks in a row make a double blink.

    private class Blink(val startS: Double, val openS: Double, val closePeak: Double) {
        var openPeak = 0.0
    }

    private class LidEvent(val type: GestureType, val first: Blink, val last: Blink, val decideS: Double)

    private var closeArmed = true
    private var openArmed = true
    private var closeS = -1.0
    private var closePeak = 0.0
    private var openKick: Blink? = null
    private var shortBlink: Blink? = null
    private var lidPending: LidEvent? = null
    private var lastMotionS = -1e9

    private fun updateLids(nowS: Double, gz: Double, g: Double, out: MutableList<GestureEvent>) {
        val kick = cfg.lidKickDps
        if (g > cfg.lidMotionDps) lastMotionS = nowS

        if (gz < -kick) {
            if (closeArmed) {
                closeArmed = false
                closeS = nowS
                closePeak = 0.0
            }
            closePeak = max(closePeak, -gz)
        } else if (gz > -kick / 2) {
            closeArmed = true
        }

        if (gz > kick) {
            if (openArmed) {
                openArmed = false
                onLidsOpen(nowS)
            }
            openKick?.let { it.openPeak = max(it.openPeak, gz) }
        } else if (gz < kick / 2) {
            openArmed = true
            openKick = null
        }

        // Wait a moment so a head turn starting right after the kick can still veto it.
        val p = lidPending ?: return
        if (nowS < p.decideS) return
        lidPending = null
        if (lastMotionS >= p.first.startS - cfg.lidMotionGuardS) return
        val peak = when (p.type) {
            GestureType.LONG_BLINK -> min(p.last.closePeak, p.last.openPeak)
            else -> min(p.first.openPeak, p.last.openPeak)
        }
        out += instant(p.type, scaled(peak, kick, 4 * kick), nowS)
    }

    private fun onLidsOpen(nowS: Double) {
        val closed = if (closeS >= 0 && nowS - closeS <= cfg.lidLongMaxS) nowS - closeS else 0.0
        val blink = Blink(if (closed > 0) closeS else nowS, nowS, closePeak)
        closeS = -1.0
        openKick = blink
        val decideS = nowS + cfg.lidConfirmS
        val prev = shortBlink
        when {
            closed >= cfg.lidLongMinS -> {
                lidPending = LidEvent(GestureType.LONG_BLINK, blink, blink, decideS)
                shortBlink = null
            }
            // Greedy pairing: ses-001 pairs sat 0.28 s apart but only 0.4 s from the next pair, so no third-blink veto.
            prev != null && nowS - prev.openS in cfg.lidDoubleMinGapS..cfg.lidDoubleMaxGapS -> {
                lidPending = LidEvent(GestureType.DOUBLE_BLINK, prev, blink, decideS)
                shortBlink = null
            }
            else -> shortBlink = blink
        }
    }

    // ---- DOUBLE BLINK, EEG fallback when the device sends no gyro: two short negative Fp 1-8 Hz lobes ~0.25 s apart (negative because the reference sits at Fpz)

    private class Lobe(val startS: Double, var peakUv: Double, var peakS: Double) {
        var endS = 0.0
        var widthS = 0.0
        var used = false
    }

    private val lobes = ArrayDeque<Lobe>()
    private var lobe: Lobe? = null
    private var halfStartS = -1.0
    private var envAboveSinceS = -1.0
    private var pendingA: Lobe? = null
    private var pendingB: Lobe? = null

    private fun updateDoubleBlink(nowS: Double, frontal: Double, envelope: Double, frontalOk: Int, out: MutableList<GestureEvent>) {
        val level = cfg.blinkLobeUv
        if (frontal < -level / 2) {
            if (halfStartS < 0) halfStartS = nowS
            if (frontal < -level) {
                val current = lobe
                if (current == null) {
                    lobe = Lobe(halfStartS, -frontal, nowS)
                } else if (-frontal > current.peakUv) {
                    current.peakUv = -frontal
                    current.peakS = nowS
                }
            }
        } else {
            lobe?.let {
                it.endS = nowS
                it.widthS = nowS - it.startS
                lobes.addLast(it)
            }
            lobe = null
            halfStartS = -1.0
        }
        envAboveSinceS = if (envelope > level / 2) (if (envAboveSinceS < 0) nowS else envAboveSinceS) else -1.0
        while (lobes.isNotEmpty() && nowS - lobes.first().peakS > LOBE_MEMORY_S) lobes.removeFirst()

        if (pendingA == null && lobes.size >= 2) {
            val a = lobes[lobes.size - 2]
            val b = lobes[lobes.size - 1]
            if (!a.used && !b.used) {
                val gap = b.peakS - a.peakS
                val before = if (lobes.size >= 3) lobes[lobes.size - 3] else null
                val quietBefore = before == null || before.used || a.peakS - before.peakS >= cfg.blinkQuietBeforeS
                // A wide lobe means the signal stayed deflected, which is what eye closure looks like.
                val narrow = a.widthS <= cfg.blinkLobeMaxWidthS && b.widthS <= cfg.blinkLobeMaxWidthS
                if (gap in cfg.blinkMinGapS..cfg.blinkMaxGapS && narrow && quietBefore) {
                    pendingA = a
                    pendingB = b
                } else {
                    a.used = true
                }
            }
        }

        val a = pendingA ?: return
        val b = pendingB ?: return
        val minPeak = min(a.peakUv, b.peakUv)
        val thirdLevel = max(level, cfg.blinkThirdLobeFraction * minPeak)
        val inProgress = lobe
        val third = lobes.any { it !== a && it !== b && it.peakS > b.peakS && it.peakUv >= thirdLevel } ||
            (inProgress != null && inProgress.startS > b.endS && inProgress.peakUv >= thirdLevel)
        val episode = nowS - a.startS
        when {
            third -> finishPair(a, b)
            // Decide once the frontal activity has settled, so a third blink or a lasting eye closure can still veto it.
            envAboveSinceS < 0 && nowS > b.endS -> {
                val headStill = lastTurnS < a.startS - TURN_GUARD_S
                if (episode <= cfg.blinkMaxEpisodeS && headStill && frontalOk > 0) {
                    val gap = b.peakS - a.peakS
                    val timing = 1 - 0.5 * (abs(gap - IDEAL_BLINK_GAP_S) / IDEAL_BLINK_GAP_S).coerceIn(0.0, 1.0)
                    val q = if (frontalOk == 2) 1.0 else 0.75
                    out += instant(GestureType.DOUBLE_BLINK, scaled(minPeak, level, 5 * level) * timing * q, nowS)
                }
                finishPair(a, b)
            }
            episode > cfg.blinkMaxEpisodeS -> finishPair(a, b)
        }
    }

    private fun finishPair(a: Lobe, b: Lobe) {
        a.used = true
        b.used = true
        pendingA = null
        pendingB = null
    }

    private fun instant(type: GestureType, score: Double, nowS: Double) =
        GestureInstant(type, ms(nowS), confidence(score))

    private fun samples(seconds: Double) = max(1, (seconds * fs).roundToInt())

    private companion object {
        val FRONTAL = intArrayOf(CH_FP2, CH_FP1)
        const val AXIS_YAW = 1
        const val AXIS_PITCH = 2
        val TURN_AXES = intArrayOf(AXIS_YAW, AXIS_PITCH)
        const val MIN_BASELINE_POINTS = 5
        const val MIN_BASELINE_UV = 0.5
        const val MIN_CLENCH_CHANNELS = 2
        const val LOBE_MEMORY_S = 3.0
        const val TURN_GUARD_S = 0.2
        const val IDEAL_BLINK_GAP_S = 0.3
        const val CONF_FLOOR = 0.4

        fun ms(seconds: Double) = (seconds * 1000).toLong()

        fun confidence(value: Double) = value.coerceIn(0.0, 1.0).toFloat()

        // Just over the threshold gives CONF_FLOOR, reaching `saturation` gives 1.
        fun scaled(value: Double, threshold: Double, saturation: Double): Double {
            if (value <= threshold) return CONF_FLOOR
            val x = ln(value / threshold) / ln(saturation / threshold)
            return (CONF_FLOOR + (1 - CONF_FLOOR) * x).coerceIn(0.0, 1.0)
        }
    }
}

internal class RunningPercentile(private val capacity: Int, private val p: Double) {
    private val ring = DoubleArray(capacity)
    private val scratch = DoubleArray(capacity)
    private var pos = 0
    var count = 0
        private set

    fun add(v: Double) {
        ring[pos] = v
        pos = (pos + 1) % capacity
        if (count < capacity) count++
    }

    fun value(): Double {
        if (count == 0) return 0.0
        System.arraycopy(ring, 0, scratch, 0, count)
        java.util.Arrays.sort(scratch, 0, count)
        val x = p * (count - 1)
        val i = x.toInt()
        val f = x - i
        return if (i + 1 < count) scratch[i] * (1 - f) + scratch[i + 1] * f else scratch[i]
    }
}

// Dry electrodes can report good contact while the channel is junk, so this gate looks at the signal itself.
internal class ChannelQuality(fs: Double, private val warmupS: Double) {
    private val lfAlpha = 1 - kotlin.math.exp(-1 / (1.0 * fs))
    private val hfAlpha = 1 - kotlin.math.exp(-1 / (4.0 * fs))
    private val lfPower = DoubleArray(EEG_CHANNELS)
    private val hfPower = DoubleArray(EEG_CHANNELS)
    private val badUntilS = DoubleArray(EEG_CHANNELS)
    private val scratch = DoubleArray(EEG_CHANNELS)
    private var n = 0L
    private var startS = 0.0
    val ok = BooleanArray(EEG_CHANNELS) { true }

    fun reset(nowS: Double) {
        lfPower.fill(0.0)
        hfPower.fill(0.0)
        badUntilS.fill(-1.0)
        n = 0
        startS = nowS
    }

    fun update(nowS: Double, hp: DoubleArray, hf: DoubleArray, contact: BooleanArray) {
        n++
        // Plain running mean until the EMA has enough history, so levels are right from the start.
        val la = max(lfAlpha, 1.0 / n)
        val ha = max(hfAlpha, 1.0 / n)
        for (c in 0 until EEG_CHANNELS) {
            lfPower[c] += la * (hp[c] * hp[c] - lfPower[c])
            hfPower[c] += ha * (hf[c] * hf[c] - hfPower[c])
        }
        val lfMedian = median { sqrt(lfPower[it]) }
        val hfMedian = median { sqrt(hfPower[it]) }
        val judging = nowS - startS >= warmupS
        for (c in 0 until EEG_CHANNELS) {
            val lfLevel = sqrt(lfPower[c])
            val hfLevel = sqrt(hfPower[c])
            val bad = !contact[c] || (judging && (
                lfLevel < FLAT_UV ||
                    lfLevel > ABSURD_UV ||
                    (lfLevel > RELATIVE_FACTOR * lfMedian && lfLevel > LF_RELATIVE_MIN_UV) ||
                    (hfLevel > RELATIVE_FACTOR * hfMedian && hfLevel > HF_RELATIVE_MIN_UV)
                ))
            if (bad) badUntilS[c] = nowS + HOLD_S
            ok[c] = nowS >= badUntilS[c]
        }
    }

    private inline fun median(level: (Int) -> Double): Double {
        for (c in 0 until EEG_CHANNELS) scratch[c] = level(c)
        scratch.sort()
        return (scratch[1] + scratch[2]) / 2
    }

    private companion object {
        const val FLAT_UV = 0.5
        const val ABSURD_UV = 3000.0
        const val RELATIVE_FACTOR = 8.0
        const val LF_RELATIVE_MIN_UV = 150.0
        const val HF_RELATIVE_MIN_UV = 20.0
        const val HOLD_S = 1.0
    }
}

class DebugTrace {
    private val data = Array(SERIES) { FloatArray(CAPACITY) }
    private var head = 0
    private var filled = 0

    fun push(values: FloatArray) {
        for (s in 0 until SERIES) data[s][head] = values[s]
        head = (head + 1) % CAPACITY
        if (filled < CAPACITY) filled++
    }

    fun snapshot(): List<FloatArray> = List(SERIES) { s ->
        FloatArray(filled) { i -> data[s][(head - filled + i + CAPACITY) % CAPACITY] }
    }

    companion object {
        const val RATE_HZ = 50
        const val SECONDS = 5
        const val CAPACITY = RATE_HZ * SECONDS
        const val SERIES = EEG_CHANNELS + 1
    }
}
