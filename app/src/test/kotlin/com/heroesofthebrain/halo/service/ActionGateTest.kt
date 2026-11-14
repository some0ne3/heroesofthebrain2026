package com.heroesofthebrain.halo.service

import com.heroesofthebrain.halo.eeg.SourceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionGateTest {

    private val streaming = SourceStatus.Streaming("HALO 1")

    @Test
    fun `only a running, streaming, armed remote with a live link may act`() {
        assertEquals(null, actionBlock(running = true, status = streaming, linkLost = false, armed = true))
        assertTrue(PipelineState(running = true, status = streaming, armed = true).actionsAllowed)
    }

    @Test
    fun `each missing condition blocks with its own reason`() {
        assertEquals(ActionBlock.NOT_RUNNING, actionBlock(running = false, status = streaming, linkLost = false, armed = true))
        assertEquals(ActionBlock.DISARMED, actionBlock(running = true, status = streaming, linkLost = false, armed = false))
        assertEquals(ActionBlock.NOT_CONNECTED, actionBlock(running = true, status = SourceStatus.Scanning, linkLost = false, armed = true))
        assertEquals(ActionBlock.NOT_CONNECTED, actionBlock(running = true, status = SourceStatus.Finished, linkLost = false, armed = true))
    }

    @Test
    fun `a lost link blocks whatever the source reports meanwhile`() {
        val during = listOf(
            SourceStatus.Reconnecting("Headset disconnected"),
            SourceStatus.Scanning,
            SourceStatus.Connecting("HALO 1"),
            SourceStatus.Waiting("Bluetooth is off"),
            // Defensive: the flag wins even if a stale Streaming is still showing.
            streaming,
        )
        during.forEach { status ->
            assertEquals(status.toString(), ActionBlock.RECONNECTING, actionBlock(running = true, status = status, linkLost = true, armed = true))
        }
    }

    @Test
    fun `the default state allows nothing`() {
        assertFalse(PipelineState().actionsAllowed)
        assertEquals(ActionBlock.NOT_RUNNING, PipelineState().actionBlock)
    }
}
