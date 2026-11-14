package com.heroesofthebrain.halo.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaHelpersTest {

    @Test
    fun `command set has every command once with a 10 s default seek`() {
        val all = MediaCommand.all
        assertEquals(17, all.size)
        assertEquals(all.size, all.map { it::class }.toSet().size)
        assertEquals(10_000L, MediaCommand.SeekForward().amountMs)
        assertEquals(10_000L, MediaCommand.SeekBack().amountMs)
        assertThrows(IllegalArgumentException::class.java) { MediaCommand.SeekForward(0) }
        assertThrows(IllegalArgumentException::class.java) { MediaCommand.SeekBack(-5_000) }
    }

    @Test
    fun `volume steps and scroll distance stay in range`() {
        assertEquals(1, MediaCommand.VolumeUp().steps)
        assertEquals(50, MediaCommand.ScrollDown().distancePercent)
        assertThrows(IllegalArgumentException::class.java) { MediaCommand.VolumeUp(0) }
        assertThrows(IllegalArgumentException::class.java) { MediaCommand.VolumeDown(11) }
        assertThrows(IllegalArgumentException::class.java) { MediaCommand.ScrollDown(5) }
        assertThrows(IllegalArgumentException::class.java) { MediaCommand.ScrollUp(90) }
    }

    @Test
    fun `only scroll and arrow commands need the input service`() {
        val needing = MediaCommand.all.filter { it.needsInputService }
        assertEquals(
            listOf(
                MediaCommand.ScrollDown(), MediaCommand.ScrollUp(),
                MediaCommand.ArrowUp, MediaCommand.ArrowDown, MediaCommand.ArrowLeft, MediaCommand.ArrowRight,
            ),
            needing,
        )
    }

    @Test
    fun `same kind ignores the amount`() {
        assertTrue(MediaCommand.VolumeUp(4).sameKindAs(MediaCommand.VolumeUp()))
        assertTrue(MediaCommand.ScrollDown(70).sameKindAs(MediaCommand.ScrollDown()))
        assertFalse(MediaCommand.ScrollDown().sameKindAs(MediaCommand.ScrollUp()))
        assertTrue(MediaCommand.ArrowLeft.sameKindAs(MediaCommand.ArrowLeft))
    }

    @Test
    fun `descriptions are readable`() {
        assertEquals("Seek +10 s", MediaCommand.SeekForward().describe())
        assertEquals("Seek -2.5 s", MediaCommand.SeekBack(2_500).describe())
        assertEquals("Volume up", MediaCommand.VolumeUp().describe())
        assertEquals("Volume down ×3", MediaCommand.VolumeDown(3).describe())
        assertEquals("Scroll down 60%", MediaCommand.ScrollDown(60).describe())
        assertEquals("Arrow left", MediaCommand.ArrowLeft.describe())
        assertEquals("volume raise ×2: 7/15", MediaResult.VolumeAdjusted(VolumeAdjust.RAISE, VolumeState(7, 15, false), steps = 2).describe())
        assertEquals("seekTo 1:00 -> 1:10 in p", MediaResult.ControllerUsed("p", ControllerAction.SeekTo(60_000, 70_000)).describe())
        assertEquals("volume toggle_mute: 0/15 (muted)", MediaResult.VolumeAdjusted(VolumeAdjust.TOGGLE_MUTE, VolumeState(0, 15, true)).describe())
        assertEquals("key NEXT -> com.spotify.music", MediaResult.KeyDispatched(MediaKey.NEXT, "com.spotify.music").describe())
    }

    @Test
    fun `position only moves on while playing`() {
        assertEquals(10_000L, estimatePositionMs(10_000, reportedAtMs = 1_000, nowMs = 3_000, speed = 1f, advancing = false))
        assertEquals(12_000L, estimatePositionMs(10_000, reportedAtMs = 1_000, nowMs = 3_000, speed = 1f, advancing = true))
        assertEquals(14_000L, estimatePositionMs(10_000, reportedAtMs = 1_000, nowMs = 3_000, speed = 2f, advancing = true))
        assertEquals(10_000L, estimatePositionMs(10_000, reportedAtMs = 0, nowMs = 3_000, speed = 1f, advancing = true))
        assertNull(estimatePositionMs(-1, reportedAtMs = 1_000, nowMs = 3_000, speed = 1f, advancing = true))
    }

    @Test
    fun `seek target stays inside the track`() {
        assertEquals(0L, clampSeekTarget(-3_000, 100_000))
        assertEquals(100_000L, clampSeekTarget(120_000, 100_000))
        assertEquals(120_000L, clampSeekTarget(120_000, null))
        assertEquals(120_000L, clampSeekTarget(120_000, 0))
    }

    @Test
    fun `primary session prefers the one actually playing`() {
        val s = { it: Pair<String, PlaybackStatus> -> it.second }
        val paused = "paused" to PlaybackStatus.PAUSED
        val playing = "playing" to PlaybackStatus.PLAYING
        val buffering = "buffering" to PlaybackStatus.BUFFERING
        val idle = "idle" to PlaybackStatus.IDLE
        assertEquals(playing, pickPrimary(listOf(paused, buffering, playing), s))
        assertEquals(buffering, pickPrimary(listOf(paused, buffering), s))
        assertEquals(paused, pickPrimary(listOf(idle, paused), s))
        assertEquals(idle, pickPrimary(listOf(idle, "stopped" to PlaybackStatus.STOPPED), s))
        assertNull(pickPrimary(emptyList(), s))
    }

    // Literal Android key codes, so a mixed-up mapping (PLAY vs PLAY_PAUSE) fails here rather than on a phone.
    @Test
    fun `media keys map to the Android key codes`() {
        val expected = mapOf(
            MediaKey.PLAY_PAUSE to 85,
            MediaKey.STOP to 86,
            MediaKey.NEXT to 87,
            MediaKey.PREVIOUS to 88,
            MediaKey.REWIND to 89,
            MediaKey.FAST_FORWARD to 90,
            MediaKey.PLAY to 126,
            MediaKey.PAUSE to 127,
        )
        assertEquals(MediaKey.entries.toSet(), expected.keys)
        expected.forEach { (key, code) -> assertEquals(key.name, code, key.keyCode()) }
    }

    @Test
    fun `volume adjustments map to AudioManager directions`() {
        assertEquals(1, VolumeAdjust.RAISE.direction())
        assertEquals(-1, VolumeAdjust.LOWER.direction())
        assertEquals(101, VolumeAdjust.TOGGLE_MUTE.direction())
    }

    @Test
    fun `playback states map to coarse statuses`() {
        assertEquals(PlaybackStatus.IDLE, playbackStatusOf(null))
        assertEquals(PlaybackStatus.IDLE, playbackStatusOf(0))
        assertEquals(PlaybackStatus.STOPPED, playbackStatusOf(1))
        assertEquals(PlaybackStatus.PAUSED, playbackStatusOf(2))
        assertEquals(PlaybackStatus.PLAYING, playbackStatusOf(3))
        assertEquals(PlaybackStatus.PLAYING, playbackStatusOf(4))
        assertEquals(PlaybackStatus.PLAYING, playbackStatusOf(5))
        assertEquals(PlaybackStatus.BUFFERING, playbackStatusOf(6))
        assertEquals(PlaybackStatus.ERROR, playbackStatusOf(7))
        assertEquals(PlaybackStatus.BUFFERING, playbackStatusOf(8))
        assertEquals(PlaybackStatus.BUFFERING, playbackStatusOf(10))
    }

    @Test
    fun `listener setting is parsed for full and short class names`() {
        val pkg = "com.heroesofthebrain.halo"
        val cls = "com.heroesofthebrain.halo.media.MediaListenerService"
        assertTrue(listenerEnabledIn("com.other/com.other.L:$pkg/$cls", pkg, cls))
        assertTrue(listenerEnabledIn("$pkg/.media.MediaListenerService", pkg, cls))
        assertFalse(listenerEnabledIn("com.other/$cls", pkg, cls))
        assertFalse(listenerEnabledIn("$pkg/.Other", pkg, cls))
        assertFalse(listenerEnabledIn("", pkg, cls))
        assertFalse(listenerEnabledIn(null, pkg, cls))
    }

    @Test
    fun `now playing without access is unknown and not playing`() {
        assertEquals(PlaybackStatus.UNKNOWN, NowPlaying.Unknown.status)
        assertFalse(NowPlaying.Unknown.hasAccess)
        assertFalse(NowPlaying.Unknown.playing)
        assertTrue(NowPlaying(hasAccess = true, status = PlaybackStatus.BUFFERING).playing)
    }
}
