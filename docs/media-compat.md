# Media output: compatibility

How the app controls other media players, how to test it on a phone, and what each player does with each command.

Status: written without a phone. Nothing in this file has been checked on a device. Every matrix cell says "untested" until someone runs the procedure below.

## What the app sends

Code: `app/src/main/kotlin/com/heroesofthebrain/halo/media/`.

| Command | Without notification access | With notification access |
|---|---|---|
| Play/pause | `KEYCODE_MEDIA_PLAY_PAUSE` (key down, then key up) | same key; the result names the app the key most likely went to |
| Play | `KEYCODE_MEDIA_PLAY` | same |
| Pause | `KEYCODE_MEDIA_PAUSE` | same |
| Stop | `KEYCODE_MEDIA_STOP` | same |
| Next | `KEYCODE_MEDIA_NEXT` | same |
| Previous | `KEYCODE_MEDIA_PREVIOUS` | same |
| Volume up (default 1 step, up to 10) | `adjustStreamVolume(STREAM_MUSIC, ADJUST_RAISE, FLAG_SHOW_UI)`, once per step | same |
| Volume down (default 1 step, up to 10) | `adjustStreamVolume(STREAM_MUSIC, ADJUST_LOWER, FLAG_SHOW_UI)`, once per step | same |
| Mute (toggle) | `adjustStreamVolume(STREAM_MUSIC, ADJUST_TOGGLE_MUTE, FLAG_SHOW_UI)` | same |
| Seek forward (default 10 s) | `KEYCODE_MEDIA_FAST_FORWARD`; the player picks the step | `transportControls.seekTo(position + amount)` on the active session, clamped to the track length. If the player does not advertise seeking: `fastForward()` if advertised, otherwise "unsupported". With no session: "no active session" |
| Seek back (default 10 s) | `KEYCODE_MEDIA_REWIND`; the player picks the step | `seekTo(position - amount)`, clamped at 0, with the same fallbacks using `rewind()` |
| Scroll down / up (default 50 %, 10-80 %) | A swipe through the accessibility service, see [Scrolling and arrow keys](#scrolling-and-arrow-keys) | same |
| Arrow up / down / left / right | `performGlobalAction(GLOBAL_ACTION_DPAD_*)` through the accessibility service, Android 13+ only | same |

"Active session" means the first session that is playing, then buffering, then paused, then the top of `MediaSessionManager.getActiveSessions`.

Every call returns a `MediaResult`:

| Result | Meaning |
|---|---|
| `KeyDispatched(key, likelyTarget)` | Android accepted the key. `likelyTarget` is null without notification access. |
| `VolumeAdjusted(adjust, volume, steps)` | The music stream was adjusted `steps` times. `volume` is the level, maximum and mute flag afterwards. |
| `ControllerUsed(package, action)` | A `MediaController` call went to that app (`seekTo from -> to`, `fastForward` or `rewind`). |
| `Scrolled(direction, distancePercent)` | The system accepted the swipe. |
| `ArrowPressed(key)` | The system accepted the arrow key. |
| `Unsupported(reason)` | Nothing was sent: fixed-volume device, the player advertises no seeking, the accessibility service is off, or arrow keys on Android 12 or older. |
| `NoActiveSession` | Notification access shows no player at all, so a seek had nowhere to go. |
| `Failed(reason)` | Android threw an exception. |

A sent key or controller call does not prove the player reacted. That is what the matrix records.

### Using it from code

```kotlin
val output: MediaOutput = AndroidMediaOutput(context)        // plain-Kotlin interface, fake it with a lambda in tests
val result = output.execute(MediaCommand.SeekForward(30_000))

val tracker = MediaSessionTracker(context).apply { start() }  // tracker.nowPlaying: StateFlow<NowPlaying>
tracker.refresh()                                              // after the user may have changed access
tracker.stop()

NotificationAccess.isGranted(context)
NotificationAccess.openSettings(context)
```

`execute` can be called from any thread. The tracker does its work on the main thread. Without notification access the tracker reports `PlaybackStatus.UNKNOWN`, and all commands still work, with seeking falling back to keys.

## Scrolling and arrow keys

Code: `app/src/main/kotlin/com/heroesofthebrain/halo/input/`.

Android has no API for sending touch, mouse-wheel or keyboard input to another app without root. The only way available to a normal app is an accessibility service, so these commands go through `HaloInputService`. The service asks for gestures only: it receives no accessibility events and cannot read window content, so it never sees what is on screen.

- Scroll down works like a mouse wheel turned down: it shows what is below. In TikTok, YouTube Shorts and Instagram Reels this should be the next video (untested). The finger moves up the middle of the screen: from 50 % + d/2 of the screen height to 50 % - d/2, where d is the distance, over 250 ms. Scroll up is the reverse.
- A 250 ms swipe is fast enough to count as a fling. Vertical video feeds usually turn the page on any fling, so the distance should hardly matter there (untested). In ordinary lists and web pages, a longer distance scrolls further, but the fling carries the content past the swipe itself.
- The distance is limited to 10-80 % so both ends of the swipe stay clear of the status bar (which would pull down the notification shade) and the gesture navigation strip (which would go home).
- If a new swipe starts while the previous one is still running, Android cancels the previous one. With "repeat while held", keep the interval above 250 ms. The editor warns when it is shorter.
- Arrow keys use `GLOBAL_ACTION_DPAD_UP/DOWN/LEFT/RIGHT`, which exist since Android 13. They reach the focused window as D-pad key events, the same codes as a keyboard's arrow keys. Apps that ignore keyboard input ignore them too. Expected but untested: browsers scroll on them, YouTube Shorts in a browser moves to the next Short on arrow down, and the first press in an app may only move focus. Before Android 13 the result is `Unsupported`.
- While the service is off, these commands return `Unsupported` and nothing is sent. The main screen shows a warning while the active profile has an enabled binding that uses them, and the editor shows one when such a command is picked.

Turning it on:

1. Settings > Accessibility > Freehand scroll and arrow keys (it can be under "Installed apps" or "Downloaded apps"), then turn it on. The warning cards open the Accessibility settings directly.
2. On Android 13+, an APK installed outside an app store can find the toggle greyed out as a "Restricted setting". Open App info for Freehand, open the menu in the top corner, choose "Allow restricted settings", and then turn the toggle on.

Play Protect: apps that declare an accessibility service get extra scrutiny, especially when sideloaded. The service is declared with `isAccessibilityTool="true"`, because the app is a hands-free accessibility tool. Untested: whether Play Protect warns about this APK.

## Test bench

`MediaTestActivity` is a standalone screen for this test, separate from the main app. Open it in either of these ways:

- the "HALO Media Test" launcher icon, which the debug APK installs next to "HALO Control"
- `adb shell am start -n com.heroesofthebrain.halo/.media.MediaTestActivity`

The screen shows:

- notification access state, with a button that opens the settings screen to grant it (on Android 11+ it opens this app's toggle directly)
- the tracked player: state, package, title and artist, and position and duration as last reported by the player
- a seek step choice (5, 10 or 30 s)
- "Send 5 s after tapping", for testing with the screen locked or with the player in front
- one button per command
- a log of each command and its `MediaResult`. Green means something was sent, amber means unsupported or no session, and red means failed.

## Procedure

Setup:

1. Install the debug APK: `./gradlew installDebug`, or `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
2. Fill in the "Test run" table: phone, Android version, audio output, and the version of each player.
3. Use the same audio output (phone speaker or one Bluetooth headset) for the whole run. Volume behaviour depends on it.
4. In each player, queue at least two items that are each longer than 3 minutes, so Next, Previous and seeking have room.

Pass A, without notification access:

1. Make sure access is off: Settings > Notifications > Device and app notifications (the name varies by phone) > "HALO Control media controls" off. The test bench should say "not granted" and show the state as "unknown".
2. Start playback in the player, then switch to the test bench.
3. Tap each command in matrix order. After each one, check the player (sound, its notification, or its own screen) and write down the effect.
4. Test Previous twice: once more than 5 s into a track, and once within the first 2 s.
5. After Stop, tap Play and note whether playback comes back.
6. Seek forward and Seek back in this pass go out as keys. Note the step the player used, if it reacted at all.

Pass B, with notification access:

1. Tap "Grant notification access" and turn the toggle on. If the toggle is greyed out, see "Restricted settings" below.
2. Back in the test bench, the state card should show the player's package, title and state.
3. Tap Seek forward and Seek back. The log line should read `seekTo a -> b in <package>`. Check that the player's position moved by the chosen step.
4. Tap Play/pause and Next and check that the state and title in the card follow.
5. Repeat any command that behaved oddly in pass A, to see whether access changes anything. It should not change keys or volume.

Screen off and player in front:

1. Tick "Send 5 s after tapping", tap Play/pause, and lock the screen within 5 s. Note whether playback toggles.
2. Repeat with Next and Volume up.
3. Tick the box again, tap a command, and switch to the player app within 5 s.

No session:

1. Force-stop every player (Settings > Apps > the player > Force stop).
2. With access on, tap Play. The log should show no target. Note whether a player starts anyway (Android 8+ can restart the last player for a media key).
3. Tap Seek forward. Expect "no active media session".

How to fill a cell: "works", "ignored", or "partial: <what happened>". Leave "untested" for anything not tried.

## Test run

| Field | Value |
|---|---|
| Date | untested |
| Phone and Android version | untested |
| Audio output | untested |
| Spotify version | untested |
| YouTube Music version | untested |
| AntennaPod version | untested |
| VLC for Android version | untested |

## Matrix

Players: Spotify (music streaming), YouTube Music (music streaming), AntennaPod (podcasts), VLC for Android (local files).

| Command | Spotify | YouTube Music | AntennaPod | VLC (local files) |
|---|---|---|---|---|
| Play/pause | untested | untested | untested | untested |
| Play | untested | untested | untested | untested |
| Pause | untested | untested | untested | untested |
| Stop | untested | untested | untested | untested |
| Play after Stop | untested | untested | untested | untested |
| Next | untested | untested | untested | untested |
| Previous (more than 5 s in) | untested | untested | untested | untested |
| Previous (first 2 s) | untested | untested | untested | untested |
| Volume up | untested | untested | untested | untested |
| Volume down | untested | untested | untested | untested |
| Mute, then Mute again | untested | untested | untested | untested |
| Seek forward, no access (key) | untested | untested | untested | untested |
| Seek back, no access (key) | untested | untested | untested | untested |
| Seek forward, with access (controller) | untested | untested | untested | untested |
| Seek back, with access (controller) | untested | untested | untested | untested |
| Title shown, with access | untested | untested | untested | untested |
| Playback state shown, with access | untested | untested | untested | untested |
| Play/pause with screen locked | untested | untested | untested | untested |
| Next with screen locked | untested | untested | untested | untested |

Commands each player ignores: none recorded yet.

## Known behaviours (unverified)

These come from the Android documentation and from common reports. None of them has been checked on a device for this project. Confirm or correct each one while filling in the matrix.

Key routing:

- Media keys go to the session that Android picks for media buttons. On Android 8+ this is the app that most recently played audio. It is not always the app the test bench shows.
- If no player has a session, Android 8+ can pass the key to the last player's media button receiver, which may start that player again.
- The default media button handling (`MediaSession.Callback.onMediaButtonEvent`, and the same logic in androidx `MediaSessionCompat`) only acts on a key when the player advertises the matching action in its `PlaybackState`. A player that does not advertise fast forward drops the fast forward key without any error, and the app still sees `KeyDispatched`.
- The same default handling holds back a single play/pause by the double-tap timeout (about 300 ms), and turns two play/pause presses within that window into "next". The rule engine should not repeat Play/pause faster than that. Players with their own key handling may behave differently.

Transport:

- Previous: many players restart the current track when you are more than a few seconds in, and only go to the previous track when pressed near the start.
- Stop: some players treat it as pause. Others end their session and remove their notification, after which Play may only work through the restart path above.
- Fast forward and rewind keys: the player picks the step. Podcast apps usually support them, and many music apps do not. AntennaPod reportedly has settings for what hardware next and previous buttons do, so Next and Previous there may skip within the episode rather than change episodes.
- `seekTo` needs the player to advertise `ACTION_SEEK_TO`. On Android 10+ the system media notification only shows a draggable progress bar for sessions that support seeking, so a draggable bar there is a good hint. Spotify, YouTube Music and VLC are expected to show one. Live streams and radio usually do not.
- Players report their position only when their state changes. The app estimates the current position from the last report and the playback speed. A player that reports a stale position makes the seek start from the wrong place.
- Seeking forward past the end is clamped to the track length. Most players then move on to the next item.

Volume:

- Volume commands change the phone's media volume (`STREAM_MUSIC`), whichever app is playing. With Bluetooth absolute volume, this also changes the headset's volume.
- While casting (Chromecast, Spotify Connect), the phone's media volume does not change the remote device. That would need `MediaController.adjustVolume` on the remote session, which is not implemented.
- Devices with fixed volume get `Unsupported`.
- Mute uses `ADJUST_TOGGLE_MUTE`, which exists since API 23, so it works on minSdk 24. Playback keeps running silently. Volume up usually unmutes as well. Some phones show mute as volume 0.
- `FLAG_SHOW_UI` shows the system volume panel. Some Android versions may not show it while the app is in the background.

Notification access:

- Granting notification access lets the app read all notifications. `MediaListenerService` ignores them. It exists only because `getActiveSessions` requires an enabled notification listener.
- Restricted settings: on Android 13+, an APK installed outside an app store can find the toggle greyed out as a "Restricted setting". To fix it, open App info for HALO Control, open the menu in the top corner, choose "Allow restricted settings", and then turn the toggle on again. Whether an `adb install` triggers this has not been checked.
- Some phones' battery managers (for example on Xiaomi, Huawei, or Samsung's "sleeping apps") stop notification listeners. The tracker then shows "unknown" and seeking falls back to keys. Keys and volume do not depend on the listener.
