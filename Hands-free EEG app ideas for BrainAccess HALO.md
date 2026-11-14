# Hands-free EEG app ideas for BrainAccess HALO

Nov 14, 2026 · @Emil

## Summary and shortlist

Build on deliberate double blinks and jaw clenches, not on "brain" signals. They are the strongest events this headband records, they need only the forehead electrodes, and a threshold detector is achievable in a few weeks.

The HALO facts in the brief hold up, with small corrections: the vendor page lists 250 and 500 Hz and 7 h of battery at 250 Hz. The Kotlin SDK is publicly documented and exposes raw samples, electrode contact, impedance, battery and sample-accurate timing. It ships as a ZIP of AAR files with one minimal sample app.

SSVEP on a phone screen is the most impressive option and the riskiest, with only two dry occipital electrodes sitting in hair. Treat it as a week-5 experiment. Motor imagery, P300 spelling and attention-based control are not worth attempting on this montage.

### Recommended top 3

| Rank | Idea | Polish name | Why |
| --- | --- | --- | --- |
| 1 | **Blink Talk**: a scanned phrase board that speaks | *Mrugnij i powiedz: tablica komunikacyjna sterowana mrugnięciem* | Clearest accessibility story, no special Android permissions, easy to measure, hard to break in a demo |
| 2 | **Hands-free remote**: media and step-by-step instructions | *Pilot bez rąk: muzyka i przepisy sterowane mrugnięciem* | Covers the "everyday use" half of the brief; smallest build; works with the screen off |
| 3 | **EEG Switch**: operate the whole phone through an accessibility service | *Przełącznik EEG: obsługa całego telefonu bez dotyku* | Highest impact and the natural extension of 1 and 2; pick it only if the detector is solid by week 3 |

All three reuse one detector, calibration screen and quality gate. A sensible path is to build the core, ship idea 1 or 2 as the guaranteed result, and add idea 3 as the stretch goal.

### How to read this document

- A statement with a link is supported by that source.
- "(inference)" marks my own reasoning. Feasibility scores, HALO-fit ratings, filter bands and timings are all inferences.
- Unknowns are collected under Open questions.
- The Sources section separates pages I read in full from those I saw only as an abstract or excerpt.

## Hardware and SDK: what the vendor confirms

Most of the brief's hardware facts check out, and the Kotlin SDK is better documented than the brief assumed. Its public docs (v2.3.1) answer nearly every "not verified" item.

