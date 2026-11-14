package com.heroesofthebrain.halo.eeg

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface EegSource {
    val label: String
    val status: StateFlow<SourceStatus>

    // Cold flow: collecting starts the source, cancelling the collector stops it.
    fun frames(): Flow<EegFrame>
}

sealed interface SourceStatus {
    data object Idle : SourceStatus
    data object Scanning : SourceStatus
    data class Connecting(val device: String) : SourceStatus
    data class Streaming(val device: String) : SourceStatus
    data class Reconnecting(val reason: String) : SourceStatus
    data class Waiting(val reason: String) : SourceStatus
    data object Finished : SourceStatus
    data class Failed(val message: String) : SourceStatus
}
