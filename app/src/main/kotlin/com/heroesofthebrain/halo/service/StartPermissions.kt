package com.heroesofthebrain.halo.service

// What to ask for before starting HaloService. On Android 14+ a connectedDevice service refuses to start
// without a Bluetooth runtime permission, so for the headset those block the start. Notifications are only
// asked for once and never block: without them the service still runs, its notification is just hidden.
object StartPermissions {
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    fun toRequest(
        headset: Boolean,
        sdk: Int,
        ble: List<String>,
        granted: (String) -> Boolean,
        askNotifications: Boolean,
    ): List<String> = buildList {
        if (headset) addAll(ble.filterNot(granted))
        if (askNotifications && sdk >= 33 && !granted(POST_NOTIFICATIONS)) add(POST_NOTIFICATIONS)
    }

    fun canStart(headset: Boolean, ble: List<String>, granted: (String) -> Boolean): Boolean = !headset || ble.all(granted)
}
