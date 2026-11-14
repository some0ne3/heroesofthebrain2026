package com.heroesofthebrain.halo.calibration

import com.heroesofthebrain.halo.detect.DetectorConfig
import kotlinx.serialization.Serializable
import kotlin.math.min

@Serializable
enum class CalibratedParam(val label: String, val unit: String, val decimals: Int) {
    CLENCH_ON_RATIO("Clench starts at", "× baseline", 2),
    CLENCH_OFF_RATIO("Clench ends below", "× baseline", 2),
    TURN_GYRO_DPS("Head turn speed", "°/s", 0),
    // These two hold 1.0 for true and 0.0 for false.
    TURN_LEFT_IS_POSITIVE_Y("Turning left reads as", "", 0),
    TURN_UP_IS_POSITIVE_Z("Looking up reads as", "", 0),
    LID_KICK_DPS("Blink kick", "°/s", 1),
    LID_LONG_MIN_S("Long blink closure", "s", 2),
    LID_DOUBLE_MAX_GAP_S("Double blink max gap", "s", 2),
    BLINK_LOBE_UV("Blink size (EEG, no gyro)", "µV", 0),
    ;

    fun defaultIn(cfg: DetectorConfig): Double = when (this) {
        CLENCH_ON_RATIO -> cfg.clenchOnRatio
        CLENCH_OFF_RATIO -> cfg.clenchOffRatio
        TURN_GYRO_DPS -> cfg.turnGyroDps
        TURN_LEFT_IS_POSITIVE_Y -> if (cfg.turnLeftIsPositiveY) 1.0 else 0.0
        TURN_UP_IS_POSITIVE_Z -> if (cfg.turnUpIsPositiveZ) 1.0 else 0.0
        LID_KICK_DPS -> cfg.lidKickDps
        LID_LONG_MIN_S -> cfg.lidLongMinS
        LID_DOUBLE_MAX_GAP_S -> cfg.lidDoubleMaxGapS
        BLINK_LOBE_UV -> cfg.blinkLobeUv
    }

    fun format(value: Double): String = when (this) {
        TURN_LEFT_IS_POSITIVE_Y -> if (value > 0.5) "+y" else "−y"
        TURN_UP_IS_POSITIVE_Z -> if (value > 0.5) "+z" else "−z"
        else -> "%.${decimals}f".format(java.util.Locale.US, value)
    }
}

@Serializable
enum class ParameterStatus(val applied: Boolean) {
    DERIVED(true),
    // derived, then held at the edge of the allowed range
    CLAMPED(true),
    TOO_FEW_TRIALS(false),
    NOT_DETECTED(false),
    // the trials disagree, e.g. half the left turns read +y and half -y
    INCONSISTENT(false),
    // the rest recording or another step it is compared with is missing
    NO_REFERENCE(false),
    NOT_CALIBRATED(false),
    NOT_APPLICABLE(false),
}

@Serializable
data class ParameterReport(
    val param: CalibratedParam,
    val value: Double,
    val defaultValue: Double,
    val status: ParameterStatus,
    val validTrials: Int = 0,
    val trials: Int = 0,
    val note: String = "",
)

