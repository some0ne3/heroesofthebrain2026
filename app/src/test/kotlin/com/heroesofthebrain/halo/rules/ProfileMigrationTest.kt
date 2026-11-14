package com.heroesofthebrain.halo.rules

import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.media.MediaCommand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileMigrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { encodeDefaults = true }

    // Encodes a current profile, then swaps the first binding's trigger for the pre-input-macro Sequence shape.
    private fun legacyProfileJson(gestures: List<GestureType>, maxGapMs: Long): String {
        val profile = BindingProfile(
            id = "legacy",
            name = "Legacy",
            createdAtMs = 1L,
            bindings = listOf(
                Binding(
                    id = "seq",
                    name = "Seq",
                    trigger = Trigger.Single(GestureType.DOUBLE_BLINK),
                    action = RuleAction.SingleCommand(MediaCommand.Next),
                ),
                Binding(
                    id = "plain",
                    trigger = Trigger.Single(GestureType.TURN_LEFT),
                    action = RuleAction.SingleCommand(MediaCommand.Previous),
                ),
            ),
        )
        val encoded = json.encodeToJsonElement(BindingProfile.serializer(), profile).jsonObject
        val legacyTrigger = JsonObject(
            mapOf(
                "type" to JsonPrimitive("com.heroesofthebrain.halo.rules.Trigger.Sequence"),
                "gestures" to JsonArray(gestures.map { JsonPrimitive(it.name) }),
                "maxGapMs" to JsonPrimitive(maxGapMs),
            ),
        )
        val bindings = encoded["bindings"]!!.jsonArray
        val first = JsonObject(bindings[0].jsonObject + ("trigger" to legacyTrigger))
        return JsonObject(encoded + ("bindings" to JsonArray(listOf(first) + bindings.drop(1)))).toString()
    }

    @Test
    fun `serial names follow the nested class name the legacy sequence used`() {
        assertEquals(
            "com.heroesofthebrain.halo.rules.Trigger.InputMacro",
            Trigger.InputMacro.serializer().descriptor.serialName,
        )
    }

    @Test
    fun `a saved sequence loads as an input macro with its gap on every step`() {
        val root = tempFolder.newFolder("profiles")
        File(root, "legacy.json").writeText(legacyProfileJson(listOf(GestureType.DOUBLE_BLINK, GestureType.TURN_RIGHT), 1500L))

        val loaded = ProfileStore(root).profile("legacy")!!

        assertEquals(
            Trigger.InputMacro(
                listOf(InputStep(GestureType.DOUBLE_BLINK, withinMs = 1500L), InputStep(GestureType.TURN_RIGHT, withinMs = 1500L)),
            ),
            loaded.bindings.first().trigger,
        )
        assertEquals(Trigger.Single(GestureType.TURN_LEFT), loaded.bindings[1].trigger)
    }

    @Test
    fun `an imported sequence is migrated too`() {
        val store = ProfileStore(tempFolder.newFolder("profiles"))

        val imported = store.importFromJson(legacyProfileJson(listOf(GestureType.TURN_LEFT, GestureType.TURN_RIGHT, GestureType.TURN_UP), 800L))

        val trigger = imported.bindings.first().trigger as Trigger.InputMacro
        assertEquals(listOf(GestureType.TURN_LEFT, GestureType.TURN_RIGHT, GestureType.TURN_UP), trigger.steps.map { it.gesture })
        assertEquals(listOf(800L, 800L, 800L), trigger.steps.map { it.withinMs })
    }

    @Test
    fun `input macros survive a save and load`() {
        val store = ProfileStore(tempFolder.newFolder("profiles"))
        val macro = Trigger.InputMacro(
            listOf(
                InputStep(GestureType.DOUBLE_BLINK),
                InputStep(GestureType.CLENCH, withinMs = 2500L, minHoldMs = 400L, maxHoldMs = 1200L),
                InputStep(GestureType.TURN_RIGHT, withinMs = 900L),
            ),
        )
        val profile = BindingProfile(
            id = "macros",
            name = "Macros",
            bindings = listOf(Binding(id = "m", trigger = macro, action = RuleAction.SingleCommand(MediaCommand.Mute))),
        )

        store.save(profile)

        assertEquals(macro, store.profile("macros")!!.bindings.first().trigger)
    }
}
