package com.heroesofthebrain.halo.eeg

const val EEG_CHANNELS = 4
val EEG_LABELS = listOf("O1", "O2", "Fp2", "Fp1")
const val CH_FP2 = 2
const val CH_FP1 = 3

// One chunk, row-major: eeg [n * 4] µV in O1, O2, Fp2, Fp1 order, accel [n * 3] m/s², gyro [n * 3] deg/s.
class EegFrame(
    val firstSampleIndex: Long,
    val hostTimeNs: Long,
    val sampleRate: Int,
    val sampleCount: Int,
    val eeg: FloatArray,
    val accel: FloatArray?,
    val gyro: FloatArray?,
    val contact: BooleanArray,
    val batteryPct: Float,
    val isDummy: BooleanArray,
) {
    init {
        require(eeg.size == sampleCount * EEG_CHANNELS) { "eeg has ${eeg.size} values for $sampleCount samples" }
        require(isDummy.size == sampleCount) { "isDummy has ${isDummy.size} flags for $sampleCount samples" }
        require(contact.size == EEG_CHANNELS) { "contact needs $EEG_CHANNELS flags" }
    }

    fun eeg(sample: Int, channel: Int): Float = eeg[sample * EEG_CHANNELS + channel]

    val dummyCount: Int get() = isDummy.count { it }
}
