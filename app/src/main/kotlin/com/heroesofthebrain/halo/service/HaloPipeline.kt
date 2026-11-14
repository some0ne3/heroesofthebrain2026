package com.heroesofthebrain.halo.service

import com.heroesofthebrain.halo.detect.Detector
import com.heroesofthebrain.halo.detect.DetectorConfig
import com.heroesofthebrain.halo.detect.GestureEvent
import com.heroesofthebrain.halo.eeg.EEG_LABELS
import com.heroesofthebrain.halo.eeg.EegFrame
import com.heroesofthebrain.halo.eeg.EegSource
import com.heroesofthebrain.halo.eeg.SourceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SourceRequest {
    data object Headset : SourceRequest
    data class Replay(val file: String) : SourceRequest
}

data class ChannelState(val label: String, val contact: Boolean, val signalOk: Boolean)

data class PipelineState(
    val running: Boolean = false,
    val source: SourceRequest? = null,
    val status: SourceStatus = SourceStatus.Idle,
    // True from a link loss until the stream is back, whatever the source reports in between.
    val linkLost: Boolean = false,
    val batteryPct: Float? = null,
    val channels: List<ChannelState> = EEG_LABELS.map { ChannelState(it, contact = false, signalOk = false) },
    val hasGyro: Boolean? = null,
    val clenchRatio: Double = 0.0,
    val missedSamples: Long = 0,
    val armed: Boolean = false,
) {
    val actionBlock: ActionBlock? get() = actionBlock(running, status, linkLost, armed)
    val actionsAllowed: Boolean get() = actionBlock == null
}

// Source -> detector -> state, with no Android in it so tests can drive it with a replay or a fake source.
// Only HaloService calls start(), so a run always has a foreground service keeping the process alive.
class HaloPipeline(
    private val scope: CoroutineScope,
    private val configProvider: () -> DetectorConfig = { DetectorConfig() },
    private val openSource: (SourceRequest) -> EegSource,
) {
    private val _state = MutableStateFlow(PipelineState())
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    // A collector that falls far behind loses the oldest frames rather than stalling detection.
    // firstSampleIndex shows where the gap is.
    private val _frames = MutableSharedFlow<EegFrame>(extraBufferCapacity = FRAME_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val frames: SharedFlow<EegFrame> = _frames.asSharedFlow()

    // Detector output as is. A hold open when the link drops is only cancelled (SIGNAL_LOST) once samples come back,
    // and a stopped run cancels nothing, so consumers should also drop open holds when actionsAllowed or running goes false.
    private val _events = MutableSharedFlow<GestureEvent>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<GestureEvent> = _events.asSharedFlow()

    private val _linkAlerts = MutableSharedFlow<LinkAlert>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val linkAlerts: SharedFlow<LinkAlert> = _linkAlerts.asSharedFlow()

    private val _trace = MutableStateFlow<List<FloatArray>>(emptyList())
    val trace: StateFlow<List<FloatArray>> = _trace.asStateFlow()

    // Copying the debug trace every frame is wasted work unless someone is looking at it.
    @Volatile
    var traceEnabled = false

    private val lock = Any()
    private var runJob: Job? = null

    fun start(request: SourceRequest) {
        synchronized(lock) {
            if (runJob?.isActive == true) return
            val source = openSource(request)
            _trace.value = emptyList()
            // Not source.status: the shared headset source may still report the previous run until it is joined.
            _state.value = PipelineState(running = true, source = request)
            val previous = runJob
            runJob = scope.launch {
                val self = coroutineContext.job
                // The previous run may still be disconnecting the shared BLE client; let it finish first.
                previous?.cancelAndJoin()
                run(source, self)
            }
        }
    }

    // Keeps the reference so the next start() can wait for this run's cleanup.
    fun stop() {
        synchronized(lock) { runJob?.cancel() }
    }

    // For when the service could not even start the run, so the UI still has something to show.
    fun fail(message: String) = _state.update { if (it.running) it else it.copy(status = SourceStatus.Failed(message)) }

    fun setArmed(armed: Boolean) = _state.update { if (it.running) it.copy(armed = armed) else it }

    fun toggleArmed() = _state.update { if (it.running) it.copy(armed = !it.armed) else it }

    private suspend fun run(source: EegSource, self: Job) = coroutineScope {
        val monitor = LinkMonitor()
        val statusJob = launch {
            source.status.collect { s ->
                val alert = monitor.onStatus(s)
                _state.update { it.copy(status = s, linkLost = monitor.lost) }
                alert?.let { _linkAlerts.tryEmit(it) }
            }
        }
        var detector: Detector? = null
        var failure: SourceStatus.Failed? = null
        try {
            source.frames().collect { frame ->
                _frames.tryEmit(frame)
                val d = detector?.takeIf { it.sampleRate == frame.sampleRate }
                    ?: Detector(frame.sampleRate, configProvider()).also { detector = it }
                val events = d.process(frame)
                val ok = d.channelOk
                _state.update { s ->
                    s.copy(
                        batteryPct = frame.batteryPct.takeIf { it >= 0f },
                        channels = EEG_LABELS.mapIndexed { i, label -> ChannelState(label, frame.contact[i], ok[i]) },
                        hasGyro = frame.gyro != null,
                        clenchRatio = d.lastRatio,
                        missedSamples = d.missedSamples,
                    )
                }
                events.forEach { _events.tryEmit(it) }
                if (traceEnabled) _trace.value = d.trace.snapshot()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = SourceStatus.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            withContext(NonCancellable) { statusJob.cancelAndJoin() }
            synchronized(lock) {
                if (runJob === self) {
                    val end = failure ?: source.status.value
                    // Alert before running flips, so the service still plays it before it shuts down.
                    monitor.onStatus(end)?.let { _linkAlerts.tryEmit(it) }
                    _state.update { it.copy(running = false, status = end, linkLost = false, armed = false) }
                }
            }
        }
    }

    private companion object {
        const val FRAME_BUFFER = 256
        const val EVENT_BUFFER = 64
    }
}
