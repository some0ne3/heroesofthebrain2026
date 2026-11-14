package com.heroesofthebrain.halo.calibration

import com.heroesofthebrain.halo.calibration.CalibratedParam.BLINK_LOBE_UV
import com.heroesofthebrain.halo.calibration.CalibratedParam.CLENCH_OFF_RATIO
import com.heroesofthebrain.halo.calibration.CalibratedParam.CLENCH_ON_RATIO
import com.heroesofthebrain.halo.calibration.CalibratedParam.LID_DOUBLE_MAX_GAP_S
import com.heroesofthebrain.halo.calibration.CalibratedParam.LID_KICK_DPS
import com.heroesofthebrain.halo.calibration.CalibratedParam.LID_LONG_MIN_S
import com.heroesofthebrain.halo.calibration.CalibratedParam.TURN_GYRO_DPS
import com.heroesofthebrain.halo.calibration.CalibratedParam.TURN_LEFT_IS_POSITIVE_Y
import com.heroesofthebrain.halo.calibration.CalibratedParam.TURN_UP_IS_POSITIVE_Z
import com.heroesofthebrain.halo.calibration.CalibrationGesture.CLENCH
import com.heroesofthebrain.halo.calibration.CalibrationGesture.DOUBLE_BLINK
import com.heroesofthebrain.halo.calibration.CalibrationGesture.LONG_BLINK
import com.heroesofthebrain.halo.calibration.CalibrationGesture.TURN_DOWN
import com.heroesofthebrain.halo.calibration.CalibrationGesture.TURN_LEFT
import com.heroesofthebrain.halo.calibration.CalibrationGesture.TURN_RIGHT
import com.heroesofthebrain.halo.calibration.CalibrationGesture.TURN_UP
import com.heroesofthebrain.halo.calibration.ParameterStatus.CLAMPED
import com.heroesofthebrain.halo.calibration.ParameterStatus.DERIVED
import com.heroesofthebrain.halo.calibration.ParameterStatus.INCONSISTENT
import com.heroesofthebrain.halo.calibration.ParameterStatus.NOT_APPLICABLE
import com.heroesofthebrain.halo.calibration.ParameterStatus.NOT_CALIBRATED
import com.heroesofthebrain.halo.calibration.ParameterStatus.NOT_DETECTED
import com.heroesofthebrain.halo.calibration.ParameterStatus.NO_REFERENCE
import com.heroesofthebrain.halo.calibration.ParameterStatus.TOO_FEW_TRIALS
import com.heroesofthebrain.halo.detect.DetectorConfig
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class CalibrationAnalysis(
    val config: DetectorConfig,
    val thresholds: CalibratedThresholds,
    val parameters: List<ParameterReport>,
)

// Each threshold sits at a share of the user's weakest valid trial, but at least a margin above what the same
// feature does at rest (and during the other gestures). The shares are the margins the hand-tuned defaults have
// on recordings/ses-001 and ses-002, so that data comes out close to the defaults (CalibrationReplayTest).
object CalibrationAnalyzer {

    fun analyze(captures: CalibrationCaptures, base: DetectorConfig = DetectorConfig()): CalibrationAnalysis =
        Analysis(captures, base).run()

    internal const val MIN_TRIALS = 3
    internal const val VALID_OVER_NOISE = 1.5

    internal const val CLENCH_SHARE = 0.35
    internal const val CLENCH_REST_MARGIN = 1.5
    internal val CLENCH_ON_RANGE = 2.0..6.0
    internal const val CLENCH_OFF_OVER_REST_MEDIAN = 1.25
    internal const val CLENCH_OFF_MIN = 1.3
    internal const val CLENCH_OFF_MAX_SHARE = 0.85

    internal const val TURN_SHARE = 0.5
    internal const val TURN_NOISE_MARGIN = 1.25
    internal val TURN_RANGE = 30.0..90.0
    internal const val SIGN_AGREEMENT = 0.8

    internal const val KICK_SHARE = 0.65
    internal const val KICK_NOISE_MARGIN = 2.0
    internal const val KICK_NOISE_PERCENTILE = 0.99
    internal val KICK_RANGE = 8.0..30.0
    // A probe this far below the default finds weak kicks too, so the weakest trial is measured, not missed.
    internal const val KICK_PROBE_SHARE = 0.5
    internal const val LONG_PROBE_MIN_S = 0.5

