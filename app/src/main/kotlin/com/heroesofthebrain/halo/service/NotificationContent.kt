package com.heroesofthebrain.halo.service

import com.heroesofthebrain.halo.eeg.SourceStatus
import kotlin.math.roundToInt

data class NotificationContent(val title: String, val text: String, val armed: Boolean) {
    companion object {
        val STARTING = NotificationContent("Starting…", "Disarmed", armed = false)
    }
}

fun notificationContent(s: PipelineState): NotificationContent {
    val status = s.status
    val title = when {
        s.linkLost -> if (status is SourceStatus.Waiting) "Reconnecting: ${status.reason}" else "Reconnecting…"
        status is SourceStatus.Streaming -> if (s.source is SourceRequest.Replay) status.device else "Connected to ${status.device}"
        // Before the first stream, Reconnecting only means the search is being retried.
        status is SourceStatus.Scanning || status is SourceStatus.Reconnecting -> "Looking for the headset…"
        status is SourceStatus.Connecting -> "Connecting to ${status.device}…"
        status is SourceStatus.Waiting -> status.reason
        status is SourceStatus.Failed -> status.message
        status is SourceStatus.Finished -> "Replay finished"
        else -> if (s.running) "Starting…" else "Stopped"
    }
    val text = listOfNotNull(
        s.batteryPct?.let { "Battery ${it.roundToInt()}%" },
        if (s.armed) "Armed" else "Disarmed",
        "actions paused".takeIf { s.linkLost && s.armed },
    ).joinToString(" · ")
    return NotificationContent(title, text, s.armed)
}
