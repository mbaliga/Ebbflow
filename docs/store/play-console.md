# Ebbflow — Play Console answer sheet

> Only the **deltas** from `Personal-Tracker/store/HOUSE_DEFAULTS.md`.

| | |
|---|---|
| applicationId | `ai.ebbflow.baseline.app` |
| Version at time of writing | `0.1.0` (versionCode `1`) |
| Category | **Health & Fitness** |
| Tags | eeg, neurofeedback, mw75, bluetooth, local first, open source |
| Contact email | `ebbflow@asystemofcells.com` |
| Website | `https://asystemofcells.com/ebbflow` |
| Privacy policy | `https://asystemofcells.com/ebbflow/privacy` |

> ⚠️ **Two build-state facts before anything else.**
> 1. `:app` does not currently compile by design: the proprietary Baseline engine
>    was carved out to its own repo, and the open app cannot ship the paid engine
>    (`settings.gradle.kts`, and the D-S decision in `CONSTELLATION.md`). There is
>    no AAB to upload yet. This sheet is the default listing, ready for when there is.
> 2. The `applicationId` still carries the pre-split name: `ai.ebbflow.baseline.app`.
>    Since Baseline is now the *engine* and Ebbflow is the *app*, `ai.ebbflow.app`
>    would be the honest id. **It is permanent after the first production release**,
>    so change it now or accept it forever.

## Deltas from the house defaults

### Health apps declaration — required, and the answers must be exact
This is the most legally sensitive listing in the house. EEG plus any implied
clinical benefit is how an app becomes a regulated medical device.

| Question | Answer |
|---|---|
| Is your app a medical device? | **No** |
| Does it provide diagnosis, treatment or prevention of a disease? | **No** |
| Is it used for clinical decision-making? | **No** |
| Does it conduct health research on human subjects? | **No** |

The last one deserves care. The project *is* a single-subject (N=1) longitudinal
study, run by the owner on the owner. Play's question is about the app conducting
research **on users**, which it does not: it recruits nobody, has no protocol
imposed on a user, collects nothing centrally, and there is no server to collect
into. If the app ever gains a study-enrolment flow, this answer changes and an IRB
question arrives with it.

**Copy rules that are load-bearing here, not stylistic:**
- Never claim the app diagnoses, treats, prevents, monitors for, or improves any
  condition. Not depression, not ADHD, not anxiety, not sleep, not "brain health".
- Never use "clinical", "therapeutic", "medical grade" or "validated" of the state
  estimate. The listing calls it a research instrument, which is what it is.
- The disclaimer is in the listing body and stays there.
- The therapist-export path is framed as **the user exporting their own data**,
  never as a clinical feature of the app.

### Data safety
**No data collected. No data shared.**

| Question | Answer |
|---|---|
| Collect or share any user data? | **No** |
| Encrypted in transit? | Yes (there is no user-data transit) |
| Deletion? | Users can delete data in the app |

EEG is health data, so state the reasoning plainly in the policy and keep it true:
readings go from the headset over Bluetooth to the phone, into a local Room
database, and nowhere else. There is no cloud sync, no account, and no server.

### Permissions — one to remove before submitting
| Permission | Why | Action |
|---|---|---|
| `BLUETOOTH_CONNECT` | Connect to the headset. | Keep. |
| `BLUETOOTH_SCAN` | Find the headset when it is not already bonded. | Keep, **and add `android:usesPermissionFlags="neverForLocation"`**. |
| `ACCESS_FINE_LOCATION` | Legacy. Only ever present because pre-Android-12 BLE scanning required it. | **Remove**, see below. |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | Legacy, `maxSdkVersion="30"`. | Keep, but confirm they carry `maxSdkVersion` so they do not apply on modern devices. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Keep the stream alive with the screen off. `connectedDevice` is the correct type and is easy to justify. | Keep. |
| `POST_NOTIFICATIONS` | The foreground-service notification. | Keep. |

**Dropping `ACCESS_FINE_LOCATION` is worth doing before the first submission.**
`minSdk` is 26, so the legacy scan path is technically still reachable, but the
supported device for this app is a modern phone paired with an MW75. Declaring
fine location on a *health* app forces a location entry into Data safety, adds a
runtime prompt users find alarming in this context, and buys nothing:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<!-- delete ACCESS_FINE_LOCATION, or gate it android:maxSdkVersion="30" -->
```

If you keep it, Data safety must declare precise location as collected, and the
listing has to explain why an EEG app wants your location. That is a much worse
place to be than a small manifest edit.

### Content rating
- Category `Utility, Productivity, Communication, or Other`.
- No health or medical claims anywhere. Everything else No. Expected **Everyone**.

### Hardware requirement
- `<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />`
  correctly restricts the device catalogue.
- The listing states plainly that a compatible headset is required. Say it in the
  **short description too** if refund complaints ever appear; "the app is the
  software half only" is already in the body.

### Monetisation
- **No in-app purchases.** The app is open source and free. The Baseline engine is
  licensed separately and is not sold through this listing.

## F-Droid
- ⛔ **Blocked.** The app licenses a proprietary engine. It is only publishable if
  that engine is genuinely optional at build time and the app is useful without it.
  Worth resolving deliberately: a local-first open EEG app is exactly F-Droid's
  audience, and this is the constellation's best candidate for that store.

## Pre-submit checklist

- [ ] `:app` actually builds (needs the engine seam resolved).
- [ ] Decide `applicationId` permanently: `ai.ebbflow.baseline.app` or `ai.ebbflow.app`.
- [ ] Remove `ACCESS_FINE_LOCATION`; add `neverForLocation` to `BLUETOOTH_SCAN`.
- [ ] Confirm `BLUETOOTH`/`BLUETOOTH_ADMIN` carry `maxSdkVersion="30"`.
- [ ] Re-read the listing for any accidental health claim.
- [ ] Screenshots: pairing, the live signal, a deviation timeline. **A real headset
      and a real signal.** Do not fake an EEG trace in a store screenshot.
