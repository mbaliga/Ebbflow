# Privacy Policy — Ebbflow

> **This is the working copy, not the hosted one.** The URL Play Console points at
> is **https://asystemofcells.com/ebbflow/privacy**. Keep the two in sync by hand.

**Last updated: 29 August 2026**

Ebbflow is an EEG application for Android (package `ai.ebbflow.baseline.app`), made
by A System of Cells. It handles brain-signal data, which is about as personal as
data gets, so this policy is specific.

## The short version

Ebbflow has no accounts, no advertising, no analytics and no tracking. **Your EEG
data never leaves your device.** There is no cloud sync, no server of ours, and no
way for us to see any of it.

## What the app collects

**Nothing reaches us.** We operate no servers and receive no data from the app.

## What stays on your device

- **Your EEG readings.** They travel from the headset to your phone over Bluetooth
  and are written to a private database on the device.
- **The state estimates computed from them**, calculated on your phone from your
  own recent history.
- **Your settings and session history.**

All of it stays on your device, and uninstalling Ebbflow deletes all of it.

## What is sent off your device, and to whom

**Nothing, automatically.** The app performs no uploads, has no sync and contacts
no service of ours.

If you choose to **export** a recording, for your own records or to share with your
own clinician, that export is an action you take, to a destination you pick, at a
moment you choose. The app does not send it anywhere on your behalf.

## Bluetooth, and why the app needs it

Ebbflow connects to an EEG headset over Bluetooth. It scans only to find that
headset, and the scan is not used to determine your location.

The app runs a foreground service while a session is active, so streaming survives
the screen turning off. That service shows a notification the whole time it runs,
and you can stop it there. It does nothing when no session is active.

## Not a medical device

Ebbflow is a research and self-knowledge instrument. It does not diagnose, treat,
prevent or monitor any medical condition, and it is not for clinical
decision-making. The state estimate is not a validated clinical measure. Nothing in
this app should be used in place of advice from a qualified professional.

## Permissions, and why each exists

| Permission | Why |
|---|---|
| `BLUETOOTH_CONNECT` | To connect to your EEG headset. |
| `BLUETOOTH_SCAN` | To find the headset if it is not already paired. Not used to derive your location. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | To keep a session streaming while the screen is off. |
| `POST_NOTIFICATIONS` | For the notification that shows a session is running, and lets you stop it. |

## Children

Ebbflow is not directed at children and collects no personal information from
anyone, including children.

## Changes

If this policy changes, the "Last updated" date above changes with it, and the
revised policy is published at this same URL.

## Contact

Ebbflow is made by **A System of Cells**. Questions about this policy or the app:
ebbflow@asystemofcells.com
