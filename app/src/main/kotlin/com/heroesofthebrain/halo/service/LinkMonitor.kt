package com.heroesofthebrain.halo.service

import com.heroesofthebrain.halo.eeg.SourceStatus

enum class LinkAlert { LOST, RECOVERED }

// Turns one run's status changes into link alerts. Only a stream that was up and then dropped counts,
// so the first connection, a user stop and the end of a replay stay silent.
class LinkMonitor {
    var lost = false
        private set
    private var wasStreaming = false

    fun onStatus(status: SourceStatus): LinkAlert? = when (status) {
        is SourceStatus.Streaming -> {
            wasStreaming = true
            if (lost) {
                lost = false
                LinkAlert.RECOVERED
            } else {
                null
            }
        }
        SourceStatus.Idle, SourceStatus.Finished -> {
            lost = false
            wasStreaming = false
            null
        }
        // Failed after streaming counts too: with the screen off the user still needs to know the remote died.
        else -> if (wasStreaming && !lost) {
            lost = true
            LinkAlert.LOST
        } else {
            null
        }
    }
}
