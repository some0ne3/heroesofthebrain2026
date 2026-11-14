package com.heroesofthebrain.halo.calibration

import android.content.Context
import com.heroesofthebrain.halo.detect.DetectorConfig
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

// Layout under root (app-private storage on the phone):
//   active-profile                      id of the profile whose latest calibration the detector uses
//   <profile id>/profile.json
//   <profile id>/calibration-<ms>.json  one per saved calibration, <ms> is its wall-clock timestamp
//   <profile id>/raw-<ms>/              only if the user opted in to keeping the raw capture
// Blocking file IO: call it off the main thread.
class CalibrationStore(val root: File, private val clock: () -> Long = System::currentTimeMillis) {

    fun profiles(): List<UserProfile> =
        root.listFiles { f -> f.isDirectory && ID.matches(f.name) }.orEmpty()
            .mapNotNull { dir -> read<UserProfile>(File(dir, PROFILE_FILE)) }
            .sortedBy { it.createdAtMs }

    fun profile(id: String): UserProfile? = if (ID.matches(id)) read(File(profileDir(id), PROFILE_FILE)) else null

    fun createProfile(name: String): UserProfile {
        val now = clock()
        val slug = name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24).ifEmpty { "user" }
        var id = "$slug-${now.toString(36)}"
        var n = 1
        while (profileDir(id).exists()) id = "$slug-${now.toString(36)}-${n++}"
        val profile = UserProfile(id, name.trim().ifEmpty { "User" }, now)
        write(File(profileDir(id), PROFILE_FILE), JSON.encodeToString(UserProfile.serializer(), profile))
        return profile
    }

    fun deleteProfile(id: String) {
        require(ID.matches(id)) { "bad profile id $id" }
        profileDir(id).deleteRecursively()
        if (activeProfileId == id) activeProfileId = null
    }

    fun save(calibration: StoredCalibration): File {
        require(profile(calibration.profileId) != null) { "no profile ${calibration.profileId}" }
        val file = calibrationFile(calibration.profileId, calibration.createdAtMs)
        write(file, JSON.encodeToString(StoredCalibration.serializer(), calibration))
        return file
    }

    // Oldest first.
    fun calibrations(profileId: String): List<StoredCalibration> {
        if (!ID.matches(profileId)) return emptyList()
        return profileDir(profileId).listFiles { f -> CALIBRATION.matches(f.name) }.orEmpty()
            .mapNotNull { read<StoredCalibration>(it) }
            .sortedBy { it.createdAtMs }
    }

    fun latest(profileId: String): StoredCalibration? = calibrations(profileId).lastOrNull()

    fun deleteCalibration(profileId: String, createdAtMs: Long) {
        require(ID.matches(profileId)) { "bad profile id $profileId" }
        calibrationFile(profileId, createdAtMs).delete()
        rawCaptureDir(profileId, createdAtMs).deleteRecursively()
    }

    var activeProfileId: String?
        get() = File(root, ACTIVE_FILE).takeIf { it.isFile }?.readText()?.trim()?.takeIf { ID.matches(it) && profileDir(it).isDirectory }
        set(value) {
            val file = File(root, ACTIVE_FILE)
            if (value == null) file.delete() else {
                require(ID.matches(value)) { "bad profile id $value" }
                write(file, value)
            }
        }

    // What the detector should run with: the active profile's latest calibration on top of the code's defaults.
    fun activeConfig(base: DetectorConfig = DetectorConfig()): DetectorConfig =
        activeProfileId?.let { latest(it) }?.detectorConfig(base) ?: base

    fun rawCaptureDir(profileId: String, createdAtMs: Long): File = File(profileDir(profileId), "raw-$createdAtMs")

    private fun profileDir(id: String) = File(root, id)

    private fun calibrationFile(profileId: String, createdAtMs: Long) = File(profileDir(profileId), "calibration-$createdAtMs.json")

    private inline fun <reified T> read(file: File): T? =
        if (!file.isFile) null else runCatching { JSON.decodeFromString<T>(file.readText()) }.getOrNull()

    // Write next to the target and rename, so a crash halfway never leaves a half-written file behind.
    private fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "could not write $file" }
        }
    }

    companion object {
        const val DIR = "calibration"
        private const val PROFILE_FILE = "profile.json"
        private const val ACTIVE_FILE = "active-profile"
        private val ID = Regex("[a-z0-9][a-z0-9-]{0,63}")
        private val CALIBRATION = Regex("calibration-\\d+\\.json")

        internal val JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun inAppStorage(context: Context) = CalibrationStore(File(context.filesDir, DIR))
    }
}
