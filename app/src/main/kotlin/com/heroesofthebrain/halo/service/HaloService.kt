package com.heroesofthebrain.halo.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Keeps the process alive while the pipeline runs: foreground, a status notification, a partial wake lock
// and link-loss feedback. It owns the run, not the data; observers read HaloRuntime.pipeline directly.
class HaloService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pipeline by lazy { HaloRuntime.pipeline(this) }
    private lateinit var feedback: LinkFeedback
    private var watching: Job? = null
    private var ruleRunnerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var foreground = false
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        HaloNotifications.ensureChannel(this)
        feedback = LinkFeedback(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_START -> begin(intent.getStringExtra(EXTRA_REPLAY)?.let { SourceRequest.Replay(it) } ?: SourceRequest.Headset)
            ACTION_STOP -> pipeline.stop()
            ACTION_ARM -> pipeline.setArmed(true)
            ACTION_DISARM -> pipeline.setArmed(false)
        }
        if (!foreground) stopSelf(startId)
        // Not sticky: after a system kill, silently reconnecting and re-arming a remote is not wanted.
        return START_NOT_STICKY
    }

    private fun begin(request: SourceRequest) {
        if (!enterForeground(request)) {
            pipeline.fail("Could not start the background service. Check the Bluetooth permission and press Connect again.")
            leave()
            return
        }
        pipeline.start(request)
        holdWakeLock()
        watch()
    }

    private fun enterForeground(request: SourceRequest): Boolean {
        val current = pipeline.state.value
        val notification = HaloNotifications.build(this, if (current.running) notificationContent(current) else NotificationContent.STARTING)
        return try {
            ServiceCompat.startForeground(this, HaloNotifications.ID, notification, foregroundType(request))
            foreground = true
            true
        } catch (e: RuntimeException) {
            // Android 14+ refuses connectedDevice without the Bluetooth permission. MainActivity asks first, so this
            // is a fallback. Going foreground anyway satisfies startForegroundService(), which crashes the app if the
            // service stops before calling startForeground().
            Log.w(TAG, "startForeground failed", e)
            if (Build.VERSION.SDK_INT >= 34) {
                runCatching {
                    ServiceCompat.startForeground(this, HaloNotifications.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    foreground = true
                }
            }
            false
        }
    }

    // Replay has no Bluetooth link to declare, so on 14+ it runs as specialUse and needs no Bluetooth permission.
    private fun foregroundType(request: SourceRequest): Int =
        if (request is SourceRequest.Replay && Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }

    private fun watch() {
        if (watching?.isActive == true) return
        HaloRuntime.mediaTracker(this).start()
        ruleRunnerJob = HaloRuntime.ruleRunner(this).start()
        watching = scope.launch {
            launch { pipeline.linkAlerts.collect { feedback.play(it) } }
            launch {
                pipeline.state.map(::notificationContent).distinctUntilChanged().collect {
                    if (foreground) HaloNotifications.update(this@HaloService, it)
                }
            }
            pipeline.state.map { it.running }.distinctUntilChanged().collect { running -> if (!running) leave() }
        }
    }

    // Runs on the main thread like onStartCommand, so a Start that raced a Stop is already visible here.
    private fun leave() {
        if (pipeline.state.value.running) return
        ruleRunnerJob?.cancel()
        ruleRunnerJob = null
        HaloRuntime.mediaTracker(this).stop()
        watching?.cancel()
        watching = null
        releaseWakeLock()
        if (foreground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        stopSelf(lastStartId)
    }

    // Without it the CPU may sleep with the screen off and frames, replay timers and actions stall.
    @SuppressLint("WakelockTimeout")
    private fun holdWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HaloControl:remote")
            ?.apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        // Normally the run is already over. If the system tears the service down anyway, don't leave BLE running headless.
        pipeline.stop()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        internal const val ACTION_START = "com.heroesofthebrain.halo.action.START"
        internal const val ACTION_STOP = "com.heroesofthebrain.halo.action.STOP"
        internal const val ACTION_ARM = "com.heroesofthebrain.halo.action.ARM"
        internal const val ACTION_DISARM = "com.heroesofthebrain.halo.action.DISARM"
        private const val EXTRA_REPLAY = "replay"
        private const val TAG = "HaloService"

        // Call from the foreground UI only, after StartPermissions.canStart.
        fun start(context: Context, request: SourceRequest) {
            val intent = Intent(context, HaloService::class.java).setAction(ACTION_START)
            if (request is SourceRequest.Replay) intent.putExtra(EXTRA_REPLAY, request.file)
            ContextCompat.startForegroundService(context, intent)
        }

        // The service notices the run ending and takes itself down once the headset is disconnected.
        fun stop(context: Context) = HaloRuntime.pipeline(context).stop()

        internal fun actionIntent(context: Context, action: String): PendingIntent = PendingIntent.getService(
            context,
            action.hashCode(),
            Intent(context, HaloService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
