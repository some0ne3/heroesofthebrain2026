package com.heroesofthebrain.halo.rules

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

// Rewrites saved profile JSON from older app versions before it is decoded. A profile that fails to decode is
// skipped by ProfileStore, so anything renamed in the model needs a rule here.
internal object ProfileMigration {
    private const val TYPE_KEY = "type"
    private const val LEGACY_SEQUENCE = "com.heroesofthebrain.halo.rules.Trigger.Sequence"
    private const val LEGACY_SEQUENCE_GAP_MS = 1000L
    private val INPUT_MACRO = Trigger.InputMacro.serializer().descriptor.serialName

    fun migrate(root: JsonElement): JsonElement {
        val profile = root as? JsonObject ?: return root
        val bindings = profile["bindings"] as? JsonArray ?: return root
        if (bindings.none { binding -> legacySequence(binding) != null }) return root
        val migrated = bindings.map { binding ->
            val trigger = legacySequence(binding) ?: return@map binding
            JsonObject((binding as JsonObject) + ("trigger" to sequenceToInputMacro(trigger)))
        }
        return JsonObject(profile + ("bindings" to JsonArray(migrated)))
    }

    private fun legacySequence(binding: JsonElement): JsonObject? {
        val trigger = (binding as? JsonObject)?.get("trigger") as? JsonObject ?: return null
        val type = (trigger[TYPE_KEY] as? JsonPrimitive)?.content
        return if (type == LEGACY_SEQUENCE) trigger else null
    }

    // The old trigger shared one gap between all steps, which becomes every step's window.
    private fun sequenceToInputMacro(sequence: JsonObject): JsonObject {
        val gapMs = (sequence["maxGapMs"] as? JsonPrimitive)?.longOrNull ?: LEGACY_SEQUENCE_GAP_MS
        val gestures = (sequence["gestures"] as? JsonArray).orEmpty()
        return buildJsonObject {
            put(TYPE_KEY, INPUT_MACRO)
            put(
                "steps",
                JsonArray(
                    gestures.map { gesture ->
                        buildJsonObject {
                            put("gesture", gesture.jsonPrimitive.content)
                            put("withinMs", gapMs)
                        }
                    },
                ),
            )
        }
    }
}
