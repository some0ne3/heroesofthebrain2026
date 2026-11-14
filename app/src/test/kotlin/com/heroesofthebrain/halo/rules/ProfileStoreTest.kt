package com.heroesofthebrain.halo.rules

import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.media.MediaCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `initial store creates and returns default profile`() {
        val store = ProfileStore(tempFolder.newFolder("profiles"))
        val profiles = store.profiles()

        assertEquals(1, profiles.size)
        assertEquals("default", profiles.first().id)
        assertEquals("Default Profile", profiles.first().name)
        assertEquals("default", store.activeProfileId)
    }

    @Test
    fun `save and load custom profile`() {
        val store = ProfileStore(tempFolder.newFolder("profiles"))
        val custom = BindingProfile(
            id = "custom-profile",
            name = "My Custom Profile",
            enabled = true,
            createdAtMs = 123456L,
            bindings = listOf(
                Binding(
                    id = "b1",
                    name = "Mute on double blink",
                    trigger = Trigger.Single(GestureType.DOUBLE_BLINK),
                    action = RuleAction.SingleCommand(MediaCommand.Mute),
                )
            ),
            globalSettings = GlobalSettings(
                armGesture = GestureType.LONG_BLINK,
                minimumConfidence = 0.6f,
            ),
        )

        store.save(custom)

        val loaded = store.profile("custom-profile")
        assertNotNull(loaded)
        assertEquals("My Custom Profile", loaded!!.name)
        assertEquals(1, loaded.bindings.size)
        assertEquals(MediaCommand.Mute, (loaded.bindings.first().action as RuleAction.SingleCommand).command)
        assertEquals(0.6f, loaded.globalSettings.minimumConfidence, 0.001f)
    }

    @Test
    fun `active profile switching and persistence`() {
        val root = tempFolder.newFolder("profiles")
        val store1 = ProfileStore(root)

        val p2 = BindingProfile(id = "p2", name = "Profile 2", createdAtMs = 200L)
        store1.save(p2)

        store1.activeProfileId = "p2"
        assertEquals("p2", store1.activeProfile().id)

        // New store instance on same folder reads persisted active profile
        val store2 = ProfileStore(root)
        assertEquals("p2", store2.activeProfileId)
        assertEquals("Profile 2", store2.activeProfile().name)
    }

    @Test
    fun `duplicate creates copy with unique id and new name`() {
        val store = ProfileStore(tempFolder.newFolder("profiles"))
        val def = store.profiles().first()

        val copy = store.duplicate(def.id, "Default Copy")

        assertNotNull(copy)
        assertEquals("Default Copy", copy.name)
        assertTrue(copy.id.startsWith("default-copy"))
        assertEquals(def.bindings.size, copy.bindings.size)

        val all = store.profiles()
        assertEquals(2, all.size)
    }

    @Test
    fun `rename updates profile name`() {
        val store = ProfileStore(tempFolder.newFolder("profiles"))
        val p = BindingProfile(id = "user1", name = "Old Name", createdAtMs = 100L)
        store.save(p)

        val renamed = store.rename("user1", "New Name")
        assertNotNull(renamed)
        assertEquals("New Name", renamed!!.name)
        assertEquals("New Name", store.profile("user1")!!.name)
    }

    @Test
    fun `delete removes profile and updates active profile if active was deleted`() {
        val store = ProfileStore(tempFolder.newFolder("profiles"))
        val p1 = BindingProfile(id = "p1", name = "P1", createdAtMs = 100L)
        val p2 = BindingProfile(id = "p2", name = "P2", createdAtMs = 200L)
        store.save(p1)
        store.save(p2)
        store.activeProfileId = "p1"

        store.delete("p1")

        assertNull(store.profile("p1"))
        assertNotNull(store.profile("p2"))
        assertFalse(store.activeProfileId == "p1")
    }

    @Test
    fun `export and import profile as JSON`() {
        val store = ProfileStore(tempFolder.newFolder("profiles"))
        val profile = BindingProfile(
            id = "export-test",
            name = "Exported Profile",
            bindings = listOf(
                Binding(
                    id = "b1",
                    trigger = Trigger.Hold(GestureType.CLENCH, minDurationMs = 600L, maxDurationMs = 1500L),
                    action = RuleAction.SingleCommand(MediaCommand.VolumeUp),
                )
            ),
        )

        val json = store.exportToJson(profile)
        assertTrue(json.contains("Exported Profile"))
        assertTrue(json.contains("VolumeUp"))

        val imported = store.importFromJson(json)
        assertNotNull(imported)
        assertEquals("Exported Profile", imported.name)
        assertEquals(1, imported.bindings.size)
        assertTrue(imported.bindings.first().trigger is Trigger.Hold)
    }
}
