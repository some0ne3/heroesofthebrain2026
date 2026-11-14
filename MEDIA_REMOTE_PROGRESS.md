# HALO media remote: progress on tasks 0-4

Snapshot of 2026-11-14. The task spec is `MEDIA_REMOTE_TASKS.md` (untracked, next to this file). Each task was done in parallel by its own agent, in its own git worktree, on its own local branch. Nothing has been pushed, and `main` is still at `1cbe06e`.

## Status

| Task | State | Branch / location |
|---|---|---|
| 0. Device check | Done by the user on a real phone (boxes ticked in the spec). Numbers not written down yet. | none |
| 1. Background service | Done, task 3 merged in, 61 tests pass | `media-remote/service` @ `59722c0` |
| 2. Media output | Done | `media-remote/media` @ `61a210f` |
| 3. Detector as states | Done | `media-remote/detector-states` @ `d2b9aba` |
| 4. Calibration | Done | `media-remote/calibration` @ `d3e95db` |
| 5. Rules & Engine | Done, 26 new tests (114 total) pass | `media-remote/rules` |
| 6. Editor UI | Done, 11 new tests (125 total) pass | `media-remote/editor-ui` |
| Integration | Done, 88 tests pass | `media-remote/integrated` @ `a7cd7ef` |

Integration of all branches into `media-remote/integrated` is complete and wired.

## If the two running agents did not finish

Check first: `git branch --list 'media-remote/*'`. If `media-remote/calibration` exists, the last agent finished. Skip to "Integration". Task 1 finished: `media-remote/service` @ `59722c0` contains task 3, the conflicts are resolved and the tree is clean.

### Task 4 (calibration) worktree `agent-aa6ee812a1ce6a3f4`
- Nothing is committed yet. HEAD is at `d2b9aba`, so task 3 is already in. The work is untracked:
  - `app/src/main/kotlin/com/heroesofthebrain/halo/calibration/`: CalibrationController, CalibrationAnalyzer, CalibrationScorer, CalibrationCapture, CaptureFeatures, CalibrationModels, CalibrationGesture, DetectorAdapter
  - tests in `app/src/test/kotlin/com/heroesofthebrain/halo/calibration/`: CalibrationReplayTest, ReplayCaptures
  - the kotlinx.serialization plugin added to `build.gradle.kts` and `app/build.gradle.kts`
- Not seen yet at snapshot time:
  - persistence (profiles plus timestamped calibrations as JSON)
  - the recalibrate-on-headset-fit prompt
  - opt-in raw capture
  - practice mode
  - the Compose screens in `ui/calibration/`
- Commit what is there before doing anything else, so it can't be lost: `git add -A && git commit -m "wip(calibration): ..."` in that worktree. Then finish the rest of section 4 of the spec. Notes:
  - `DetectorAdapter.kt` should be the only file that reads detector events.
  - UI entry points take flows as parameters: `CalibrationRoute(frames: Flow<EegFrame>, ...)` and `PracticeRoute(events: Flow<GestureEvent>, ...)`.
  - Score a clench trial as a hit on a `GestureStart` inside the cue window. Hold lengths come from `GestureEnd.durationMs`.
  - Ideally, cue the turns by direction and derive `turnLeftIsPositiveY` / `turnUpIsPositiveZ` from the measured gyro sign.
  - When done: `git branch -f media-remote/calibration HEAD`.

## Task 3 API (both other branches must use it)

`detect/Gesture.kt`. `ControlType` and `ControlEvent` were removed, including `NONE`; "no gesture" is now `GestureType? = null`.
- `enum GestureType { CLENCH, TURN_LEFT, TURN_RIGHT, TURN_UP, TURN_DOWN, DOUBLE_BLINK, LONG_BLINK }`, with `.held` true only for CLENCH.
- `sealed interface GestureEvent { type; timestampMs; confidence }`:
  - `GestureStart(onsetMs)`: fires `clenchMinS` (0.4 s) after the clench began; `onsetMs` is the real start.
  - `GestureEnd(durationMs = end.timestampMs - start.onsetMs)`
  - `GestureCancel(reason: CancelReason { HEAD_MOTION, BLINK, SIGNAL_LOST })`
  - `GestureInstant`: turns, double blink, long blink.
- `GestureEvent.describe()` gives a short label. `Detector.process(frame): List<GestureEvent>`.
- New `DetectorConfig` fields: `turnMinDeg = 12.0`, `turnReturnMaxS = 6.0`, `turnLeftIsPositiveY = true`, `turnUpIsPositiveZ = true`. The last two are a **provisional** sign guess.
- Turn-and-return is suppressed in the detector (integrated gyro angle). A return after 6 s counts as a new turn.
- Tainted clench: nothing is emitted before the start, and `GestureCancel` after it. Signal gaps cancel with `SIGNAL_LOST`.
- Ground truth for the tests comes from `tools/gesture_ground_truth.py`, not from the detector: 14 horizontal turns, 13 vertical, and clench holds of 0.93/0.98/1.06/1.54/0.56 s.

## Task 1 design (`media-remote/service`)

- **Shared state:** a shared `StateFlow`, not a bound service. `HaloRuntime.pipeline(context)` is the process-wide `HaloPipeline`.
- **Who runs the pipeline:** only `HaloService` calls `pipeline.start()`. Start and stop go through `HaloService.start(ctx, SourceRequest)` and `HaloService.stop(ctx)`. There is no Application subclass.
- **Taps on `HaloPipeline`:**
  - `frames: SharedFlow<EegFrame>` (256 frames, drops the oldest)
  - `events: SharedFlow<GestureEvent>` (64)
  - `linkAlerts`, `trace`
  - `state: StateFlow<PipelineState>`: running, status, linkLost, battery, channels, armed, `actionBlock`, `actionsAllowed`
  - controls: `setArmed` and `toggleArmed`
