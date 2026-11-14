package com.heroesofthebrain.halo.ui

import com.heroesofthebrain.halo.eeg.NearbyHeadset
import com.heroesofthebrain.halo.eeg.SavedHeadset
import com.heroesofthebrain.halo.eeg.withSighting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsetPickerTest {

    private val a = NearbyHeadset("AA", "HALO 001", -70)
    private val b = NearbyHeadset("BB", "HALO 002", -50)

    @Test
    fun `a new advert updates the row in place instead of reordering`() {
        val list = emptyList<NearbyHeadset>().withSighting(a).withSighting(b).withSighting(a.copy(rssi = -40))
        assertEquals(listOf("AA", "BB"), list.map { it.id })
        assertEquals(-40, list.first().rssi)
    }

    @Test
    fun `connected headset is listed first and not twice`() {
        val rows = pickerRows(HeadsetPicker(found = listOf(a, b), connected = b), chosen = null)
        assertEquals(listOf("BB", "AA"), rows.map { it.id })
        assertEquals("CONNECTED", rows.first().tag)
    }

    @Test
    fun `saved headset stays visible when it is out of range`() {
        val saved = SavedHeadset("CC", "HALO 003")
        val rows = pickerRows(HeadsetPicker(scanning = false, found = listOf(a)), chosen = saved)
        assertEquals(listOf("AA", "CC"), rows.map { it.id })
        assertEquals("Not in range", rows.last().detail)
    }

    @Test
    fun `header shows the live name only while running`() {
        val saved = SavedHeadset("AA", "HALO 001")
        assertEquals("HALO 002", headsetLabel(running = true, liveName = "HALO 002", chosen = saved))
        assertEquals("HALO 001", headsetLabel(running = false, liveName = "HALO 002", chosen = saved))
        assertEquals("Closest HALO", headsetLabel(running = false, liveName = null, chosen = null))
    }

    @Test
    fun `only a different headset or an unfinished search forces a reconnect`() {
        assertFalse(needsReconnect(choice = null, connected = a))
        assertFalse(needsReconnect(choice = SavedHeadset("AA", "HALO 001"), connected = a))
        assertTrue(needsReconnect(choice = SavedHeadset("BB", "HALO 002"), connected = a))
        assertTrue(needsReconnect(choice = null, connected = null))
    }

    @Test
    fun `signal label buckets`() {
        assertEquals("Strong signal", signalLabel(-55))
        assertEquals("Good signal", signalLabel(-70))
        assertEquals("Weak signal", signalLabel(-90))
    }
}
