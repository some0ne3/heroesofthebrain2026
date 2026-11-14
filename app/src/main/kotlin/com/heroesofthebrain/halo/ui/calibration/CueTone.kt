package com.heroesofthebrain.halo.ui.calibration

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.heroesofthebrain.halo.calibration.CueKind

// Media stream, since that is the volume a media-remote user already has turned up.
class CueTone {
    // ToneGenerator throws when the audio system has no free tone slot; the cue then stays visual only.
    private val generator: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }.getOrNull()

    fun play(kind: CueKind) {
        when (kind) {
            CueKind.GO -> generator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            CueKind.STEP_DONE -> generator?.startTone(ToneGenerator.TONE_PROP_ACK, 300)
        }
    }

    fun release() {
        generator?.release()
    }

    private companion object {
        const val VOLUME = 80
    }
}

@Composable
fun rememberCueTone(): CueTone {
    val tone = remember { CueTone() }
    DisposableEffect(tone) { onDispose { tone.release() } }
    return tone
}
