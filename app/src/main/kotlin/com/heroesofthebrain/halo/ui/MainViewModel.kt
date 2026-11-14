package com.heroesofthebrain.halo.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heroesofthebrain.halo.calibration.CalibrationStore
import com.heroesofthebrain.halo.calibration.RecalibrationOffer
import com.heroesofthebrain.halo.calibration.RecalibrationPolicy
import com.heroesofthebrain.halo.calibration.StoredCalibration
import com.heroesofthebrain.halo.calibration.UserProfile
import com.heroesofthebrain.halo.detect.GestureCancel
import com.heroesofthebrain.halo.detect.GestureEnd
import com.heroesofthebrain.halo.detect.GestureEvent
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.detect.describe
import com.heroesofthebrain.halo.eeg.EegFrame
import com.heroesofthebrain.halo.eeg.SourceStatus
import com.heroesofthebrain.halo.service.HaloRuntime
import com.heroesofthebrain.halo.service.HaloService
import com.heroesofthebrain.halo.service.PipelineState
import com.heroesofthebrain.halo.service.SourceRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

enum class SourceKind { DEVICE, REPLAY }

enum class ActiveScreen { MAIN, CALIBRATION, PRACTICE, EDITOR }

data class LogEntry(val id: Long, val wallTimeMs: Long, val streamTimeMs: Long, val type: GestureType, val text: String, val confidence: Float)

data class UiState(
    val pipeline: PipelineState = PipelineState(),
    val label: GestureType? = null,
    val confidence: Float = 0f,
    val log: List<LogEntry> = emptyList(),
    val sourceKind: SourceKind = SourceKind.DEVICE,
    val replayFiles: List<String> = emptyList(),
    val replayFile: String? = null,
    val debugExpanded: Boolean = false,
    val permissionDenied: Boolean = false,
    // Assume exempt until MainActivity has checked, so the warning does not flash on launch.
    val batteryExempt: Boolean = true,
    val showBatteryDialog: Boolean = false,
    val activeScreen: ActiveScreen = ActiveScreen.MAIN,
    val recalibrationOffer: RecalibrationOffer? = null,
    val deviceName: String? = null,
)

// Reconnecting carries no name, so callers keep the last one they saw instead of blanking it.
internal fun headsetName(p: PipelineState): String? {
    if (p.source != SourceRequest.Headset) return null
    val name = when (val s = p.status) {
        is SourceStatus.Connecting -> s.device
        is SourceStatus.Streaming -> s.device
        else -> null
    }
    return name?.takeIf { it.isNotBlank() }
}

