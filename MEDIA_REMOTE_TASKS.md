# HALO media remote: tasks

Goal: control the phone's media player with gestures detected from the HALO headset. Each user calibrates their own gestures, and a macro-style editor binds gestures to actions with timing settings (hold length, repeats, delay before and after, cooldown).

Starting point (2026-11-14): the app builds and the detector passes replay tests on `recordings/ses-001`. The connection to a real headset and the UI have never been run on a phone.

Tasks are ordered by dependency. Each section ends with what "done" means.

## 0. Check the existing app on a real device

- [x] Install the debug APK on a phone, connect the HALO and confirm frames arrive: 4 EEG channels, gyro, contact flags, battery, at the expected sample rate.
- [x] Record each gesture live from the phone and compare the detector's output with the replay results.
- [x] Measure the delay from headset to phone (`session.link`, `sampleHostTimeNs`) and how often samples go missing (`missedSamples`).
- [x] Update the "never run on a device" note once this is done.

Done when: the app gets live data from the headset, and the detector fires on live clenches, double blinks and head turns.

## 1. Background service

- [ ] Move `EegSource` and `Detector` out of `MainViewModel` (`MainViewModel.kt:93`) into a foreground service of type `connectedDevice`. Declare `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_CONNECTED_DEVICE`, and request `POST_NOTIFICATIONS` on Android 13+.
- [ ] Show a persistent notification with connection state, headset battery, armed/disarmed state and a Stop action.
- [ ] Let the UI observe the service (bound service or a shared `StateFlow`) instead of owning the pipeline.
- [ ] Reconnect automatically after a disconnect. Signal it with vibration or a sound, and block all actions until the connection is back.
- [ ] Ask the user to exempt the app from battery optimisation, and explain why.

Done when: gestures still control media with the screen off and the app swiped away.

## 2. Media output

- [ ] Define a `MediaCommand` set: play/pause toggle, play, pause, stop, next, previous, volume up, volume down, mute, seek forward, seek back.
- [ ] Implement the key-based commands with `AudioManager.dispatchMediaKeyEvent` (key down followed by key up). Implement volume with `AudioManager.adjustStreamVolume`.
- [ ] Optional: add a `NotificationListenerService` and use `MediaSessionManager.getActiveSessions` for seeking by a set amount, the current track title and the playback state. This lets bindings depend on whether something is playing, and lets feedback read the track name aloud.
- [ ] Test against Spotify, YouTube Music, a podcast app and a local file player. Write down which commands each one ignores.

Done when: every command works in the tested players, or the exceptions are documented.

## 3. Detector: gestures as states

- [ ] Change the detector output from one-shot `ControlEvent`s to start and end events, for example `GestureStart(type, t)` and `GestureEnd(type, t, durationMs)`. Instant gestures such as a double blink stay single events.
- [ ] Clench: emit an end event when the ratio falls below `clenchOffRatio`, with the duration measured from `clenchSinceS` (`Detector.kt:257-276`). Today the detector fires once after `clenchMinS` and never reports a release.
- [ ] Clench: decide what a tainted clench (head moving, or a blink nearby) produces: no start event, or a cancel event after a start.
- [ ] Head turn: keep the sign of the dominant axis so the detector reports left, right, up and down. The axis energy is currently squared, which loses the direction (`Detector.kt:294-296`). Check on the device which sign is which direction.
- [ ] Head turn: make a turn followed by turning back count as one gesture. Either suppress the return movement in the detector, or report both and let the rule engine merge them.
- [ ] Add replay tests that check the number of head turns in `obrot-glowa-*`. The current tests only check the event type.
- [ ] Optional: report a long eye closure as its own gesture (for example to disarm). Eye closure differs from a double blink by lobe width, not total duration.
- [ ] Update the replay tests for the new event shapes. Add tests for clench hold durations on `szczeka-docisk`.

Done when: the detector reports clench start and end with a duration, signed head turns, and double blinks, all covered by replay tests.

## 4. Calibration