    internal const val LONG_SHARE = 0.55
    internal const val LONG_OVER_SHORT = 1.25
    internal val LONG_RANGE = 0.5..1.5

    internal const val GAP_MARGIN = 1.5
    internal const val GAP_PLAUSIBLE_MAX_S = 1.5
    internal val GAP_RANGE = 0.5..1.2

    internal const val LOBE_SHARE = 0.3
    internal const val LOBE_NOISE_MARGIN = 2.0
    internal val LOBE_RANGE = 150.0..800.0
}

private class Analysis(private val captures: CalibrationCaptures, private val base: DetectorConfig) {
    private val series = captures.all.associate { it.step to it.series(base) }
    private val reports = ArrayList<ParameterReport>()

    private val rest = captures.rest
    private val restSeries = rest?.let { series.getValue(it.step) }
    private val restRange = rest?.let { restSeries!!.range(it.analysisStartMs, it.durationMs) }

    fun run(): CalibrationAnalysis {
        clench()
        turns()
        blinks()
        val thresholds = CalibratedThresholds.from(reports)
        return CalibrationAnalysis(thresholds.applyTo(base), thresholds, reports.sortedBy { it.param.ordinal })
    }

    private fun capture(g: CalibrationGesture) = captures.gestures[g]

    private fun seriesOf(c: StepCapture) = series.getValue(c.step)

    private fun trialFeatures(c: StepCapture, feature: (CaptureSeries, IntRange) -> Double): List<Double> {
        val s = seriesOf(c)
        return c.trials.map { feature(s, s.range(it)) }
    }

    private fun skip(params: List<CalibratedParam>, status: ParameterStatus, note: String) {
        params.forEach { reports += ParameterReport(it, it.defaultIn(base), it.defaultIn(base), status, note = note) }
    }

    // ---- clench: rest ratio vs. the level each clench held for clenchMinS

    private fun clench() {
        val c = capture(CLENCH) ?: return skip(listOf(CLENCH_ON_RATIO, CLENCH_OFF_RATIO), NOT_CALIBRATED, "Clench was not calibrated")
        val w = seriesOf(c).samples(base.clenchMinS)
        val peaks = trialFeatures(c) { s, r -> s.ratio.sustainedPeak(r, w) }
        val noise = restRange?.let { restSeries!!.ratio.sustainedPeak(it, w) }
        val on = derive(CLENCH_ON_RATIO, peaks, noise, CalibrationAnalyzer.CLENCH_SHARE, CalibrationAnalyzer.CLENCH_REST_MARGIN, CalibrationAnalyzer.CLENCH_ON_RANGE)
        reports += on
        reports += if (on.status.applied) {
            val restMedian = restSeries!!.ratio.percentile(listOf(restRange!!), 0.5)
            val raw = max(on.value * base.clenchOffRatio / base.clenchOnRatio, restMedian * CalibrationAnalyzer.CLENCH_OFF_OVER_REST_MEDIAN)
            val value = raw.coerceIn(CalibrationAnalyzer.CLENCH_OFF_MIN, on.value * CalibrationAnalyzer.CLENCH_OFF_MAX_SHARE)
            val note = "Same share of the start level as the default, and above the rest median ${CLENCH_OFF_RATIO.format(restMedian)}"
            ParameterReport(CLENCH_OFF_RATIO, value, base.clenchOffRatio, if (value == raw) DERIVED else CLAMPED, on.validTrials, on.trials, note)
        } else {
            on.copy(param = CLENCH_OFF_RATIO, value = base.clenchOffRatio, defaultValue = base.clenchOffRatio, note = "Follows the start level. ${on.note}")
        }
    }

    // ---- head turns: gyro magnitude held for turnMinS against rest and the other gestures, then which sign each way is

