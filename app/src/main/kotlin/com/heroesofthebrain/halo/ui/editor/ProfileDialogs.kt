package com.heroesofthebrain.halo.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heroesofthebrain.halo.rules.BindingProfile
import com.heroesofthebrain.halo.ui.theme.FreehandColors

@Composable
fun NewProfileDialog(
    onConfirm: (name: String, copyDefault: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var copyDefault by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FreehandColors.Ink800,
        titleContentColor = FreehandColors.Cream,
        textContentColor = FreehandColors.Sand,
        shape = RoundedCornerShape(28.dp),
        title = { Text("New Profile", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    placeholder = { Text("e.g. Walking, Workout, Podcasts") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = copyDefault,
                        onCheckedChange = { copyDefault = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = FreehandColors.Flame,
                            checkmarkColor = FreehandColors.Ink900,
                        ),
                    )
                    Text("Preload with default media bindings", style = MaterialTheme.typography.bodySmall, color = FreehandColors.Cream)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim().ifEmpty { "New Profile" }, copyDefault) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FreehandColors.Flame,
                    contentColor = FreehandColors.Ink900,
                ),
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = FreehandColors.Sand),
            ) { Text("Cancel") }
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
        containerColor = FreehandColors.Ink800,
        titleContentColor = FreehandColors.Cream,
        textContentColor = FreehandColors.Sand,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Rename Profile", fontWeight = FontWeight.SemiBold) },
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
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FreehandColors.Flame,
                    contentColor = FreehandColors.Ink900,
                ),
            ) {
                Text("Rename", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = FreehandColors.Sand),
            ) { Text("Cancel") }
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
        containerColor = FreehandColors.Ink800,
        titleContentColor = FreehandColors.Cream,
        textContentColor = FreehandColors.Sand,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Duplicate Profile", fontWeight = FontWeight.SemiBold) },
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
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FreehandColors.Flame,
                    contentColor = FreehandColors.Ink900,
                ),
            ) {
                Text("Duplicate", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = FreehandColors.Sand),
            ) { Text("Cancel") }
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
        containerColor = FreehandColors.Ink800,
        titleContentColor = FreehandColors.Cream,
        textContentColor = FreehandColors.Sand,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Delete Profile?", fontWeight = FontWeight.SemiBold) },
        text = {
            Text("Are you sure you want to delete '$profileName'? This cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = FreehandColors.Error, contentColor = FreehandColors.Ink900),
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = FreehandColors.Sand),
            ) { Text("Cancel") }
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
        containerColor = FreehandColors.Ink800,
        titleContentColor = FreehandColors.Cream,
        textContentColor = FreehandColors.Sand,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Export Profile (JSON)", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    text = "Profile: ${profile.name} (${profile.bindings.size} bindings)",
                    style = MaterialTheme.typography.bodySmall,
                    color = FreehandColors.Sand,
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(FreehandColors.Ink900)
                        .border(1.dp, FreehandColors.Ink600, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = jsonText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = FreehandColors.Cream,
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(jsonText))
                        copied = true
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FreehandColors.Cream),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (copied) "Copied to Clipboard!" else "Copy JSON to Clipboard")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FreehandColors.Flame,
                    contentColor = FreehandColors.Ink900,
                ),
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
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
        containerColor = FreehandColors.Ink800,
        titleContentColor = FreehandColors.Cream,
        textContentColor = FreehandColors.Sand,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Import Profile (JSON)", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text("Paste exported Freehand profile JSON below:", style = MaterialTheme.typography.bodySmall, color = FreehandColors.Sand)
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
                    Text(error, color = FreehandColors.Error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        val clip = clipboard.getText()?.text
                        if (!clip.isNullOrBlank()) text = clip
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = FreehandColors.Flame),
                ) {
                    Text("Paste from Clipboard")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(text) },
                enabled = text.isNotBlank(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FreehandColors.Flame,
                    contentColor = FreehandColors.Ink900,
                ),
            ) {
                Text("Import", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = FreehandColors.Sand),
            ) { Text("Cancel") }
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
        containerColor = FreehandColors.Ink800,
        titleContentColor = FreehandColors.Cream,
        textContentColor = FreehandColors.Sand,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Restore Default Profile?", fontWeight = FontWeight.SemiBold) },
        text = {
            Text("This will replace current bindings with the default media control bindings (Play/Pause, Next, Previous, Volume Up/Down).")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FreehandColors.Flame,
                    contentColor = FreehandColors.Ink900,
                ),
            ) {
                Text("Restore Defaults", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = FreehandColors.Sand),
            ) { Text("Cancel") }
        },
    )
}
