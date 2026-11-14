package com.heroesofthebrain.halo.service

import android.content.Context
import com.heroesofthebrain.halo.calibration.CalibrationStore
import com.heroesofthebrain.halo.eeg.BrainAccessSource
import com.heroesofthebrain.halo.eeg.ReplaySource
import com.heroesofthebrain.halo.media.AndroidMediaOutput
import com.heroesofthebrain.halo.media.MediaOutput
import com.heroesofthebrain.halo.media.MediaSessionTracker
import com.heroesofthebrain.halo.rules.ProfileStore
import com.heroesofthebrain.halo.rules.RuleRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// One pipeline per process. HaloService runs it; the UI and later features (calibration, diagnostics,
// the action layer) observe it from here, so none of them has to bind to the service.
object HaloRuntime {
    const val REPLAY_DIR = "replay"

    @Volatile
    private var instance: HaloPipeline? = null
    @Volatile
    private var profileStoreInstance: ProfileStore? = null
    @Volatile
    private var mediaOutputInstance: MediaOutput? = null
    @Volatile
    private var trackerInstance: MediaSessionTracker? = null
    @Volatile
    private var ruleRunnerInstance: RuleRunner? = null

    fun pipeline(context: Context): HaloPipeline =
        instance ?: synchronized(this) { instance ?: create(context.applicationContext).also { instance = it } }

    fun profileStore(context: Context): ProfileStore =
        profileStoreInstance ?: synchronized(this) {
            profileStoreInstance ?: ProfileStore.inAppStorage(context.applicationContext).also { profileStoreInstance = it }
        }

    fun mediaOutput(context: Context): MediaOutput =
        mediaOutputInstance ?: synchronized(this) {
            mediaOutputInstance ?: AndroidMediaOutput(context.applicationContext).also { mediaOutputInstance = it }
        }

    fun mediaTracker(context: Context): MediaSessionTracker =
        trackerInstance ?: synchronized(this) {
            trackerInstance ?: MediaSessionTracker(context.applicationContext).also { trackerInstance = it }
        }

    fun ruleRunner(context: Context): RuleRunner =
        ruleRunnerInstance ?: synchronized(this) {
            ruleRunnerInstance ?: createRuleRunner(context.applicationContext).also { ruleRunnerInstance = it }
        }

    private fun createRuleRunner(app: Context): RuleRunner {
        val pl = pipeline(app)
        val mo = mediaOutput(app)
        val ps = profileStore(app)
        val tracker = mediaTracker(app)
        return RuleRunner(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            pipeline = pl,
            mediaOutput = mo,
            profileProvider = { ps.activeProfile() },
            nowPlayingSource = tracker,
        )
    }

    private fun create(app: Context): HaloPipeline {
        // Created on first use so Replay mode never touches Bluetooth.
        val device by lazy { BrainAccessSource(app) }
        val store = CalibrationStore.inAppStorage(app)
        return HaloPipeline(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            configProvider = { store.activeConfig() },
        ) { request ->
            when (request) {
                SourceRequest.Headset -> device
                is SourceRequest.Replay -> ReplaySource(request.file) { app.assets.open("$REPLAY_DIR/${request.file}.csv") }
            }
        }
    }
}
