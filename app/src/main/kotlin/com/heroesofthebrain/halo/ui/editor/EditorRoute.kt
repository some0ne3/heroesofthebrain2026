package com.heroesofthebrain.halo.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heroesofthebrain.halo.calibration.CalibrationStore
import com.heroesofthebrain.halo.detect.GestureEvent
import com.heroesofthebrain.halo.detect.GestureType
import com.heroesofthebrain.halo.rules.AmbiguityResolver
import com.heroesofthebrain.halo.rules.Binding
import com.heroesofthebrain.halo.rules.GlobalSettings
import com.heroesofthebrain.halo.rules.ProfileStore
import com.heroesofthebrain.halo.rules.Trigger
import com.heroesofthebrain.halo.ui.color
import com.heroesofthebrain.halo.ui.title
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val Green = Color(0xFF43A047)
private val Amber = Color(0xFFFFB300)
private val Red = Color(0xFFE53935)
private val Grey = Color(0xFF9E9E9E)

@Composable
fun EditorRoute(
    profileStore: ProfileStore,
    calibrationStore: CalibrationStore,
    events: Flow<GestureEvent>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: EditorViewModel = viewModel {
        EditorViewModel(
            profileStore = profileStore,
            calibrationStore = calibrationStore,
            events = events,
        )
    }

    val state by vm.uiState.collectAsStateWithLifecycle()

    // 1. If currently editing a binding, show the full-screen BindingEditorScreen
    state.editingBinding?.let { binding ->
        BackHandler { vm.cancelEditing() }
        BindingEditorScreen(
            initialBinding = binding,
            allBindings = state.currentProfile.bindings,
            calibrationHolds = state.calibrationHolds,
            onSave = vm::saveBinding,
            onCancel = vm::cancelEditing,
            onTryIt = vm::startTryIt,
            modifier = modifier,
        )
        return
    }

    // 2. If Try-It bench is active
    state.tryItSession?.let { session ->
        BackHandler { vm.stopTryIt() }
        Box(modifier.fillMaxSize()) {
            TryItSheet(
                session = session,
                onReset = vm::resetTryIt,
                onClose = vm::stopTryIt,
                onTick = vm::tickTryIt,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        return
    }

    // 3. Otherwise show the profile and bindings list
    BackHandler(onBack = onClose)

    // Dialogs
    if (state.showNewProfileDialog) {
        NewProfileDialog(
            onConfirm = vm::createProfile,
            onDismiss = vm::dismissNewProfileDialog,
        )
    }
    if (state.showRenameProfileDialog) {
        RenameProfileDialog(
            currentName = state.currentProfile.name,
            onConfirm = vm::renameProfile,
            onDismiss = vm::dismissRenameProfileDialog,
        )
    }
    if (state.showDuplicateProfileDialog) {
        DuplicateProfileDialog(
            originalName = state.currentProfile.name,
            onConfirm = vm::duplicateProfile,
            onDismiss = vm::dismissDuplicateProfileDialog,
        )
    }
    if (state.showDeleteProfileDialog) {
        DeleteProfileDialog(
            profileName = state.currentProfile.name,
            onConfirm = vm::deleteProfile,
            onDismiss = vm::dismissDeleteProfileDialog,
        )
    }
    if (state.showExportDialog) {
        ExportProfileDialog(
            profile = state.currentProfile,
            jsonText = state.exportJsonText,
            onDismiss = vm::dismissExportDialog,
        )
    }
    if (state.showImportDialog) {
        ImportProfileDialog(
            onImport = vm::importProfile,
            onDismiss = vm::dismissImportDialog,
            error = state.importError,
        )
    }
    if (state.showResetDefaultDialog) {
        ResetDefaultDialog(
            onConfirm = vm::resetToDefault,
            onDismiss = vm::dismissResetDefaultDialog,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            EditorTopBar(
                state = state,
                onClose = onClose,
                onSelectProfile = vm::selectProfile,
                onSetActiveProfile = vm::setActiveProfile,
                onNewProfile = vm::showNewProfileDialog,
                onRenameProfile = vm::showRenameProfileDialog,
                onDuplicateProfile = vm::showDuplicateProfileDialog,
                onDeleteProfile = vm::showDeleteProfileDialog,
                onExportProfile = vm::showExportDialog,
                onImportProfile = vm::showImportDialog,
                onResetDefault = vm::showResetDefaultDialog,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
        ) {
            item { Spacer(Modifier.height(12.dp)) }

            // Ambiguity warning banner if any warnings
            if (state.ambiguityWarnings.isNotEmpty()) {
                item {
                    AmbiguityBanner(warnings = state.ambiguityWarnings)
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Global profile settings
            item {
                GlobalSettingsCard(
                    settings = state.currentProfile.globalSettings,
                    onUpdate = vm::updateGlobalSettings,
                )
                Spacer(Modifier.height(16.dp))
            }

            // Section header: Bindings
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "GESTURE BINDINGS (${state.currentProfile.bindings.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            text = "Toggle on/off, edit timing, or test live with Try It",
                            style = MaterialTheme.typography.bodySmall,
                            color = Grey,
                        )
                    }
                    Button(onClick = vm::startNewBinding) {
                        Text("+ Add")
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Empty state
            if (state.currentProfile.bindings.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No bindings in this profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text("Create custom bindings or start from the default media profile.", color = Grey, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = vm::startNewBinding) { Text("Create Binding") }
                                OutlinedButton(onClick = vm::resetToDefault) { Text("Load Default Template") }
                            }
                        }
                    }
                }
            } else {
                items(state.currentProfile.bindings, key = { it.id }) { binding ->
                    val shadowWait = AmbiguityResolver.maxShadowWaitMs(binding, state.currentProfile.bindings)
                    BindingCard(
                        binding = binding,
                        shadowWaitMs = shadowWait,
                        onToggle = { enabled -> vm.toggleBinding(binding.id, enabled) },
                        onEdit = { vm.startEditing(binding) },
                        onTryIt = { vm.startTryIt(binding) },
                        onDelete = { vm.deleteBinding(binding.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

@Composable
private fun EditorTopBar(
    state: EditorUiState,
    onClose: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onSetActiveProfile: (String) -> Unit,
    onNewProfile: () -> Unit,
    onRenameProfile: () -> Unit,
    onDuplicateProfile: () -> Unit,
    onDeleteProfile: () -> Unit,
    onExportProfile: () -> Unit,
    onImportProfile: () -> Unit,
    onResetDefault: () -> Unit,
) {
    var showProfileMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onClose) {
                    Text("←", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                // Profile picker dropdown
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { showProfileMenu = true },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            val isActive = state.selectedProfileId == state.activeProfileId
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Green, CircleShape),
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = state.currentProfile.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("▾", fontSize = 12.sp)
                        }
                    }

                    DropdownMenu(
                        expanded = showProfileMenu,
                        onDismissRequest = { showProfileMenu = false },
                    ) {
                        state.profiles.forEach { profile ->
                            val isActive = profile.id == state.activeProfileId
                            val isSelected = profile.id == state.selectedProfileId
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isActive) {
                                            Box(Modifier.size(6.dp).background(Green, CircleShape))
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = profile.name + (if (isActive) " (Active)" else ""),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                },
                                onClick = {
                                    showProfileMenu = false
                                    onSelectProfile(profile.id)
                                },
                            )
                        }
                    }
                }
            }

            // Options menu button
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Text("⋮", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                ) {
                    val isActive = state.selectedProfileId == state.activeProfileId
                    if (!isActive) {
                        DropdownMenuItem(
                            text = { Text("✓ Set as Active Remote Profile") },
                            onClick = {
                                showMoreMenu = false
                                onSetActiveProfile(state.selectedProfileId)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("+ New Profile") },
                        onClick = {
                            showMoreMenu = false
                            onNewProfile()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("✎ Rename Profile") },
                        onClick = {
                            showMoreMenu = false
                            onRenameProfile()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("⧉ Duplicate Profile") },
                        onClick = {
                            showMoreMenu = false
                            onDuplicateProfile()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("↑ Export Profile (JSON)") },
                        onClick = {
                            showMoreMenu = false
                            onExportProfile()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("↓ Import Profile (JSON)") },
                        onClick = {
                            showMoreMenu = false
                            onImportProfile()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("↺ Restore Shipped Defaults") },
                        onClick = {
                            showMoreMenu = false
                            onResetDefault()
                        },
                    )
                    if (state.profiles.size > 1) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("✕ Delete Profile", color = Red) },
                            onClick = {
                                showMoreMenu = false
                                onDeleteProfile()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbiguityBanner(warnings: List<com.heroesofthebrain.halo.rules.AmbiguityWarning>) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Amber.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Amber.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Amber, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "CONFLICT WARNINGS (${warnings.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Amber,
                )
            }
            Spacer(Modifier.height(6.dp))
            warnings.forEach { warning ->
                Text(
                    text = "• ${warning.message}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun GlobalSettingsCard(
    settings: GlobalSettings,
    onUpdate: (GlobalSettings) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "GLOBAL REMOTE SETTINGS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = "Arming gesture: ${settings.armGesture?.title() ?: "None"} • Cooldown: ${settings.globalCooldownMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = Grey,
                    )
                }
                Text(if (expanded) "▴" else "▾", fontWeight = FontWeight.Bold)
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(Modifier.padding(bottom = 10.dp))

                    Text("Global Arm Gesture", style = MaterialTheme.typography.labelSmall)
                    GestureOptionRow(
                        selected = settings.armGesture,
                        onSelect = { onUpdate(settings.copy(armGesture = it)) },
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("Auto-Disarm After Inactivity", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        listOf(null to "Off", 60_000L to "1 min", 300_000L to "5 min", 600_000L to "10 min").forEach { (timeout, label) ->
                            val isSelected = settings.autoDisarmTimeoutMs == timeout
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.clickable { onUpdate(settings.copy(autoDisarmTimeoutMs = timeout)) },
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Global Cooldown: ${settings.globalCooldownMs} ms", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = settings.globalCooldownMs.toFloat(),
                        onValueChange = { onUpdate(settings.copy(globalCooldownMs = it.roundToLong())) },
                        valueRange = 0f..500f,
                        steps = 9,
                    )

                    Spacer(Modifier.height(4.dp))
                    Text("Minimum Confidence: ${(settings.minimumConfidence * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = settings.minimumConfidence,
                        onValueChange = { onUpdate(settings.copy(minimumConfidence = it)) },
                        valueRange = 0.2f..0.9f,
                        steps = 7,
                    )
                }
            }
        }
    }
}

@Composable
private fun GestureOptionRow(
    selected: GestureType?,
    onSelect: (GestureType?) -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf<GestureType?>(null, GestureType.LONG_BLINK, GestureType.DOUBLE_BLINK, GestureType.CLENCH).forEach { g ->
            val isSelected = g == selected
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                modifier = Modifier.clickable { onSelect(g) },
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    text = g?.title() ?: "None",
                    fontSize = 11.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun BindingCard(
    binding: Binding,
    shadowWaitMs: Long,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onTryIt: () -> Unit,
    onDelete: () -> Unit,
) {
    val gesture = when (val t = binding.trigger) {
        is Trigger.Single -> t.gesture
        is Trigger.Hold -> t.gesture
        is Trigger.Repeat -> t.gesture
        is Trigger.Sequence -> t.gestures.firstOrNull()
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (binding.enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (binding.enabled) MaterialTheme.colorScheme.outlineVariant else Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            // Header Row: Gesture Pill, Name, Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = gesture.color().copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, gesture.color()),
                    ) {
                        Text(
                            text = gesture.title(),
                            color = gesture.color(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = binding.name.ifEmpty { binding.id },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Switch(checked = binding.enabled, onCheckedChange = onToggle)
            }

            Spacer(Modifier.height(6.dp))

            // Subtitle: Trigger & Action details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = binding.triggerDescription(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "▶ ${binding.actionDescription()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Warnings / Chips
            if (shadowWaitMs > 0L) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Amber.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = "Shadowed by sequence/repeat (+${shadowWaitMs}ms extra wait)",
                        fontSize = 10.sp,
                        color = Amber,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(4.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEdit) {
                        Text("Edit")
                    }
                    OutlinedButton(onClick = onTryIt) {
                        Text("Try it")
                    }
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = Red)
                }
            }
        }
    }
}
