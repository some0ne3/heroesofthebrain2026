package com.heroesofthebrain.halo.media

import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

object NotificationAccess {

    fun isGranted(context: Context): Boolean {
        val component = MediaListenerService.component(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        }
        val setting = Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS_SETTING)
        return listenerEnabledIn(setting, component.packageName, component.className)
    }

    // Android 11+ can jump straight to this app's toggle; older versions show the whole list.
    fun openSettings(context: Context) {
        val newTask = if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(newTask)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, MediaListenerService.component(context).flattenToString())
                .addFlags(newTask)
            try {
                context.startActivity(detail)
                return
            } catch (e: ActivityNotFoundException) {
                // some OEM settings apps leave the detail screen out
            }
        }
        context.startActivity(list)
    }

    // Hidden constant behind Settings.Secure.ENABLED_NOTIFICATION_LISTENERS, only needed before API 27.
    private const val ENABLED_LISTENERS_SETTING = "enabled_notification_listeners"
}

// The setting is a ':'-separated list of flattened component names, "pkg/cls" or "pkg/.Cls".
internal fun listenerEnabledIn(setting: String?, packageName: String, className: String): Boolean =
    setting.orEmpty().split(':').any { entry ->
        val pkg = entry.substringBefore('/', "")
        val cls = entry.substringAfter('/', "")
        pkg == packageName && (cls == className || (cls.startsWith('.') && packageName + cls == className))
    }
