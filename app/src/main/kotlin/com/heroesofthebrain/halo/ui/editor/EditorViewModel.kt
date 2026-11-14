package com.heroesofthebrain.halo.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heroesofthebrain.halo.calibration.CalibrationStore
import com.heroesofthebrain.halo.calibration.HoldStats
import com.heroesofthebrain.halo.detect.GestureEvent
import com.heroesofthebrain.halo.rules.AmbiguityResolver
import com.heroesofthebrain.halo.rules.AmbiguityWarning
import com.heroesofthebrain.halo.rules.Binding
import com.heroesofthebrain.halo.rules.BindingProfile
import com.heroesofthebrain.halo.rules.GlobalSettings
import com.heroesofthebrain.halo.rules.ProfileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class EditorUiState(
    val profiles: List<BindingProfile> = emptyList(),
    val selectedProfileId: String = "",
    val activeProfileId: String? = null,
    val currentProfile: BindingProfile = BindingProfile.defaultProfile(),
    val editingBinding: Binding? = null,
    val isNewBinding: Boolean = false,
    val ambiguityWarnings: List<AmbiguityWarning> = emptyList(),
    val calibrationHolds: HoldStats? = null,
    val tryItSession: TryItSession? = null,
    val showNewProfileDialog: Boolean = false,
    val showRenameProfileDialog: Boolean = false,
    val showDuplicateProfileDialog: Boolean = false,
    val showDeleteProfileDialog: Boolean = false,
    val showExportDialog: Boolean = false,
    val showImportDialog: Boolean = false,
    val showResetDefaultDialog: Boolean = false,
    val importError: String? = null,
    val exportJsonText: String = "",
)

