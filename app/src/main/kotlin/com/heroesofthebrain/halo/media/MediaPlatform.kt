package com.heroesofthebrain.halo.media

// The thin seam between PlatformMediaOutput's decisions and the Android calls,
// so the command mapping can be unit-tested on the JVM.
interface MediaPlatform {
    val volumeFixed: Boolean

    // Key down followed by key up.
    fun dispatchKey(key: MediaKey)

    fun adjustMusicVolume(adjust: VolumeAdjust)

    fun musicVolume(): VolumeState

    fun lookupSession(): SessionLookup
}

enum class MediaKey { PLAY_PAUSE, PLAY, PAUSE, STOP, NEXT, PREVIOUS, FAST_FORWARD, REWIND }

enum class VolumeAdjust { RAISE, LOWER, TOGGLE_MUTE }

data class VolumeState(val level: Int, val max: Int, val muted: Boolean)

sealed interface SessionLookup {
    data object NoAccess : SessionLookup
    data object NoSession : SessionLookup
    data class Found(val session: ActiveSession) : SessionLookup
}

interface ActiveSession {
    val packageName: String
    val status: PlaybackStatus

    // From the session's advertised PlaybackState actions.
    val canSeekTo: Boolean
    val canFastForward: Boolean
    val canRewind: Boolean

    val durationMs: Long?

    // Estimated for now, since players only report the position when their state changes.
    fun positionMs(): Long?

    fun seekTo(positionMs: Long)
    fun fastForward()
    fun rewind()
}

// Players report a position plus the time it was taken; while playing it moves on at playback speed.
fun estimatePositionMs(reportedMs: Long, reportedAtMs: Long, nowMs: Long, speed: Float, advancing: Boolean): Long? {
    if (reportedMs < 0) return null
    if (!advancing || reportedAtMs <= 0 || nowMs <= reportedAtMs) return reportedMs
    return reportedMs + ((nowMs - reportedAtMs) * speed).toLong()
}

fun clampSeekTarget(targetMs: Long, durationMs: Long?): Long {
    val floor = targetMs.coerceAtLeast(0)
    return if (durationMs != null && durationMs > 0) floor.coerceAtMost(durationMs) else floor
}

// The session list comes in priority order, but a paused app can outrank the one actually playing.
fun <T> pickPrimary(sessions: List<T>, status: (T) -> PlaybackStatus): T? =
    sessions.firstOrNull { status(it) == PlaybackStatus.PLAYING }
        ?: sessions.firstOrNull { status(it) == PlaybackStatus.BUFFERING }
        ?: sessions.firstOrNull { status(it) == PlaybackStatus.PAUSED }
        ?: sessions.firstOrNull()
