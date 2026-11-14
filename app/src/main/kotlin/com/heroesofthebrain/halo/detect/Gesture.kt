package com.heroesofthebrain.halo.detect

import kotlinx.serialization.Serializable
import java.util.Locale

// Held gestures report GestureStart and then exactly one GestureEnd or GestureCancel; the rest are GestureInstant.
@Serializable
enum class GestureType(val held: Boolean = false) {
    CLENCH(held = true),
    TURN_LEFT,
    TURN_RIGHT,
    TURN_UP,
    TURN_DOWN,
    DOUBLE_BLINK,
    LONG_BLINK,
}

@Serializable
enum class CancelReason { HEAD_MOTION, BLINK, SIGNAL_LOST }

sealed interface GestureEvent {
    val type: GestureType

    // Stream time since the detector started, gaps included, at the moment the detector decided.
    val timestampMs: Long

    // Heuristic 0..1 (how far past the threshold, times signal quality), NOT a calibrated probability.
    val confidence: Float
}

// Fires once the gesture has lasted long enough to trust, so onsetMs is earlier than timestampMs.
data class GestureStart(
    override val type: GestureType,
    override val timestampMs: Long,
    override val confidence: Float,
    val onsetMs: Long,
) : GestureEvent

// durationMs runs from the onset, so it is longer than timestampMs minus the start's timestampMs.
// End and cancel repeat the start's confidence.
data class GestureEnd(
    override val type: GestureType,
    override val timestampMs: Long,
    override val confidence: Float,
    val durationMs: Long,
) : GestureEvent

// The hold stopped being trustworthy before it ended; treat it as if it never happened.
data class GestureCancel(
    override val type: GestureType,
    override val timestampMs: Long,
    override val confidence: Float,
    val reason: CancelReason,
) : GestureEvent

data class GestureInstant(
    override val type: GestureType,
    override val timestampMs: Long,
    override val confidence: Float,
) : GestureEvent

fun GestureEvent.describe(): String = when (this) {
    is GestureStart -> "$type start"
    is GestureEnd -> "$type end %.2f s".format(Locale.US, durationMs / 1000.0)
    is GestureCancel -> "$type cancel ${reason.name.lowercase(Locale.US)}"
    is GestureInstant -> type.name
}