// Only observes: the run belongs to HaloService, so it carries on when this screen goes away.
class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val pipeline = HaloRuntime.pipeline(app)
    val store = CalibrationStore.inAppStorage(app)
    val profileStore = HaloRuntime.profileStore(app)
    val recalibrationPolicy = RecalibrationPolicy()

    val frames: Flow<EegFrame> get() = pipeline.frames
    val events: Flow<GestureEvent> get() = pipeline.events

    private val _ui = MutableStateFlow(initialState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val trace: StateFlow<List<FloatArray>> = pipeline.trace

    private var holdJob: Job? = null
    private val nextLogId = AtomicLong()

    var notificationsAsked = false
        private set
    private var batteryPromptDismissed = false

    init {
        recalibrationPolicy.lastCalibratedAtMs = store.activeProfileId?.let { store.latest(it) }?.createdAtMs
        viewModelScope.launch {
            pipeline.state.collect { p ->
                _ui.update { it.copy(pipeline = p, deviceName = headsetName(p) ?: it.deviceName) }
                val streaming = p.status is SourceStatus.Streaming
                val contactCount = p.channels.count { it.contact }
                recalibrationPolicy.onHeadset(
                    streaming = streaming,
                    channelsWithContact = contactCount,
                    nowMs = SystemClock.elapsedRealtime(),
                )
            }
        }
        viewModelScope.launch {
            recalibrationPolicy.offer.collect { offer ->
                _ui.update { it.copy(recalibrationOffer = offer) }
            }
        }
        viewModelScope.launch { pipeline.events.collect(::show) }
    }

    private fun initialState(): UiState {
        val files = app.assets.list(HaloRuntime.REPLAY_DIR).orEmpty().filter { it.endsWith(".csv") }.map { it.removeSuffix(".csv") }.sorted()
        val current = pipeline.state.value
        // Reopened while the service is still running a replay: show that instead of the defaults.
        val replay = (current.source as? SourceRequest.Replay)?.takeIf { current.running }
        return UiState(
            pipeline = current,
            sourceKind = if (replay != null) SourceKind.REPLAY else SourceKind.DEVICE,
            replayFiles = files,
            replayFile = replay?.file ?: files.firstOrNull { it == DEFAULT_REPLAY } ?: files.firstOrNull(),
            deviceName = headsetName(current),
        )
    }

    // MainActivity calls this once the permissions the source needs are granted.
    fun start() {
        val state = _ui.value
        val request = when (state.sourceKind) {
            SourceKind.DEVICE -> SourceRequest.Headset
            SourceKind.REPLAY -> SourceRequest.Replay(state.replayFile ?: return)
        }
        holdJob?.cancel()
        _ui.update {
            it.copy(
                permissionDenied = false, log = emptyList(), label = null, confidence = 0f,
                showBatteryDialog = it.sourceKind == SourceKind.DEVICE && !it.batteryExempt && !batteryPromptDismissed,
            )
        }
        try {
            HaloService.start(app, request)
        } catch (e: RuntimeException) {
            pipeline.fail("Could not start the background service: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun stop() = HaloService.stop(app)

    fun setArmed(armed: Boolean) = pipeline.setArmed(armed)

    fun selectSource(kind: SourceKind) {
        if (kind == _ui.value.sourceKind) return
        stop()
        _ui.update { it.copy(sourceKind = kind, permissionDenied = false) }
    }

    fun selectReplay(name: String) {
        stop()
        _ui.update { it.copy(replayFile = name) }
    }

    fun setDebugExpanded(expanded: Boolean) {
        pipeline.traceEnabled = expanded
        _ui.update { it.copy(debugExpanded = expanded) }
    }

    fun onPermissionDenied() = _ui.update { it.copy(permissionDenied = true) }

    fun markNotificationsAsked() {
        notificationsAsked = true
    }

    fun setBatteryExempt(exempt: Boolean) = _ui.update { it.copy(batteryExempt = exempt, showBatteryDialog = it.showBatteryDialog && !exempt) }

    fun dismissBatteryDialog() {
        batteryPromptDismissed = true
        _ui.update { it.copy(showBatteryDialog = false) }
    }

    fun openCalibration() {
        recalibrationPolicy.dismiss()
        _ui.update { it.copy(activeScreen = ActiveScreen.CALIBRATION) }
    }

    fun openPractice() {
        _ui.update { it.copy(activeScreen = ActiveScreen.PRACTICE) }
    }

    fun openEditor() {
        recalibrationPolicy.dismiss()
        _ui.update { it.copy(activeScreen = ActiveScreen.EDITOR) }
    }

    fun closeChildScreen() {
        _ui.update { it.copy(activeScreen = ActiveScreen.MAIN) }
    }

    fun dismissRecalibration() = recalibrationPolicy.dismiss()

    fun activeProfile(): UserProfile {
        val activeId = store.activeProfileId
        val existing = activeId?.let { store.profile(it) } ?: store.profiles().firstOrNull()
        if (existing != null) {
            if (store.activeProfileId != existing.id) store.activeProfileId = existing.id
            return existing
        }
        val created = store.createProfile("Default")
        store.activeProfileId = created.id
        return created
    }

    fun onCalibrationFinished(stored: StoredCalibration?) {
        if (stored != null) {
            recalibrationPolicy.onCalibrated(stored.createdAtMs)
        }
        _ui.update { it.copy(activeScreen = ActiveScreen.MAIN) }
    }

    private fun show(event: GestureEvent) {
        val entry = LogEntry(nextLogId.incrementAndGet(), System.currentTimeMillis(), event.timestampMs, event.type, event.describe(), event.confidence)
        _ui.update { it.copy(log = (listOf(entry) + it.log).take(LOG_SIZE)) }
        // Ends and cancels only go to the log; the label shows what was just recognised.
        if (event is GestureEnd || event is GestureCancel) return
        _ui.update { it.copy(label = event.type, confidence = event.confidence) }
        // Hold the label briefly so it can be read, then fall back to NONE.
        holdJob?.cancel()
        holdJob = viewModelScope.launch {
            delay(HOLD_MS)
            _ui.update { it.copy(label = null, confidence = 0f) }
        }
    }

    override fun onCleared() {
        pipeline.traceEnabled = false
    }

    private companion object {
        const val DEFAULT_REPLAY = "szczeka-docisk"
        const val HOLD_MS = 750L
        const val LOG_SIZE = 50
    }
}