    private fun turns() {
        val signs = listOf(TURN_GYRO_DPS, TURN_LEFT_IS_POSITIVE_Y, TURN_UP_IS_POSITIVE_Z)
        val caps = captures.gestures.filterKeys { it.isTurn }.values
        if (caps.isEmpty()) return skip(signs, NOT_CALIBRATED, "Head turns were not calibrated")
        if (!captures.hasGyro) return skip(signs, NOT_APPLICABLE, "The headset sends no gyroscope data")
        val w = seriesOf(caps.first()).samples(base.turnMinS)
        val peaks = caps.flatMap { c -> trialFeatures(c) { s, r -> s.gyroMag!!.sustainedPeak(r, w) } }
        val others = captures.gestures.filterKeys { !it.isTurn }.values
        val noise = (
            listOfNotNull(restRange?.let { restSeries!!.gyroMag!!.sustainedPeak(it, w) }) +
                others.flatMap { c -> trialFeatures(c) { s, r -> s.gyroMag!!.sustainedPeak(r, w) } }
            ).filter { !it.isNaN() }.maxOrNull()
        reports += derive(TURN_GYRO_DPS, peaks, noise, CalibrationAnalyzer.TURN_SHARE, CalibrationAnalyzer.TURN_NOISE_MARGIN, CalibrationAnalyzer.TURN_RANGE)
        reports += turnSign(TURN_LEFT_IS_POSITIVE_Y, TURN_LEFT, TURN_RIGHT, "left", "y") { it.gy!! }
        reports += turnSign(TURN_UP_IS_POSITIVE_Z, TURN_UP, TURN_DOWN, "up", "z") { it.gz!! }
    }

    // Each cued trial votes on which sign its direction has; the opposite direction's trials vote the other way round.
    private fun turnSign(
        param: CalibratedParam,
        positive: CalibrationGesture,
        negative: CalibrationGesture,
        name: String,
        axis: String,
        rate: (CaptureSeries) -> DoubleArray,
    ): ParameterReport {
        val default = param.defaultIn(base)
        if (capture(positive) == null && capture(negative) == null) {
            return ParameterReport(param, default, default, NOT_CALIBRATED, note = "Neither ${positive.title.lowercase()} nor ${negative.title.lowercase()} was calibrated")
        }
        val bias = restRange?.let { rate(restSeries!!).meanIn(it) }?.takeIf { !it.isNaN() } ?: 0.0
        val votes = listOf(positive to 1, negative to -1).flatMap { (g, expected) ->
            capture(g)?.let { c -> trialFeatures(c) { s, r -> (rate(s).firstExcursionSign(r, s.sampleRate, bias, base.turnMinDeg) * expected).toDouble() } }.orEmpty()
        }
        val valid = votes.filter { it != 0.0 }
        trialStatus(valid.size, votes.size)?.let { (status, note) -> return ParameterReport(param, default, default, status, valid.size, votes.size, note) }
        val plus = valid.count { it > 0 }
        val agreeing = max(plus, valid.size - plus)
        val note = "$plus of ${valid.size} trials say $name is +$axis"
        if (agreeing < CalibrationAnalyzer.SIGN_AGREEMENT * valid.size) {
            return ParameterReport(param, default, default, INCONSISTENT, valid.size, votes.size, "$note, too mixed to decide")
        }
        return ParameterReport(param, if (plus > valid.size - plus) 1.0 else 0.0, default, DERIVED, valid.size, votes.size, note)
    }

    // ---- blinks

    private fun blinks() {
        val db = capture(DOUBLE_BLINK)
        val lb = capture(LONG_BLINK)
        val lidParams = listOf(LID_KICK_DPS, LID_LONG_MIN_S, LID_DOUBLE_MAX_GAP_S)
        if (db == null && lb == null) return skip(lidParams + BLINK_LOBE_UV, NOT_CALIBRATED, "Blinks were not calibrated")
        if (!captures.hasGyro) {
            skip(lidParams, NOT_APPLICABLE, "The headset sends no gyroscope data, so blinks use the EEG fallback")
            return eegBlinks(db)
        }
        skip(listOf(BLINK_LOBE_UV), NOT_APPLICABLE, "Only used when the headset sends no gyroscope data")
        lidBlinksFromGyro(db, lb)
    }

    private fun blinksPerTrial(c: StepCapture, kick: Double): List<List<LidBlink>> {
        val s = seriesOf(c)
        val all = lidBlinks(s.gz!!, s.sampleRate, kick, base.lidLongMaxS)
        return c.trials.map { t -> s.range(t).let { r -> all.filter { it.openIndex in r } } }
    }

