package com.heroesofthebrain.halo.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heroesofthebrain.halo.media.describe
import com.heroesofthebrain.halo.rules.Binding
import com.heroesofthebrain.halo.ui.theme.FreehandColors

private val BorderColor = FreehandColors.Ink600
private val ConnectorColor = FreehandColors.Ink500
private val NodeBackground = FreehandColors.Ink800
private val AccentTeal = FreehandColors.Pupil
private val AccentOrange = FreehandColors.Flame
private val AccentBlue = FreehandColors.Ember

/**
 * Razer Synapse-style horizontal timeline preview representing the chronological execution flow:
 * [Trigger] ──> [Pre-Delay] ──> [Step 1: Cmd] ──> [Step Delay] ──> [Step 2: Cmd] ──> [Cooldown]
 */
@Composable
fun MacroTimelineView(
    binding: Binding,
    ambiguityWaitMs: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val items = buildTimelineItems(binding, ambiguityWaitMs)
    val scrollState = rememberScrollState()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FreehandColors.Ink800),
        border = androidx.compose.foundation.BorderStroke(1.dp, FreehandColors.Ink600),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "EXECUTION TIMELINE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                )
                val totalMs = items.sumOf { it.durationMs }
                Text(
                    text = if (totalMs > 0) "Est. ~${totalMs} ms" else "Instant trigger",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    TimelineNode(item)
                    if (index < items.lastIndex) {
                        Connector(item.durationMs)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineNode(item: MacroTimelineItem) {
    val (nodeColor, iconText) = when (item.type) {
        MacroItemType.TRIGGER -> AccentOrange to "⚡"
        MacroItemType.PRE_DELAY -> Color(0xFF757575) to "⏱"
        MacroItemType.COMMAND -> AccentBlue to (item.stepIndex?.let { "#$it" } ?: "▶")
        MacroItemType.STEP_DELAY -> Color(0xFF616161) to "⏳"
        MacroItemType.COOLDOWN -> AccentTeal to "❄"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, nodeColor.copy(alpha = 0.6f)),
        modifier = Modifier.width(130.dp),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(nodeColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = iconText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = nodeColor,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = item.type.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = nodeColor,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = item.command?.describe() ?: item.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )

            Text(
                text = if (item.durationMs > 0) "${item.durationMs} ms" else item.subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Connector(delayMs: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(2.dp)
                .background(ConnectorColor),
        )
        if (delayMs > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "+${delayMs}ms",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(2.dp)
                .background(ConnectorColor),
        )
    }
}
