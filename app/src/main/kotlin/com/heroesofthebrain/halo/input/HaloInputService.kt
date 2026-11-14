package com.heroesofthebrain.halo.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Swipes and arrow keys into whatever app is in front. It asks for no events and no window content,
// so it cannot see what is on screen. The system binds it only after the user switches it on.
class HaloInputService : AccessibilityService() {

    override fun onServiceConnected() {
        current = this
        _running.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        if (current === this) current = null
        _running.value = false
    }

    internal fun screenSize(): ScreenSize = resources.displayMetrics.let { ScreenSize(it.widthPixels, it.heightPixels) }

    // The rule runner calls these off the main thread. Both are binder calls that lock internally, so that is fine.
    internal fun swipe(path: SwipePath): Boolean {
        val stroke = Path().apply {
            moveTo(path.x, path.fromY)
            lineTo(path.x, path.toY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(stroke, 0L, path.durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    internal fun pressArrow(key: ArrowKey): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val action = when (key) {
            ArrowKey.UP -> GLOBAL_ACTION_DPAD_UP
            ArrowKey.DOWN -> GLOBAL_ACTION_DPAD_DOWN
            ArrowKey.LEFT -> GLOBAL_ACTION_DPAD_LEFT
            ArrowKey.RIGHT -> GLOBAL_ACTION_DPAD_RIGHT
        }
        return performGlobalAction(action)
    }

    companion object {
        @Volatile
        internal var current: HaloInputService? = null
            private set

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun component(context: Context) = ComponentName(context, HaloInputService::class.java)

        fun openSettings(context: Context) {
            val key = component(context).flattenToString()
            // An unofficial extra that AOSP-based settings apps use to scroll to one entry; others ignore it.
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).putExtra(":settings:fragment_args_key", key)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}

object AccessibilityInputPlatform : InputPlatform {
    override val connected: Boolean
        get() = HaloInputService.current != null

    override val arrowKeysSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun screenSize(): ScreenSize = service().screenSize()

    override fun swipe(path: SwipePath): Boolean = HaloInputService.current?.swipe(path) ?: false

    override fun pressArrow(key: ArrowKey): Boolean = HaloInputService.current?.pressArrow(key) ?: false

    private fun service() = HaloInputService.current ?: throw IllegalStateException("accessibility service stopped")
}
