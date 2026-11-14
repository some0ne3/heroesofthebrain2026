package com.heroesofthebrain.halo.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.heroesofthebrain.halo.MainActivity
import com.heroesofthebrain.halo.R

object HaloNotifications {
    const val ID = 1
    private const val CHANNEL = "remote_status"

    // Low importance: the notification is a status display. Alerts go through LinkFeedback instead.
    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Remote status")
            .setDescription("Headset connection and armed state while the remote runs in the background")
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun build(context: Context, content: NotificationContent): Notification {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val open = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val arm = if (content.armed) {
            NotificationCompat.Action(0, "Disarm", HaloService.actionIntent(context, HaloService.ACTION_DISARM))
        } else {
            NotificationCompat.Action(0, "Arm", HaloService.actionIntent(context, HaloService.ACTION_ARM))
        }
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_halo)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // Android 12+ otherwise holds a new service notification back for up to 10 s.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(arm)
            .addAction(NotificationCompat.Action(0, "Stop", HaloService.actionIntent(context, HaloService.ACTION_STOP)))
            .build()
    }

    fun update(context: Context, content: NotificationContent) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(ID, build(context, content))
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