    private fun closedS(c: StepCapture, b: LidBlink): Double? = b.closeIndex?.let { (b.openIndex - it).toDouble() / c.sampleRate }

    // The detector needs both kicks of a long blink but only the opening kicks of a double blink.
    private fun lidBlinksFromGyro(db: StepCapture?, lb: StepCapture?) {
        val noiseValues = buildList {
            if (restRange != null) restRange.forEach { i -> restSeries!!.gz!![i].let { if (!it.isNaN()) add(abs(it)) } }
            capture(CLENCH)?.let { c ->
                val s = seriesOf(c)
                c.trials.forEach { t -> s.range(t).forEach { i -> s.gz!![i].let { if (!it.isNaN()) add(abs(it)) } } }
            }
        }
        val noise = if (noiseValues.isEmpty()) null else percentileOf(noiseValues, CalibrationAnalyzer.KICK_NOISE_PERCENTILE)

        val probe = base.lidKickDps * CalibrationAnalyzer.KICK_PROBE_SHARE
        val doublePeaks = db?.let { c ->
            blinksPerTrial(c, probe).map { blinks ->
                val two = blinks.sortedByDescending { it.openPeak }.take(2)
                if (two.size < 2) Double.NaN else two.minOf { it.openPeak }
            }
        }.orEmpty()
        // Lids squeezed shut can twitch above a low probe, which would split one closure in two, so the long blink
        // takes its strongest opening kick and the strongest closing kick before it.
        val longPeaks = lb?.let { c ->
            trialFeatures(c) { s, r ->
                val gz = s.gz!!
                val open = r.filter { !gz[it].isNaN() }.maxByOrNull { gz[it] } ?: return@trialFeatures Double.NaN
                val close = (r.first until open).filter { !gz[it].isNaN() }.minByOrNull { gz[it] } ?: return@trialFeatures Double.NaN
                if ((open - close).toDouble() / s.sampleRate < CalibrationAnalyzer.LONG_PROBE_MIN_S) Double.NaN else min(-gz[close], gz[open])
            }
        }.orEmpty()
        val kickReport = derive(LID_KICK_DPS, doublePeaks + longPeaks, noise, CalibrationAnalyzer.KICK_SHARE, CalibrationAnalyzer.KICK_NOISE_MARGIN, CalibrationAnalyzer.KICK_RANGE)
        reports += kickReport
        val kick = if (kickReport.status.applied) kickReport.value else base.lidKickDps

        // Closures and gaps are measured again at the kick level the detector will actually use.
        val doubles = db?.let { c -> blinksPerTrial(c, kick).map { it.sortedByDescending { b -> b.openPeak }.take(2).sortedBy { b -> b.openIndex } } }
        val shortClosures = db?.let { c -> doubles!!.flatten().mapNotNull { closedS(c, it) } }.orEmpty()

        reports += if (lb == null) {
            ParameterReport(LID_LONG_MIN_S, base.lidLongMinS, base.lidLongMinS, NOT_CALIBRATED, note = "Long blink was not calibrated")
        } else {
            val longClosures = blinksPerTrial(lb, kick).map { blinks -> blinks.mapNotNull { closedS(lb, it) }.maxOrNull() ?: Double.NaN }
            derive(LID_LONG_MIN_S, longClosures, shortClosures.maxOrNull() ?: 0.0, CalibrationAnalyzer.LONG_SHARE, CalibrationAnalyzer.LONG_OVER_SHORT, CalibrationAnalyzer.LONG_RANGE)
        }

        reports += if (db == null) {
            ParameterReport(LID_DOUBLE_MAX_GAP_S, base.lidDoubleMaxGapS, base.lidDoubleMaxGapS, NOT_CALIBRATED, note = "Double blink was not calibrated")
        } else {
            doubleBlinkGap(doubles!!.map { two -> if (two.size < 2) Double.NaN else (two[1].openIndex - two[0].openIndex).toDouble() / db.sampleRate })
        }
    }