- **`ActionGate`:** `actionBlock()` returns NOT_RUNNING, RECONNECTING, NOT_CONNECTED, DISARMED, or null when allowed.
- **`LinkMonitor` / `LinkFeedback`:** alerts on loss and recovery only, not on the first connect, a user stop or a replay end.
  - Lost: two long buzzes and an error beep.
  - Recovered: one short buzz and a beep.
  - Both respect silent mode.
- **Foreground service type:** `connectedDevice` for the headset. Replay runs as `specialUse` on Android 14+, so it needs no Bluetooth permission. Partial wake lock while in the foreground. `START_NOT_STICKY`, so the system killing the process does not silently reconnect and re-arm.
- **Arming:** every start and stop resets to disarmed; a link loss does not disarm.
- **Permissions and battery:** `MainActivity` orders the requests: Bluetooth first, then notifications (asked once, never blocking). Battery optimisation gets an explanation dialog on Connect, then `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, and an amber "Fix" row while the app isn't exempt.
- **`BrainAccessSource`:** one-line fix so Reconnecting is set before the disconnect call; before, the gate stayed open during the loss.
- **Manifest:** 7 permissions plus the `HaloService` entry. Adds `androidx.core:core-ktx:1.13.1`.
- **For task 5:** a hold open when the link drops only gets `GestureCancel(SIGNAL_LOST)` once samples resume, and a stopped run sends no cancel. The rule engine must drop open holds and repeats when `actionsAllowed` or `running` goes false.
- **Tests:** 25 new tests in `service/`, 61 in total on the branch.

## Task 2 design (`media-remote/media`)

- `media/`:
  - `MediaCommand` (11 commands; seek carries `amountMs`, default 10 s) and `MediaOutput.execute(cmd): MediaResult`. These are plain Kotlin and never throw.
  - `PlatformMediaOutput` (mapping) over `MediaPlatform`; the Android implementation is `AndroidMediaPlatform` / `AndroidMediaOutput(context)`.
  - `MediaSessionTracker` (`StateFlow<NowPlaying>`), `MediaListenerService` (an empty notification listener, needed for `getActiveSessions`) and `NotificationAccess`.
  - `MediaTestActivity`: the "HALO Media Test" launcher icon, or `adb shell am start -n com.heroesofthebrain.halo/.media.MediaTestActivity`.
- `docs/media-compat.md`: the test procedure and the matrix of command × Spotify, YouTube Music, AntennaPod and VLC. Every cell is "untested".
- 25 tests. Manifest changes are additions only. No serialization plugin, so `MediaCommand` is not `@Serializable` yet; task 5 needs that.
- Android delays a single play/pause by about 300 ms and turns two quick presses into "next". Task 5 must not repeat play/pause faster than that.

## Integration (done @ `a7cd7ef`)

Completed in worktree `.claude/worktrees/integrate` on branch `media-remote/integrated`.
1. Merged `media-remote/service` (includes task 3) + `media-remote/media` (manifest conflict resolved keeping both service and media activities/services).
2. Merged `media-remote/calibration` cleanly with `serialization` plugin.
3. Completed the wiring:
   - Hosted `CalibrationRoute` and `PracticeRoute` directly from `MainScreen`, fed by `pipeline.frames` and `pipeline.events`.
   - Wired `CalibrationStore.activeConfig()` into `HaloPipeline` via `configProvider` so the `Detector` runs with the active user's calibrated thresholds.
   - Connected `RecalibrationPolicy` to trigger the "recalibrate?" dialog on `MainScreen` whenever the headset refits/reconnects after disconnect.
   - Added Calibrate and Practice action buttons to `MainScreen`.
4. Verification:
   - All 88 unit tests pass (`testDebugUnitTest`).
   - Debug APK builds successfully (`assembleDebug`).

## Needs a phone

- Task 0 numbers to write down: latency, missed-sample rate, and which gyro sign is left/right and up/down. Flip `turnLeftIsPositiveY` / `turnUpIsPositiveZ` if the guess is wrong.
- Task 1: run the agent's check list.
  - The notification shows state, battery and Arm/Stop.
  - The battery dialog and its "Fix" row work.
  - Screen off for at least 5 minutes, then the app swiped away: still streaming.
    - `adb shell dumpsys activity services com.heroesofthebrain.halo` shows isForeground=true.
    - `adb shell dumpsys power | grep HaloControl:remote` shows the wake lock.
    - Also test with Doze forced: `adb shell dumpsys deviceidle force-idle`, later `adb shell dumpsys deviceidle unforce`.
  - Headset off and on again: the buzz-and-beep feedback plays, and actions pause and resume.
  - On Android 14+, Replay works without the Bluetooth permission.
  - With notifications denied, the service still runs without crashing.
  - The feedback works in normal, vibrate and silent ringer modes.
  - Samsung and Xiaomi battery savers may still kill the app on swipe.
- Task 2: every cell in `docs/media-compat.md`. Also whether the notification-access toggle appears. On Android 13+, a sideloaded APK may show "Restricted setting" first; allow it in the app's info screen.
- Task 3: real turn-and-return overshoot, and clenches longer than about 4.8 s, which probably end early because the baseline adapts.
- Task 4: a full calibration run with a new user.
- Input macros: the step editor fits on the phone screen, and "double blink, then a head turn within the window" fires on real gestures. A macro step can't use the arm gesture (long blink in the default profile), because the arm gesture is handled before anything else.

## Rules used for this work

- Low-memory build command as above (several Gradle builds were running at once on 16 GB).
- Commits use conventional messages ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Finished work goes to a local branch with `git branch -f <name> HEAD`. Never push.
- No time estimates anywhere.
