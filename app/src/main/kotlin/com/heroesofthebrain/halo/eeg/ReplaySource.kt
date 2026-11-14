package com.heroesofthebrain.halo.eeg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.InputStream

// Each chunk is emitted when its last sample would have arrived from the headset.
class ReplaySource(
    override val label: String,
    private val chunkSize: Int = ReplayRecording.DEFAULT_CHUNK,
    private val nanoTime: () -> Long = System::nanoTime,
    private val open: () -> InputStream,
) : EegSource {

    private val _status = MutableStateFlow<SourceStatus>(SourceStatus.Idle)
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    override fun frames(): Flow<EegFrame> = flow {
        try {
            val recording = withContext(Dispatchers.IO) {
                open().bufferedReader().use { ReplayCsv.parse(it) }
            }
            _status.value = SourceStatus.Streaming("Replay: $label")
            val start = nanoTime()
            var from = 0
            while (from < recording.size) {
                val to = minOf(from + chunkSize, recording.size)
                val dueNs = start + to * 1_000_000_000L / recording.sampleRate
                val waitMs = (dueNs - nanoTime()) / 1_000_000
                if (waitMs > 0) delay(waitMs)
                emit(recording.frame(from, to, nanoTime()))
                from = to
            }
            _status.value = SourceStatus.Finished
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                _status.value = SourceStatus.Idle
                throw e
            }
            _status.value = SourceStatus.Failed("Replay failed: ${e.message}")
            throw e
        }
    }
}