- [ ] Calibration flow: contact check, then rest, then cued gestures (each gesture several times, with a sound and an on-screen cue), then a results screen.
- [ ] Derive a per-user `DetectorConfig` from the captures: clench on/off ratios from rest compared with clench, `blinkLobeUv` from blink peaks, `turnGyroDps` from turn peaks, and the user's typical clench hold lengths.
- [ ] Show the hit rate for each gesture and any false detections during rest. Allow retrying a single gesture without redoing the whole flow.
- [ ] Save calibrations per user profile with a timestamp. Offer to recalibrate each time the headset is put on, because dry-electrode amplitude changes between fittings.
- [ ] Optional, opt-in: keep the raw calibration capture so it can be replayed in tests. Say clearly what is stored and where.
- [ ] Practice mode: the user performs gestures and sees detections live, with no actions fired.

Done when: a new user can calibrate, sees their hit rates, and the detector runs with their thresholds.

## 5. Binding model and rule engine

- [x] Data model: `Profile { bindings, globalSettings }` and `Binding { trigger, conditions, action, timing }`.
- [x] Trigger types:
  - single gesture
  - hold with minimum and maximum duration
  - repeat count with a maximum gap between repeats
  - sequence: gesture A, then gesture B within a time window
- [x] Action types:
  - one media command
  - repeat a command at an interval while the gesture is held
  - a macro: several commands in order, with delays between them
- [x] Timing per binding: delay before, cooldown after, and a cancel window (the action waits and a cancel gesture aborts it).
- [x] Conditions per binding: only when armed; only when media is playing or paused; only when the channels the gesture needs have good contact.
- [x] Global settings: arm/disarm gesture, auto-disarm after inactivity, a global cooldown, a minimum confidence.
- [x] Engine: a state machine that turns the gesture stream plus a clock into actions. Write it in plain Kotlin with no Android dependencies, so it can be unit-tested.
- [x] Ambiguity: when the start of a gesture matches several bindings (short clench vs. long clench, single gesture vs. sequence), the engine waits until only one can match. Report the extra wait to the editor.
- [x] Persistence: store profiles as JSON (kotlinx.serialization or DataStore). Add profile import and export.
- [x] Tests: the engine on synthetic gesture streams, and end to end from replay CSVs to actions.

Done when: a profile loaded from JSON turns replayed recordings into the expected actions in tests.

## 6. Editor UI

- [x] Profile list: create, duplicate, rename, delete, choose the active profile.
- [x] Binding list for a profile, with an on/off toggle per binding.
- [x] Binding editor: choose the trigger, set durations, counts and gaps, choose the action, set the delays, cooldown and repeat interval.
- [x] Prefill duration ranges from the user's calibration, and show their measured hold lengths next to the controls.
- [x] Conflict warnings: overlapping hold ranges, sequences that shadow a single-gesture binding, and the wait an ambiguous binding adds.
- [x] "Try it" for one binding: the user performs the gesture and sees whether it matched and with what timing, with no action fired.
- [x] Ship a default profile.
- [x] Optional: a timeline view of a macro's steps, similar to Razer Synapse.

Done when: a user can build the default profile from scratch in the editor and it behaves the same as the shipped one.

## 7. Feedback

- [ ] Vibration or a sound when a gesture is recognised, when a hold passes each threshold (so the user can tell a short hold from a long one), when an action fires, and when the remote is armed or disarmed.
- [ ] Optional spoken confirmations through TextToSpeech.
- [ ] All feedback works from the service with the screen off.
- [ ] Live status screen: contact dots, armed state, last gesture, last action.

Done when: the user can operate the remote with the screen off using only vibration and sound.

## 8. Evaluation

- [ ] Measure the detection rate for each gesture with cued trials.
- [ ] Measure false actions per minute while sitting still, talking, chewing, walking and listening to music.
- [ ] Compare false actions with and without the arm gesture.
- [ ] Test with several people, since the current thresholds come from one person.
- [ ] Log gestures and actions (not raw EEG) and allow exporting the log for analysis.

Done when: detection rate and false actions per minute are measured for each scenario and person.

## Out of scope

- Recognising arbitrary movements that a user records by example (template matching or a trained model). With dry electrodes and little data, it would mostly learn noise, and the false-trigger rate could not be measured. Calibration of the built-in gestures covers the realistic part of "capture an example".

## Open questions

- Which gesture arms and disarms the remote?
- Which media apps must work?
- One profile per user, or several (for example "walking" and "sitting")?
- Does arming need to work with the screen off?
