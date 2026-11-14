package com.heroesofthebrain.halo.service

import com.heroesofthebrain.halo.eeg.SourceStatus

// Why an action may not fire right now. The action layer checks PipelineState.actionsAllowed right
// before firing, and should also drop any held or repeating action when it turns false.
enum class ActionBlock { NOT_RUNNING, RECONNECTING, NOT_CONNECTED, DISARMED }

fun actionBlock(running: Boolean, status: SourceStatus, linkLost: Boolean, armed: Boolean): ActionBlock? = when {
    !running -> ActionBlock.NOT_RUNNING
    linkLost -> ActionBlock.RECONNECTING
    status !is SourceStatus.Streaming -> ActionBlock.NOT_CONNECTED
    !armed -> ActionBlock.DISARMED
    else -> null
}
