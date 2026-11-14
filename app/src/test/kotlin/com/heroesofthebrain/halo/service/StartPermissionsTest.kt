package com.heroesofthebrain.halo.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPermissionsTest {

    private val ble = listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT")
    private val notifications = StartPermissions.POST_NOTIFICATIONS

    private fun request(headset: Boolean, sdk: Int, granted: Set<String>, ask: Boolean = true) =
        StartPermissions.toRequest(headset, sdk, ble, { it in granted }, askNotifications = ask)

    @Test
    fun `headset on Android 14 asks for Bluetooth and notifications together`() {
        assertEquals(ble + notifications, request(headset = true, sdk = 34, granted = emptySet()))
        assertFalse(StartPermissions.canStart(headset = true, ble = ble) { false })
    }

    @Test
    fun `replay never asks for Bluetooth and can always start`() {
        assertEquals(listOf(notifications), request(headset = false, sdk = 34, granted = emptySet()))
        assertTrue(StartPermissions.canStart(headset = false, ble = ble) { false })
    }

    @Test
    fun `notifications are not asked before Android 13 or a second time`() {
        assertEquals(ble, request(headset = true, sdk = 32, granted = emptySet()))
        assertEquals(ble, request(headset = true, sdk = 34, granted = emptySet(), ask = false))
        assertEquals(emptyList<String>(), request(headset = false, sdk = 34, granted = emptySet(), ask = false))
    }

    @Test
    fun `a denied notification permission does not block the start`() {
        assertTrue(StartPermissions.canStart(headset = true, ble = ble) { it in ble })
        assertEquals(emptyList<String>(), request(headset = true, sdk = 34, granted = ble.toSet() + notifications))
    }
}
