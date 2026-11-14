package com.heroesofthebrain.halo.media

import kotlinx.coroutines.flow.StateFlow

// Safe to call from any thread. Never throws; problems come back as MediaResult.Failed.
fun interface MediaOutput {
    fun execute(cmd: MediaCommand): MediaResult
}

interface NowPlayingSource {
    val nowPlaying: StateFlow<NowPlaying>
}

enum class PlaybackStatus {
    // No notification access, so we cannot see the players.
    UNKNOWN,
    NO_SESSION,
    PLAYING,
    PAUSED,
    STOPPED,
    BUFFERING,
    ERROR,

    // A session exists but reports no state (PlaybackState.STATE_NONE or no state at all).
    IDLE,
}

data class NowPlaying(
    val hasAccess: Boolean,
    val status: PlaybackStatus,
    val packageName: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
    // As reported with the last state change, not ticking.
    val positionMs: Long? = null,
) {
    val playing: Boolean
        get() = status == PlaybackStatus.PLAYING || status == PlaybackStatus.BUFFERING

    companion object {
        val Unknown = NowPlaying(hasAccess = false, status = PlaybackStatus.UNKNOWN)
        val NoSession = NowPlaying(hasAccess = true, status = PlaybackStatus.NO_SESSION)
    }
}
