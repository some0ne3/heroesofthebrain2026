package com.heroesofthebrain.halo.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heroesofthebrain.halo.rules.BindingProfile

@Composable
fun NewProfileDialog(
    onConfirm: (name: String, copyDefault: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var copyDefault by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    placeholder = { Text("e.g. Walking, Podcast Mode") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = copyDefault, onCheckedChange = { copyDefault = it })
                    Text("Preload with default media bindings", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim().ifEmpty { "New Profile" }, copyDefault) },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun RenameProfileDialog(
    currentName: String,
    onConfirm: (newName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Profile") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim().ifEmpty { currentName }) },
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun DuplicateProfileDialog(
    originalName: String,
    onConfirm: (newName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("$originalName Copy") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicate Profile") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("New Profile Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim().ifEmpty { "$originalName Copy" }) },
            ) {
                Text("Duplicate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun DeleteProfileDialog(
    profileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Profile?") },
        text = {
            Text("Are you sure you want to delete '$profileName'? This cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun ExportProfileDialog(
    profile: BindingProfile,
    jsonText: String,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Profile (JSON)") },
        text = {
            Column {
                Text(
                    text = "Profile: ${profile.name} (${profile.bindings.size} bindings)",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                ) {
                    Text(
                        text = jsonText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(jsonText))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (copied) "Copied to Clipboard!" else "Copy JSON to Clipboard")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
fun ImportProfileDialog(
    onImport: (jsonText: String) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
) {
    var text by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Profile (JSON)") },
        text = {
            Column {
                Text("Paste exported JSON below:", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("{ \"id\": \"...\", \"bindings\": [...] }") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                )
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = Color(0xFFE53935), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        val clip = clipboard.getText()?.text
                        if (!clip.isNullOrBlank()) text = clip
                    },
                ) {
                    Text("Paste from Clipboard")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(text) },
                enabled = text.isNotBlank(),
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun ResetDefaultDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore Default Profile?") },
        text = {
            Text("This will replace current bindings with the default media control bindings (Play/Pause, Next, Previous, Volume Up/Down).")
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Restore Defaults") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
