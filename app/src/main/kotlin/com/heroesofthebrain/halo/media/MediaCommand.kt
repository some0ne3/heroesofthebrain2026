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

    // Profiles saved before steps existed decode as one step, which is what the old object did.
    @Serializable
    data class VolumeUp(val steps: Int = DEFAULT_VOLUME_STEPS) : MediaCommand {
        init {
            require(steps in VOLUME_STEPS_RANGE) { "volume steps must be in $VOLUME_STEPS_RANGE, got $steps" }
        }
    }

    @Serializable
    data class VolumeDown(val steps: Int = DEFAULT_VOLUME_STEPS) : MediaCommand {
        init {
            require(steps in VOLUME_STEPS_RANGE) { "volume steps must be in $VOLUME_STEPS_RANGE, got $steps" }
        }
    }

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

    // Like a mouse wheel: scrolling down shows what is below, which in a vertical video feed is the next video.
    // The distance is the swipe length as a percentage of the screen height.
    @Serializable
    data class ScrollDown(val distancePercent: Int = DEFAULT_SCROLL_PERCENT) : MediaCommand {
        init {
            require(distancePercent in SCROLL_PERCENT_RANGE) { "scroll distance must be in $SCROLL_PERCENT_RANGE %, got $distancePercent" }
        }
    }

    @Serializable
    data class ScrollUp(val distancePercent: Int = DEFAULT_SCROLL_PERCENT) : MediaCommand {
        init {
            require(distancePercent in SCROLL_PERCENT_RANGE) { "scroll distance must be in $SCROLL_PERCENT_RANGE %, got $distancePercent" }
        }
    }

    @Serializable
    data object ArrowUp : MediaCommand
    @Serializable
    data object ArrowDown : MediaCommand
    @Serializable
    data object ArrowLeft : MediaCommand
    @Serializable
    data object ArrowRight : MediaCommand

    companion object {
        const val DEFAULT_SEEK_MS = 10_000L
        const val DEFAULT_VOLUME_STEPS = 1
        val VOLUME_STEPS_RANGE = 1..10
        const val DEFAULT_SCROLL_PERCENT = 50
        // Swipes run through the middle of the screen, so this keeps both ends clear of the status bar
        // and the gesture navigation strip.
        val SCROLL_PERCENT_RANGE = 10..80

        val all: List<MediaCommand>
            get() = listOf(
                PlayPause, Play, Pause, Stop, Next, Previous, VolumeUp(), VolumeDown(), Mute, SeekForward(), SeekBack(),
                ScrollDown(), ScrollUp(), ArrowUp, ArrowDown, ArrowLeft, ArrowRight,
            )
    }
}

// These go through the accessibility service instead of the media APIs.
val MediaCommand.needsInputService: Boolean
    get() = when (this) {
        is MediaCommand.ScrollDown, is MediaCommand.ScrollUp,
        MediaCommand.ArrowUp, MediaCommand.ArrowDown, MediaCommand.ArrowLeft, MediaCommand.ArrowRight,
        -> true
        else -> false
    }

fun MediaCommand.describe(): String = when (this) {
    MediaCommand.PlayPause -> "Play/pause"
    MediaCommand.Play -> "Play"
    MediaCommand.Pause -> "Pause"
    MediaCommand.Stop -> "Stop"
    MediaCommand.Next -> "Next"
    MediaCommand.Previous -> "Previous"
    is MediaCommand.VolumeUp -> "Volume up" + stepsSuffix(steps)
    is MediaCommand.VolumeDown -> "Volume down" + stepsSuffix(steps)
    MediaCommand.Mute -> "Mute"
    is MediaCommand.SeekForward -> "Seek +${formatSeconds(amountMs)}"
    is MediaCommand.SeekBack -> "Seek -${formatSeconds(amountMs)}"
    is MediaCommand.ScrollDown -> "Scroll down $distancePercent%"
    is MediaCommand.ScrollUp -> "Scroll up $distancePercent%"
    MediaCommand.ArrowUp -> "Arrow up"
    MediaCommand.ArrowDown -> "Arrow down"
    MediaCommand.ArrowLeft -> "Arrow left"
    MediaCommand.ArrowRight -> "Arrow right"
}

// The picker lists each command once with its default amount; this lets it keep the amount the user already set.
fun MediaCommand.sameKindAs(other: MediaCommand): Boolean = this::class == other::class

private fun stepsSuffix(steps: Int): String = if (steps == 1) "" else " ×$steps"

private fun formatSeconds(ms: Long): String = if (ms % 1000 == 0L) "${ms / 1000} s" else "${ms / 1000.0} s"
