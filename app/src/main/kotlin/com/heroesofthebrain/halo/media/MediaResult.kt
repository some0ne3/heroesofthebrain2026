package com.heroesofthebrain.halo.media

import com.heroesofthebrain.halo.input.ArrowKey
import com.heroesofthebrain.halo.input.ScrollDirection
import java.util.Locale

// What execute() actually did. A sent key or controller call only means Android accepted it;
// whether the player reacted is what docs/media-compat.md records per app.
sealed interface MediaResult {

    // likelyTarget is the session we think the system routes keys to; null without notification access.
    data class KeyDispatched(val key: MediaKey, val likelyTarget: String?) : MediaResult

    data class VolumeAdjusted(val adjust: VolumeAdjust, val volume: VolumeState, val steps: Int = 1) : MediaResult

    data class ControllerUsed(val packageName: String, val action: ControllerAction) : MediaResult

    // Like KeyDispatched, this only means the system took the gesture, not that the app in front reacted.
    data class Scrolled(val direction: ScrollDirection, val distancePercent: Int) : MediaResult

    data class ArrowPressed(val key: ArrowKey) : MediaResult

    data class Unsupported(val reason: String) : MediaResult

    // Only reported when notification access lets us see that no player has a session.
    data object NoActiveSession : MediaResult

    data class Failed(val reason: String) : MediaResult

    val delivered: Boolean
        get() = this is KeyDispatched || this is VolumeAdjusted || this is ControllerUsed || this is Scrolled || this is ArrowPressed
}

sealed interface ControllerAction {
    data class SeekTo(val fromMs: Long, val toMs: Long) : ControllerAction

    // The player picks the step size for these.
    data object FastForward : ControllerAction
    data object Rewind : ControllerAction
}

fun MediaResult.describe(): String = when (this) {
    is MediaResult.KeyDispatched -> "key ${key.name}" + (likelyTarget?.let { " -> $it" } ?: "")
    is MediaResult.VolumeAdjusted ->
        "volume ${adjust.name.lowercase()}" + (if (steps > 1) " ×$steps" else "") +
            ": ${volume.level}/${volume.max}" + if (volume.muted) " (muted)" else ""
    is MediaResult.Scrolled -> "scrolled ${direction.name.lowercase()} $distancePercent%"
    is MediaResult.ArrowPressed -> "arrow ${key.name.lowercase()}"
    is MediaResult.ControllerUsed -> when (action) {
        is ControllerAction.SeekTo -> "seekTo ${formatPosition(action.fromMs)} -> ${formatPosition(action.toMs)} in $packageName"
        ControllerAction.FastForward -> "fastForward in $packageName"
        ControllerAction.Rewind -> "rewind in $packageName"
    }
    is MediaResult.Unsupported -> "unsupported: $reason"
    MediaResult.NoActiveSession -> "no active media session"
    is MediaResult.Failed -> "failed: $reason"
}

fun formatPosition(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(Locale.US, s / 60, s % 60)
}
