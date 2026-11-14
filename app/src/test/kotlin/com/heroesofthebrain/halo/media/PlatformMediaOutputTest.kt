package com.heroesofthebrain.halo.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformMediaOutputTest {

    private class FakeSession(
        override val packageName: String = "com.example.player",
        override val status: PlaybackStatus = PlaybackStatus.PLAYING,
        override val canSeekTo: Boolean = true,
        override val canFastForward: Boolean = false,
        override val canRewind: Boolean = false,
        override val durationMs: Long? = 200_000,
        private val position: Long? = 60_000,
    ) : ActiveSession {
        val calls = mutableListOf<String>()
        override fun positionMs() = position
        override fun seekTo(positionMs: Long) {
            calls += "seekTo $positionMs"
        }
        override fun fastForward() {
            calls += "fastForward"
        }
        override fun rewind() {
            calls += "rewind"
        }
    }

    private class FakePlatform(
        var lookup: SessionLookup = SessionLookup.NoAccess,
        override var volumeFixed: Boolean = false,
        var failure: Exception? = null,
    ) : MediaPlatform {
        val keys = mutableListOf<MediaKey>()
        val adjustments = mutableListOf<VolumeAdjust>()
        var volume = VolumeState(level = 5, max = 15, muted = false)

        override fun dispatchKey(key: MediaKey) {
            failure?.let { throw it }
            keys += key
        }
        override fun adjustMusicVolume(adjust: VolumeAdjust) {
            failure?.let { throw it }
            adjustments += adjust
            volume = when (adjust) {
                VolumeAdjust.RAISE -> volume.copy(level = volume.level + 1, muted = false)
                VolumeAdjust.LOWER -> volume.copy(level = volume.level - 1)
                VolumeAdjust.TOGGLE_MUTE -> volume.copy(muted = !volume.muted)
            }
        }
        override fun musicVolume() = volume
        override fun lookupSession() = lookup
    }

    private val platform = FakePlatform()
    private val output = PlatformMediaOutput(platform)

    private fun withSession(session: FakeSession): FakeSession {
        platform.lookup = SessionLookup.Found(session)
        return session
    }

    @Test
    fun `transport commands become the matching media keys`() {
        val expected = mapOf(
            MediaCommand.PlayPause to MediaKey.PLAY_PAUSE,
            MediaCommand.Play to MediaKey.PLAY,
            MediaCommand.Pause to MediaKey.PAUSE,
            MediaCommand.Stop to MediaKey.STOP,
            MediaCommand.Next to MediaKey.NEXT,
            MediaCommand.Previous to MediaKey.PREVIOUS,
        )
        expected.forEach { (cmd, key) ->
            assertEquals(MediaResult.KeyDispatched(key, likelyTarget = null), output.execute(cmd))
        }
        assertEquals(expected.values.toList(), platform.keys)
        assertTrue(platform.adjustments.isEmpty())
    }

    @Test
    fun `key result names the visible session as likely target`() {
        withSession(FakeSession(packageName = "com.spotify.music"))
        assertEquals(MediaResult.KeyDispatched(MediaKey.NEXT, "com.spotify.music"), output.execute(MediaCommand.Next))
    }

    @Test
    fun `keys are still sent when access shows no session`() {
        platform.lookup = SessionLookup.NoSession
        assertEquals(MediaResult.KeyDispatched(MediaKey.PLAY, null), output.execute(MediaCommand.Play))
        assertEquals(listOf(MediaKey.PLAY), platform.keys)
    }

    @Test
    fun `volume commands adjust the music stream and report the new level`() {
        assertEquals(
            MediaResult.VolumeAdjusted(VolumeAdjust.RAISE, VolumeState(6, 15, false)),
            output.execute(MediaCommand.VolumeUp),
        )
        assertEquals(
            MediaResult.VolumeAdjusted(VolumeAdjust.LOWER, VolumeState(5, 15, false)),
            output.execute(MediaCommand.VolumeDown),
        )
        assertEquals(
            MediaResult.VolumeAdjusted(VolumeAdjust.TOGGLE_MUTE, VolumeState(5, 15, true)),
            output.execute(MediaCommand.Mute),
        )
        assertEquals(
            MediaResult.VolumeAdjusted(VolumeAdjust.TOGGLE_MUTE, VolumeState(5, 15, false)),
            output.execute(MediaCommand.Mute),
        )
        assertTrue(platform.keys.isEmpty())
    }

    @Test
    fun `fixed volume devices report unsupported without touching the stream`() {
        platform.volumeFixed = true
        val result = output.execute(MediaCommand.VolumeUp)
        assertTrue(result is MediaResult.Unsupported)
        assertTrue(platform.adjustments.isEmpty())
    }

    @Test
    fun `seek with access goes through the controller by the requested amount`() {
        val session = withSession(FakeSession(position = 60_000))
        assertEquals(
            MediaResult.ControllerUsed("com.example.player", ControllerAction.SeekTo(60_000, 70_000)),
            output.execute(MediaCommand.SeekForward()),
        )
        assertEquals(
            MediaResult.ControllerUsed("com.example.player", ControllerAction.SeekTo(60_000, 30_000)),
            output.execute(MediaCommand.SeekBack(30_000)),
        )
        assertEquals(listOf("seekTo 70000", "seekTo 30000"), session.calls)
        assertTrue(platform.keys.isEmpty())
    }

    @Test
    fun `seek is clamped to the start and end of the track`() {
        val early = withSession(FakeSession(position = 4_000, durationMs = 200_000))
        output.execute(MediaCommand.SeekBack())
        assertEquals(listOf("seekTo 0"), early.calls)

        val late = withSession(FakeSession(position = 195_000, durationMs = 200_000))
        output.execute(MediaCommand.SeekForward())
        assertEquals(listOf("seekTo 200000"), late.calls)

        val live = withSession(FakeSession(position = 195_000, durationMs = null))
        output.execute(MediaCommand.SeekForward())
        assertEquals(listOf("seekTo 205000"), live.calls)
    }

    @Test
    fun `seek without access falls back to fast forward and rewind keys`() {
        platform.lookup = SessionLookup.NoAccess
        assertEquals(MediaResult.KeyDispatched(MediaKey.FAST_FORWARD, null), output.execute(MediaCommand.SeekForward()))
        assertEquals(MediaResult.KeyDispatched(MediaKey.REWIND, null), output.execute(MediaCommand.SeekBack()))
        assertEquals(listOf(MediaKey.FAST_FORWARD, MediaKey.REWIND), platform.keys)
    }

    @Test
    fun `seek with access but no session reports it and sends nothing`() {
        platform.lookup = SessionLookup.NoSession
        assertEquals(MediaResult.NoActiveSession, output.execute(MediaCommand.SeekForward()))
        assertTrue(platform.keys.isEmpty())
    }

    @Test
    fun `seek uses fast forward or rewind when the player cannot seek to a position`() {
        val session = withSession(FakeSession(canSeekTo = false, canFastForward = true, canRewind = true))
        assertEquals(
            MediaResult.ControllerUsed("com.example.player", ControllerAction.FastForward),
            output.execute(MediaCommand.SeekForward()),
        )
        assertEquals(
            MediaResult.ControllerUsed("com.example.player", ControllerAction.Rewind),
            output.execute(MediaCommand.SeekBack()),
        )
        assertEquals(listOf("fastForward", "rewind"), session.calls)
    }

    @Test
    fun `unknown position falls back to fast forward when available`() {
        val session = withSession(FakeSession(position = null, canFastForward = true))
        assertEquals(
            MediaResult.ControllerUsed("com.example.player", ControllerAction.FastForward),
            output.execute(MediaCommand.SeekForward()),
        )
        assertEquals(listOf("fastForward"), session.calls)
    }

    @Test
    fun `players that advertise no seeking are reported as unsupported`() {
        val session = withSession(FakeSession(canSeekTo = false))
        assertEquals(MediaResult.Unsupported("com.example.player: no seek support advertised"), output.execute(MediaCommand.SeekForward()))

        val noPosition = withSession(FakeSession(position = null))
        assertEquals(MediaResult.Unsupported("com.example.player: position unknown"), output.execute(MediaCommand.SeekBack()))

        assertTrue(session.calls.isEmpty())
        assertTrue(noPosition.calls.isEmpty())
        assertTrue(platform.keys.isEmpty())
    }

    @Test
    fun `platform exceptions come back as failed results`() {
        platform.failure = SecurityException("not allowed")
        assertEquals(MediaResult.Failed("not allowed"), output.execute(MediaCommand.Play))
        platform.failure = IllegalStateException()
        assertEquals(MediaResult.Failed("IllegalStateException"), output.execute(MediaCommand.VolumeUp))
    }

    @Test
    fun `delivered is true only when something was sent`() {
        assertTrue(MediaResult.KeyDispatched(MediaKey.PLAY, null).delivered)
        assertTrue(MediaResult.VolumeAdjusted(VolumeAdjust.RAISE, VolumeState(1, 15, false)).delivered)
        assertTrue(MediaResult.ControllerUsed("p", ControllerAction.FastForward).delivered)
        assertFalse(MediaResult.NoActiveSession.delivered)
        assertFalse(MediaResult.Unsupported("x").delivered)
        assertFalse(MediaResult.Failed("x").delivered)
    }

    @Test
    fun `a lambda is enough to fake the output for the rule engine`() {
        val sent = mutableListOf<MediaCommand>()
        val fake = MediaOutput { cmd ->
            sent += cmd
            MediaResult.KeyDispatched(MediaKey.PLAY_PAUSE, null)
        }
        fake.execute(MediaCommand.PlayPause)
        assertEquals(listOf<MediaCommand>(MediaCommand.PlayPause), sent)
    }
}
