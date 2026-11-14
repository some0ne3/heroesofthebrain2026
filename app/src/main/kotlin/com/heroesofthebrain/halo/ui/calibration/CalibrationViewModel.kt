package com.heroesofthebrain.halo.ui.calibration

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heroesofthebrain.halo.calibration.CalibrationCaptures
import com.heroesofthebrain.halo.calibration.CalibrationController
import com.heroesofthebrain.halo.calibration.CalibrationGesture
import com.heroesofthebrain.halo.calibration.CalibrationPlan
import com.heroesofthebrain.halo.calibration.CalibrationResult
import com.heroesofthebrain.halo.calibration.CalibrationScorer
import com.heroesofthebrain.halo.calibration.CalibrationState
import com.heroesofthebrain.halo.calibration.CalibrationStep
import com.heroesofthebrain.halo.calibration.CalibrationStore
import com.heroesofthebrain.halo.calibration.Cue
import com.heroesofthebrain.halo.calibration.RawCapture
import com.heroesofthebrain.halo.calibration.StoredCalibration
import com.heroesofthebrain.halo.calibration.UserProfile
import com.heroesofthebrain.halo.eeg.EegFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CalibrationScreen {
    data object Intro : CalibrationScreen
    data class Running(val state: CalibrationState) : CalibrationScreen
    data object Analyzing : CalibrationScreen
    data class Results(
        val result: CalibrationResult,
        val createdAtMs: Long,
        // exact place the raw capture would go if the user opts in
        val rawDir: String,
        val rawBytes: Long,
        val saveError: String? = null,
    ) : CalibrationScreen
    data object Saving : CalibrationScreen
    data class Saved(val calibration: StoredCalibration) : CalibrationScreen
    data object Cancelled : CalibrationScreen
    data class Failed(val message: String) : CalibrationScreen
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationViewModel(
    private val frames: Flow<EegFrame>,
    private val store: CalibrationStore,
    val profile: UserProfile,
    val plan: CalibrationPlan = CalibrationPlan(),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _screen = MutableStateFlow<CalibrationScreen>(CalibrationScreen.Intro)
    val screen: StateFlow<CalibrationScreen> = _screen.asStateFlow()

    private val _cues = MutableSharedFlow<Cue>(extraBufferCapacity = 16)
    val cues: SharedFlow<Cue> = _cues.asSharedFlow()

    // The controller is single-threaded, so every call into it goes through this one-at-a-time dispatcher.
    private val worker = Dispatchers.Default.limitedParallelism(1)
    private var controller: CalibrationController? = null
    private var captures = CalibrationCaptures()
    private var runJob: Job? = null

    fun start(gestures: List<CalibrationGesture>) {
        if (gestures.isEmpty()) return
        val c = CalibrationController(plan.copy(gestures = gestures), clock) { _cues.tryEmit(it) }
        controller = c
        drive(c) { start() }
    }

    fun retry(step: CalibrationStep) {
        val c = controller ?: return
        drive(c) { retry(step) }
    }

    fun skipContactCheck() {
        viewModelScope.launch(worker) { controller?.skipContactCheck() }
    }

    fun cancel() {
        if (runJob?.isActive != true) return discard()
        viewModelScope.launch(worker) { controller?.cancel() }
    }

    fun discard() {
        release()
        _screen.value = CalibrationScreen.Cancelled
    }

    fun accept(keepRaw: Boolean) {
        val results = _screen.value as? CalibrationScreen.Results ?: return
        _screen.value = CalibrationScreen.Saving
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rawDir = if (keepRaw) {
                    store.rawCaptureDir(profile.id, results.createdAtMs).also { RawCapture.write(it, captures) }.absolutePath
                } else {
                    null
                }
                val stored = results.result.toStored(profile.id, results.createdAtMs, rawDir)
                store.save(stored)
                // Calibrating a profile means you want to use it.
                store.activeProfileId = profile.id
                release()
                _screen.value = CalibrationScreen.Saved(stored)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _screen.value = results.copy(saveError = "Could not save: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    // Drops the recorded frames once they are saved or thrown away; the ViewModel may outlive the screen.
    private fun release() {
        runJob?.cancel()
        controller = null
        captures = CalibrationCaptures()
    }

    private fun drive(c: CalibrationController, begin: CalibrationController.() -> Unit) {
        runJob?.cancel()
        runJob = viewModelScope.launch(worker) {
            c.begin()
            val pump = launch {
                launch {
                    while (true) {
                        c.tick()
                        delay(TICK_MS)
                    }
                }
                frames.collect { c.onFrame(it) }
            }
            val end = c.state
                .onEach { if (it !is CalibrationState.Finished && it !is CalibrationState.Cancelled) _screen.value = CalibrationScreen.Running(it) }
                .first { it is CalibrationState.Finished || it is CalibrationState.Cancelled }
            pump.cancel()
            if (end is CalibrationState.Finished) analyze(end) else _screen.value = CalibrationScreen.Cancelled
        }
    }

    private suspend fun analyze(finished: CalibrationState.Finished) {
        _screen.value = CalibrationScreen.Analyzing
        try {
            captures = finished.captures
            val result = withContext(Dispatchers.Default) { CalibrationScorer.evaluate(finished.captures, skipped = finished.skipped) }
            val createdAt = wallClock()
            _screen.value = CalibrationScreen.Results(
                result,
                createdAt,
                store.rawCaptureDir(profile.id, createdAt).absolutePath,
                RawCapture.estimateBytes(finished.captures),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _screen.value = CalibrationScreen.Failed("Analysis failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TICK_MS = 50L
    }
}