class EditorViewModel(
    private val profileStore: ProfileStore,
    private val calibrationStore: CalibrationStore,
    private val events: Flow<GestureEvent>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var tryItTester: TryItTester? = null

    init {
        loadProfiles()
        viewModelScope.launch {
            events.collect { event ->
                tryItTester?.let { tester ->
                    val updated = tester.processEvent(event)
                    _uiState.update { it.copy(tryItSession = updated) }
                }
            }
        }
    }

    fun loadProfiles() {
        val all = profileStore.profiles()
        val activeId = profileStore.activeProfileId ?: all.firstOrNull()?.id.orEmpty()
        val selectedId = _uiState.value.selectedProfileId.ifEmpty { activeId }
        val current = all.firstOrNull { it.id == selectedId } ?: all.firstOrNull() ?: BindingProfile.defaultProfile()

        val holds = calibrationStore.activeProfileId?.let { calibrationStore.latest(it) }?.clenchHolds
            ?: calibrationStore.profiles().firstOrNull()?.id?.let { calibrationStore.latest(it) }?.clenchHolds

        val warnings = AmbiguityResolver.analyze(current.bindings)

        _uiState.update {
            it.copy(
                profiles = all,
                selectedProfileId = current.id,
                activeProfileId = activeId,
                currentProfile = current,
                ambiguityWarnings = warnings,
                calibrationHolds = holds,
            )
        }
    }

    fun selectProfile(id: String) {
        val profile = profileStore.profile(id) ?: return
        val warnings = AmbiguityResolver.analyze(profile.bindings)
        _uiState.update {
            it.copy(
                selectedProfileId = id,
                currentProfile = profile,
                ambiguityWarnings = warnings,
                editingBinding = null,
            )
        }
    }

    fun setActiveProfile(id: String) {
        profileStore.activeProfileId = id
        _uiState.update { it.copy(activeProfileId = id) }
    }

    fun showNewProfileDialog() = _uiState.update { it.copy(showNewProfileDialog = true) }
    fun dismissNewProfileDialog() = _uiState.update { it.copy(showNewProfileDialog = false) }

    fun createProfile(name: String, copyDefault: Boolean) {
        val base = if (copyDefault) BindingProfile.defaultProfile() else BindingProfile(id = "", name = "")
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24).ifEmpty { "profile" }
        val newId = "$slug-${System.currentTimeMillis().toString(36)}"
        val newProfile = base.copy(
            id = newId,
            name = name,
            createdAtMs = System.currentTimeMillis(),
        )
        profileStore.save(newProfile)
        dismissNewProfileDialog()
        loadProfiles()
        selectProfile(newId)
    }

    fun showRenameProfileDialog() = _uiState.update { it.copy(showRenameProfileDialog = true) }
    fun dismissRenameProfileDialog() = _uiState.update { it.copy(showRenameProfileDialog = false) }

    fun renameProfile(newName: String) {
        val current = _uiState.value.currentProfile
        profileStore.rename(current.id, newName)
        dismissRenameProfileDialog()
        loadProfiles()
    }

    fun showDuplicateProfileDialog() = _uiState.update { it.copy(showDuplicateProfileDialog = true) }
    fun dismissDuplicateProfileDialog() = _uiState.update { it.copy(showDuplicateProfileDialog = false) }

    fun duplicateProfile(newName: String) {
        val current = _uiState.value.currentProfile
        val dup = profileStore.duplicate(current.id, newName)
        dismissDuplicateProfileDialog()
        loadProfiles()
        selectProfile(dup.id)
    }

    fun showDeleteProfileDialog() = _uiState.update { it.copy(showDeleteProfileDialog = true) }
    fun dismissDeleteProfileDialog() = _uiState.update { it.copy(showDeleteProfileDialog = false) }

    fun deleteProfile() {
        val current = _uiState.value.currentProfile
        profileStore.delete(current.id)
        dismissDeleteProfileDialog()
        loadProfiles()
    }

    fun showExportDialog() {
        val current = _uiState.value.currentProfile
        val json = profileStore.exportToJson(current)
        _uiState.update { it.copy(showExportDialog = true, exportJsonText = json) }
    }

    fun dismissExportDialog() = _uiState.update { it.copy(showExportDialog = false) }

    fun showImportDialog() = _uiState.update { it.copy(showImportDialog = true, importError = null) }
    fun dismissImportDialog() = _uiState.update { it.copy(showImportDialog = false, importError = null) }

    fun importProfile(jsonText: String) {
        try {
            val imported = profileStore.importFromJson(jsonText)
            dismissImportDialog()
            loadProfiles()
            selectProfile(imported.id)
        } catch (e: Exception) {
            _uiState.update { it.copy(importError = "Invalid JSON format: ${e.message}") }
        }
    }

    fun showResetDefaultDialog() = _uiState.update { it.copy(showResetDefaultDialog = true) }
    fun dismissResetDefaultDialog() = _uiState.update { it.copy(showResetDefaultDialog = false) }

    fun resetToDefault() {
        val current = _uiState.value.currentProfile
        val def = BindingProfile.defaultProfile(id = current.id, name = current.name)
        profileStore.save(def)
        dismissResetDefaultDialog()
        loadProfiles()
    }

    fun toggleBinding(bindingId: String, enabled: Boolean) {
        val current = _uiState.value.currentProfile
        val updatedBindings = current.bindings.map {
            if (it.id == bindingId) it.copy(enabled = enabled) else it
        }
        val updated = current.copy(bindings = updatedBindings)
        profileStore.save(updated)
        val warnings = AmbiguityResolver.analyze(updatedBindings)
        _uiState.update { it.copy(currentProfile = updated, ambiguityWarnings = warnings) }
    }

    fun deleteBinding(bindingId: String) {
        val current = _uiState.value.currentProfile
        val updatedBindings = current.bindings.filter { it.id != bindingId }
        val updated = current.copy(bindings = updatedBindings)
        profileStore.save(updated)
        val warnings = AmbiguityResolver.analyze(updatedBindings)
        _uiState.update { it.copy(currentProfile = updated, ambiguityWarnings = warnings) }
    }

    fun startEditing(binding: Binding) {
        _uiState.update { it.copy(editingBinding = binding, isNewBinding = false) }
    }

    fun startNewBinding() {
        val newId = "binding-${UUID.randomUUID().toString().take(8)}"
        val newBinding = Binding(
            id = newId,
            name = "",
            trigger = com.heroesofthebrain.halo.rules.Trigger.Single(com.heroesofthebrain.halo.detect.GestureType.DOUBLE_BLINK),
            action = com.heroesofthebrain.halo.rules.RuleAction.SingleCommand(com.heroesofthebrain.halo.media.MediaCommand.PlayPause),
        )
        _uiState.update { it.copy(editingBinding = newBinding, isNewBinding = true) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editingBinding = null) }
    }

    fun saveBinding(binding: Binding) {
        val current = _uiState.value.currentProfile
        val exists = current.bindings.any { it.id == binding.id }
        val updatedBindings = if (exists) {
            current.bindings.map { if (it.id == binding.id) binding else it }
        } else {
            current.bindings + binding
        }
        val updated = current.copy(bindings = updatedBindings)
        profileStore.save(updated)
        val warnings = AmbiguityResolver.analyze(updatedBindings)
        _uiState.update {
            it.copy(
                currentProfile = updated,
                ambiguityWarnings = warnings,
                editingBinding = null,
            )
        }
    }

    fun updateGlobalSettings(settings: GlobalSettings) {
        val current = _uiState.value.currentProfile
        val updated = current.copy(globalSettings = settings)
        profileStore.save(updated)
        _uiState.update { it.copy(currentProfile = updated) }
    }

    fun startTryIt(binding: Binding) {
        val current = _uiState.value.currentProfile
        val shadowWait = AmbiguityResolver.maxShadowWaitMs(binding, current.bindings)
        val tester = TryItTester(binding = binding, ambiguityWaitMs = shadowWait)
        tryItTester = tester
        _uiState.update { it.copy(tryItSession = tester.session) }
    }

    fun stopTryIt() {
        tryItTester = null
        _uiState.update { it.copy(tryItSession = null) }
    }

    fun resetTryIt() {
        tryItTester?.reset()
        _uiState.update { it.copy(tryItSession = tryItTester?.session) }
    }

    fun tickTryIt() {
        tryItTester?.tick()
        _uiState.update { it.copy(tryItSession = tryItTester?.session) }
    }
}
