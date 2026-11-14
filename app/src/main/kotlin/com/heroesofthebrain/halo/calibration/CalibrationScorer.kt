package com.heroesofthebrain.halo.calibration

import com.heroesofthebrain.halo.detect.Detector
import com.heroesofthebrain.halo.detect.DetectorConfig
import com.heroesofthebrain.halo.detect.GestureCancel
import com.heroesofthebrain.halo.detect.GestureEnd
import com.heroesofthebrain.halo.detect.GestureEvent
import com.heroesofthebrain.halo.detect.GestureStart

data class CalibrationResult(
    val analysis: CalibrationAnalysis,
    val scores: List<GestureScore>,
    val rest: RestScore?,
    // from the detector's clench ends with the derived thresholds
    val clenchHolds: HoldStats?,
    val sampleRate: Int,
    val hasGyro: Boolean,
    val trialsPerGesture: Int,
    val skipped: Map<CalibrationGesture, String>,
) {
    val config: DetectorConfig get() = analysis.config

    fun toStored(profileId: String, createdAtMs: Long, rawCaptureDir: String? = null) = StoredCalibration(
        profileId = profileId,
        createdAtMs = createdAtMs,
        sampleRate = sampleRate,
        hasGyro = hasGyro,
        trialsPerGesture = trialsPerGesture,
        thresholds = analysis.thresholds,
        parameters = analysis.parameters,
        clenchHolds = clenchHolds,
        scores = scores,
        rest = rest,
        skipped = skipped,
        rawCaptureDir = rawCaptureDir,
    )
}

class TrialTally(val hits: Int, val otherDetections: Int, val holdsMs: List<Long>, val cancelled: Int)

// Runs the real detector over each capture: a hit is the cued gesture starting (or happening) inside its trial window.
object CalibrationScorer {

    fun evaluate(
        captures: CalibrationCaptures,
        base: DetectorConfig = DetectorConfig(),
        skipped: Map<CalibrationGesture, String> = emptyMap(),
    ): CalibrationResult {
        val analysis = CalibrationAnalyzer.analyze(captures, base)
        var holds: HoldStats? = null
        val scores = captures.gestures.entries.sortedBy { it.key.ordinal }.map { (gesture, capture) ->
            val derived = tally(capture, gesture, analysis.config)
            val defaults = tally(capture, gesture, base)
            if (gesture == CalibrationGesture.CLENCH) holds = HoldStats.of(derived.holdsMs)
            GestureScore(
                gesture, capture.trials.size, derived.hits, derived.otherDetections, defaults.hits, defaults.otherDetections,
                capture.signalOkFraction, derived.cancelled,
            )
        }
        val rest = captures.rest?.let {
            RestScore(it.durationMs - it.analysisStartMs, falseDetections(it, analysis.config), falseDetections(it, base).size)
        }
        return CalibrationResult(
            analysis = analysis,
            scores = scores,
            rest = rest,
            clenchHolds = holds,
            sampleRate = captures.sampleRate ?: 0,
            hasGyro = captures.hasGyro,
            trialsPerGesture = captures.gestures.values.maxOfOrNull { it.trials.size } ?: 0,
            skipped = skipped,
        )
    }

    fun tally(capture: StepCapture, gesture: CalibrationGesture, config: DetectorConfig): TrialTally {
        val events = events(capture, config)
        val onsets = events.indices.filter { events[it].isOnset && events[it].timestampMs >= capture.analysisStartMs }
        val used = HashSet<Int>()
        val holds = ArrayList<Long>()
        var cancelled = 0
        for (window in capture.trials) {
            val hit = onsets.firstOrNull { it !in used && events[it].gesture == gesture && events[it].timestampMs in window } ?: continue
            used += hit
            val start = events[hit] as? GestureStart ?: continue
            when (val release = events.drop(hit + 1).firstOrNull { it.isRelease && it.type == start.type }) {
                is GestureEnd -> holds += release.durationMs
                is GestureCancel -> cancelled++
                else -> Unit
            }
        }
        // A turn and the way back both land in the window; only what falls outside or is the wrong gesture counts.
        val other = onsets.count { i ->
            i !in used && !(events[i].gesture == gesture && capture.trials.any { events[i].timestampMs in it })
        }
        return TrialTally(used.size, other, holds, cancelled)
    }

    fun falseDetections(capture: StepCapture, config: DetectorConfig): List<DetectionRecord> =
        events(capture, config).filter { it.isOnset && it.timestampMs >= capture.analysisStartMs }.map { DetectionRecord(it.gesture, it.timestampMs) }

    // Detector time starts at the capture's first sample and counts gaps, the same clock as the trial windows.
    fun events(capture: StepCapture, config: DetectorConfig): List<GestureEvent> {
        val detector = Detector(capture.sampleRate, config)
        return capture.frames.flatMap { detector.process(it) }
    }
}
