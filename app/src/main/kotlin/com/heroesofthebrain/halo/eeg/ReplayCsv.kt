package com.heroesofthebrain.halo.eeg

import java.io.Reader

// Columns written by tools/convert_recordings.py, same order as the BrainAccess Board recording.
object ReplayCsv {
    val COLUMNS = listOf(
        "O1", "O2", "Fp2", "Fp1",
        "contact_O1", "contact_O2", "contact_Fp2", "contact_Fp1",
        "Accel_x", "Accel_y", "Accel_z",
        "Gyro_x", "Gyro_y", "Gyro_z",
        "Sample", "Streaming", "Battery",
    )

    fun parse(reader: Reader, sampleRate: Int = 250): ReplayRecording {
        val lines = reader.buffered().lineSequence().iterator()
        require(lines.hasNext()) { "empty replay file" }
        val header = lines.next().trim().split(',')
        require(header == COLUMNS) { "unexpected replay header: $header" }

        val rows = ArrayList<FloatArray>()
        var lineNo = 1
        for (line in lines) {
            lineNo++
            if (line.isBlank()) continue
            val parts = line.split(',')
            require(parts.size == COLUMNS.size) { "line $lineNo has ${parts.size} columns" }
            rows += FloatArray(parts.size) { parts[it].toFloat() }
        }

        val n = rows.size
        return ReplayRecording(
            sampleRate = sampleRate,
            sampleIndex = LongArray(n) { rows[it][14].toLong() },
            eeg = FloatArray(n * EEG_CHANNELS) { rows[it / EEG_CHANNELS][it % EEG_CHANNELS] },
            contact = BooleanArray(n * EEG_CHANNELS) { rows[it / EEG_CHANNELS][4 + it % EEG_CHANNELS] != 0f },
            accel = FloatArray(n * 3) { rows[it / 3][8 + it % 3] },
            gyro = FloatArray(n * 3) { rows[it / 3][11 + it % 3] },
            streaming = BooleanArray(n) { rows[it][15] != 0f },
            battery = FloatArray(n) { rows[it][16] },
        )
    }
}

class ReplayRecording(
    val sampleRate: Int,
    val sampleIndex: LongArray,
    val eeg: FloatArray,
    val contact: BooleanArray,
    val accel: FloatArray,
    val gyro: FloatArray,
    val streaming: BooleanArray,
    val battery: FloatArray,
) {
    val size: Int get() = sampleIndex.size

    // Rows with Streaming == 0 were gap-filled by the SDK, so they become dummy samples here too.
    fun frame(from: Int, to: Int, hostTimeNs: Long = 0L): EegFrame {
        val n = to - from
        val contactAll = BooleanArray(EEG_CHANNELS) { c -> (from until to).all { contact[it * EEG_CHANNELS + c] } }
        return EegFrame(
            firstSampleIndex = sampleIndex[from],
            hostTimeNs = hostTimeNs,
            sampleRate = sampleRate,
            sampleCount = n,
            eeg = eeg.copyOfRange(from * EEG_CHANNELS, to * EEG_CHANNELS),
            accel = accel.copyOfRange(from * 3, to * 3),
            gyro = gyro.copyOfRange(from * 3, to * 3),
            contact = contactAll,
            batteryPct = battery[to - 1],
            isDummy = BooleanArray(n) { !streaming[from + it] },
        )
    }

    fun frames(chunkSize: Int = DEFAULT_CHUNK): List<EegFrame> =
        (0 until size step chunkSize).map { frame(it, minOf(it + chunkSize, size)) }

    companion object {
        // BrainAccess Board stored 25 samples per row at 250 Hz, i.e. one chunk every 100 ms.
        const val DEFAULT_CHUNK = 25
    }
}
