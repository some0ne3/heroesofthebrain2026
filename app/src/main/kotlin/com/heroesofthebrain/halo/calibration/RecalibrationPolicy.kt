package com.heroesofthebrain.halo.calibration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecalibrationOffer(
    // null when this profile has never been calibrated
    val lastCalibratedAtMs: Long?,
)

// Dry-electrode amplitude changes with every fitting, so each time the headset goes on again we offer to recalibrate.
// "Off" is a disconnect or no contact on any channel; a short BLE drop or a glitch shorter than minOffMs is the
// same fitting. Feed it from the connection status and the frames' contact flags; nowMs is a monotonic clock.
class RecalibrationPolicy(
    private val minOffMs: Long = 20_000,
    var enabled: Boolean = true,
) {
    private val _offer = MutableStateFlow<RecalibrationOffer?>(null)
    val offer: StateFlow<RecalibrationOffer?> = _offer.asStateFlow()

    // Wall-clock time of the active profile's latest calibration, shown in the prompt.
    var lastCalibratedAtMs: Long? = null

    private var worn = false
    private var offSinceMs: Long? = null
    private var everWorn = false

    fun onHeadset(streaming: Boolean, channelsWithContact: Int, nowMs: Long) {
        val nowWorn = streaming && channelsWithContact >= MIN_WORN_CHANNELS
        if (nowWorn == worn) return
        worn = nowWorn
        if (!nowWorn) {
            offSinceMs = nowMs
            return
        }
        val off = offSinceMs
        offSinceMs = null
        val newFitting = !everWorn || (off != null && nowMs - off >= minOffMs)
        everWorn = true
        if (newFitting && enabled) _offer.value = RecalibrationOffer(lastCalibratedAtMs)
    }

    // "Not now": stays quiet until the headset comes off and goes on again.
    fun dismiss() {
        _offer.value = null
    }

    fun onCalibrated(createdAtMs: Long) {
        lastCalibratedAtMs = createdAtMs
        _offer.value = null
    }

    private companion object {
        const val MIN_WORN_CHANNELS = 2
    }
}
