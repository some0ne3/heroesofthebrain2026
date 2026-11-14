package com.heroesofthebrain.halo.media

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.heroesofthebrain.halo.ui.theme.FreehandColors
import com.heroesofthebrain.halo.ui.theme.FreehandTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Manual test bench for the media layer, separate from the main app.
// Launch it from its own launcher icon ("HALO Media Test") or with
// adb shell am start -n com.heroesofthebrain.halo/.media.MediaTestActivity
class MediaTestActivity : ComponentActivity() {

    private lateinit var tracker: MediaSessionTracker
    private lateinit var output: MediaOutput

    private val log = mutableStateListOf<LogLine>()
    private var seekMs by mutableLongStateOf(MediaCommand.DEFAULT_SEEK_MS)
    private var delayed by mutableStateOf(false)
    private var pending by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        tracker = MediaSessionTracker(this).also { it.start() }
        output = AndroidMediaOutput(this)
        setContent {
            FreehandTheme(darkTheme = true) {
                Screen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tracker.refresh()
    }

    override fun onDestroy() {
        tracker.stop()
        super.onDestroy()
    }

    private fun send(cmd: MediaCommand) {
        val wait = delayed
        lifecycleScope.launch {
            if (wait) {
                pending = "${cmd.describe()} in ${DELAY_MS / 1000} s"
                delay(DELAY_MS)
                pending = null
            }
            // Off the main thread on purpose: the background service will call it that way too.
            val result = withContext(Dispatchers.Default) { output.execute(cmd) }
            log.add(0, LogLine(System.currentTimeMillis(), cmd, result))
            if (log.size > LOG_SIZE) log.removeAt(log.lastIndex)
        }
    }

    private fun commands(): List<MediaCommand> = MediaCommand.all.map {
        when (it) {
            is MediaCommand.SeekForward -> MediaCommand.SeekForward(seekMs)
            is MediaCommand.SeekBack -> MediaCommand.SeekBack(seekMs)
            else -> it
        }
    }

    @Composable
    private fun Screen() {
        val now by tracker.nowPlaying.collectAsStateWithLifecycle()
        Scaffold(
            containerColor = FreehandColors.Ink900,
            contentColor = FreehandColors.Cream,
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(8.dp))
                Text("Media output test", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                AccessCard(now)
                Spacer(Modifier.height(8.dp))
                NowPlayingCard(now)
                Spacer(Modifier.height(12.dp))
                Options()
                Spacer(Modifier.height(8.dp))
                commands().chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { cmd ->
                            OutlinedButton(onClick = { send(cmd) }, modifier = Modifier.weight(1f)) {
                                Text(cmd.describe(), maxLines = 1, fontSize = 13.sp)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                pending?.let {
                    Text("Sending $it…", style = MaterialTheme.typography.bodySmall, color = Amber)
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Text("Results", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
                if (log.isEmpty()) Text("Nothing sent yet", style = MaterialTheme.typography.bodySmall, color = Grey)
                log.forEach { LogRow(it) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    @Composable
    private fun AccessCard(now: NowPlaying) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    if (now.hasAccess) "Notification access: granted" else "Notification access: not granted",
                    color = if (now.hasAccess) Green else Amber,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Access is only used to see media sessions: exact seeking, track title and playback state. " +
                        "Without it, keys and volume still work, seeking falls back to the fast forward and rewind keys, " +
                        "and the state shows as unknown.",
                    style = MaterialTheme.typography.labelSmall,
                )
                Button(onClick = { NotificationAccess.openSettings(this@MediaTestActivity) }) {
                    Text(if (now.hasAccess) "Notification access settings" else "Grant notification access")
                }
            }
        }
    }

    @Composable
    private fun NowPlayingCard(now: NowPlaying) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("State: ${now.status.name.lowercase()}", style = MaterialTheme.typography.bodyMedium)
                now.packageName?.let { Text("App: $it", style = MaterialTheme.typography.bodySmall) }
                if (now.title != null || now.artist != null) {
                    Text(listOfNotNull(now.title, now.artist).joinToString(" - "), style = MaterialTheme.typography.bodySmall)
                }
                if (now.positionMs != null || now.durationMs != null) {
                    Text(
                        "Position ${now.positionMs?.let(::formatPosition) ?: "?"} / ${now.durationMs?.let(::formatPosition) ?: "?"} " +
                            "(as last reported by the player)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Grey,
                    )
                }
            }
        }
    }

    @Composable
    private fun Options() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Seek step", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(4.dp))
            SEEK_STEPS_MS.forEach { step ->
                RadioButton(selected = seekMs == step, onClick = { seekMs = step })
                Text("${step / 1000} s", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = delayed, onCheckedChange = { delayed = it })
            Text(
                "Send ${DELAY_MS / 1000} s after tapping, so you can lock the screen or bring the player to the front",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    @Composable
    private fun LogRow(line: LogLine) {
        val color = when {
            line.result.delivered -> Green
            line.result is MediaResult.Failed -> Red
            else -> Amber
        }
        Row(Modifier.padding(vertical = 3.dp)) {
            Text(timeFormat.format(Date(line.wallTimeMs)), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(line.command.describe(), fontSize = 13.sp)
                Text(line.result.describe(), fontSize = 12.sp, color = color)
            }
        }
    }

    private data class LogLine(val wallTimeMs: Long, val command: MediaCommand, val result: MediaResult)

    private companion object {
        const val DELAY_MS = 5_000L
        const val LOG_SIZE = 50
        val SEEK_STEPS_MS = listOf(5_000L, 10_000L, 30_000L)
        val Green = FreehandColors.Success
        val Amber = FreehandColors.Warning
        val Red = FreehandColors.Error
        val Grey = FreehandColors.SandMuted
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    }
}
