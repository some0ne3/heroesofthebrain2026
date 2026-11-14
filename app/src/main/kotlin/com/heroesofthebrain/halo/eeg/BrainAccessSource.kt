package com.heroesofthebrain.halo.eeg

import android.content.Context
import com.neurotec.brainaccess.core.BrainAccessClient
import com.neurotec.brainaccess.core.BrainAccessError
import com.neurotec.brainaccess.core.DeviceId
import com.neurotec.brainaccess.core.DeviceSession
import com.neurotec.brainaccess.core.DeviceState
import com.neurotec.brainaccess.core.DiscoveredDevice
import com.neurotec.brainaccess.core.EegChunk
import com.neurotec.brainaccess.protocol.DeviceModel
import com.neurotec.brainaccess.transport.NordicTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

// Scans for a HALO, connects, streams, and keeps reconnecting until the collector is cancelled.
// With a saved choice it only ever connects to that headset; without one it takes the closest.
class BrainAccessSource(
    context: Context,
    private val choice: StateFlow<SavedHeadset?> = MutableStateFlow(null),
) : EegSource {

    override val label = "HALO headset"

    private val client = BrainAccessClient(NordicTransport(context.applicationContext))
    private val _status = MutableStateFlow<SourceStatus>(SourceStatus.Idle)
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    private val _connected = MutableStateFlow<NearbyHeadset?>(null)
    val connected: StateFlow<NearbyHeadset?> = _connected.asStateFlow()

    val bluetoothOn: StateFlow<Boolean> get() = client.bluetoothState

    // After the first connection, reconnects go back to the same headset instead of whichever is closest.
    @Volatile
    private var preferred: DeviceId? = null

    override fun frames(): Flow<EegFrame> = channelFlow {
        var failures = 0
        try {
            while (isActive) {
                val reason = try {
                    val device = findDevice()
                    if (device == null) {
                        choice.value?.let { "${it.name} not found nearby" } ?: "No HALO headset found nearby"
                    } else {
                        _status.value = SourceStatus.Connecting(displayName(device))
                        val session = client.connect(device).getOrThrow()
                        preferred = device.id
                        failures = 0
                        _connected.value = NearbyHeadset(device.id.value, displayName(device), device.rssi)
                        try {
                            _status.value = SourceStatus.Streaming(displayName(device))
                            // Report the loss before disconnecting, which can take a while on a dead link.
                            streamUntilLost(session) { send(it) }.also { _status.value = SourceStatus.Reconnecting(it) }
                        } finally {
                            withContext(NonCancellable) { runCatching { client.disconnect(device.id) } }
                            _connected.value = null
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: BrainAccessError.MissingPermission) {
                    _status.value = SourceStatus.Failed("Bluetooth permission missing. Grant it and press Start again.")
                    return@channelFlow
                } catch (e: Throwable) {
                    describeBleError(e)
                }
                failures++
                _status.value = SourceStatus.Reconnecting(reason)
                delay(backoffMs(failures))
            }
        } finally {
            withContext(NonCancellable) { runCatching { client.disconnectAll() } }
            if (_status.value !is SourceStatus.Failed) _status.value = SourceStatus.Idle
        }
    }

    private suspend fun findDevice(): DiscoveredDevice? {
        if (!client.bluetoothState.value) {
            _status.value = SourceStatus.Waiting("Bluetooth is off. Turn it on to connect.")
            client.bluetoothState.first { it }
        }
        _status.value = SourceStatus.Scanning
        choice.value?.let { saved ->
            return withTimeoutOrNull(RECONNECT_SCAN_MS) { client.scan().first { it.id.value == saved.id } }
        }
        preferred?.let { wanted ->
            withTimeoutOrNull(RECONNECT_SCAN_MS) { client.scan().first { it.id == wanted } }?.let { return it }
        }
        // At an event many headsets are in range; the strongest signal is most likely the one being worn.
        val seen = LinkedHashMap<DeviceId, DiscoveredDevice>()
        withTimeoutOrNull(SCAN_WINDOW_MS) {
            client.scan().collect { d ->
                if (d.model == DeviceModel.HALO) {
                    val previous = seen[d.id]
                    if (previous == null || d.rssi > previous.rssi) seen[d.id] = d
                }
            }
        }
        return seen.values.maxByOrNull { it.rssi }
    }

    // For the headset picker. A connected HALO usually stops advertising, so it will not show up here; see connected.
    fun nearby(): Flow<List<NearbyHeadset>> = flow {
        var list = emptyList<NearbyHeadset>()
        emit(list)
        client.scan().collect { d ->
            if (d.model == DeviceModel.HALO) {
                list = list.withSighting(NearbyHeadset(d.id.value, displayName(d), d.rssi))
                emit(list)
            }
        }
    }

    private fun displayName(d: DiscoveredDevice) = d.name.ifBlank { d.id.value }

    // Returns why the stream stopped: link loss, an SDK error, or no data for a while.
    private suspend fun streamUntilLost(session: DeviceSession, sink: suspend (EegFrame) -> Unit): String = coroutineScope {
        val lost = CompletableDeferred<String>()
        val lastChunkNs = AtomicLong(System.nanoTime())
        val mapper = ChannelMapper()
        val jobs = listOf(
            launch {
                session.state.collect { state ->
                    when (state) {
                        is DeviceState.Disconnected -> lost.complete("Headset disconnected")
                        is DeviceState.Error -> lost.complete(describeBleError(state.cause))
                        else -> Unit
                    }
                }
            },
            launch {
                try {
                    session.stream().collect { chunk ->
                        lastChunkNs.set(System.nanoTime())
                        sink(mapper.toFrame(chunk))
                    }
                    lost.complete("Stream ended")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    lost.complete(describeBleError(e))
                }
            },
            launch {
                while (isActive) {
                    delay(1_000)
                    if (System.nanoTime() - lastChunkNs.get() > STALL_NS) lost.complete("No data from the headset for 3 s")
                }
            },
        )
        val reason = lost.await()
        jobs.forEach { it.cancel() }
        reason
    }

    private fun backoffMs(failures: Int): Long = minOf(1_000L shl minOf(failures - 1, 3), 8_000L)

    private companion object {
        const val SCAN_WINDOW_MS = 3_000L
        const val RECONNECT_SCAN_MS = 6_000L
        const val STALL_NS = 3_000_000_000L
    }
}

internal fun describeBleError(e: Throwable): String = when (e) {
    is BrainAccessError.BluetoothOff -> "Bluetooth is off"
    is BrainAccessError.LocationOff -> "Location services are off (needed to scan on Android 11 and older)"
    is BrainAccessError.MissingPermission -> "Bluetooth permission missing"
    is BrainAccessError.Timeout -> "Headset did not answer in time"
    is BrainAccessError.GattError -> "Bluetooth error (GATT ${e.status})"
    is BrainAccessError.Disconnected -> "Headset disconnected"
    else -> e.message ?: e.javaClass.simpleName
}

// The SDK sends labelled channels; this reorders them to O1, O2, Fp2, Fp1 whatever order they arrive in.
private class ChannelMapper {
    private var labels: List<String>? = null
    private var index = IntArray(EEG_CHANNELS)

    fun toFrame(chunk: EegChunk): EegFrame {
        if (chunk.channels != labels) {
            val found = IntArray(EEG_CHANNELS) { k ->
                chunk.channels.indexOfFirst { it.equals(EEG_LABELS[k], ignoreCase = true) }
            }
            require(found.all { it >= 0 }) { "Unexpected channel layout ${chunk.channels}" }
            index = found
            labels = chunk.channels
        }
        val n = chunk.sampleCount
        val stride = chunk.channels.size
        val eeg = FloatArray(n * EEG_CHANNELS)
        for (s in 0 until n) {
            for (k in 0 until EEG_CHANNELS) eeg[s * EEG_CHANNELS + k] = chunk.samples[s * stride + index[k]]
        }
        return EegFrame(
            firstSampleIndex = chunk.firstSampleIndex,
            hostTimeNs = chunk.hostTimeNs,
            sampleRate = chunk.srate,
            sampleCount = n,
            eeg = eeg,
            accel = chunk.accel,
            gyro = chunk.gyro,
            contact = BooleanArray(EEG_CHANNELS) { k -> chunk.contact.getOrElse(index[k]) { false } },
            batteryPct = chunk.batteryPct,
            isDummy = BooleanArray(n) { chunk.isDummy.getOrElse(it) { false } },
        )
    }
}
