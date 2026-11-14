package com.heroesofthebrain.halo.media

class PlatformMediaOutput(private val platform: MediaPlatform) : MediaOutput {

    override fun execute(cmd: MediaCommand): MediaResult = try {
        when (cmd) {
            MediaCommand.PlayPause -> key(MediaKey.PLAY_PAUSE)
            MediaCommand.Play -> key(MediaKey.PLAY)
            MediaCommand.Pause -> key(MediaKey.PAUSE)
            MediaCommand.Stop -> key(MediaKey.STOP)
            MediaCommand.Next -> key(MediaKey.NEXT)
            MediaCommand.Previous -> key(MediaKey.PREVIOUS)
            MediaCommand.VolumeUp -> volume(VolumeAdjust.RAISE)
            MediaCommand.VolumeDown -> volume(VolumeAdjust.LOWER)
            MediaCommand.Mute -> volume(VolumeAdjust.TOGGLE_MUTE)
            is MediaCommand.SeekForward -> seek(cmd.amountMs)
            is MediaCommand.SeekBack -> seek(-cmd.amountMs)
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

    private fun volume(adjust: VolumeAdjust): MediaResult {
        if (platform.volumeFixed) return MediaResult.Unsupported("this device has fixed volume")
        platform.adjustMusicVolume(adjust)
        return MediaResult.VolumeAdjusted(adjust, platform.musicVolume())
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
}
