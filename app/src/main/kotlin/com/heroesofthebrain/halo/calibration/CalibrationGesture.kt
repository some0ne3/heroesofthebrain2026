package com.heroesofthebrain.halo.calibration

import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.eeg.CH_FP1
import com.heroesofthebrain.halo.eeg.CH_FP2
import kotlinx.serialization.Serializable

// One per detector gesture. trialMs is how long after the cue a detection still counts as a hit.
@Serializable
enum class CalibrationGesture(val type: GestureType, val title: String, val key: String, val trialMs: Long) {
    CLENCH(GestureType.CLENCH, "Jaw clench", "clench", 3_000),
    TURN_LEFT(GestureType.TURN_LEFT, "Turn left", "turn-left", 2_500),
    TURN_RIGHT(GestureType.TURN_RIGHT, "Turn right", "turn-right", 2_500),
    TURN_UP(GestureType.TURN_UP, "Look up", "turn-up", 2_500),
    TURN_DOWN(GestureType.TURN_DOWN, "Look down", "turn-down", 2_500),
    DOUBLE_BLINK(GestureType.DOUBLE_BLINK, "Double blink", "double-blink", 2_500),
    LONG_BLINK(GestureType.LONG_BLINK, "Long blink", "long-blink", 4_000),
    ;

    val instruction: String
        get() = when (this) {
            CLENCH -> "At each tone, clench your jaw firmly, hold it for about a second, then relax."
            TURN_LEFT -> "At each tone, turn your head to the left, then back to the centre."
            TURN_RIGHT -> "At each tone, turn your head to the right, then back to the centre."
            TURN_UP -> "At each tone, tilt your head back to look up, then back to level."
            TURN_DOWN -> "At each tone, tilt your head forward to look down, then back to level."
            DOUBLE_BLINK -> "At each tone, blink twice, quickly and firmly."
            LONG_BLINK -> "At each tone, squeeze your eyes shut for about a second and a half, then open them."
        }

    val prompt: String
        get() = when (this) {
            CLENCH -> "Clench and hold"
            TURN_LEFT -> "Left and back"
            TURN_RIGHT -> "Right and back"
            TURN_UP -> "Up and back"
            TURN_DOWN -> "Down and back"
            DOUBLE_BLINK -> "Blink twice"
            LONG_BLINK -> "Eyes shut... open"
        }

    val isTurn: Boolean get() = this == TURN_LEFT || this == TURN_RIGHT || this == TURN_UP || this == TURN_DOWN

    // The detector only finds turns and long blinks from the gyro; double blinks have an EEG fallback.
    val needsGyro: Boolean get() = isTurn || this == LONG_BLINK

    companion object {
        fun forKey(key: String): CalibrationGesture? = entries.firstOrNull { it.key == key }

        fun of(type: GestureType): CalibrationGesture = entries.first { it.type == type }
    }
}

sealed interface CalibrationStep {
    val key: String
    val title: String

    data object Rest : CalibrationStep {
        override val key = "rest"
        override val title = "Rest"
    }

    data class Gesture(val gesture: CalibrationGesture) : CalibrationStep {
        override val key get() = gesture.key
        override val title get() = gesture.title
    }

    companion object {
        fun forKey(key: String): CalibrationStep? =
            if (key == Rest.key) Rest else CalibrationGesture.forKey(key)?.let { Gesture(it) }
    }
}

internal const val MIN_CLENCH_CHANNELS = 2

// What a step needs from the headset.
internal fun CalibrationGesture.signalReady(channelOk: BooleanArray, hasGyro: Boolean): Boolean = when {
    this == CalibrationGesture.CLENCH -> channelOk.count { it } >= MIN_CLENCH_CHANNELS
    needsGyro -> hasGyro
    else -> hasGyro || (channelOk[CH_FP1] && channelOk[CH_FP2])
}

internal fun CalibrationGesture.signalProblem(channelOk: BooleanArray, hasGyro: Boolean): String? {
    if (signalReady(channelOk, hasGyro)) return null
    return when {
        this == CalibrationGesture.CLENCH -> "Clench needs good signal on at least $MIN_CLENCH_CHANNELS channels"
        isTurn -> "Head turns need the gyroscope"
        this == CalibrationGesture.LONG_BLINK -> "Long blinks need the gyroscope"
        else -> "Without a gyroscope, double blinks need good signal on Fp1 and Fp2"
    }
}
