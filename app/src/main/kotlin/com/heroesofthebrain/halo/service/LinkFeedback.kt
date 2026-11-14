package com.heroesofthebrain.halo.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

// Buzz and beep on link loss and recovery, so the user notices with the phone in a pocket.
// Notification usage and stream, so silent mode and the user's vibration settings still apply.
class LinkFeedback(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val main = Handler(Looper.getMainLooper())

    fun play(alert: LinkAlert) = when (alert) {
        LinkAlert.LOST -> {
            vibrate(longArrayOf(0, 400, 150, 400))
            tone(ToneGenerator.TONE_PROP_NACK, 400)
        }
        LinkAlert.RECOVERED -> {
            vibrate(longArrayOf(0, 120))
            tone(ToneGenerator.TONE_PROP_ACK, 200)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1), attributes)
        } else {
            v.vibrate(pattern, -1, attributes)
        }
    }

    // A ToneGenerator holds an audio track, so make one per beep instead of keeping it for hours.
    private fun tone(type: Int, durationMs: Int) {
        val generator = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME) }.getOrNull() ?: return
        if (!generator.startTone(type, durationMs)) {
            generator.release()
            return
        }
        main.postDelayed({ generator.release() }, durationMs + 200L)
    }

    private companion object {
        const val VOLUME = 80
    }
}