// Only what calibration measured; everything else keeps following the code's defaults.
@Serializable
data class CalibratedThresholds(
    val clenchOnRatio: Double? = null,
    val clenchOffRatio: Double? = null,
    val turnGyroDps: Double? = null,
    val turnLeftIsPositiveY: Boolean? = null,
    val turnUpIsPositiveZ: Boolean? = null,
    val lidKickDps: Double? = null,
    val lidLongMinS: Double? = null,
    val lidDoubleMaxGapS: Double? = null,
    val blinkLobeUv: Double? = null,
) {
    fun applyTo(base: DetectorConfig = DetectorConfig()): DetectorConfig {
        val on = clenchOnRatio ?: base.clenchOnRatio
        return base.copy(
            clenchOnRatio = on,
            // An off level at or above on would end every clench the moment it starts.
            clenchOffRatio = min(clenchOffRatio ?: base.clenchOffRatio, on * 0.9),
            turnGyroDps = turnGyroDps ?: base.turnGyroDps,
            turnLeftIsPositiveY = turnLeftIsPositiveY ?: base.turnLeftIsPositiveY,
            turnUpIsPositiveZ = turnUpIsPositiveZ ?: base.turnUpIsPositiveZ,
            lidKickDps = lidKickDps ?: base.lidKickDps,
            lidLongMinS = lidLongMinS ?: base.lidLongMinS,
            lidDoubleMaxGapS = lidDoubleMaxGapS ?: base.lidDoubleMaxGapS,
            blinkLobeUv = blinkLobeUv ?: base.blinkLobeUv,
        )
    }

    companion object {
        fun from(reports: List<ParameterReport>): CalibratedThresholds {
            fun v(p: CalibratedParam) = reports.firstOrNull { it.param == p && it.status.applied }?.value
            return CalibratedThresholds(
                clenchOnRatio = v(CalibratedParam.CLENCH_ON_RATIO),
                clenchOffRatio = v(CalibratedParam.CLENCH_OFF_RATIO),
                turnGyroDps = v(CalibratedParam.TURN_GYRO_DPS),
                turnLeftIsPositiveY = v(CalibratedParam.TURN_LEFT_IS_POSITIVE_Y)?.let { it > 0.5 },
                turnUpIsPositiveZ = v(CalibratedParam.TURN_UP_IS_POSITIVE_Z)?.let { it > 0.5 },
                lidKickDps = v(CalibratedParam.LID_KICK_DPS),
                lidLongMinS = v(CalibratedParam.LID_LONG_MIN_S),
                lidDoubleMaxGapS = v(CalibratedParam.LID_DOUBLE_MAX_GAP_S),
                blinkLobeUv = v(CalibratedParam.BLINK_LOBE_UV),
            )
        }
    }
}

@Serializable
data class HoldStats(val holdsMs: List<Long>, val minMs: Long, val medianMs: Long, val maxMs: Long) {
    companion object {
        fun of(holds: List<Long>): HoldStats? {
            if (holds.isEmpty()) return null
            val sorted = holds.sorted()
            val mid = sorted.size / 2
            val median = if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
            return HoldStats(holds, sorted.first(), median, sorted.last())
        }
    }
}

@Serializable
data class DetectionRecord(val gesture: CalibrationGesture, val atMs: Long)

@Serializable
data class GestureScore(
    val gesture: CalibrationGesture,
    val trials: Int,
    val hits: Int,
    // wrong gesture, or the right one outside every trial window
    val otherDetections: Int,
    val defaultHits: Int,
    val defaultOtherDetections: Int,
    val signalOkFraction: Float = 1f,
    // hits whose hold was cancelled afterwards (head motion, blink, signal lost)
    val cancelled: Int = 0,
) {
    val hitRate: Float get() = if (trials == 0) 0f else hits.toFloat() / trials
}

@Serializable
data class RestScore(
    val durationMs: Long,
    val falseDetections: List<DetectionRecord>,
    val defaultFalseDetections: Int,
) {
    val perMinute: Double get() = if (durationMs <= 0) 0.0 else falseDetections.size * 60_000.0 / durationMs
}

@Serializable
data class UserProfile(val id: String, val name: String, val createdAtMs: Long)

@Serializable
data class StoredCalibration(
    val version: Int = FORMAT_VERSION,
    val profileId: String,
    val createdAtMs: Long,
    val sampleRate: Int,
    val hasGyro: Boolean,
    val trialsPerGesture: Int,
    val thresholds: CalibratedThresholds,
    val parameters: List<ParameterReport>,
    val clenchHolds: HoldStats? = null,
    val scores: List<GestureScore> = emptyList(),
    val rest: RestScore? = null,
    val skipped: Map<CalibrationGesture, String> = emptyMap(),
    // absolute path, only when the user opted in to keeping the raw capture
    val rawCaptureDir: String? = null,
) {
    fun detectorConfig(base: DetectorConfig = DetectorConfig()): DetectorConfig = thresholds.applyTo(base)

    companion object {
        const val FORMAT_VERSION = 1
    }
}
