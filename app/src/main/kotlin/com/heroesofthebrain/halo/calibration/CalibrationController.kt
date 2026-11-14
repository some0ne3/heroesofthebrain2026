package com.heroesofthebrain.halo.calibration

import com.heroesofthebrain.halo.detect.Detector
import com.heroesofthebrain.halo.eeg.EEG_CHANNELS
import com.heroesofthebrain.halo.eeg.EEG_LABELS
import com.heroesofthebrain.halo.eeg.EegFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CalibrationPlan(
    val gestures: List<CalibrationGesture> = CalibrationGesture.entries,
    val trialsPerGesture: Int = 5,
    val restMs: Long = 20_000,
    val leadInMs: Long = 3_000,
    val gapMs: Long = 2_000,
    val contactStableMs: Long = 2_000,
    val stallMs: Long = 3_000,
) {
    init {
        require(trialsPerGesture > 0) { "need at least one trial" }
    }
}

enum class StepPhase { GET_READY, RECORDING, TRIAL, PAUSE }

enum class CueKind { GO, STEP_DONE }

data class Cue(val step: CalibrationStep, val kind: CueKind, val trial: Int?, val prompt: String)

data class ChannelStatus(val label: String, val contact: Boolean, val signalOk: Boolean)

sealed interface CalibrationState {
    data object Idle : CalibrationState

    data class CheckingContact(
        val channels: List<ChannelStatus>,
        // null until the first frame arrives
        val hasGyro: Boolean?,
        val problems: List<String>,
        val heldMs: Long,
        val neededMs: Long,
        val upcoming: List<CalibrationStep>,
    ) : CalibrationState

    data class Running(
        val step: CalibrationStep,
        val stepNumber: Int,
        val stepCount: Int,
        val phase: StepPhase,
        // 0-based, only while trials run
        val trial: Int?,
        val trials: Int,
        val prompt: String,
        val phaseElapsedMs: Long,
        val phaseMs: Long,
        val signalOk: Boolean,
        val retry: Boolean,
    ) : CalibrationState

    data class Stalled(val step: CalibrationStep?, val message: String) : CalibrationState

    data class Finished(val captures: CalibrationCaptures, val skipped: Map<CalibrationGesture, String>) : CalibrationState

    data object Cancelled : CalibrationState
}

