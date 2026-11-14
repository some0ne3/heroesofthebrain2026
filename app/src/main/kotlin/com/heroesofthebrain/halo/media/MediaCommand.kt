package com.heroesofthebrain.halo.media

import kotlinx.serialization.Serializable

@Serializable
sealed interface MediaCommand {
    @Serializable
    data object PlayPause : MediaCommand
    @Serializable
    data object Play : MediaCommand
    @Serializable
    data object Pause : MediaCommand
    @Serializable
    data object Stop : MediaCommand
    @Serializable
    data object Next : MediaCommand
    @Serializable
    data object Previous : MediaCommand
    @Serializable
    data object VolumeUp : MediaCommand
    @Serializable
    data object VolumeDown : MediaCommand

    // A toggle: the second Mute unmutes.
    @Serializable
    data object Mute : MediaCommand

    @Serializable
    data class SeekForward(val amountMs: Long = DEFAULT_SEEK_MS) : MediaCommand {
        init {
            require(amountMs > 0) { "seek amount must be positive, got $amountMs" }
        }
    }

    @Serializable
    data class SeekBack(val amountMs: Long = DEFAULT_SEEK_MS) : MediaCommand {
        init {
            require(amountMs > 0) { "seek amount must be positive, got $amountMs" }
        }
    }

    companion object {
        const val DEFAULT_SEEK_MS = 10_000L

        val all: List<MediaCommand>
            get() = listOf(PlayPause, Play, Pause, Stop, Next, Previous, VolumeUp, VolumeDown, Mute, SeekForward(), SeekBack())
    }
}

fun MediaCommand.describe(): String = when (this) {
    MediaCommand.PlayPause -> "Play/pause"
    MediaCommand.Play -> "Play"
    MediaCommand.Pause -> "Pause"
    MediaCommand.Stop -> "Stop"
    MediaCommand.Next -> "Next"
    MediaCommand.Previous -> "Previous"
    MediaCommand.VolumeUp -> "Volume up"
    MediaCommand.VolumeDown -> "Volume down"
    MediaCommand.Mute -> "Mute"
    is MediaCommand.SeekForward -> "Seek +${formatSeconds(amountMs)}"
    is MediaCommand.SeekBack -> "Seek -${formatSeconds(amountMs)}"
}

private fun formatSeconds(ms: Long): String = if (ms % 1000 == 0L) "${ms / 1000} s" else "${ms / 1000.0} s"
