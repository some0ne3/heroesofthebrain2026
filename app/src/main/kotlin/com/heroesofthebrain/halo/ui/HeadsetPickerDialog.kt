package com.heroesofthebrain.halo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heroesofthebrain.halo.eeg.NearbyHeadset
import com.heroesofthebrain.halo.eeg.SavedHeadset
import com.heroesofthebrain.halo.ui.theme.FreehandColors

internal fun signalLabel(rssi: Int): String = when {
    rssi >= -60 -> "Strong signal"
    rssi >= -75 -> "Good signal"
    else -> "Weak signal"
}

internal data class PickerRow(val id: String, val name: String, val detail: String, val tag: String?)

// The connected headset has stopped advertising and a saved one may be out of range, so both are added by hand.
internal fun pickerRows(picker: HeadsetPicker, chosen: SavedHeadset?): List<PickerRow> = buildList {
    picker.connected?.let { add(PickerRow(it.id, it.name, "In use by the remote", "CONNECTED")) }
    picker.found.filter { it.id != picker.connected?.id }.forEach { add(PickerRow(it.id, it.name, signalLabel(it.rssi), null)) }
    if (chosen != null && none { it.id == chosen.id }) {
        add(PickerRow(chosen.id, chosen.name, if (picker.scanning) "Looking for it…" else "Not in range", "SAVED"))
    }
}

@Composable
internal fun HeadsetPickerDialog(
    picker: HeadsetPicker,
    chosen: SavedHeadset?,
    onChoose: (SavedHeadset?) -> Unit,
    onRescan: () -> Unit,
    onDismiss: () -> Unit,
) {
    val list = pickerRows(picker, chosen)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FreehandColors.Ink800,
        titleContentColor = FreehandColors.Cream,
        textContentColor = FreehandColors.Sand,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Choose Headset", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChoiceRow(
                    title = "Closest HALO",
                    detail = "Connects to the strongest signal nearby",
                    tag = null,
                    selected = chosen == null,
                    onClick = { onChoose(null) },
                )
                HorizontalDivider(color = FreehandColors.Ink600, modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    "NEARBY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = FreehandColors.SandMuted,
                )
                Spacer(Modifier.height(6.dp))
                if (picker.scanning) {
                    LinearProgressIndicator(
                        color = FreehandColors.Flame,
                        trackColor = FreehandColors.Ink700,
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                }

                list.forEach { row ->
                    ChoiceRow(
                        title = row.name,
                        detail = row.detail,
                        tag = row.tag,
                        selected = chosen?.id == row.id,
                        onClick = { onChoose(SavedHeadset(row.id, row.name)) },
                    )
                }

                val message = when {
                    picker.error != null -> picker.error
                    list.isNotEmpty() -> null
                    picker.scanning -> "Looking for headsets…"
                    else -> "No HALO headsets found. Make sure the headset is switched on and not connected to another phone."
                }
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (picker.error != null) FreehandColors.Error else FreehandColors.Sand,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = FreehandColors.Cream)) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (!picker.scanning) {
                TextButton(onClick = onRescan, colors = ButtonDefaults.textButtonColors(contentColor = FreehandColors.Flame)) {
                    Text("Scan Again")
                }
            }
        },
    )
}

@Composable
private fun ChoiceRow(title: String, detail: String, tag: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = FreehandColors.Flame, unselectedColor = FreehandColors.Ink500),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FreehandColors.Cream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                tag?.let { Tag(it, if (it == "CONNECTED") FreehandColors.Success else FreehandColors.Sand) }
            }
            Text(detail, style = MaterialTheme.typography.labelSmall, color = FreehandColors.Sand)
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}
