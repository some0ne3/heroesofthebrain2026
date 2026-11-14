package com.heroesofthebrain.halo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heroesofthebrain.halo.detect.ControlEvent
import com.heroesofthebrain.halo.detect.ControlType
import com.heroesofthebrain.halo.detect.Detector
import com.heroesofthebrain.halo.eeg.BrainAccessSource
import com.heroesofthebrain.halo.eeg.EEG_LABELS
import com.heroesofthebrain.halo.eeg.EegSource
import com.heroesofthebrain.halo.eeg.ReplaySource
import com.heroesofthebrain.halo.eeg.SourceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

enum class SourceKind { DEVICE, REPLAY }

data class LogEntry(val id: Long, val wallTimeMs: Long, val streamTimeMs: Long, val type: ControlType, val confidence: Float)

data class ChannelIndicator(val label: String, val contact: Boolean, val signalOk: Boolean)

data class UiState(
    val label: ControlType = ControlType.NONE,
    val confidence: Float = 0f,
    val status: SourceStatus = SourceStatus.Idle,
    val running: Boolean = false,
    val batteryPct: Float? = null,
    val channels: List<ChannelIndicator> = EEG_LABELS.map { ChannelIndicator(it, contact = false, signalOk = false) },
    val hasGyro: Boolean? = null,
    val clenchRatio: Double = 0.0,
    val missedSamples: Long = 0,
    val log: List<LogEntry> = emptyList(),
    val sourceKind: SourceKind = SourceKind.DEVICE,
    val replayFiles: List<String> = emptyList(),
    val replayFile: String? = null,
    val debugExpanded: Boolean = false,
    val permissionDenied: Boolean = false,
)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(initialState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _trace = MutableStateFlow<List<FloatArray>>(emptyList())
    val trace: StateFlow<List<FloatArray>> = _trace.asStateFlow()

    // Created on first use so Replay mode never touches Bluetooth.
    private val device by lazy { BrainAccessSource(app) }

    @Volatile
    private var runJob: Job? = null

    @Volatile
    private var holdJob: Job? = null
    private val nextLogId = AtomicLong()

    private fun initialState(): UiState {
        val files = app.assets.list(REPLAY_DIR).orEmpty().filter { it.endsWith(".csv") }.map { it.removeSuffix(".csv") }.sorted()
        return UiState(replayFiles = files, replayFile = files.firstOrNull { it == DEFAULT_REPLAY } ?: files.firstOrNull())
    }

    fun start() {
        if (runJob?.let { it.isActive && !it.isCancelled } == true) return
        val state = _ui.value
        val source: EegSource = when (state.sourceKind) {
            SourceKind.DEVICE -> device
            SourceKind.REPLAY -> {
                val file = state.replayFile ?: return
                ReplaySource(file) { app.assets.open("$REPLAY_DIR/$file.csv") }
            }
        }
        holdJob?.cancel()
        _trace.value = emptyList()
        _ui.update {
            it.copy(
                running = true, permissionDenied = false, log = emptyList(), label = ControlType.NONE,
                confidence = 0f, batteryPct = null, hasGyro = null, missedSamples = 0, clenchRatio = 0.0,
            )
        }
        val previous = runJob
        runJob = viewModelScope.launch(Dispatchers.Default) {
            val self = coroutineContext.job
            // The previous run may still be disconnecting the shared BLE client; let it finish first.
            previous?.cancelAndJoin()
            val statusJob = launch { source.status.collect { s -> _ui.update { it.copy(status = s) } } }
            var detector: Detector? = null
            try {
                source.frames().collect { frame ->
                    val d = detector?.takeIf { it.sampleRate == frame.sampleRate } ?: Detector(frame.sampleRate).also { detector = it }
                    val events = d.process(frame)
                    val ok = d.channelOk
                    _ui.update { s ->
                        s.copy(
                            batteryPct = frame.batteryPct.takeIf { it >= 0f },
                            channels = EEG_LABELS.mapIndexed { i, label -> ChannelIndicator(label, frame.contact[i], ok[i]) },
                            hasGyro = frame.gyro != null,
                            clenchRatio = d.lastRatio,
                            missedSamples = d.missedSamples,
                        )
                    }
                    events.forEach(::show)
                    if (_ui.value.debugExpanded) _trace.value = d.trace.snapshot()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(status = SourceStatus.Failed(e.message ?: e.javaClass.simpleName)) }
            } finally {
                statusJob.cancel()
                if (runJob === self) {
                    _ui.update { s -> s.copy(running = false, status = if (s.status is SourceStatus.Failed) s.status else source.status.value) }
                }
            }
        }
    }

    // Keeps the reference so the next start() can wait for this run's cleanup.
    fun stop() {
        runJob?.cancel()
    }

    fun selectSource(kind: SourceKind) {
        if (kind == _ui.value.sourceKind) return
        stop()
        _ui.update { it.copy(sourceKind = kind, status = SourceStatus.Idle, permissionDenied = false) }
    }

    fun selectReplay(name: String) {
        stop()
        _ui.update { it.copy(replayFile = name, status = SourceStatus.Idle) }
    }

    fun setDebugExpanded(expanded: Boolean) = _ui.update { it.copy(debugExpanded = expanded) }

    fun onPermissionResult(granted: Boolean) {
        if (granted) start() else _ui.update { it.copy(permissionDenied = true) }
    }

    private fun show(event: ControlEvent) {
        val entry = LogEntry(nextLogId.incrementAndGet(), System.currentTimeMillis(), event.timestampMs, event.type, event.confidence)
        _ui.update { it.copy(label = event.type, confidence = event.confidence, log = (listOf(entry) + it.log).take(LOG_SIZE)) }
        // Hold the label briefly so it can be read, then fall back to NONE.
        holdJob?.cancel()
        holdJob = viewModelScope.launch {
            delay(HOLD_MS)
            _ui.update { it.copy(label = ControlType.NONE, confidence = 0f) }
        }
    }

    override fun onCleared() {
        stop()
    }

    private companion object {
        const val REPLAY_DIR = "replay"
        const val DEFAULT_REPLAY = "szczeka-docisk"
        const val HOLD_MS = 750L
        const val LOG_SIZE = 50
    }
}
