package com.heroesofthebrain.halo.calibration

import com.heroesofthebrain.halo.eeg.EegFrame
import kotlinx.serialization.Serializable

// Times are ms from the capture's first sample, counted by sample index so they line up with detector timestamps.
@Serializable
data class TrialWindow(val index: Int, val startMs: Long, val endMs: Long, val prompt: String = "") {
    operator fun contains(tMs: Long) = tMs in startMs..endMs
}

class StepCapture(
    val step: CalibrationStep,
    val sampleRate: Int,
    val frames: List<EegFrame>,
    val trials: List<TrialWindow>,
    // Statistics skip the lead-in before this point.
    val analysisStartMs: Long,
    // Share of frames during which the step's channels had contact and passed the quality gate.
    val signalOkFraction: Float = 1f,
) {
    init {
        require(frames.isNotEmpty()) { "empty capture for ${step.key}" }
    }

    val firstSampleIndex: Long get() = frames.first().firstSampleIndex
    val endSampleIndex: Long get() = frames.last().let { it.firstSampleIndex + it.sampleCount }
    val sampleSpan: Int get() = (endSampleIndex - firstSampleIndex).toInt()
    val durationMs: Long get() = sampleSpan * 1000L / sampleRate
    val sampleCount: Int get() = frames.sumOf { it.sampleCount }
    val hasGyro: Boolean get() = frames.all { it.gyro != null }
}

data class CalibrationCaptures(
    val rest: StepCapture? = null,
    val gestures: Map<CalibrationGesture, StepCapture> = emptyMap(),
) {
    val all: List<StepCapture> get() = listOfNotNull(rest) + gestures.values

    fun with(capture: StepCapture): CalibrationCaptures = when (val step = capture.step) {
        CalibrationStep.Rest -> copy(rest = capture)
        is CalibrationStep.Gesture -> copy(gestures = gestures + (step.gesture to capture))
    }

    operator fun get(step: CalibrationStep): StepCapture? = when (step) {
        CalibrationStep.Rest -> rest
        is CalibrationStep.Gesture -> gestures[step.gesture]
    }

    val sampleRate: Int? get() = all.firstOrNull()?.sampleRate
    val hasGyro: Boolean get() = all.isNotEmpty() && all.all { it.hasGyro }
}

internal class CaptureBuilder(val step: CalibrationStep, val sampleRate: Int) {
    private val frames = ArrayList<EegFrame>()
    private val trials = ArrayList<TrialWindow>()
    private var analysisStartMs = 0L
    private var okFrames = 0

    val isEmpty: Boolean get() = frames.isEmpty()

    // Where the next sample will land. A cue stamps this, so its window starts up to one chunk plus the BLE
    // delay before the user actually heard the tone; nobody reacts that fast, so nothing is cut off.
    val nextOffsetMs: Long
        get() {
            if (frames.isEmpty()) return 0
            val first = frames.first().firstSampleIndex
            val next = frames.last().let { it.firstSampleIndex + it.sampleCount }
            return (next - first) * 1000 / sampleRate
        }

    // Index of the next sample we expect, or null before the first frame.
    val expectedIndex: Long? get() = frames.lastOrNull()?.let { it.firstSampleIndex + it.sampleCount }

    fun add(frame: EegFrame, signalOk: Boolean) {
        frames += frame
        if (signalOk) okFrames++
    }

    fun markAnalysisStart() {
        analysisStartMs = nextOffsetMs
    }

    fun openTrial(index: Int, durationMs: Long, prompt: String) {
        val start = nextOffsetMs
        trials += TrialWindow(index, start, start + durationMs, prompt)
    }

    fun build(): StepCapture? {
        if (frames.isEmpty()) return null
        return StepCapture(step, sampleRate, frames.toList(), trials.toList(), analysisStartMs, okFrames.toFloat() / frames.size)
    }
}
