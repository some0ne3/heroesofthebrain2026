package com.heroesofthebrain.halo.media

import com.heroesofthebrain.halo.input.ArrowKey
import com.heroesofthebrain.halo.input.InputPlatform
import com.heroesofthebrain.halo.input.NoInputPlatform
import com.heroesofthebrain.halo.input.ScrollDirection
import com.heroesofthebrain.halo.input.scrollPath

class PlatformMediaOutput(
    private val platform: MediaPlatform,
    private val input: InputPlatform = NoInputPlatform,
) : MediaOutput {

    override fun execute(cmd: MediaCommand): MediaResult = try {
        when (cmd) {
            MediaCommand.PlayPause -> key(MediaKey.PLAY_PAUSE)
            MediaCommand.Play -> key(MediaKey.PLAY)
            MediaCommand.Pause -> key(MediaKey.PAUSE)
            MediaCommand.Stop -> key(MediaKey.STOP)
            MediaCommand.Next -> key(MediaKey.NEXT)
            MediaCommand.Previous -> key(MediaKey.PREVIOUS)
            is MediaCommand.VolumeUp -> volume(VolumeAdjust.RAISE, cmd.steps)
            is MediaCommand.VolumeDown -> volume(VolumeAdjust.LOWER, cmd.steps)
            MediaCommand.Mute -> volume(VolumeAdjust.TOGGLE_MUTE, 1)
            is MediaCommand.SeekForward -> seek(cmd.amountMs)
            is MediaCommand.SeekBack -> seek(-cmd.amountMs)
            is MediaCommand.ScrollDown -> scroll(ScrollDirection.DOWN, cmd.distancePercent)
            is MediaCommand.ScrollUp -> scroll(ScrollDirection.UP, cmd.distancePercent)
            MediaCommand.ArrowUp -> arrow(ArrowKey.UP)
            MediaCommand.ArrowDown -> arrow(ArrowKey.DOWN)
            MediaCommand.ArrowLeft -> arrow(ArrowKey.LEFT)
            MediaCommand.ArrowRight -> arrow(ArrowKey.RIGHT)
        }
    } catch (e: Exception) {
        MediaResult.Failed(e.message ?: e.javaClass.simpleName)
    }

    // Keys go out even when no session is visible: on Android 8+ the system can restart the last player for them.
    private fun key(key: MediaKey): MediaResult {
        val target = (platform.lookupSession() as? SessionLookup.Found)?.session?.packageName
        platform.dispatchKey(key)
        return MediaResult.KeyDispatched(key, target)
    }

    // One adjust per step, so the system's own step size and its headphone volume limit still apply.
    private fun volume(adjust: VolumeAdjust, steps: Int): MediaResult {
        if (platform.volumeFixed) return MediaResult.Unsupported("this device has fixed volume")
        repeat(steps) { platform.adjustMusicVolume(adjust) }
        return MediaResult.VolumeAdjusted(adjust, platform.musicVolume(), steps)
    }

    private fun scroll(direction: ScrollDirection, distancePercent: Int): MediaResult {
        if (!input.connected) return MediaResult.Unsupported(INPUT_SERVICE_OFF)
        val path = scrollPath(direction, distancePercent, input.screenSize())
        if (!input.swipe(path)) return MediaResult.Failed("the system did not accept the swipe")
        return MediaResult.Scrolled(direction, distancePercent)
    }

    private fun arrow(key: ArrowKey): MediaResult {
        if (!input.arrowKeysSupported) return MediaResult.Unsupported("arrow keys need Android 13 or newer")
        if (!input.connected) return MediaResult.Unsupported(INPUT_SERVICE_OFF)
        if (!input.pressArrow(key)) return MediaResult.Failed("the system did not accept the arrow key")
        return MediaResult.ArrowPressed(key)
    }

    private fun seek(deltaMs: Long): MediaResult {
        val forward = deltaMs > 0
        val session = when (val lookup = platform.lookupSession()) {
            // Without access the keys are all we have, and the player picks the step.
            SessionLookup.NoAccess -> return key(if (forward) MediaKey.FAST_FORWARD else MediaKey.REWIND)
            SessionLookup.NoSession -> return MediaResult.NoActiveSession
            is SessionLookup.Found -> lookup.session
        }
        val position = session.positionMs()
        if (session.canSeekTo && position != null) {
            val target = clampSeekTarget(position + deltaMs, session.durationMs)
            session.seekTo(target)
            return MediaResult.ControllerUsed(session.packageName, ControllerAction.SeekTo(position, target))
        }
        if (forward && session.canFastForward) {
            session.fastForward()
            return MediaResult.ControllerUsed(session.packageName, ControllerAction.FastForward)
        }
        if (!forward && session.canRewind) {
            session.rewind()
            return MediaResult.ControllerUsed(session.packageName, ControllerAction.Rewind)
        }
        val why = if (session.canSeekTo) "position unknown" else "no seek support advertised"
        return MediaResult.Unsupported("${session.packageName}: $why")
    }

    companion object {
        const val INPUT_SERVICE_OFF = "turn on Freehand in Accessibility settings to scroll and press arrow keys"
    }
}
