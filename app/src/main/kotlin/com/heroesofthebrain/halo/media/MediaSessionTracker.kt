package com.heroesofthebrain.halo.media

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Follows the most relevant player (the one playing, else the top-priority session).
// Without notification access it stays at NowPlaying.Unknown. All work happens on the main thread.
class MediaSessionTracker(context: Context) : NowPlayingSource {

    private val app = context.applicationContext
    private val manager = app.getSystemService(MediaSessionManager::class.java)
    private val component = MediaListenerService.component(app)
    private val main = Handler(Looper.getMainLooper())

    private val _nowPlaying = MutableStateFlow(NowPlaying.Unknown)
    override val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private var started = false
    private var listening = false
    private var hasAccess = false
    private var controllers: List<MediaController> = emptyList()

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        if (started && hasAccess) bind(list.orEmpty())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refreshNow()
    }

    private val listenerChanged: () -> Unit = { refresh() }

    fun start() = onMain {
        if (started) return@onMain
        started = true
        MediaListenerService.addConnectionListener(listenerChanged)
        refreshNow()
    }

    fun stop() = onMain {
        if (!started) return@onMain
        started = false
        MediaListenerService.removeConnectionListener(listenerChanged)
        stopListening()
        hasAccess = false
        bind(emptyList())
    }

    // Call when access may have changed, e.g. in onResume after the user visited the settings screen.
    fun refresh() = onMain { refreshNow() }

    private fun refreshNow() {
        if (!started) return
        val list = activeControllers(app, manager, component)
        hasAccess = list != null
        if (list == null) {
            stopListening()
            bind(emptyList())
            return
        }
        if (!listening) {
            try {
                manager.addOnActiveSessionsChangedListener(sessionsChanged, component, main)
                listening = true
            } catch (e: SecurityException) {
                // access was revoked between the two calls; the next refresh sorts it out
            }
        }
        bind(list)
    }

    private fun stopListening() {
        if (!listening) return
        manager.removeOnActiveSessionsChangedListener(sessionsChanged)
        listening = false
    }

    private fun bind(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers = list
        list.forEach { it.registerCallback(controllerCallback, main) }
        publish()
    }

    private fun publish() {
        if (!hasAccess) {
            _nowPlaying.value = NowPlaying.Unknown
            return
        }
        val primary = pickPrimary(controllers) { playbackStatusOf(it.playbackState?.state) }
        _nowPlaying.value = primary?.let(::snapshot) ?: NowPlaying.NoSession
    }

    private fun snapshot(c: MediaController): NowPlaying {
        val state = c.playbackState
        val meta = c.metadata
        return NowPlaying(
            hasAccess = true,
            status = playbackStatusOf(state?.state),
            packageName = c.packageName,
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 },
            positionMs = state?.position?.takeIf { it >= 0 },
        )
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }
}
