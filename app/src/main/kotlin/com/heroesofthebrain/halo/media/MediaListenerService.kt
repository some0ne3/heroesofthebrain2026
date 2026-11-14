package com.heroesofthebrain.halo.media

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import java.util.concurrent.CopyOnWriteArraySet

// Never reads notifications. It exists because MediaSessionManager.getActiveSessions only answers
// apps that hold an enabled notification listener.
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        connectionListeners.forEach { it() }
    }

    override fun onListenerDisconnected() {
        connectionListeners.forEach { it() }
    }

    companion object {
        private val connectionListeners = CopyOnWriteArraySet<() -> Unit>()

        fun component(context: Context) = ComponentName(context, MediaListenerService::class.java)

        internal fun addConnectionListener(listener: () -> Unit) {
            connectionListeners += listener
        }

        internal fun removeConnectionListener(listener: () -> Unit) {
            connectionListeners -= listener
        }
    }
}
