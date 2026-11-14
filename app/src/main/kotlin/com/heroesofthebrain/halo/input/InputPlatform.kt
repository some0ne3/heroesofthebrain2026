package com.heroesofthebrain.halo.input

enum class ScrollDirection { UP, DOWN }

enum class ArrowKey { UP, DOWN, LEFT, RIGHT }

data class ScreenSize(val widthPx: Int, val heightPx: Int)

data class SwipePath(val x: Float, val fromY: Float, val toY: Float, val durationMs: Long)

// The seam between PlatformMediaOutput and the accessibility service, so the scroll mapping can be tested on the JVM.
interface InputPlatform {
    // Only the user can turn the service on, in the system's Accessibility settings.
    val connected: Boolean

    // GLOBAL_ACTION_DPAD_* only exists on Android 13+.
    val arrowKeysSupported: Boolean

    fun screenSize(): ScreenSize

    // False when the system refused the gesture, for example because the service just went away.
    fun swipe(path: SwipePath): Boolean

    fun pressArrow(key: ArrowKey): Boolean
}

object NoInputPlatform : InputPlatform {
    override val connected = false
    override val arrowKeysSupported = false
    override fun screenSize() = ScreenSize(0, 0)
    override fun swipe(path: SwipePath) = false
    override fun pressArrow(key: ArrowKey) = false
}

// Fast enough to count as a fling, which is what vertical video feeds page on.
const val SWIPE_DURATION_MS = 250L

// The swipe is centred on the screen, so its ends stay away from the status bar and the navigation strip.
fun scrollPath(direction: ScrollDirection, distancePercent: Int, screen: ScreenSize): SwipePath {
    require(distancePercent in 1..100) { "distance must be 1..100 %, got $distancePercent" }
    val half = screen.heightPx * distancePercent / 200f
    val mid = screen.heightPx / 2f
    // The finger moves against the scroll: pushing the content up reveals what is below it.
    val (from, to) = when (direction) {
        ScrollDirection.DOWN -> mid + half to mid - half
        ScrollDirection.UP -> mid - half to mid + half
    }
    return SwipePath(screen.widthPx / 2f, from, to, SWIPE_DURATION_MS)
}
