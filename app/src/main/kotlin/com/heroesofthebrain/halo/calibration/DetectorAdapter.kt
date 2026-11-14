package com.heroesofthebrain.halo.calibration

import com.heroesofthebrain.halo.detect.Detector
import com.heroesofthebrain.halo.detect.DetectorConfig
import com.heroesofthebrain.halo.detect.GestureCancel
import com.heroesofthebrain.halo.detect.GestureEnd
import com.heroesofthebrain.halo.detect.GestureEvent
import com.heroesofthebrain.halo.detect.GestureInstant
import com.heroesofthebrain.halo.detect.GestureStart
import com.heroesofthebrain.halo.eeg.EegFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

val GestureEvent.gesture: CalibrationGesture get() = CalibrationGesture.of(type)

// What a trial can be matched against: the start of a hold, or an instant gesture. Ends and cancels belong to a start.
val GestureEvent.isOnset: Boolean get() = this is GestureStart || this is GestureInstant

val GestureEvent.isRelease: Boolean get() = this is GestureEnd || this is GestureCancel

// Runs a private detector, for practising with thresholds that are not active yet.
fun Flow<EegFrame>.detectWith(config: DetectorConfig): Flow<GestureEvent> = flow {
    var detector: Detector? = null
    collect { frame ->
        val d = detector?.takeIf { it.sampleRate == frame.sampleRate } ?: Detector(frame.sampleRate, config).also { detector = it }
        d.process(frame).forEach { emit(it) }
    }
}
