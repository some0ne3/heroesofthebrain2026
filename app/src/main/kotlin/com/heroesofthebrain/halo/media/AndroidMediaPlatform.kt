package com.heroesofthebrain.halo.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import com.heroesofthebrain.halo.input.AccessibilityInputPlatform

fun AndroidMediaOutput(context: Context, showVolumeUi: Boolean = true): MediaOutput =
    PlatformMediaOutput(AndroidMediaPlatform(context, showVolumeUi), AccessibilityInputPlatform)

class AndroidMediaPlatform(context: Context, private val showVolumeUi: Boolean = true) : MediaPlatform {

    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val sessions = app.getSystemService(MediaSessionManager::class.java)
    private val listener = MediaListenerService.component(app)

    override val volumeFixed: Boolean
        get() = audio.isVolumeFixed

    override fun dispatchKey(key: MediaKey) {
        val code = key.keyCode()
        val down = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(down, down, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
    }

    override fun adjustMusicVolume(adjust: VolumeAdjust) {
        val flags = if (showVolumeUi) AudioManager.FLAG_SHOW_UI else 0
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust.direction(), flags)
    }

    override fun musicVolume(): VolumeState = VolumeState(
        level = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
    )

    override fun lookupSession(): SessionLookup {
        val controllers = activeControllers(app, sessions, listener) ?: return SessionLookup.NoAccess
        val primary = pickPrimary(controllers) { playbackStatusOf(it.playbackState?.state) } ?: return SessionLookup.NoSession
        return SessionLookup.Found(AndroidActiveSession(primary))
    }
}

private class AndroidActiveSession(private val controller: MediaController) : ActiveSession {
    private val actions = controller.playbackState?.actions ?: 0L

    override val packageName: String = controller.packageName
    override val status: PlaybackStatus = playbackStatusOf(controller.playbackState?.state)
    override val canSeekTo = (actions and PlaybackState.ACTION_SEEK_TO) != 0L
    override val canFastForward = (actions and PlaybackState.ACTION_FAST_FORWARD) != 0L
    override val canRewind = (actions and PlaybackState.ACTION_REWIND) != 0L
    override val durationMs: Long? = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }

    override fun positionMs(): Long? {
        val s = controller.playbackState ?: return null
        val advancing = s.state == PlaybackState.STATE_PLAYING ||
            s.state == PlaybackState.STATE_FAST_FORWARDING ||
            s.state == PlaybackState.STATE_REWINDING
        return estimatePositionMs(s.position, s.lastPositionUpdateTime, SystemClock.elapsedRealtime(), s.playbackSpeed, advancing)
    }

    override fun seekTo(positionMs: Long) = controller.transportControls.seekTo(positionMs)
    override fun fastForward() = controller.transportControls.fastForward()
    override fun rewind() = controller.transportControls.rewind()
}

// Null means no notification access, which getActiveSessions enforces with a SecurityException.
internal fun activeControllers(context: Context, sessions: MediaSessionManager, listener: ComponentName): List<MediaController>? {
    if (!NotificationAccess.isGranted(context)) return null
    return try {
        sessions.getActiveSessions(listener)
    } catch (e: SecurityException) {
        null
    }
}

internal fun MediaKey.keyCode(): Int = when (this) {
    MediaKey.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    MediaKey.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
    MediaKey.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
    MediaKey.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
    MediaKey.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
    MediaKey.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
    MediaKey.FAST_FORWARD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
    MediaKey.REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND
}

// ADJUST_TOGGLE_MUTE exists since API 23, so it is safe on minSdk 24.
internal fun VolumeAdjust.direction(): Int = when (this) {
    VolumeAdjust.RAISE -> AudioManager.ADJUST_RAISE
    VolumeAdjust.LOWER -> AudioManager.ADJUST_LOWER
    VolumeAdjust.TOGGLE_MUTE -> AudioManager.ADJUST_TOGGLE_MUTE
}

internal fun playbackStatusOf(state: Int?): PlaybackStatus = when (state) {
    PlaybackState.STATE_PLAYING, PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> PlaybackStatus.PLAYING
    PlaybackState.STATE_PAUSED -> PlaybackStatus.PAUSED
    PlaybackState.STATE_STOPPED -> PlaybackStatus.STOPPED
    PlaybackState.STATE_BUFFERING,
    PlaybackState.STATE_CONNECTING,
    PlaybackState.STATE_SKIPPING_TO_NEXT,
    PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
    -> PlaybackStatus.BUFFERING
    PlaybackState.STATE_ERROR -> PlaybackStatus.ERROR
    else -> PlaybackStatus.IDLE
}
