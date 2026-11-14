package com.heroesofthebrain.halo.ui

import com.heroesofthebrain.halo.eeg.SourceStatus
import com.heroesofthebrain.halo.service.PipelineState
import com.heroesofthebrain.halo.service.SourceRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadsetNameTest {

    private fun headset(status: SourceStatus) = PipelineState(running = true, source = SourceRequest.Headset, status = status)

    @Test
    fun `takes the name from connecting and streaming`() {
        assertEquals("HALO 049", headsetName(headset(SourceStatus.Connecting("HALO 049"))))
        assertEquals("HALO 049", headsetName(headset(SourceStatus.Streaming("HALO 049"))))
    }

    @Test
    fun `reports nothing while the status has no name`() {
        assertNull(headsetName(headset(SourceStatus.Reconnecting("link lost"))))
        assertNull(headsetName(headset(SourceStatus.Scanning)))
        assertNull(headsetName(headset(SourceStatus.Streaming(" "))))
    }

    @Test
    fun `ignores the replay label`() {
        val replay = PipelineState(running = true, source = SourceRequest.Replay("szczeka-docisk"), status = SourceStatus.Streaming("Replay: szczeka-docisk"))
        assertNull(headsetName(replay))
    }
}
