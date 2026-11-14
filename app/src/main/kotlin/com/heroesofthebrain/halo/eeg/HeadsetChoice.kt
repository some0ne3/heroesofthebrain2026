package com.heroesofthebrain.halo.eeg

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SavedHeadset(val id: String, val name: String)

data class NearbyHeadset(val id: String, val name: String, val rssi: Int)

// Keeps first-seen order so rows don't jump under the user's finger as RSSI wobbles between adverts.
internal fun List<NearbyHeadset>.withSighting(seen: NearbyHeadset): List<NearbyHeadset> {
    val i = indexOfFirst { it.id == seen.id }
    return if (i < 0) this + seen else toMutableList().also { it[i] = seen }
}

// No saved headset means "connect to the closest HALO", which is what the app did before there was a choice.
class HeadsetPreference(private val prefs: SharedPreferences) {
    private val _saved = MutableStateFlow(read())
    val saved: StateFlow<SavedHeadset?> = _saved.asStateFlow()

    fun choose(headset: SavedHeadset?) {
        val edit = prefs.edit()
        if (headset == null) {
            edit.remove(KEY_ID).remove(KEY_NAME)
        } else {
            edit.putString(KEY_ID, headset.id).putString(KEY_NAME, headset.name)
        }
        edit.apply()
        _saved.value = headset
    }

    private fun read(): SavedHeadset? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        return SavedHeadset(id, prefs.getString(KEY_NAME, null) ?: id)
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"

        fun inAppStorage(context: Context) =
            HeadsetPreference(context.getSharedPreferences("headset", Context.MODE_PRIVATE))
    }
}
