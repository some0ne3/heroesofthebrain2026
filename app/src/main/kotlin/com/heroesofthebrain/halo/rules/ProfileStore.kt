package com.heroesofthebrain.halo.rules

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

class ProfileStore(
    val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        root.mkdirs()
    }

    private val _activeProfileFlow: MutableStateFlow<BindingProfile> by lazy {
        MutableStateFlow(loadInitialActiveProfile())
    }
    val activeProfileFlow: StateFlow<BindingProfile> get() = _activeProfileFlow.asStateFlow()

    private fun loadInitialActiveProfile(): BindingProfile = activeProfile()

    private fun refreshActiveProfile() {
        val current = activeProfile()
        _activeProfileFlow.value = current
    }

    /**
     * Lists all saved profiles, oldest first.
     * If no profiles exist, initializes and returns the default profile.
     */
    fun profiles(): List<BindingProfile> {
        val files = root.listFiles { f -> f.isFile && f.name.endsWith(EXT) && !f.name.startsWith(".") }.orEmpty()
        val loaded = files.mapNotNull { readProfile(it) }.sortedBy { it.createdAtMs }
        return if (loaded.isEmpty()) {
            val def = BindingProfile.defaultProfile(createdAtMs = clock())
            save(def)
            activeProfileId = def.id
            listOf(def)
        } else {
            if (activeProfileId == null) {
                activeProfileId = loaded.first().id
            }
            loaded
        }
    }

    fun profile(id: String): BindingProfile? =
        if (SAFE_ID.matches(id)) readProfile(profileFile(id)) else null

    fun save(profile: BindingProfile): File {
        require(SAFE_ID.matches(profile.id)) { "Invalid profile id: ${profile.id}" }
        val file = profileFile(profile.id)
        writeAtomically(file, json.encodeToString(BindingProfile.serializer(), profile))
        val currentActiveId = activeProfileId
        if (currentActiveId == null || currentActiveId == profile.id) {
            if (currentActiveId == null) {
                activeProfileId = profile.id
            } else {
                refreshActiveProfile()
            }
        }
        return file
    }

    fun delete(id: String) {
        if (!SAFE_ID.matches(id)) return
        profileFile(id).delete()
        if (activeProfileId == id) {
            val remaining = root.listFiles { f -> f.isFile && f.name.endsWith(EXT) }.orEmpty()
            activeProfileId = remaining.firstOrNull()?.name?.removeSuffix(EXT)
        } else {
            refreshActiveProfile()
        }
    }

    fun duplicate(id: String, newName: String): BindingProfile {
        val original = profile(id) ?: throw IllegalArgumentException("No profile with id $id")
        val now = clock()
        val slug = slugify(newName).ifEmpty { "profile" }
        var newId = "$slug-${now.toString(36)}"
        var counter = 1
        while (profileFile(newId).exists()) {
            newId = "$slug-${now.toString(36)}-${counter++}"
        }

        val dup = original.copy(
            id = newId,
            name = newName.trim().ifEmpty { "${original.name} Copy" },
            createdAtMs = now,
        )
        save(dup)
        return dup
    }

    fun rename(id: String, newName: String): BindingProfile? {
        val existing = profile(id) ?: return null
        val updated = existing.copy(name = newName.trim().ifEmpty { existing.name })
        save(updated)
        return updated
    }

    var activeProfileId: String?
        get() {
            val file = File(root, ACTIVE_FILE)
            if (!file.isFile) return null
            val id = file.readText().trim()
            return if (SAFE_ID.matches(id) && profileFile(id).isFile) id else null
        }
        set(value) {
            val file = File(root, ACTIVE_FILE)
            if (value == null) {
                file.delete()
            } else {
                require(SAFE_ID.matches(value)) { "Invalid profile id: $value" }
                writeAtomically(file, value)
            }
            refreshActiveProfile()
        }

    fun activeProfile(): BindingProfile {
        val active = activeProfileId?.let { profile(it) }
        if (active != null) return active
        val all = profiles()
        val picked = all.firstOrNull() ?: BindingProfile.defaultProfile(createdAtMs = clock()).also {
            save(it)
        }
        activeProfileId = picked.id
        return picked
    }

    fun exportToJson(profile: BindingProfile): String =
        json.encodeToString(BindingProfile.serializer(), profile)

    fun importFromJson(jsonText: String): BindingProfile {
        val parsed = json.decodeFromString(BindingProfile.serializer(), jsonText)
        var id = parsed.id
        var counter = 1
        while (profileFile(id).exists()) {
            id = "${parsed.id}-${counter++}"
        }
        val imported = parsed.copy(id = id, createdAtMs = clock())
        save(imported)
        return imported
    }

    private fun profileFile(id: String): File = File(root, "$id$EXT")

    private fun readProfile(file: File): BindingProfile? =
        if (!file.isFile) null else runCatching {
            json.decodeFromString<BindingProfile>(file.readText())
        }.getOrNull()

    private fun writeAtomically(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Failed to atomically write $file" }
        }
    }

    private fun slugify(name: String): String =
        name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24)

    companion object {
        private const val EXT = ".json"
        private const val ACTIVE_FILE = "active-profile"
        private val SAFE_ID = Regex("^[a-zA-Z0-9_-]+$")

        fun inAppStorage(context: Context): ProfileStore =
            ProfileStore(File(context.filesDir, "profiles"))
    }
}
