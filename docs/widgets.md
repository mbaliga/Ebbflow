# Home-screen widgets

Ebbflow ships six standard Android App Widgets (`AppWidgetProvider` + `RemoteViews`), so any
launcher that hosts App Widgets can place them. They report **acquisition integrity only**:
stream phase, headset battery, the usable / clipped / flatline shares, the app's own quality
label, capture summaries and what is stored. No widget shows or implies focus, attention or any
other person state; no widget shows raw EEG, a µV value, the headset's name or an error message's
free text. Nothing on a widget starts Bluetooth, starts a capture or stops one: every tap opens
the app.

Status: built and unit-tested on the JVM only. **Nothing here has run on a phone yet**; see
[Owner verify](#owner-verify).

## The widgets

Every widget is resizable horizontally and vertically, defaults to 4x2, and has three or more
layouts. The thresholds (`WidgetSizing`) are calibrated to the sizes Android 12+ launchers report
for a phone grid, about (73n − 16) × (118m − 16) dp in portrait and (142n − 15) × (66m − 15) dp
in landscape, and each one is where its layout still fits at 1.3× font scale:

| Layout | From (w × h dp) | Typical cells |
|---|---|---|
| one line (`stat`, `card`) / one tap target (`pads`) | 110 × 40 | landscape 2x1 or 4x1 (≈ 51 dp tall); for `pads` also portrait 2x1 and 4x1 |
| small | 110 × 56 (`stat`, `card`), 110 × 40 (`image`), 110 × 110 (`pads`) | portrait 2x1, 2x2, 4x1; landscape 4x2 (≈ 117 dp tall) |
| medium | 250 × 130 | portrait 4x2 (≈ 276 × 220), the default; landscape 4x3 and 4x4 |
| large | 250 × 280 | portrait 4x3 (≈ 276 × 338) and taller |

On Android 12+ all layouts are handed to the launcher at once (`RemoteViews(Map<SizeF,
RemoteViews>)`), so resizing never wakes the app; each size is no smaller than the one before in
either direction, so the launcher's pick (the closest size that fits) is the largest that fits.
Below 12 the layout is chosen from `OPTION_APPWIDGET_MIN_WIDTH` × `OPTION_APPWIDGET_MAX_HEIGHT`
and redrawn in `onAppWidgetOptionsChanged`. In the large `stat`/`card` layout the detail rows
take the height that is left, so at a big font scale they are cut before the integrity note.
Tapping any widget body opens the acquisition screen (the app's only screen).

| Widget | Template | Small | Medium adds | Large adds |
|---|---|---|---|---|
| **Headset** | `stat` | phase as shape + word; "Battery 72% · 14:02", stamped with when the battery was read (once per session, at activation), not with the latest update | title, "as of" time; Battery, Read at, Started / Ended / Last heard | a caption for error or "not running"; valid / invalid packets and packet-error rate for the session; the integrity note |
| **Signal quality** | `stat` | the app's label as shape + word, said of the signal (● Good signal / ◐ Fair signal / ○ Poor signal); "Usable 94% · 14:32" | title, "as of"; Usable, Clipped, Flatline shares | "Live" or "Last known, <time>"; the label rule itself (Good = 90%+ usable and under 1% clipped, Fair = 65%+ and under 5%, Poor otherwise); the integrity note |
| **Last capture** | `card` | duration; "93% usable avg · 14:44" (or "· so far") | title ("Last capture" / "This capture"), "as of"; usable, clipped, flatline averages | share of the capture spent Good / Fair / Poor; Started, Ended (or Updated), number of summaries; a note when the read limit cut it short |
| **Capture shortcut** | `pads` | below 2x2 it collapses to one tap target ("■ Stopped · Tap to open ebbflow"); at 2x2: status + one pad | title, "as of"; two pads side by side | the two pads full width, and the line "Capture starts inside ebbflow, never from the home screen." |
| **Stored on this phone** | `stat` | "○ Raw EEG off"; "Quality summaries: 5,040" | Raw EEG Off/On, summary count, database size | "No raw-EEG recorder. Each summary also keeps one averaged µV value." (each stored summary row carries `meanUv`, the channel average of one packet); oldest and newest summary; whether the app requests internet permission |
| **Quality history** | `image` | the line alone (24 points) | title, "as of", subtitle, caption "Low 71% · avg 93% · last 95%" (48 points) | the Good (long dashes) and Fair (dots) threshold lines with a legend, and the time span (64 points) |

Pads on the capture shortcut:

| Pad | Shown | Does |
|---|---|---|
| Open to connect | not live, every size from 2x2 | opens the app; the user taps Start there |
| Open live view | live, every size from 2x2 | opens the app |
| Bluetooth settings | medium and large, live or not | opens the app, which opens the system Bluetooth screen on top, so Back lands on Start (pairing is the usual first-run blocker: "MW75 not found — pair it in Bluetooth settings first") |

There is no Stop pad: ending a capture is a state change, and a home screen is a public surface
with no confirmation step, so it stays inside the app, where the user can see the session.

## States

| State | When | What the widgets say |
|---|---|---|
| first run | no session ever seen | "○ Not connected yet · Open ebbflow to connect", "No signal yet", "No captures yet", "No quality history yet", "Nothing stored yet" |
| live | the streaming service runs in this process | values with an "as of" time; titles switch to "This capture", "so far" |
| waiting | a new session is connecting, no summary yet | "Waiting for signal" with the phase; last session's packet counts are not shown as this one's |
| last known | the last session stopped or failed | values with "as of" / "Ended" times; "Last known, <time>" |
| not running | the saved state says a session was running but no process holds it (killed, crashed, stopped from the Task Manager, force-stopped) | "△ Not running · Last known: Streaming", "Last heard <time>". It appears at the next redraw: the live-session check (within about 10 minutes, see Refresh), opening ebbflow, or a restart. After a force-stop Android cancels the app's alarms, so only opening ebbflow or restarting the phone corrects it |
| error (session) | the session ended in an error | "▲ Error · Open ebbflow for details" (the app's message is not copied: it is free text) |
| error (widget) | the database could not be read | "▲ Couldn't read ebbflow's data · Open ebbflow for details" |
| withheld | not used: no widget ever binds anything sensitive, so there is nothing to hide behind a setting | — |

Times print as "14:02" for today and "3 Nov, 14:02" otherwise, in the phone's 12/24-hour style,
so an "as of" time stays true even if no redraw comes.

## Data sources

- **Live and last-known state**: `StreamHub.state` (`model/StreamHub.kt`) while the service runs.
  `WidgetPublisher` (started from `Mw75StreamingService.onCreate`) saves the integrity part of it
  (`AcquisitionSnapshot`: phase, battery and when it was read, the three shares, the label, packet
  counts, session start) to `noBackupFilesDir/widgets/acquisition.snapshot`, which is excluded from
  cloud backup. Device name, µV values, stored-sample count and messages are deliberately not kept.
- `StreamState` gained three timestamps, set by `Mw75Controller`: `sessionStartedMs`,
  `batteryReadMs`, `qualityUpdatedMs`. They let a widget tell this session's values from ones
  carried over from the previous session.
- **History**: Room `signal_quality_samples` via `SignalQualitySampleDao` (in `FocusSampleDao.kt`):
  `latestQuality(limit)` (integrity columns only, never `meanUv`), `count()`,
  `oldestTimestampMs()`, `newestTimestampMs()`. A *capture* is an unbroken run of summaries with no
  silence over 30 s (`CaptureHistory.GAP_MS`); two captures closer together than that read as one.
  At most 20,000 newest rows are read per refresh; a capture longer than that says so. Rows
  migrated from the retired bring-up table (all three shares 0.0, which a real summary can't be)
  are left out.
- **Labels**: per-summary labels are recomputed with `SignalQualityLabel.classify`, now the single
  place the rule lives in `:core-eeg-community` (the estimator calls it too).
- **Stored**: `RawEegStorage.ENABLED` (`false`: this build has no raw recorder; a future setting
  must be read there), the database files' size (`ebbflow.db` plus `-wal`/`-shm`/`-journal`), and
  whether the manifest requests `INTERNET` (it does not).

## Refresh

- Pushed, never polled: `updatePeriodMillis="0"`.
- `WidgetPublisher` redraws on phase, session and battery changes at once, on a label change after
  5 s, and otherwise at most every 30 s while streaming (`PublishPolicy`). That interval also bounds
  how often the history widgets re-read Room.
- One self-scheduled redraw, an inexact, non-waking `AlarmManager.set(RTC, …)` to the app's
  non-exported `WidgetActionReceiver` (`WidgetFormat.nextRefresh`): just after local midnight, so
  "today" times gain their date, and while a session is live no later than 10 minutes ahead
  (`LIVE_CHECK_MS`). Every publish moves it, so the live check only fires once pushes stop; if the
  process died mid-stream it then runs in a fresh process, finds no session and shows "Not
  running". No exact-alarm permission.
- Opening the app redraws every placed widget (`MainActivity` → `WidgetUpdater.refreshInBackground`).
- Each provider also redraws on `TIME_SET`, `TIMEZONE_CHANGED` and `LOCALE_CHANGED`.
- Work runs off the main thread under `goAsync()` with an 8 s budget.

## Privacy

A home screen is public. The widgets show integrity counts, shares and times only. They never
show the headset's Bluetooth name (users can rename it after themselves), µV values, raw EEG or
the controller's free-text messages. The snapshot file lives under `noBackupFilesDir`. No widget
uses the network (the app has no internet permission), adds a permission or a dependency.

Not changed, but worth knowing: the manifest sets `android:allowBackup="true"`, so the Room
database of quality summaries is eligible for Android backup. That predates the widgets.

## Colour and accessibility

Colours follow the app's palette (dark ground `#101418`/`#181D23`, light-blue accent) with day and
night pairs in `values/colors.xml` and `values-night/colors.xml`. Colour never carries meaning:
every state is a shape and a word. Red and green are not used (a unit test checks the widget
colours). The history line is an alpha mask tinted by the layout, so it follows day/night without a
redraw. Text is in sp (12sp minimum), every text slot ellipsizes, layouts use start/end, every pad
is at least 48 dp tall with a content description, and the history image's content description
carries the numbers it draws.

## Code map

| File | Role |
|---|---|
| `app/.../widget/WidgetModel.kt` | snapshot + codec, liveness, status words and shapes, publish policy, sizing, formatting (pure) |
| `app/.../widget/CaptureHistory.kt` | capture grouping, summaries, sparkline buckets and geometry (pure) |
| `app/.../widget/WidgetContent.kt` | what each widget shows, as data (pure) |
| `app/.../widget/WidgetText.kt` | text keys (each one a `widget_*` string) |
| `app/.../widget/WidgetRenderer.kt`, `SparklineBitmap.kt` | RemoteViews and the line bitmap |
| `app/.../widget/WidgetUpdater.kt`, `WidgetPublisher.kt`, `WidgetSnapshotStore.kt` | reading data, pushing updates, the snapshot file |
| `app/.../widget/WidgetProviders.kt`, `WidgetIntents.kt` | the six providers, the refresh receiver, PendingIntents |
| `app/src/test/.../widget/` | JVM tests for all of the above logic, the wording law, colours, manifest and provider XML |

## Owner verify

Only a device can confirm these:

1. All six widgets appear in the picker of Pixel Launcher, One UI, Nova and Orrery Home, with the
   Android 12+ preview layouts (and the vector previews below 12).
2. Each widget renders and switches layout when resized through 2x1, 2x2, 4x1, 4x2 and 4x3+ in
   portrait (the default 4x2 shows the medium layout, 4x3 the large one) and in landscape if the
   launcher rotates (2x1 shows one line); the capture shortcut collapses to one tap target below
   2x2; nothing clips badly at 1.3× and 2× font scale. The thresholds come from Android's
   reference grid, not from the owner's launcher; One UI, Nova and Orrery Home may report other
   sizes.
3. Live updates while streaming with a real MW75: phase changes appear at once, shares within
   about 30 s; after Stop the widgets show "Stopped" with the right times.
4. Kill the app mid-stream (or stop it from the Task Manager): within about 10 minutes of the
   phone being awake the widgets show "Not running" with "Last heard" (the check is inexact and OEM
   battery managers may delay it). After a force-stop they correct only when ebbflow is opened or
   the phone restarts (Android 15+ may grey the widget until then).
5. No widget tap starts Bluetooth, starts a capture or stops one; every pad opens the app.
6. Bluetooth settings pad opens the app and then the system Bluetooth screen; Back returns to the
   app. A relaunch from Recents does not reopen Bluetooth settings.
7. The history line is tinted correctly in day and night themes (it is an `ALPHA_8` mask tinted by
   `android:tint`), and stays sharp enough at each size.
8. The midnight redraw adds dates to yesterday's times (inexact; OEM battery managers may delay it).
9. TalkBack reads each widget as one sentence ending in "Tap to open ebbflow", and each pad by
   name.
10. Rounded corners match the launcher on Android 12+ (`system_app_widget_background_radius`).
