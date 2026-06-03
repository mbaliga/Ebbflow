# ebbflow `:app` — Android MW75 EEG focus app

The Android application module: it activates the MW75 over BLE, streams 12-channel
EEG over Bluetooth Classic RFCOMM, parses packets with the pure-JVM
[`:core-eeg`](../core-eeg) module, computes a **placeholder** focus index, persists
readings with Room, and renders everything in a small Jetpack Compose UI — kept
alive by a `connectedDevice` foreground service.

This is the layer described as "what's next" in PR #2 (the scaffold + packet-parser
core). It is a faithful Android port of the reference Python streamer's
device layer (`mw75-streamer/mw75_streamer/device/`).

## Architecture

```
MainActivity ── Compose ──> StreamScreen
     │                          ▲
     └─ StreamViewModel ────────┘   reads StreamHub.state + Room (recent samples)
                │ start()/stop()
                ▼
        Mw75StreamingService (foreground, type=connectedDevice)
                │ owns
                ▼
        Mw75Controller  (port of mw75_device.py lifecycle)
          ├─ Mw75DeviceFinder     bonded device, else BLE scan
          ├─ Mw75BleActivator     BLE activation handshake  (port of ble_manager.py)
          ├─ Mw75RfcommConnection RFCOMM ch.25 + reflection  (port of rfcomm_manager.py)
          ├─ PacketParser         :core-eeg  (0xAA framing, checksum, µV scaling)
          ├─ FocusEstimator       :core-eeg  (theta/beta → 0..1 placeholder focus)
          └─ Room (FocusSample)   durable readings on-device
```

State flows one way: the service-owned `Mw75Controller` publishes a `StreamState`
to the process-wide `StreamHub`; the UI only reads it. See
[`model/StreamHub.kt`](src/main/kotlin/ai/ebbflow/baseline/app/model/StreamHub.kt).

## Connection lifecycle (mirrors the Python `MW75Device`)

1. **Find** a bonded device named `MW75…`; if none, BLE-scan for one.
2. **Activate** over BLE GATT: enable notifications, then write `ENABLE_EEG`
   (+100 ms), `ENABLE_RAW_MODE` (+500 ms), `BATTERY` (+500 ms); confirm the EEG and
   raw-mode acknowledgements; read battery. Then **disconnect BLE** — RFCOMM is
   more reliable with GATT released (the reference does the same for macOS Tahoe).
3. **Settle** 500 ms, then open **RFCOMM channel 25**. The public Android API only
   offers SDP-based sockets, so we use the hidden `createRfcommSocket(int)` via
   reflection, falling back to the SPP UUID (secure then insecure).
4. **Stream**: raw bytes → `PacketParser.feed` → `EegPacket` → `FocusEstimator`
   (recomputes at ~2 Hz) → `StreamHub` + Room.
5. **Stop/error**: close RFCOMM, best-effort BLE disable, publish a terminal state.

## The focus metric is a placeholder

`FocusEstimator` (in `:core-eeg`, so it is unit-tested in CI) maps a theta/beta
band-power ratio to a 0..1 index. It does **no** filtering, artefact rejection, or
per-user baselining and makes **no clinical claim**. The real state estimator is the
Phase 2 "sensing core" (see [`../build_plan.md`](../build_plan.md) §2). The UI labels
the number accordingly.

## Building

The Android Gradle Plugin needs the Android SDK at configuration time, so the
`:app` module is included **only when an SDK is present** (see
[`../settings.gradle.kts`](../settings.gradle.kts)). Without an SDK — e.g. in CI —
only `:core-eeg` is configured and built.

```bash
# Point Gradle at your SDK (either works):
export ANDROID_HOME=$HOME/Android/Sdk          # or add sdk.dir=… to local.properties

./gradlew :app:assembleDebug                    # build the APK
./gradlew :core-eeg:test                        # pure-JVM tests (no SDK needed)
```

- JDK 17+, Android SDK with platform 35 installed.
- Versions are pinned in [`../gradle/libs.versions.toml`](../gradle/libs.versions.toml).
  AGP/Gradle are a compatible pair (AGP 8.7.x, Gradle 8.14.3); bump together if you
  upgrade.

> **Not yet build-verified.** This module has not been compiled against the Android
> SDK in this environment (no SDK available here) and has not run on hardware —
> Bluetooth cannot run in CI. `:core-eeg` (parser + focus metric) **is** verified:
> `./gradlew :core-eeg:test` → 13 tests pass.

## Verifying on hardware (manual)

1. **Pair the MW75** in Android Bluetooth settings (Classic pairing for RFCOMM).
2. Install: `./gradlew :app:installDebug`.
3. Launch **ebbflow**, tap **Start**, grant the Bluetooth (and, on Android 13+,
   notification) permissions.
4. Expect: phase `Scanning → Activating → Connecting → Streaming`, a battery %, a
   live focus number, a low/stable error rate, and a rising "Samples stored" / "Read
   back from DB" count (Room write+read round-trip).
5. **Confirm the sample rate empirically** before trusting any θ/β value —
   `NOMINAL_SAMPLE_RATE_HZ` is hardcoded upstream, not transmitted in-band.

## Known unknowns / risks

- **RFCOMM channel via reflection** may be blocked on some hardened OEM builds; the
  SPP-UUID fallbacks exist but the MW75 may not expose an SPP record. Verify on the
  target phone.
- **BLE vs. Classic address.** We connect GATT to the same `BluetoothDevice` used for
  RFCOMM; confirm the dual-mode device exposes the GATT service on that handle.
- **`disconnect_after_activation`** was a macOS Tahoe workaround; whether Android
  needs BLE released before RFCOMM is unconfirmed — the code does it defensively.
- **Sample rate** (see above).
