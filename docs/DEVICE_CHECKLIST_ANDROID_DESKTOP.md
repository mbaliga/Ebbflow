# Ebbflow device checklist: Android desktop mode

Covers the wave-1 desktop-mode changes (commit 9890895) and the hardware list in `app/README.md`.
Nothing here has been run; the Android `:app` has never been built against the SDK or run on hardware.
Every row is `NEEDS-DEVICE-VALIDATION`.

**Devices.** An Android device with a desktop or freeform mode (external display, or a tablet/phone
desktop mode), a keyboard and mouse, and a paired MW75.

**Scope note.** Ebbflow reports acquisition integrity only. It does not estimate focus or mental state.

| ID | Check | Pass when | Status |
|---|---|---|---|
| DV-AD-01 | Freeform window default size: launch in desktop mode | the window opens at the manifest's default size and is not forced fullscreen | NEEDS-DEVICE-VALIDATION |
| DV-AD-02 | Resize the window during a stream | the layout adapts and the stream is not restarted or dropped | NEEDS-DEVICE-VALIDATION |
| DV-AD-03 | Play-store install on a device with no touchscreen (`touchscreen` is `required=false`) | the app is installable and not filtered out | NEEDS-DEVICE-VALIDATION |
| DV-AD-04 | Keyboard: Tab reaches Start and Stop; Enter activates the focused button | both work without touch and focus order is sensible | NEEDS-DEVICE-VALIDATION |
| DV-AD-05 | BLUETOOTH permission flow with mouse and keyboard only (Android 12+ and the older permission era) | permission dialogs can be answered without touch; denial is shown in the UI | NEEDS-DEVICE-VALIDATION |
| DV-AD-06 | Keyboard attach/detach and display change mid-session (configChanges handled) | no activity restart, stream continues | NEEDS-DEVICE-VALIDATION |
| DV-AD-07 | MW75 activation over BLE | EEG mode switches on after Start is pressed | NEEDS-DEVICE-VALIDATION |
| DV-AD-08 | RFCOMM capture and packet error rate | packets arrive at 500 Hz, 12 channels; error rate is recorded | NEEDS-DEVICE-VALIDATION |
| DV-AD-09 | Signal quality: clipping and flatlining detection with a real headset | clipped and flat channels are reported correctly | NEEDS-DEVICE-VALIDATION |
| DV-AD-10 | Reconnect after headphones power-cycle | app shows the loss and recovers or waits for Start | NEEDS-DEVICE-VALIDATION |
| DV-AD-11 | Permission revocation mid-stream | stream stops cleanly and the UI says why | NEEDS-DEVICE-VALIDATION |
| DV-AD-12 | Cleanup after Bluetooth loss | no crash, sockets and the foreground service are released | NEEDS-DEVICE-VALIDATION |
| DV-AD-13 | Start-button-only streaming | nothing pairs, activates or streams until Start is pressed | NEEDS-DEVICE-VALIDATION |