    // An upper bound, so it follows the user's slowest pair rather than the weakest one.
    private fun doubleBlinkGap(gaps: List<Double>): ParameterReport {
        val default = base.lidDoubleMaxGapS
        val valid = gaps.filter { it.isFinite() && it >= base.lidDoubleMinGapS && it <= CalibrationAnalyzer.GAP_PLAUSIBLE_MAX_S }
        trialStatus(valid.size, gaps.size)?.let { (status, note) -> return ParameterReport(LID_DOUBLE_MAX_GAP_S, default, default, status, valid.size, gaps.size, note) }
        val slowest = valid.max()
        val raw = CalibrationAnalyzer.GAP_MARGIN * slowest
        val value = raw.coerceIn(CalibrationAnalyzer.GAP_RANGE)
        val note = "Slowest pair ${LID_DOUBLE_MAX_GAP_S.format(slowest)} s apart" + clampNote(LID_DOUBLE_MAX_GAP_S, raw, value)
        return ParameterReport(LID_DOUBLE_MAX_GAP_S, value, default, if (value == raw) DERIVED else CLAMPED, valid.size, gaps.size, note)
    }

    private fun eegBlinks(db: StepCapture?) {
        if (db == null) {
            reports += ParameterReport(BLINK_LOBE_UV, base.blinkLobeUv, base.blinkLobeUv, NOT_CALIBRATED, note = "Double blink was not calibrated")
            return
        }
        val noise = (
            listOfNotNull(restRange?.let { restSeries!!.frontal.maxIn(it) { v -> -v } }) +
                listOfNotNull(capture(CLENCH)).flatMap { c -> trialFeatures(c) { s, r -> s.frontal.maxIn(r) { v -> -v } } }
            ).filter { !it.isNaN() }.maxOrNull()
        val level = base.blinkLobeUv / 2
        val peaks = trialFeatures(db) { s, r ->
            val found = lobes(s.frontal, r, level).sortedByDescending { it.peakUv }
            if (found.size < 2) Double.NaN else found[1].peakUv
        }
        reports += derive(BLINK_LOBE_UV, peaks, noise, CalibrationAnalyzer.LOBE_SHARE, CalibrationAnalyzer.LOBE_NOISE_MARGIN, CalibrationAnalyzer.LOBE_RANGE)
    }

    // ---- shared

    private fun derive(
        param: CalibratedParam,
        peaks: List<Double>,
        noise: Double?,
        share: Double,
        margin: Double,
        range: ClosedFloatingPointRange<Double>,
    ): ParameterReport {
        val default = param.defaultIn(base)
        if (noise == null || noise.isNaN()) {
            return ParameterReport(param, default, default, NO_REFERENCE, 0, peaks.size, "Needs the rest recording to compare with")
        }
        val valid = peaks.filter { it.isFinite() && it > noise * CalibrationAnalyzer.VALID_OVER_NOISE && it > range.start }
        trialStatus(valid.size, peaks.size)?.let { (status, note) -> return ParameterReport(param, default, default, status, valid.size, peaks.size, note) }
        val weakest = valid.min()
        val raw = max(share * weakest, margin * noise)
        val value = raw.coerceIn(range)
        val note = buildString {
            append("Weakest trial ${param.format(weakest)}, rest and other gestures up to ${param.format(noise)}")
            append(clampNote(param, raw, value))
            if (value >= weakest) append(". The weakest trial does not clear it")
        }
        return ParameterReport(param, value, default, if (value == raw) DERIVED else CLAMPED, valid.size, peaks.size, note)
    }

    private fun trialStatus(valid: Int, trials: Int): Pair<ParameterStatus, String>? {
        val needed = min(CalibrationAnalyzer.MIN_TRIALS, trials).coerceAtLeast(1)
        return when {
            valid == 0 -> NOT_DETECTED to "Not found in any of the $trials trials"
            valid < needed -> TOO_FEW_TRIALS to "Found in $valid of $trials trials, needs $needed"
            else -> null
        }
    }

    private fun clampNote(param: CalibratedParam, raw: Double, value: Double) =
        if (raw == value) "" else ". Held at ${param.format(value)} instead of ${param.format(raw)}"
}

internal fun percentileOf(values: List<Double>, p: Double): Double {
    val sorted = values.sorted()
    if (sorted.isEmpty()) return Double.NaN
    val x = p * (sorted.size - 1)
    val i = x.toInt()
    val f = x - i
    return if (i + 1 < sorted.size) sorted[i] * (1 - f) + sorted[i + 1] * f else sorted[i]
}
