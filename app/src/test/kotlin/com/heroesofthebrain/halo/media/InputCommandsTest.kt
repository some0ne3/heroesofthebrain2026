package com.heroesofthebrain.halo.media

import com.heroesofthebrain.halo.input.ArrowKey
import com.heroesofthebrain.halo.input.InputPlatform
import com.heroesofthebrain.halo.input.SWIPE_DURATION_MS
import com.heroesofthebrain.halo.input.ScreenSize
import com.heroesofthebrain.halo.input.ScrollDirection
import com.heroesofthebrain.halo.input.SwipePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InputCommandsTest {

    private class FakeInput(
        override var connected: Boolean = true,
        override var arrowKeysSupported: Boolean = true,
        var accepts: Boolean = true,
    ) : InputPlatform {
        val swipes = mutableListOf<SwipePath>()
        val arrows = mutableListOf<ArrowKey>()
        override fun screenSize() = ScreenSize(1080, 2400)
        override fun swipe(path: SwipePath): Boolean {
            swipes += path
            return accepts
        }
        override fun pressArrow(key: ArrowKey): Boolean {
            arrows += key
            return accepts
        }
    }

    // Scrolling must never fall through to the media keys or the volume.
    private object UntouchedMedia : MediaPlatform {
        override val volumeFixed = false
        override fun dispatchKey(key: MediaKey) = fail("media key $key sent")
        override fun adjustMusicVolume(adjust: VolumeAdjust) = fail("volume $adjust changed")
        override fun musicVolume(): VolumeState = throw AssertionError("volume read")
        override fun lookupSession(): SessionLookup = SessionLookup.NoAccess
    }

    private val input = FakeInput()
    private val output = PlatformMediaOutput(UntouchedMedia, input)

    @Test
    fun `scroll down swipes up through the middle of the screen by the set distance`() {
        assertEquals(MediaResult.Scrolled(ScrollDirection.DOWN, 50), output.execute(MediaCommand.ScrollDown(50)))
        assertEquals(listOf(SwipePath(x = 540f, fromY = 1800f, toY = 600f, durationMs = SWIPE_DURATION_MS)), input.swipes)
    }

    @Test
    fun `scroll up swipes down`() {
        assertEquals(MediaResult.Scrolled(ScrollDirection.UP, 30), output.execute(MediaCommand.ScrollUp(30)))
        assertEquals(listOf(SwipePath(x = 540f, fromY = 840f, toY = 1560f, durationMs = SWIPE_DURATION_MS)), input.swipes)
    }

    @Test
    fun `the longest scroll stays clear of the screen edges`() {
        output.execute(MediaCommand.ScrollDown(MediaCommand.SCROLL_PERCENT_RANGE.last))
        val path = input.swipes.single()
        assertEquals(2160f, path.fromY)
        assertEquals(240f, path.toY)
    }

    @Test
    fun `arrow commands press the matching key`() {
        val expected = mapOf(
            MediaCommand.ArrowUp to ArrowKey.UP,
            MediaCommand.ArrowDown to ArrowKey.DOWN,
            MediaCommand.ArrowLeft to ArrowKey.LEFT,
            MediaCommand.ArrowRight to ArrowKey.RIGHT,
        )
        expected.forEach { (cmd, key) -> assertEquals(MediaResult.ArrowPressed(key), output.execute(cmd)) }
        assertEquals(expected.values.toList(), input.arrows)
        assertTrue(input.swipes.isEmpty())
    }

    @Test
    fun `nothing is sent while the accessibility service is off`() {
        input.connected = false
        val off = MediaResult.Unsupported(PlatformMediaOutput.INPUT_SERVICE_OFF)
        assertEquals(off, output.execute(MediaCommand.ScrollDown()))
        assertEquals(off, output.execute(MediaCommand.ArrowDown))
        assertTrue(input.swipes.isEmpty())
        assertTrue(input.arrows.isEmpty())
    }

    @Test
    fun `arrow keys report unsupported before Android 13 but scrolling still works`() {
        input.arrowKeysSupported = false
        assertEquals(MediaResult.Unsupported("arrow keys need Android 13 or newer"), output.execute(MediaCommand.ArrowUp))
        assertTrue(input.arrows.isEmpty())
        assertEquals(MediaResult.Scrolled(ScrollDirection.DOWN, 50), output.execute(MediaCommand.ScrollDown()))
    }

    @Test
    fun `a refused gesture or key comes back as failed`() {
        input.accepts = false
        assertEquals(MediaResult.Failed("the system did not accept the swipe"), output.execute(MediaCommand.ScrollDown()))
        assertEquals(MediaResult.Failed("the system did not accept the arrow key"), output.execute(MediaCommand.ArrowRight))
    }

    @Test
    fun `an output without an input platform reports scrolling as unsupported`() {
        val mediaOnly = PlatformMediaOutput(UntouchedMedia)
        assertEquals(MediaResult.Unsupported(PlatformMediaOutput.INPUT_SERVICE_OFF), mediaOnly.execute(MediaCommand.ScrollUp()))
    }
}
