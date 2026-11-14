package com.heroesofthebrain.halo.service

import com.heroesofthebrain.halo.eeg.SourceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationContentTest {

    @Test
    fun `streaming headset shows device, battery and armed state`() {
        val s = PipelineState(running = true, source = SourceRequest.Headset, status = SourceStatus.Streaming("HALO 1"), batteryPct = 71.6f, armed = true)
        assertEquals(NotificationContent("Connected to HALO 1", "Battery 72% · Armed", armed = true), notificationContent(s))
    }

    @Test
    fun `a lost link says reconnecting and that actions are paused`() {
        val s = PipelineState(
            running = true, source = SourceRequest.Headset, status = SourceStatus.Scanning,
            linkLost = true, batteryPct = 50f, armed = true,
        )
        assertEquals(NotificationContent("Reconnecting…", "Battery 50% · Armed · actions paused", armed = true), notificationContent(s))
        val off = s.copy(status = SourceStatus.Waiting("Bluetooth is off. Turn it on to connect."))
        assertEquals("Reconnecting: Bluetooth is off. Turn it on to connect.", notificationContent(off).title)
    }

    @Test
    fun `before the first stream a retried scan is still just looking`() {
        val s = PipelineState(running = true, source = SourceRequest.Headset, status = SourceStatus.Reconnecting("No HALO headset found nearby"))
        assertEquals(NotificationContent("Looking for the headset…", "Disarmed", armed = false), notificationContent(s))
    }

    @Test
    fun `replay shows the recording name`() {
        val s = PipelineState(running = true, source = SourceRequest.Replay("podw-mrug"), status = SourceStatus.Streaming("Replay: podw-mrug"))
        assertEquals("Replay: podw-mrug", notificationContent(s).title)
    }

    @Test
    fun `battery jitter below a percent does not change the content`() {
        val a = PipelineState(running = true, status = SourceStatus.Streaming("HALO 1"), batteryPct = 64.2f)
        assertEquals(notificationContent(a), notificationContent(a.copy(batteryPct = 63.8f, clenchRatio = 4.0, missedSamples = 12)))
    }
}
