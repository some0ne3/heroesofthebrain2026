package com.heroesofthebrain.halo.rules

import com.heroesofthebrain.halo.media.MediaOutput
import com.heroesofthebrain.halo.media.MediaResult
import com.heroesofthebrain.halo.media.NowPlayingSource
import com.heroesofthebrain.halo.service.HaloPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RuleRunner(
    private val scope: CoroutineScope,
    private val pipeline: HaloPipeline,
    private val mediaOutput: MediaOutput,
    private val profileProvider: () -> BindingProfile,
    private val nowPlayingSource: NowPlayingSource? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val engine = RuleEngine(profile = profileProvider(), clock = clock)

    private val _effects = MutableSharedFlow<EngineEffect>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<EngineEffect> = _effects.asSharedFlow()

    private val _lastResult = MutableSharedFlow<Pair<EngineEffect.ExecuteCommand, MediaResult>>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val lastResult: SharedFlow<Pair<EngineEffect.ExecuteCommand, MediaResult>> = _lastResult.asSharedFlow()

    fun start(): Job = scope.launch {
        engine.profile = profileProvider()

        // 1. Observe pipeline state (actionsAllowed, armed, good channels)
        launch {
            pipeline.state.collect { state ->
                val goodChannels = state.channels
                    .filter { it.contact && it.signalOk }
                    .map { it.label }
                    .toSet()

                val context = EngineContext(
                    actionsAllowed = state.actionsAllowed,
                    isArmed = state.armed,
                    isPlaying = nowPlayingSource?.nowPlaying?.value?.playing,
                    goodChannels = goodChannels,
                )
                val newEffects = engine.updateContext(context)
                handleEffects(newEffects)
            }
        }

        // 2. Observe nowPlaying if available
        nowPlayingSource?.let { nps ->
            launch {
                nps.nowPlaying.collect { np ->
                    val currentState = pipeline.state.value
                    val goodChannels = currentState.channels
                        .filter { it.contact && it.signalOk }
                        .map { it.label }
                        .toSet()
                    val context = EngineContext(
                        actionsAllowed = currentState.actionsAllowed,
                        isArmed = currentState.armed,
                        isPlaying = np.playing,
                        goodChannels = goodChannels,
                    )
                    val newEffects = engine.updateContext(context)
                    handleEffects(newEffects)
                }
            }
        }

        // 3. Collect gestures and feed them to engine
        launch {
            pipeline.events.collect { event ->
                val newEffects = engine.processEvent(event, clock())
                handleEffects(newEffects)
            }
        }

        // 4. Time ticker for scheduled actions, macros, repeats, and timeouts
        launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                val newEffects = engine.advanceTime(clock())
                handleEffects(newEffects)
            }
        }
    }

    private fun handleEffects(newEffects: List<EngineEffect>) {
        for (effect in newEffects) {
            _effects.tryEmit(effect)
            when (effect) {
                is EngineEffect.ExecuteCommand -> {
                    if (pipeline.state.value.actionsAllowed) {
                        val result = mediaOutput.execute(effect.command)
                        _lastResult.tryEmit(effect to result)
                    }
                }
                is EngineEffect.ArmChanged -> {
                    pipeline.setArmed(effect.armed)
                }
                else -> Unit
            }
        }
    }

    companion object {
        const val TICK_INTERVAL_MS = 50L
    }
}