| Claim in the brief | Status | What the source says |
| --- | --- | --- |
| Dry-contact EEG headband by Neurotechnology, Vilnius | Confirmed | [2023 launch release](https://neurotechnology.com/press_release_brainaccess_halo.html), [2024 v2 release](https://neurotechnology.com/press_release_brainaccess_halo_2024.html) |
| 4 EEG channels plus common reference | Confirmed | [HALO product page](https://www.brainaccess.ai/hardware/brainaccess-halo/) |
| Two forehead and two occipital electrodes, fixed positions | Confirmed | [2023 release](https://neurotechnology.com/press_release_brainaccess_halo.html). No 10-20 labels are published; Fp1/Fp2/O1/O2 is an assumption |
| Bluetooth Low Energy | Confirmed | Bluetooth 4.2 BLE, range up to 15 m ([product page](https://www.brainaccess.ai/hardware/brainaccess-halo/)) |
| About 8 h streaming | Partly | Press releases say up to 8 h; the product page says 400 mAh, up to 7 h at 250 Hz |
| Firmware raised sample rate to 500 Hz | Confirmed | 250 and 500 Hz on every board; HALO V3 adds 1000 Hz ([SDK usage](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/usage.html), [2025 release](https://neurotechnology.com/press_release_brainaccess_2025.html)) |
| Board, SDK (C/Python), Kotlin SDK, Mobile and Mobile Lite | Confirmed | [Software page](https://www.brainaccess.ai/software/) |

Other specs from the product page: 24-bit input, 70 g, head perimeter 53-58 cm, USB-C charging in about 2 h. The page states HALO is not a medical device.

### Kotlin SDK 2.3.1

| Question from the brief | Answer | Source |
| --- | --- | --- |
| Maven coordinates | None. Shipped as `BrainAccessKit-2.3.1.zip` with local AAR/JAR files; customers request access from the vendor | [Installation](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/installation.html), [SDK page](https://www.brainaccess.ai/software/kotlin-sdk/) |
| Build requirements | Kotlin 2.0.x, minSdk 24, compileSdk 36+, Nordic BLE 2.9.0, kotlinx coroutines and serialization | [Installation](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/installation.html) |
| Raw samples | Yes. `session.stream()` returns `Flow<EegChunk>` with µV samples, channel labels, sample rate and sample index | [Usage](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/usage.html) |
| Electrode contact | Yes. `session.contact` gives a per-channel boolean | [Usage](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/usage.html) |
| Impedance | Yes, as a separate `impedanceMode` reporting kΩ; it is not available alongside normal streaming | [Usage](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/usage.html) |
| Battery | Yes. `session.battery`, `charging`, `batteryLow`, `readBatteryGauge()` | [Usage](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/usage.html) |
| Event markers | No annotation API. `session.sampleIndexAt(System.nanoTime())` maps a phone timestamp to a sample; needs firmware 3.8.0+ | [What's new](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/whatsnew.html) |
| Motion sensors | `accel` in every chunk when present; `gyro` on V3 boards only | [Usage](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/usage.html) |
| Dropped packets | Lost samples are zero-filled and flagged `isDummy`; `missedSamples` counts them | [Usage](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/usage.html) |
| Sample apps | One app, `MinimalGUI` (scan, connect, stream, contact, battery), plus an LSL forwarding snippet. No blink, alpha or SSVEP demo | [Examples](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/examples.html) |

The SDK is headless and leaves all signal processing to the app, so the team writes every detector itself.

## Signal capabilities on this layout

Deliberate blinks, jaw clenches and eyes-closed alpha are the dependable signals on two frontal and two occipital dry channels. SSVEP is possible but user-dependent, and P300, attention scores and motor imagery are poor bets.

None of the figures below were measured on HALO. They come from other hardware, often with more channels or gel electrodes, so treat them as upper bounds. The "Fit for HALO" column is my inference.

| Signal | Fit for HALO | Typical detection method | Latency | Accuracy reported in the literature |
| --- | --- | --- | --- | --- |
| Single blink | High | Band-pass the frontal channels, then adaptive amplitude threshold or template match | About 0.2-0.5 s (inference from 200 ms windows) | Over 98% on four single-channel datasets ([Agarwal and Sivakumar](https://par.nsf.gov/servlets/purl/10321749)). Consumer headsets did worse: 75.6% Emotiv EPOC, 49.6% NeuroSky ([Maskeliunas et al. 2016](https://peerj.com/articles/1746)) |
| Double blink | High | Count blink peaks inside a 0.5 s gap, decide after a short wait | About 1-1.5 s, set by the wait window | 89.0% for no/single/double blink ([Rabbani et al. 2025](https://doaj.org/article/7089246344704edb99c85f074b25160f)). A one-person study used a 0.5 s gap and 1.5 s wait and gave no double-blink figure ([Sci Rep 2023](https://www.nature.com/articles/s41598-023-44645-y)) |
| Wink (left vs right) | Unknown | Compare amplitude between the two forehead channels | Similar to a blink | 97.28% average with dedicated vertical and horizontal EOG electrodes ([EOG study](https://bohrium.dp.tech/paper/arxiv/56d8944bdabfae2eee019b3f)). Not shown on a forehead-only montage |
| Horizontal eye movement | Low to medium | Opposite-polarity deflection between left and right frontal sites | Under 1 s | Offline worst case 84.6% left, 77.8% right at F7/F8, one subject; in real time left was often read as right ([Sci Rep 2023](https://www.nature.com/articles/s41598-023-44645-y)) |
| Vertical eye movement | Low | Slow frontal deflection | Under 1 s | No figure found. It resembles a blink on forehead channels (inference) |
| Jaw clench | High | High-pass above about 30 Hz, RMS envelope on all channels, threshold with minimum duration | About 0.3-0.5 s (inference) | Precision over 70% against 26 other facial actions, one user, ear-EEG ([Knierim et al. 2022](https://pmc.ncbi.nlm.nih.gov/articles/PMC9529594)). Online clench control is published, but I could not retrieve its numbers ([Khoshnam et al. 2017](https://doi.org/10.1007/978-3-319-56148-6_44)) |
| Eyes-closed alpha | High | 8-13 Hz power on occipital channels against an eyes-open baseline | Typically 2-3 s after closing the eyes ([IEEE 2016](https://www.infona.pl/resource/bwmeta1.element.ieee-art-000007839045)) | Described as robust across subjects and sessions; I found no accuracy figure in the pages I could open |
| SSVEP, 3-4 targets | Medium, user-dependent | Canonical correlation analysis against sine and cosine references, 2-5 s window | 2-5 s per selection | See the SSVEP notes below |
| P300 / ERP | Low | Average 5-15 stimulus repetitions, then LDA | 10 s or more per selection (inference) | Parietal and occipital sites carry most weight for the classifier ([McCane poster](https://oupub.etsu.edu/cas/psychology/bcilab/documents/mccane_bci2010poster_tmv-1_jrw.pdf)). Scalp ERP AUC fell from 0.90 standing to 0.67 at fast walking ([Lee et al. 2021](https://arxiv.org/pdf/2112.04176)) |
| Attention or relaxation index | Low for control | Band-power ratios, slow smoothing | 5-10 s (inference) | Judged too variable to serve as a control input on two consumer headsets ([Maskeliunas et al. 2016](https://peerj.com/articles/1746)) |

### Notes that affect design

- **Natural blinks are the false-positive source.** Use a double blink or a long blink as the command, never a single blink.
- **Chewing and talking look like jaw clenches.** That matters for kitchen use. Require a hold of about 0.5 s or a double clench (inference).
- **Eyes-closed alpha cannot be used with a visual UI.** Pair it with sound or vibration.
- **Motion.** With 32 gel channels and 5 s windows, SSVEP accuracy went from 88.7% standing to 80.7% at fast walking and 54.8% running ([Lee et al. 2021](https://arxiv.org/pdf/2112.04176)). Expect worse with 2 dry occipital channels, so design for a seated or standing user.

### SSVEP on a phone screen

- **Refresh rate limits frequencies.** A 60 Hz display cleanly produces 7.5, 8.57, 10, 12 and 15 Hz, each a whole number of frames per cycle ([Nakanishi et al. 2014](https://pmc.ncbi.nlm.nih.gov/articles/PMC4053390)). The same paper shows a frame-approximation method for any frequency below half the refresh rate.
- **90 and 120 Hz phones** offer more whole-frame divisors (inference). Lock the refresh rate in the app, because adaptive refresh will smear the stimulus.
- **Phone-based SSVEP has worked.** A 4-channel system with a phone doing the processing reached 95.9% dialling accuracy and 28.5 bits/min ([Wang et al.](https://sccn.ucsd.edu/~yijun/pdfs/BCIMeeting10.pdf)).
- **Dry electrodes span a wide range.** Eight purpose-built dry electrodes reached 93.2% and 92.35 bits/min on 12 targets; an earlier 8-pin dry system got 63% and 23 bits/min on 4 targets ([Xing et al. 2018](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6168577/)).
- **Few occipital channels cost accuracy.** One review cites 76% with three occipital channels ([Ma et al. 2025](https://www.frontiersin.org/journals/neurorobotics/articles/10.3389/fnbot.2024.1502560/pdf)). HALO has two.
- **Jaw clench and SSVEP can coexist** if stimuli stay below 20 Hz ([Xu et al.](https://searchworks.jioinstitute.edu.in/articles/cmedm__34892518)).

### Not feasible with this hardware

- **Motor imagery.** It relies on mu-rhythm changes over the sensorimotor cortex, with C3 and C4 as the minimum sites. HALO has no central electrodes.
- **P300 spellers at useful speed.** The montage lacks the parietal and central sites, and averaging makes each selection slow.
- **Decoding words, intentions or emotions.** Nothing in the sources supports this on four dry channels.
- **Continuous 2D cursor control from EEG alone.** Every reliable signal here is a discrete event (inference).

## Interaction patterns for hands-free control

Treat the headband as one or two binary switches and build on scanning, the pattern switch-access users already know. Two switches (blink and clench) make it self-paced and faster than one.

| Pattern | How it works | Fit with HALO signals |
| --- | --- | --- |
| Single-switch auto-scan | A highlight steps through items on a timer; the switch selects the current one | Double blink as the switch. The scan step must exceed detector latency, so about 1.5-2 s per item (inference) |
| Row-column scanning | Pick a row, then a column | Works for grids of 9-20 items. Too slow for letter-by-letter typing |
| Two-switch step scanning | One switch moves, the other selects | Clench moves, double blink selects, or the reverse. No timing pressure on the user (inference) |
| Dwell selection | A pointer rests on a target for a set time | EEG gives no pointer. Head tilt from the accelerometer could supply one, but that is untested |
| SSVEP menu | Each tile flickers at its own frequency; looking at one selects it | 3-4 tiles. Needs an idle state so that not looking selects nothing |
| Eyes-closed toggle | Closing the eyes for 2-3 s fires one action | Good as an arm/disarm or pause gesture with audio feedback |

Scanning is slow for text. A survey cited by [Bonaker et al. 2023](https://par.nsf.gov/servlets/purl/10521368) found a mean of 1.27 words per minute for character-only scanning. Design around whole phrases, actions and small menus instead of spelling.

### Driving Android from an EEG switch

- **Switch Access expects keystrokes.** It takes input from a USB or Bluetooth switch or keyboard, and each key is assigned to Next, Select or Auto-scan ([Google help](https://support.google.com/accessibility/android/answer/6122836?hl=en)).
- **An ordinary app cannot send keystrokes to other apps.** Injecting input events needs a system-level permission. This is my understanding of Android and worth confirming.
- **Route 1: stay inside your own app.** The app owns the scanning UI, and no special permission is needed. This is the lowest-risk route.
- **Route 2: your own AccessibilityService.** It can call `performGlobalAction` for Back and Home and `dispatchGesture` for taps and swipes once `canPerformGestures` is declared ([Android guide](https://developer.android.com/guide/topics/ui/accessibility/service)). The team then builds its own scanning overlay.
- **Route 3: act as a Bluetooth keyboard for a second device.** `BluetoothHidDevice` exists from API 28 ([reference](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice)). The phone wearing the headband could then drive stock Switch Access on a tablet. This is my inference and is untested.
- **Precedent.** Google's Camera Switches turns six face gestures into Switch Access switches, and Project Activate maps a gesture to one action such as speaking a phrase ([Google blog](https://blog.google/outreach-initiatives/accessibility/making-android-more-accessible/)). An EEG switch is the same idea without a camera.
- **Voice Access** covers users who can speak in a quiet place. EEG input is useful where speech is unavailable, noisy or unwanted (inference).

### Google Play policy on AccessibilityService

Source for all four points: [Play Console help](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en).

- Only services built to help people with disabilities may set `isAccessibilityTool`.
- Apps that set the flag complete an accessibility declaration in Play Console, naming the core feature and the disabilities served. Motor is one of the listed options.
- Apps without the flag need a prominent in-app disclosure and explicit consent before enabling the service.
- Autonomous use of the API is prohibited, with an exemption for verified accessibility tools.

A course project installed by APK never meets Play review (inference). Still write the disclosure screen, because an accessibility service can read the whole screen.

## Prior art and benchmarks

Published systems back blink and SSVEP control on small montages, and they warn against attention scores. I found no published benchmark on HALO itself.

| Work | What it does | Hardware | Reported result |
| --- | --- | --- | --- |
| [Wang, Wang and Jung](https://sccn.ucsd.edu/~yijun/pdfs/BCIMeeting10.pdf) | SSVEP phone dialling with processing on the phone | Custom 4-channel wireless EEG | 95.9% accuracy, 28.5 bits/min |
| [Xing et al. 2018](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6168577/) | 12-target SSVEP speller | 8 dry electrodes in a headband | 93.2%, 92.35 bits/min |
| [Nakanishi et al. 2014](https://pmc.ncbi.nlm.nih.gov/articles/PMC4053390) | SSVEP at flexible frequencies on a 60 Hz LCD | EEG system not captured | 95.0 bits/min average over 10 subjects |
| [Lee et al. 2021](https://arxiv.org/pdf/2112.04176) | SSVEP and ERP dataset while standing, walking, running | 32-channel scalp EEG | SSVEP 88.7% standing, 80.7% fast walking, 54.8% running |
| [Agarwal and Sivakumar, "Blink"](https://par.nsf.gov/servlets/purl/10321749) | Unsupervised blink detection that learns the user's blink shape | Single-channel EEG | Over 98% on four datasets, 2300+ blinks |
| [Rabbani et al. 2025](https://doaj.org/article/7089246344704edb99c85f074b25160f) | Real-time no/single/double blink classifier | EEG; device not captured | 89.0% best model |
| [Sci Rep 2023](https://www.nature.com/articles/s41598-023-44645-y) | Blinks and left/right looks drive a GUI and an assistive robot | TMSi SAGA 64+, using Fp1, F7, F8 | Offline worst case 78.8% blink; detection tested on one person |
| [Maskeliunas et al. 2016](https://peerj.com/articles/1746) | Tests blink and attention/meditation as control inputs | Emotiv EPOC, NeuroSky MindWave | Blink 75.6% and 49.6%; attention values too variable for control; 10 subjects |
| [NeuroTechX EEG-ExPy notebooks](https://nbviewer.org/github/NeuroTechX/EEG-ExPy/blob/devcontainer-test/examples/sandbox/SSVEP_linux.ipynb) | Open-source SSVEP experiments | Muse plus an extra electrode at POz | Peaks at 20 and 30 Hz much larger at POz than at the stock temporal sites; no accuracy captured |
| [Hands-Free Dino Jump](https://tomlandhilarious.github.io/projects/3_project/) | Blink-controlled game, single vs double blink by thresholds | OpenBCI Cyton, Fp1/Fp2 | No accuracy given; authors say the second blink stayed hard to detect; 4 participants |
| [Knierim et al. 2022](https://pmc.ncbi.nlm.nih.gov/articles/PMC9529594) | Jaw-clench detection among facial actions | OpenBCI Cyton with ear electrodes | Precision over 70%, one user |
| [Eyes-closed alpha switch](https://www.infona.pl/resource/bwmeta1.element.ieee-art-000007839045) | Self-paced brain switch from eye closure | EEG; montage not captured | Activation typically 2-3 s after closing the eyes |
| [Camera Switches and Project Activate](https://blog.google/outreach-initiatives/accessibility/making-android-more-accessible/) | Face gestures as Android switches or one-shot actions | Phone front camera, no EEG | No accuracy published |

Gaps: I did not find accuracy or ITR figures for a BrainFlow-based project, nor a published blink or SSVEP result on BrainAccess hardware.

## All app ideas

Eleven ideas, sorted by feasibility. Scores are my judgement for 3-4 students over 4-6 weeks: feasibility 5 is easiest, impact 5 is highest. Reliability ratings are inferences from the signal table, not measurements.

| # | Idea and target user | Signals and interaction | Reliability and fallback | Android integration | Feas. | Impact | Main risks |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | **Blink Talk.** A phrase board that speaks. Tiles such as Yes, No, Thirsty, Call nurse are scanned and spoken aloud. For people with limited speech and hand movement, and their carers. | Double blink selects during auto-scan. Optional jaw clench goes back. Editable phrase sets. | High. Fallback: long blink instead of double blink; a carer can tap the screen. | TextToSpeech; all UI in-app, no special permissions | 5 | 4 | Natural-blink false positives; fatigue from repeated deliberate blinks; contact loss on the forehead |
| 2 | **Hands-free remote.** Controls music, podcasts and step-by-step recipes or manuals with messy or busy hands. For cooks, makers and mechanics. | Double blink = play/pause or next step. Jaw clench = next track or previous step. Steps are read aloud. | High when still; medium while moving or chewing. Fallback: require a double clench; voice or touch still work. | Media key events via AudioManager or a MediaSession; TextToSpeech; foreground service | 5 | 3 | Chewing and talking trigger the clench detector; motion artifacts at a workbench; headband under headwear |
| 3 | **Blink runner.** A one-button game plus an alpha relaxation trainer. Doubles as the calibration and practice screen for other apps. For new users and demos. | Blink = jump. Eyes-closed alpha fills a calm meter. | High. Fallback: tap to play. | In-app only | 5 | 2 | Low accessibility value on its own; looks like a toy to a jury |
| 4 | **Page turner.** Turns pages of a PDF, e-book or sheet music. For musicians, readers with tremor or limb loss. | Double blink = next page, clench = previous. | High. Fallback: auto-advance timer. | Own PDF viewer in-app; turning pages in other apps needs an AccessibilityService swipe | 4 | 3 | A misfire mid-performance is costly; players blink more under stage lights (inference) |
| 5 | **Smart-home scenes.** A short scanned list: lights, blinds, plug, TV. For people with limited mobility at home. | Two-switch scanning: clench moves, double blink confirms. | High for 4-6 scenes. Fallback: confirm step before anything irreversible, such as a door lock. | Home Assistant REST or a webhook is simplest; Google Home APIs are the official route | 4 | 4 | Dependence on venue Wi-Fi and devices in a demo; Google Home API access and approval steps are unchecked |
| 6 | **Caregiver call.** An always-on alert: a deliberate pattern sends a message and sounds an alarm. For bed-bound users. | Triple clench or a 3 s eye closure plus a cancel window with vibration. | Medium. A miss is the dangerous error. Fallback: must never replace a physical call button. | Foreground service; SMS or a push message; full-screen notification | 4 | 4 | False alarms overnight; BLE drops while unattended; battery lasts 7-8 h, not a full night and day |
| 7 | **Eyes-closed pause.** Pauses an audiobook when the eyes stay closed, bookmarks the spot, and resumes on a deliberate gesture. For listeners who doze off and for rest breaks. | Occipital alpha rise for 2-3 s. Audio and vibration feedback only. | High for most users; some people show weak alpha. Fallback: timeout sleep timer. | Media key events; foreground service | 4 | 3 | Alpha varies by person; occipital electrodes sit in hair; not usable lying on the back of the head |
| 8 | **EEG Switch.** Operates the whole phone: a scanning overlay highlights on-screen controls, and the switch taps them. For users who cannot touch the screen. | Clench = next, double blink = select; long eye closure = pause input. | Medium. Detection is solid, but scanning arbitrary apps is slow and layouts vary. Fallback: restrict to a launcher of favourite apps. | Own AccessibilityService with `dispatchGesture` and `performGlobalAction`; overlay window; disclosure screen | 3 | 5 | Largest engineering load; overlay and focus bugs across apps; Play policy if ever published |
| 9 | **Blink Morse keyboard.** Types text from short and long blinks. For users who need free text, not fixed phrases. | Blink length = dot or dash; clench = space or delete. | Medium. Timing is hard to learn. Fallback: phrase prediction. | InputMethodService | 3 | 3 | Steep learning curve; slow; eye strain |
| 10 | **Notification triage.** Reads incoming notifications aloud and lets the user dismiss or send a canned reply. For commuters as passengers and for busy hands. | Double blink = dismiss, clench = quick reply, within a 5 s window after the readout. | Medium on public transport because of motion. Fallback: ignore input when the accelerometer shows heavy movement. | NotificationListenerService; reply actions; TextToSpeech | 3 | 3 | Privacy of reading messages aloud; motion artifacts; many permissions |
| 11 | **SSVEP quick menu.** Three or four flickering tiles; looking at one triggers it. For users who cannot blink or clench on purpose. | SSVEP at whole-frame frequencies such as 7.5, 10 and 12 Hz, CCA over 2-4 s. | Low to medium and very user-dependent on 2 dry occipital channels. Fallback: blink scanning on the same menu. | In-app rendering locked to the display refresh rate | 2 | 5 | May not work for some team members at all; hair; flicker discomfort and photosensitivity; small phone screen |

Android API names other than AccessibilityService and BluetoothHidDevice come from my own knowledge and were not re-checked against the docs.

None of these is for use while driving. Blink and eye-closure gestures are unsafe for a driver, so the "commuting" ideas assume a passenger.

## The three finalists

All three share one detector core, so the team builds the hard part once and chooses the front end by week 3. Everything in this section is a design proposal, not a sourced fact; thresholds and timings are starting points to tune on real data.

### Shared core

1. **Device layer.** `BrainAccessClient` scan and connect, 250 Hz stream, auto-reconnect, battery and contact state exposed as flows.
2. **Quality gate.** Drop windows that contain `isDummy` samples, a `contact` flag that is false, or an accelerometer spike.
3. **Filters.** 50 Hz notch for Polish mains, then two branches per window.
4. **Blink branch.** Average the two frontal channels, band-pass about 0.5-8 Hz, find peaks above a per-user threshold. Two peaks 150-600 ms apart make a double blink.
5. **Clench branch.** Band-pass about 40-110 Hz on all four channels, RMS envelope, threshold against the user's resting baseline, held for 300 ms on at least three channels.
6. **Event bus.** Emits `DoubleBlink`, `Clench`, `SignalLost`. Front ends subscribe and never touch raw EEG.
7. **Calibration screen.** 30 s rest, 10 cued double blinks, 10 cued clenches. Sets thresholds and shows the hit rate.

Start with thresholds. Add a small classifier, such as logistic regression on peak height, width and spacing, only if recorded data shows thresholds failing.

### 1. Blink Talk

- **Architecture.** Single-activity Compose app: core service, phrase-board screen, editor for phrase sets, TextToSpeech output.
- **Interaction.** Auto-scan at an adjustable 1.5-3 s per tile, double blink to speak. Two levels: category, then phrase. A "pause input" tile stops scanning.
- **Signal use.** Double blink only for the MVP. Clench as Back is a stretch goal.
- **MVP.** One board of 8 phrases, spoken reliably by all team members after calibration.

### 2. Hands-free remote

- **Architecture.** Foreground service holding the headset connection, plus two modes: media keys for whatever player is active, and an in-app step reader.
- **Interaction.** Double blink = play/pause or next step. Clench = next track or previous step. A sound confirms every command. Input auto-mutes while the accelerometer shows walking.
- **Signal use.** Both events. Add a "chewing lock": a clench longer than 2 s is ignored.
- **MVP.** Control a music player with the screen off, and step through one recipe with spoken steps.

### 3. EEG Switch

- **Architecture.** Core service plus an AccessibilityService that reads clickable nodes, draws a highlight overlay and dispatches taps. Start with a built-in launcher grid before scanning arbitrary apps.
- **Interaction.** Clench moves the highlight, double blink selects. Global menu for Back, Home and Notifications. An eye closure of 3 s pauses all input.
- **Signal use.** Both events, plus eyes-closed alpha as the pause gesture if time allows.
- **MVP.** Open an app from the launcher, go Back and Home, and scroll a list, without touching the phone.
- **Biggest unknown.** How well node-based scanning behaves in apps with custom views. Budget a week for it.

### Milestones

| Week | Shared work | Product work |
| --- | --- | --- |
| 1 | Get SDK access, run `MinimalGUI`, record raw data from every team member doing blinks, clenches, rest and chewing | Paper prototype of the chosen front end |
| 2 | Offline analysis in Python on the recordings; fix filter bands and thresholds; port to Kotlin | Skeleton UI driven by a fake on-screen switch button |
| 3 | Live detector plus calibration screen; measure hit rate and false positives per minute | Decide which finalist to finish; wire events to the UI |
| 4 | Quality gate, reconnect handling, feedback sounds | MVP complete and usable end to end |
| 5 | Tuning from user tests | User tests with 5-8 people; fix the top three problems |
| 6 | Freeze; record a backup demo video | Demo script, metrics slide, fallback mode rehearsed |

Keep the fake switch button for the whole project. It lets UI work continue without the headband and serves as the demo fallback.

## Engineering considerations

The app's quality depends more on rejecting bad signal than on detecting good signal. Most points here are engineering judgement; sourced facts carry a link.

### Pipeline and classifier choice

- Run everything on the phone in a foreground service. Four channels at 250 Hz is a trivial load.
- Use 250 Hz unless a test shows a need for 500 Hz. Clench EMG up to about 110 Hz fits under the 125 Hz limit, and the vendor's 7 h battery figure is quoted at 250 Hz.
- Prefer thresholds with per-user calibration over machine learning. Blink and clench events are much larger than background EEG, so thresholds are usually enough (inference).
- Record labelled raw data from day one. It lets the team replay sessions through new detector versions and count errors offline.

### Latency budget

| Stage | Budget | Note |
| --- | --- | --- |
| Headset to phone over BLE | Measure it | `session.link` reports the connection interval; `sampleHostTimeNs` gives per-sample timing |
| Analysis window | 100-200 ms | Hop of 50 ms |
| Decision wait | 0 ms for clench, about 600 ms for double blink | Needed to rule out a second or third blink |
| Feedback | Under 50 ms | Sound or vibration before the action runs |

Targets: clench under 0.5 s, double blink under 1.2 s from the end of the gesture.

### Calibration

- Keep it under 90 seconds: rest, cued double blinks, cued clenches.
- Store thresholds per user and per session. Dry-electrode amplitude shifts each time the band is put on.
- Adapt the resting baseline slowly during use, and freeze it while an event is in progress.
- Show the result as a hit count, for example "9 of 10 blinks detected", and offer a retry.

### Signal quality and contact

- Use `session.contact` continuously and the SDK's impedance mode once at fit time; impedance mode replaces normal streaming ([Usage](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/usage.html)).
- Show a four-dot contact indicator on every screen. Mute commands when a needed channel drops.
- Watch `missedSamples`. A burst of zero-filled samples can look like a step in the signal, so skip those windows.
- Handle `Disconnected` by pausing input, announcing it aloud and reconnecting.

### Hair and dry electrodes

- The occipital electrodes use spring-loaded pillar contacts to reach the scalp through hair ([2023 release](https://neurotechnology.com/press_release_brainaccess_halo.html)). Thick or long hair will still be the main cause of poor occipital signal.
- Blink and clench ideas need only the forehead pads, which is one more reason to prefer them.
- The band adjusts from 53 to 58 cm ([product page](https://www.brainaccess.ai/hardware/brainaccess-halo/)). Check fit on every test participant first.
- Allow a minute for dry contacts to settle before calibrating.

### Flicker safety for SSVEP

- Flashes at 3-60 Hz, especially 15-20 Hz, brighter than 20 cd/m² and covering 10-25% or more of the visual field are a seizure risk; light-provoked seizures affect up to about 1 in 4000 people ([Epilepsy Foundation review, 2022](https://repository.essex.ac.uk/34275/)).
- Every frequency a phone can render for SSVEP sits inside that band.
- If the team tries SSVEP: show a warning and require opt-in, exclude anyone with epilepsy or a family history, keep tiles small, avoid red and 15-20 Hz, cap exposure time, and give a one-tap stop.
- Do not run flicker on a projector in the demo room.

### Privacy of EEG data

- Process on the device and keep raw EEG out of storage by default. The SDK declares Bluetooth permissions, plus location on Android 11 and older ([Installation](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/installation.html)).
- For user tests, collect written consent, store recordings under participant codes, and delete them after grading.
- EEG may count as sensitive personal data under GDPR depending on use. I am not a lawyer; ask the course supervisor what the university requires.
- If an AccessibilityService is used, state plainly that it can read screen content and that nothing leaves the phone.

## Evaluation

Report two numbers above all: how often a deliberate command is detected, and how many false commands fire per minute when the user is not trying. The second one decides whether the app is usable.

### Metrics

| Metric | How to measure |
| --- | --- |
| Hit rate (accuracy) | Cued trials: the app prompts a gesture and logs whether the right event fired within 2 s |
| False positives per minute | 5 minutes of idle use per condition: reading, talking, chewing gum, walking in place |
| Time per selection | From the prompt to the confirmed action, including scan time |
| Information transfer rate | Wolpaw formula below, in bits per minute |
| Task success | Share of realistic tasks finished without touching the phone |
| Usability | System Usability Scale: 10 questions, scored 0-100 |
| Setup time | From picking up the band to first successful command |

```latex
B = \log_2 N + P \log_2 P + (1 - P)\log_2\frac{1 - P}{N - 1}, \qquad \mathrm{ITR} = B \cdot \frac{60}{T}
```

N is the number of choices, P the accuracy, and T the seconds per selection. The formula is standard in the BCI papers cited here, though I did not open its original source.

### User-test protocol

Six to eight participants, about 30 minutes each, seated.

1. Consent and screening, including the photosensitivity question if any flicker is shown (3 min).
2. Fit the band, wait for contact, record setup time (5 min).
3. Calibration (2 min).
4. Cued block: 30 prompts in random order across double blink, clench and "do nothing" (5 min).
5. Idle block for false positives: read aloud, then chew, then sit quietly (5 min).
6. Task block: five realistic tasks, such as "say you are thirsty" or "skip two tracks" (5 min).
7. SUS questionnaire and two open questions: what was tiring, what misfired (5 min).

Log every event with a timestamp and the raw EEG. Report per-participant results as well as the mean, because BCI performance varies widely between people.

### A demo that survives a noisy venue

- Seat the presenter and keep the phone within a metre of the headband. A hall full of phones is a hard Bluetooth environment.
- Fit and calibrate ten minutes before the slot, then keep the band on.
- Show a live strip of the two frontal channels and the contact dots, so the audience sees the blink that triggers the action.
- Demo blink and clench only. Leave alpha and SSVEP for a slide; bright stage lights and nerves hurt both.
- Use a wired or loud speaker for TextToSpeech output.
- Keep the on-screen fake switch as a visible fallback, and have a recorded video of a full run.
- Charge the headset fully and bring a second trained presenter in case the band does not suit one person on the day.

## Open questions to check

These need the vendor, the SDK in hand, or the course supervisor.

- [ ] Which HALO revision will teams receive? The SDK docs mention a HALO V3 with 1 kHz and a gyroscope; the brief says v2.
- [ ] Which firmware is installed? Sample-accurate timing needs 3.8.0 or later.
- [ ] Where exactly are the four electrodes and the reference? No 10-20 labels are published. This decides whether winks and sideways looks are detectable.
- [ ] What channel labels does `EegChunk.channels` return for HALO?
- [ ] Does HALO v2 report an accelerometer (`session.hardware.hasAccel`)?
- [ ] How large are BLE packets and how often do chunks arrive? This sets the real latency floor.
- [ ] Is battery life 7 h or 8 h in practice at 250 Hz?
- [ ] Does `session.contact` reliably flag a lifted dry occipital electrode in hair?
- [ ] Does the team's SDK bundle include anything beyond `MinimalGUI`?
- [ ] Can an app really not inject key events for Switch Access? Confirm in the Android docs.
- [ ] What is the current status of the Google Home APIs, and what approval does an app need? I saw only the 2025 public-beta announcement.
- [ ] What consent or ethics steps does the university require for recording EEG from test users?

## Sources

### Opened and read

- [BrainAccess HALO product page](https://www.brainaccess.ai/hardware/brainaccess-halo/)
- [BrainAccess Kotlin SDK product page](https://www.brainaccess.ai/software/kotlin-sdk/)
- Kotlin SDK 2.3.1 docs: [overview](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/), [installation](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/installation.html), [usage](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/usage.html), [examples](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/examples.html), [what's new](https://www.brainaccess.ai/documentation/kotlin-sdk/2.3.1/whatsnew.html)
- [BrainAccess Release 2025 blog post](https://www.brainaccess.ai/brainaccess-release-2025-more-adaptable-and-versatile-than-ever/)
- [BrainAccess tutorials index](https://www.brainaccess.ai/tutorials/)
- [Lee et al. 2021, mobile BCI dataset while standing, walking and running](https://arxiv.org/pdf/2112.04176)
- [Real-time blink and eye-direction BCI for an assistive robot, Scientific Reports 2023](https://www.nature.com/articles/s41598-023-44645-y) (first 100,000 characters of the page)
- [Maskeliunas et al. 2016, consumer-grade EEG for control tasks, PeerJ](https://peerj.com/articles/1746)

### Read as abstract or search excerpt only

Figures from these should be re-checked in the full text before they go into a report.

- Neurotechnology press releases: [HALO launch 2023](https://neurotechnology.com/press_release_brainaccess_halo.html), [HALO v2 2024](https://neurotechnology.com/press_release_brainaccess_halo_2024.html), [2025 upgrades](https://neurotechnology.com/press_release_brainaccess_2025.html)
- [BrainAccess software page](https://www.brainaccess.ai/software/)
- [Agarwal and Sivakumar, Blink algorithm](https://par.nsf.gov/servlets/purl/10321749)
- [Rabbani et al. 2025, two consecutive blinks](https://doaj.org/article/7089246344704edb99c85f074b25160f)
- [Voluntary blink and wink detection with EOG](https://bohrium.dp.tech/paper/arxiv/56d8944bdabfae2eee019b3f)
- [Knierim et al. 2022, OpenBCI ear-EEG](https://pmc.ncbi.nlm.nih.gov/articles/PMC9529594)
- [Khoshnam et al. 2017, jaw-clench control](https://doi.org/10.1007/978-3-319-56148-6_44)
- [Xu et al., jaw clench and SSVEP](https://searchworks.jioinstitute.edu.in/articles/cmedm__34892518)
- [Eyes-closed alpha BCI switch, IEEE](https://www.infona.pl/resource/bwmeta1.element.ieee-art-000007839045)
- [Nakanishi et al. 2014, flicker frequencies and refresh rate](https://pmc.ncbi.nlm.nih.gov/articles/PMC4053390)
- [Wang, Wang and Jung, cell-phone SSVEP BCI](https://sccn.ucsd.edu/~yijun/pdfs/BCIMeeting10.pdf)
- [Xing et al. 2018, dry-electrode SSVEP](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6168577/)
- [Ma et al. 2025, limited-channel SSVEP](https://www.frontiersin.org/journals/neurorobotics/articles/10.3389/fnbot.2024.1502560/pdf)
- [McCane et al., P300 speller poster](https://oupub.etsu.edu/cas/psychology/bcilab/documents/mccane_bci2010poster_tmv-1_jrw.pdf)
- [Bonaker et al. 2023, Nomon and scanning rates](https://par.nsf.gov/servlets/purl/10521368)
- [Fisher et al. 2022, visually sensitive seizures, Epilepsia](https://repository.essex.ac.uk/34275/)
- [NeuroTechX EEG-ExPy SSVEP notebook](https://nbviewer.org/github/NeuroTechX/EEG-ExPy/blob/devcontainer-test/examples/sandbox/SSVEP_linux.ipynb)
- [Hands-Free Dino Jump project](https://tomlandhilarious.github.io/projects/3_project/)
- [Google: Camera Switches and Project Activate](https://blog.google/outreach-initiatives/accessibility/making-android-more-accessible/)
- [Google: Switch Access overview](https://support.google.com/accessibility/android/answer/6122836?hl=en)
- [Google Play: use of the AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)
- [Android: create an accessibility service](https://developer.android.com/guide/topics/ui/accessibility/service)
- [Android: BluetoothHidDevice reference](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice)
- [Google Home APIs public beta announcement](https://developers.googleblog.com/build-the-future-of-home-with-google-home-apis/)