// Contact check, rest, then each gesture K times on cue. Not thread-safe: call it from one thread or coroutine.
// Feed it every frame plus a regular tick(), so cues still fire on time when frames bunch up or stop.
class CalibrationController(
    val plan: CalibrationPlan = CalibrationPlan(),
    private val clock: () -> Long,
    private val onCue: (Cue) -> Unit = {},
) {
    private val _state = MutableStateFlow<CalibrationState>(CalibrationState.Idle)
    val state: StateFlow<CalibrationState> = _state.asStateFlow()

    var captures = CalibrationCaptures()
        private set
    private val skipped = LinkedHashMap<CalibrationGesture, String>()

    private val pending = ArrayDeque<CalibrationStep>()
    private var stepCount = 0
    private var stepsDone = 0
    private var retrying = false

    private var mode: Mode = Mode.Idle
    private var lastFrameAtMs = 0L
    private var gate: Detector? = null
    private var sampleRate = 0
    private var contact = BooleanArray(EEG_CHANNELS)
    private var channelOk = BooleanArray(EEG_CHANNELS)
    private var hasGyro: Boolean? = null

    private sealed interface Mode {
        data object Idle : Mode
        class Checking(var stableSinceMs: Long? = null) : Mode
        class Running(val run: StepRun) : Mode
        class Stalled(val step: CalibrationStep?) : Mode
        data object Finished : Mode
        data object Cancelled : Mode
    }

    private class Segment(val phase: StepPhase, val durationMs: Long, val trial: Int?)

    private class StepRun(val step: CalibrationStep, val builder: CaptureBuilder, val segments: List<Segment>, var segStartMs: Long) {
        var index = 0
        val segment: Segment get() = segments[index]
    }

    fun start() {
        captures = CalibrationCaptures()
        skipped.clear()
        pending.clear()
        pending += CalibrationStep.Rest
        plan.gestures.distinct().forEach { pending += CalibrationStep.Gesture(it) }
        beginPass(retry = false)
    }

    // Redo one step after the results are in; the other captures stay.
    fun retry(step: CalibrationStep) {
        if (mode != Mode.Finished) return
        pending.clear()
        pending += step
        beginPass(retry = true)
    }

    fun skipContactCheck() {
        if (mode is Mode.Checking) startNextStep(clock())
    }

    fun cancel() {
        mode = Mode.Cancelled
        _state.value = CalibrationState.Cancelled
    }

    fun tick() = advance(clock())

    fun onFrame(frame: EegFrame) {
        val now = clock()
        if (mode == Mode.Idle || mode == Mode.Finished || mode == Mode.Cancelled) return
        lastFrameAtMs = now
        val g = gate?.takeIf { it.sampleRate == frame.sampleRate } ?: Detector(frame.sampleRate).also { gate = it }
        // Only the quality gate is used here; the events are thrown away.
        g.process(frame)
        sampleRate = frame.sampleRate
        channelOk = g.channelOk
        contact = frame.contact.copyOf()
        hasGyro = frame.gyro != null

        when (val m = mode) {
            is Mode.Stalled -> mode = Mode.Checking()
            is Mode.Checking -> dropStepsWithoutGyro()
            is Mode.Running -> {
                val b = m.run.builder
                val expected = b.expectedIndex
                val jump = if (expected == null) 0 else frame.firstSampleIndex - expected
                // A long gap or a restarted sample counter means the headset reconnected mid-step.
                if (frame.sampleRate != b.sampleRate || jump < 0 || jump > plan.stallMs * frame.sampleRate / 1000) {
                    pending.addFirst(m.run.step)
                    mode = Mode.Checking()
                } else {
                    b.add(frame, stepReady(m.run.step))
                }
            }
            else -> Unit
        }
        advance(now)
    }

    private fun beginPass(retry: Boolean) {
        retrying = retry
        stepCount = pending.size
        stepsDone = 0
        lastFrameAtMs = clock()
        mode = Mode.Checking()
        dropStepsWithoutGyro()
        advance(clock())
    }

    private fun dropStepsWithoutGyro() {
        if (hasGyro != false) return
        val removed = pending.filter { it is CalibrationStep.Gesture && it.gesture.needsGyro }
        if (removed.isEmpty()) return
        pending.removeAll(removed)
        removed.forEach { skipped[(it as CalibrationStep.Gesture).gesture] = "This headset sends no gyroscope data" }
        stepCount -= removed.size
    }

    private fun advance(now: Long) {
        when (val m = mode) {
            is Mode.Checking -> {
                if (pending.isEmpty()) return finish()
                if (now - lastFrameAtMs > plan.stallMs) return stall(null)
                val problems = problems(pending)
                if (problems.isEmpty()) {
                    val since = m.stableSinceMs ?: now.also { m.stableSinceMs = it }
                    if (now - since >= plan.contactStableMs) return startNextStep(now)
                } else {
                    m.stableSinceMs = null
                }
                _state.value = CalibrationState.CheckingContact(
                    channels = EEG_LABELS.mapIndexed { i, label -> ChannelStatus(label, contact[i], channelOk[i]) },
                    hasGyro = hasGyro,
                    problems = problems,
                    heldMs = m.stableSinceMs?.let { now - it } ?: 0,
                    neededMs = plan.contactStableMs,
                    upcoming = pending.toList(),
                )
            }
            is Mode.Running -> {
                if (now - lastFrameAtMs > plan.stallMs) {
                    pending.addFirst(m.run.step)
                    return stall(m.run.step)
                }
                val run = m.run
                while (now - run.segStartMs >= run.segment.durationMs) {
                    run.segStartMs += run.segment.durationMs
                    run.index++
                    if (run.index == run.segments.size) return finishStep(run, now)
                    enterSegment(run)
                }
                publishRunning(run, now)
            }
            else -> Unit
        }
    }

    private fun startNextStep(now: Long) {
        val step = pending.removeFirstOrNull() ?: return finish()
        val segments = buildList {
            add(Segment(StepPhase.GET_READY, plan.leadInMs, null))
            when (step) {
                CalibrationStep.Rest -> add(Segment(StepPhase.RECORDING, plan.restMs, null))
                is CalibrationStep.Gesture -> repeat(plan.trialsPerGesture) {
                    add(Segment(StepPhase.TRIAL, step.gesture.trialMs, it))
                    add(Segment(StepPhase.PAUSE, plan.gapMs, it))
                }
            }
        }
        val run = StepRun(step, CaptureBuilder(step, sampleRate.takeIf { it > 0 } ?: DEFAULT_RATE), segments, now)
        mode = Mode.Running(run)
        publishRunning(run, now)
    }

    private fun enterSegment(run: StepRun) {
        val seg = run.segment
        when (seg.phase) {
            StepPhase.RECORDING -> {
                run.builder.markAnalysisStart()
                onCue(Cue(run.step, CueKind.GO, null, prompt(run.step, seg)))
            }
            StepPhase.TRIAL -> {
                val gesture = (run.step as CalibrationStep.Gesture).gesture
                val trial = seg.trial ?: 0
                if (trial == 0) run.builder.markAnalysisStart()
                run.builder.openTrial(trial, gesture.trialMs, gesture.prompt)
                onCue(Cue(run.step, CueKind.GO, trial, gesture.prompt))
            }
            StepPhase.GET_READY, StepPhase.PAUSE -> Unit
        }
    }

    private fun finishStep(run: StepRun, now: Long) {
        onCue(Cue(run.step, CueKind.STEP_DONE, null, "Done"))
        run.builder.build()?.let { captures = captures.with(it) }
        stepsDone++
        if (pending.isEmpty()) finish() else startNextStep(now)
    }

    private fun finish() {
        mode = Mode.Finished
        _state.value = CalibrationState.Finished(captures, skipped.toMap())
    }

    private fun stall(step: CalibrationStep?) {
        mode = Mode.Stalled(step)
        val seconds = plan.stallMs / 1000
        _state.value = CalibrationState.Stalled(
            step,
            "No data from the headset for over $seconds s. " +
                if (step != null) "This step starts over when data comes back." else "Waiting for data.",
        )
    }

    private fun publishRunning(run: StepRun, now: Long) {
        val seg = run.segment
        _state.value = CalibrationState.Running(
            step = run.step,
            stepNumber = stepsDone + 1,
            stepCount = stepCount,
            phase = seg.phase,
            trial = seg.trial,
            trials = if (run.step is CalibrationStep.Gesture) plan.trialsPerGesture else 0,
            prompt = prompt(run.step, seg),
            phaseElapsedMs = (now - run.segStartMs).coerceIn(0, seg.durationMs),
            phaseMs = seg.durationMs,
            signalOk = stepReady(run.step),
            retry = retrying,
        )
    }

    private fun prompt(step: CalibrationStep, seg: Segment): String = when (seg.phase) {
        StepPhase.GET_READY -> when (step) {
            CalibrationStep.Rest -> "Sit still, relax your face and jaw, and look ahead. Recording starts at the tone."
            is CalibrationStep.Gesture -> step.gesture.instruction
        }
        StepPhase.RECORDING -> "Stay still and relaxed"
        StepPhase.TRIAL -> (step as CalibrationStep.Gesture).gesture.prompt
        StepPhase.PAUSE -> "Relax"
    }

    // Rest is the reference for every gesture, so it needs what all of them need.
    private fun gesturesServedBy(step: CalibrationStep): List<CalibrationGesture> = when (step) {
        CalibrationStep.Rest -> plan.gestures.filter { it !in skipped }
        is CalibrationStep.Gesture -> listOf(step.gesture)
    }

    private fun stepReady(step: CalibrationStep): Boolean {
        val gyro = hasGyro ?: return false
        return gesturesServedBy(step).all { it.signalReady(channelOk, gyro) }
    }

    private fun problems(steps: Collection<CalibrationStep>): List<String> {
        val gyro = hasGyro ?: return listOf("Waiting for data from the headset")
        return steps.flatMap { gesturesServedBy(it) }.distinct().mapNotNull { it.signalProblem(channelOk, gyro) }.distinct()
    }

    private companion object {
        const val DEFAULT_RATE = 250
    }
}
