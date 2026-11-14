package com.heroesofthebrain.halo.service

import com.heroesofthebrain.halo.eeg.SourceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkMonitorTest {

    private fun alerts(vararg statuses: SourceStatus): List<LinkAlert> {
        val m = LinkMonitor()
        return statuses.mapNotNull { m.onStatus(it) }
    }

    private val streaming = SourceStatus.Streaming("HALO 1")

    @Test
    fun `first connection is silent, even after failed scans`() {
        val out = alerts(
            SourceStatus.Idle,
            SourceStatus.Scanning,
            SourceStatus.Reconnecting("No HALO headset found nearby"),
            SourceStatus.Scanning,
            SourceStatus.Connecting("HALO 1"),
            streaming,
        )
        assertEquals(emptyList<LinkAlert>(), out)
    }

    @Test
    fun `a drop alerts once and the recovery alerts once`() {
        val out = alerts(
            streaming,
            SourceStatus.Reconnecting("Headset disconnected"),
            SourceStatus.Scanning,
            SourceStatus.Reconnecting("No HALO headset found nearby"),
            SourceStatus.Waiting("Bluetooth is off"),
            SourceStatus.Connecting("HALO 1"),
            streaming,
            SourceStatus.Reconnecting("No data from the headset for 3 s"),
            streaming,
        )
        assertEquals(listOf(LinkAlert.LOST, LinkAlert.RECOVERED, LinkAlert.LOST, LinkAlert.RECOVERED), out)
    }

    @Test
    fun `the status flow may skip Reconnecting and the drop still counts`() {
        assertEquals(listOf(LinkAlert.LOST), alerts(streaming, SourceStatus.Scanning))
    }

    @Test
    fun `user stop and replay end are silent, a failure is not`() {
        assertEquals(emptyList<LinkAlert>(), alerts(streaming, SourceStatus.Idle))
        assertEquals(emptyList<LinkAlert>(), alerts(streaming, SourceStatus.Finished))
        assertEquals(listOf(LinkAlert.LOST), alerts(streaming, SourceStatus.Failed("Bluetooth permission missing")))
    }

    @Test
    fun `lost is set between the alerts`() {
        val m = LinkMonitor()
        m.onStatus(streaming)
        m.onStatus(SourceStatus.Reconnecting("Headset disconnected"))
        assertTrue(m.lost)
        m.onStatus(streaming)
        assertFalse(m.lost)
        m.onStatus(SourceStatus.Reconnecting("Headset disconnected"))
        m.onStatus(SourceStatus.Idle)
        assertFalse("stopping while reconnecting clears it", m.lost)
    }
}
