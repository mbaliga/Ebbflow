# Ebbflow — multi-platform porting plan

> Part of the constellation-wide porting program (`Personal-Tracker/PORTING_PROGRAM.md`, 2026-10-06).
> Status: **PLAN — nothing in this document has been built.** Every claim about a target platform is
> labelled with its evidence class (§0). Nothing here was compiled, run or paired against an MW75 for
> this plan; the author's container has no Bluetooth radio, no headphones and no device. This repo has
> no decision log and no state file, so proposals live in §8 until the owner rules. PR base: this repo's
> default branch, `claude/meta-trunk-normalization-2y198w` (there is no `main`).

## 0. Evidence labels (never dropped)

`LAB` · `CI (hosted VM) evidence` · `EMULATOR EVIDENCE` · `SIMULATOR` · `CI-APPROX — NOT DEVICE EVIDENCE` ·
`SIMULATED — NOT DEVICE EVIDENCE` · `VIRTUALIZED — NOT DEVICE EVIDENCE` · `SYNTHETIC` · `CI-ONLY / NOT RUN` ·
`NEEDS-DEVICE-VALIDATION` (NDV) · `NEEDS-OWNER-VALIDATION` (NOV). Program additions: `PLAN` ·
`NOT-APPLICABLE (<reason>)` · `CONTAINER-BUILD-ONLY` · `BROWSER-HEADLESS`. Effort figures are
engineer-weeks and are estimates. Ids: `PT:D-S` is Personal-Tracker decision D-S; `OQ-n` and `F-n` are
the program's owner questions and shared-foundation items; `R1`–`R12` are its rules; "master" means
`Personal-Tracker/PORTING_PROGRAM.md`.

## 1. What this repo is, in porting terms

**Product.** Ebbflow is the open, local-first EEG acquisition app for the Master & Dynamic MW75 Neuro
headphones: 12 channels at 500 Hz, a BLE handshake to switch EEG mode on, then Bluetooth Classic RFCOMM
channel 25 carrying 63-byte packets (`README.md`; `core-eeg-community/.../Mw75Constants.kt`). It reports
acquisition integrity only (usable, clipped and flatlined channels), never focus, mood or clinical state.
The proprietary Baseline engine is an optional, not-yet-defined adapter; Ebbflow must build and capture
without it (`PT:D-S`, 2026-07-06; commits `f612972`, `d36d3eb`/`80544d8`). The registry hints "licenses
the Baseline engine" and "any hardware" are ahead of the code: only the MW75 transport exists.

**State (repo's own evidence).** Seeded: 17 commits, 2026-04-23 to 2026-09-12; no tags or releases; **no
`LICENSE`**, no `CLAUDE.md`, no `STATE`/`PROGRESS` file. The Android `:app` has never been built against
the SDK nor run on hardware (commit `185a871`; `app/README.md`: "Hardware validation still requires a
paired MW75 and a real Android device"). CI (`.github/workflows/ci.yml`) runs only
`./gradlew :core-eeg-community:test` on Temurin 21; that command was not executed for this plan, so its
pass status is per the workflow file, unverified here. Five scaffold directories (`ambient-controller/`,
`music-controller/`, `probe-app/`, `streamer-android/`, `streamer-linux/`) hold only `.gitkeep`.
`build_plan.md`, `research_brief.md` and `docs/session_handoff.md` are historical (`README.md` says so);
`docs/streamer_port_plan.md` (2026-05-24) is the only written Linux transport design: a Linux backend
for the Python `arctop/mw75-streamer`, developed in the fork `mbaliga/mw75-streamer`, design only, never
implemented. Current targets: Android phone/tablet (`:app`, minSdk 26, compile/targetSdk 35, neither
build- nor hardware-verified) and a JVM library (`:core-eeg-community`, the only CI-built artefact).

**Stack.** Kotlin 2.1.0; Gradle 8.14.3 wrapper; AGP 8.7.3; KSP 2.1.0-1.0.29; Jetpack Compose (BoM
2024.12.01) with Material 3; Room 2.6.1; kotlinx-coroutines 1.9.0 (`:app` only); lifecycle 2.8.7;
JUnit 4.13.2 (`gradle/libs.versions.toml`). Native dependencies: none; the only trick is reflection on the
hidden `BluetoothDevice.createRfcommSocket(int)` (`Mw75RfcommConnection.kt`). `:app` is included only when
`local.properties` carries `sdk.dir`, deliberately not keyed off `ANDROID_HOME` (`settings.gradle.kts`).
No Hyle dependency; plain Material 3, accent `#4FC3F7`.

**Size (measured in the checkout, 2026-10-06).** `find . -path ./.git -prune -o -name '*.kt' -print | wc -l`
gives 21 files (19 main, 2 test); piping the same list through `xargs cat | wc -l` gives 1,484 lines;
`grep -rn "@Test" --include=*.kt . | wc -l` gives 4 tests, all in `:core-eeg-community`.

## 2. Portable core vs platform-bound layers

| Module / dir | Role | Portability | Approx LOC | Notes |
|---|---|---|---|---|
| `:core-eeg-community` — `core-eeg-community/` | MW75 constants, incremental checksum-validating `PacketParser`, `EegPacket`, `SignalQualityEstimator` (GOOD/FAIR/POOR) | pure JVM, zero `android.*` | 168 main, 88 test | Only `PacketParser.kt` touches `java.nio.ByteBuffer`/`System.currentTimeMillis`; `EegPacket.kt` and `SignalQualityEstimator.kt` are already Kotlin-common |
| `:app` model — `app/.../app/model/` | `StreamPhase`, `StreamState`, `StreamHub` (process-wide `StateFlow`) | pure Kotlin | 64 | `System.currentTimeMillis` in `StreamHub.kt` only |
| `:app` UI screen — `ui/StreamScreen.kt`, `ui/theme/Theme.kt` | Status, quality and diagnostics cards, Start/Stop | portable under Compose Multiplatform: no `android.*`, only `androidx.compose.*` | 220 | six `String.format` calls (`StreamScreen.kt` lines 124, 126, 129, 147, 168, 170) are JVM-only and block an iOS `commonMain` |
| `:app` UI shell — `MainActivity.kt`, `StreamViewModel.kt` | permission launcher, `AndroidViewModel` | android-bound | 87 | replaced per head |
| `:app` bluetooth — `bluetooth/` | `Mw75DeviceFinder`, `Mw75BleActivator`, `Mw75RfcommConnection`, `Mw75Controller`, `BluetoothPermissions` | android-bound (28 `import android.*` lines in 5 files) | 652 | the whole porting problem; lifecycle ordering, command bytes and timings are portable logic trapped in Android types |
| `:app` service — `service/Mw75StreamingService.kt` | foreground `LifecycleService` owning one controller | android-bound | 121 | no desktop or iOS equivalent |
| `:app` data — `data/` | Room `ebbflow.db` v2, `signal_quality_samples` (file still named `FocusSample.kt`), `MIGRATION_1_2` | android-bound (Room 2.6.1) | 84 | entity, DAO and migration SQL are reusable |
| resources, manifest | three strings, two colours, launcher icon, BLE/foreground-service permissions | android-bound | small | re-declared per head |
| scaffold dirs | `.gitkeep` only | none | 0 | nothing to port |

Platform-bound APIs that matter:

| API | Where | Porting impact |
|---|---|---|
| RFCOMM channel 25 by channel number (hidden reflection, SPP-UUID fallbacks), blocking read loop | `Mw75RfcommConnection.kt` | The decisive constraint. The MW75 reportedly advertises no SDP record for channel 25 (KDoc in that file), so channel-number addressing is required. Linux: `AF_BLUETOOTH`/`BTPROTO_RFCOMM`, no JVM binding. macOS: `IOBluetoothRFCOMMChannel` (Objective-C). Windows: Winsock `AF_BTH` or WinRT. iOS: not available to third parties without MFi. UT: `QBluetoothSocket`, reserved `bluetooth` group |
| BLE GATT activation (`connectGatt`, CCCD, command writes, disconnect after activation) | `Mw75BleActivator.kt` (245) | Per-platform GATT stack; the handshake (`ENABLE_EEG`, 100 ms, `ENABLE_RAW_MODE`, 500 ms, `BATTERY`) is pure logic. The code notes some stacks block Classic callbacks while GATT is open: re-verify per platform |
| Bonded-device lookup, BLE scan | `Mw75DeviceFinder.kt`, `Mw75Controller.kt` | Pairing stays a manual OS step everywhere (hardware-safety rule) |
| Runtime permissions, foreground service | `BluetoothPermissions.kt`, `MainActivity.kt`, `AndroidManifest.xml` | Desktop Linux/Windows: none. macOS: `NSBluetoothAlwaysUsageDescription`. UT: AppArmor policy group |
| Room 2.6.1 + KSP | `data/`, `app/build.gradle.kts` | Room 2.7+ is multiplatform; SQLDelight is the alternative; neither is decided |
| `AndroidViewModel`, `Log`, `ComponentActivity.setContent` | `ui/`, `bluetooth/` | Small: a plain `StateFlow` holder, a logging interface, a `Window` or `UIViewController` host |

## 3. Binding rules this port must not break

- **No engine dependency.** The proprietary Baseline engine lives in the private `baseline` repo and is never vendored here; every platform build needs a documented "no engine" mode (`PT:D-S`; `README.md`; `settings.gradle.kts`).
- **Baseline output stays separate and labelled**, with source, model version, uncertainty and evidence grade preserved (`README.md`; `app/README.md`).
- **No mental-state inference in the default UI.** Signal quality is acquisition integrity only; the theta/beta placeholder was removed and must not return (`README.md`; `SignalQualityEstimator.kt` KDoc; `StreamScreen.kt` caption). This binds every viewer and port screen.
- **Offline-first, local-first.** Raw EEG storage is off unless the user enables it (`README.md`); nothing leaves the device without an explicit per-export action; no cloud sync (`build_plan.md` Phase 2; `research_brief.md` §6.5). No telemetry or analytics (program I-1).
- **CI stays Android-SDK-free.** `:core-eeg-community` is pure JVM and the only CI-built module; `./gradlew :core-eeg-community:test` must keep working on a bare JDK (`settings.gradle.kts`; commit `19e650a`).
- **Hardware safety.** Never auto-pair, never auto-enable EEG mode, never raise volume past a safe ceiling without an explicit toggle; streaming starts only from the Start button (`build_plan.md` working agreements).
- **Environment honesty.** On-device behaviour is owner-verified only; capture is not production-ready until `app/README.md`'s checklist passes (activation, reconnect, packet error rate, clipping, flatlining, cadence, permission revocation, cleanup after Bluetooth loss).
- **Protocol fidelity and one parser.** The documented constants, handshake and packet layout must not change; `PT:D-S` says MW75 parsing is not to be re-implemented outside `mw75-streamer`, and `docs/streamer_port_plan.md` lists the handshake, parsing, output servers and WebSocket JSON schema as "Untouched". The Android Kotlin parser already exists; whether desktop may add another is OQ-23 (§8, Q2).
- **Never decide a scientific or methodology question unilaterally** (`build_plan.md`); architecture calls carry a one-line reason.
- **Historical documents are not architecture.** Platform findings may be cited; the Baseline-three-surfaces framing may not be resurrected (`README.md`; `docs/session_handoff.md`).
- **No secrets or data in the repo**; `data/` is gitignored (`.gitignore`). Harness branch policy: never push to `main` without explicit permission; work on `claude/*` branches with draft PRs (`docs/session_handoff.md`).
- **Licence is undecided.** No `LICENSE` and no SPDX headers; `docs/phase_0_runbook.md` lists MIT, Apache-2.0 or AGPL-3.0 as an open owner item. Nothing is distributed, listed or upstreamed until OQ-12 is ruled; no SPDX header is added by a port.
- **Colour never carries meaning alone** (program I-3: the owner is red-green colourblind). The repo states no colour rule and uses Material 3's default error red for message text; whether I-3 binds Ebbflow is OQ-26. This plan applies it voluntarily to new UI: words and shapes always (quality is already a word, `StreamScreen.kt`), and no red/green meaning is introduced.
- **Program rules R1–R6, R11, R12** bind every step in §6: disjoint directories, the existing gate stays green, new workflow files only, pure core first, nothing signed or stored, no identifier minted before a `NAMES.md` row, reframes labelled as reframes. R6's `main`/tags rule maps here to the repo's default branch (`claude/meta-trunk-normalization-2y198w` today; there is no `main`, and `ci.yml`'s `push: branches: [main]` trigger is dead) plus tags; whether Ebbflow gets a `main` is for the owner.
- **Actions storage is exhausted.** `.github/workflows/cleanup-artifacts.yml` purges artifacts and caches every six hours; new lanes upload no artifacts and add no `cache:` option without OQ-20.

## 4. Target matrix (owner's order)

| Target | Feasibility | Approach | Blockers | Effort (eng-weeks, estimate) | Evidence today |
|---|---|---|---|---|---|
| Ubuntu Touch | reframe | A thin LAN **viewer** click (webapp-container) showing the signal-quality screen from a stream on a Linux/Android/macOS host; not a port of the Android app (R12). Native capture (Qt/QML + `QBluetoothSocket`, reserved `bluetooth` group, manual review, open source only) is not planned and would need OQ-1 and a ruling against `PT:D-S` | No device on record (OQ-1); AppArmor behaviour for RFCOMM channel 25 unverified; no stream source exists yet; confined apps are suspended in the background, so foreground-only; licence (OQ-12) | 5 (master figure; the underlying estimate splits into a thin viewer of about 1 and a native Qt/QML capture app of about 4 to 6) | PLAN |
| Linux desktop | moderate | **This repo's own declared primary platform.** Compose Multiplatform desktop (JVM) head consuming `mw75-streamer`'s WebSocket server mode (the letter of `PT:D-S`), reusing the portable core and screen; a Kotlin `Mw75Transport` only if OQ-23 rules for it. Flatpak first, jpackage tarball as fallback | No `LICENSE`; OQ-23 undecided; `mw75-streamer` Linux port is open PR #1 (OQ-13); Android bring-up never built, so a mock-transport parity test precedes the port; non-root RFCOMM (`CAP_NET_RAW`) unsettled; Flatpak Bluetooth on SteamOS untested | 4 (about 2 shared head and seam work, about 1.5 sidecar and packaging, about 0.5 validation docs; a Kotlin transport adds an unestimated increment) | PLAN |
| iOS / iPadOS | reframe (capture: not-applicable) | **Capture is impossible**: the EEG stream is Classic RFCOMM, third-party apps get Classic Bluetooth only through MFi accessories, and CoreBluetooth is BLE-only (`research_brief.md` §8.2; the program's framework brief labels this `ASSUMPTION`). Deliverable is a LAN **viewer** (Compose Multiplatform iOS, SwiftUI shell) on the iPad Pro M4, the "passive screen" role `build_plan.md` names | MFi restriction (hard block for capture); no stream source off-device yet; Apple Developer Program and delivery route (OQ-2); `String.format` in the screen; official MW75 iOS app's transport unknown | 3 (thin client; add 1 to 2 if SwiftUI is preferred) | PLAN |
| macOS | moderate | Same JVM desktop build as a `.dmg`, with `mw75-streamer` as the bundled macOS helper (its `main` is the macOS backend today) and the app consuming its WebSocket; alternative Swift helper only if Python packaging proves too heavy | No Mac on record (OQ-5): build is CI-only, hardware gates NOV; notarisation secrets (OQ-3); TCC attributes Bluetooth to the parent app, so the app's `Info.plist` carries the usage string; licence | 3 (about 1.5 if the sidecar route holds) | PLAN |
| Windows | moderate | Same JVM desktop build as an MSI via `jpackage`; capture through `mw75-streamer`'s Windows backend (PR #3), consumed over WebSocket; no JVM-to-WinRT bridge | The only Windows machine (the Dell) may become Linux (OQ-5); streamer PR #3 unmerged and unverified (OQ-13); signing route (OQ-3); licence | 3 | PLAN |

Effort is the master plan's per-target figure; the five cells overlap (macOS and Windows ride the Linux
build) and are not additive. Under the sidecar default the cost of rewriting the 652-line Bluetooth layer
drops out of the Linux cell, so 4 is an upper-ish estimate until OQ-23 is ruled. `mw75-streamer` has its own plan
(`PORTING_PLAN.md` in that repo); this plan consumes its outcome and does not duplicate it.

## 5. Tier and sequencing

**Tier B (port in sequence), matching `PORTING_PROGRAM.md` §5.** It is not A: the Android bring-up has
never been built or run with an MW75, there is no licence, the whole value rests on per-platform Classic
RFCOMM that no hosted runner can verify, and three of five targets are reframes or hardware-gated (iOS
cannot capture at all; Ubuntu Touch is a viewer or a Qt rewrite; no Mac, Windows or UT device is on
record). It is not lower: Linux is the repo's own primary platform, the core is already pure Kotlin,
and the Compose screen ports almost verbatim.

**Gate before any wave (master §5 row):** no `LICENSE` (OQ-12); OQ-23 (transport layer); the Android
app was never hardware-run, so the mock-transport parity test (E0.3) precedes any port code.

| Target | Wave (master §7) | Build-entry | Device-entry |
|---|---|---|---|
| Linux desktop | **P-LX**, in the slot "Ebbflow desktop over the mw75 streamer + mw75 PR #1 (after OQ-13)" | E0.1 to E0.3 done; F5 where adopted | `DEVICE_CHECKLIST_LINUX.md` on the Deck (Desktop Mode, optionally Gaming Mode) with the owner's MW75; the Dell only if OQ-5 says Linux; Redmagic-Edge cannot reach a radio (no BlueZ under proot) |
| macOS | **P-mac** (rides P-LX binaries) | P-LX desktop build green; OQ-3 for signing | no Mac on record, so NOV until OQ-5 changes |
| Windows | **P-win** (rides P-LX binaries; streamer PR #3 after OQ-13) | R3 path lint in place; P-LX desktop build green | the Dell while it is still Windows; afterwards `CI (hosted VM)` only |
| iOS / iPadOS | master §7 lists "Not on iOS: Ebbflow/mw75 capture" and gives the viewer no slot; **proposed:** P-iOS, after the CMP-iOS recipe is proven on Clavis in the simulator and the desktop stream path exists | `macos-latest` lanes; F1/F9/F10 iOS recipe | Apple Developer Program and delivery route (OQ-2); iPad Pro M4; iPhone items NDV |
| Ubuntu Touch | master §7 lists no Ebbflow entry; **proposed:** P-UT a (webapp-container shape), after the desktop stream path exists | F7's webapp template; OQ-1 or an explicit CI-only waiver | a UT device (OQ-1); without one, `CI (hosted VM)`/`CI-APPROX — NOT DEVICE EVIDENCE` only |

## 6. Work breakdown

All code and files below are **proposed placements**. Nothing in the existing build changes except
additive files inside `core-eeg-community/` (E0) and, only after the owner's go-ahead, an Android adoption
PR (E0.5) through the repo's normal PR process. The root `settings.gradle.kts`, `build.gradle.kts` and
`ci.yml` are not edited.

```
core-eeg-community/src/{main,test}/.../eeg/       additive: EegSource seam, optional Mw75Transport + Mw75Session, parity tests
core-eeg-community/src/test/resources/parity/     SYNTHETIC byte-stream and JSON fixtures
desktop/            separate Gradle build (own settings.gradle.kts) mapping ../core-eeg-community by directory; Compose Desktop head
streamer-linux/     the existing scaffold dir: a SHA pin of mbaliga/mw75-streamer (never a re-implementation)
packaging/{linux,macos,windows}/                  jpackage, Flatpak, MSI/DMG recipes (identifier placeholders until OQ-25)
ui-shared/          later, only for iOS: KMP module extracted from the zero-android files
apple/              iOS/iPadOS viewer: XcodeGen head over ui-shared
ubuntu-touch/       click viewer
.github/workflows/  new files only: android-compile.yml (proposed), desktop-linux.yml, desktop-macos.yml,
                    desktop-windows.yml, packaging-linux.yml, ios-sim.yml, ubuntu-touch.yml
```

### E0. Preconditions (repo-local, no platform code)

| # | Step | Done when | Here? |
|---|---|---|---|
| E0.1 | Owner rulings in §8: OQ-12 licence, OQ-23 transport, OQ-5 hardware, OQ-25 identifiers, OQ-26 colour rule | answers recorded in §8 with dates; until OQ-12, no packaging lane publishes anything | owner |
| E0.2 | **Proposed `android-compile.yml`**: on `ubuntu-latest` write `sdk.dir` into `local.properties` inside the job and run `./gradlew :app:assembleDebug` compile-only; no artifact upload | first run recorded; labelled `CI (hosted VM) evidence` for compilation only, device behaviour stays NDV; a red first run is an Android-side fix through the normal PR process | no (needs the Android SDK) |
| E0.3 | **Mock-transport parity suite** in `:core-eeg-community` (additive). Deterministic `SYNTHETIC` streams of valid 63-byte frames (sync `0xAA`, event 239, counter, ref, DRL, 12 little-endian floats, checksum over the first 61 bytes, scale 0.023842) fed through `PacketParser` at every chunk split, with corrupt checksums, leading garbage and buffer-overflow resets; golden `SignalQualitySummary` sequences for clean, clipped and flatlined windows. The same packets are also stored as `mw75-streamer` server-mode `eeg_data` JSON fixtures; because the core has no JSON library, that half lives with the adapter in `desktop/` tests (L2) and reads the shared fixtures by directory. Both paths must yield the same summaries; any tolerance needed is stated in the test, not hidden (timestamps differ in meaning, device receipt time versus streamer clock, and the estimator ignores them) | `./gradlew :core-eeg-community:test` green on a bare JDK; no fixture claims to be real MW75 data; this proves the shared pipeline, not the Android wiring (E0.5) | yes (JVM) |
| E0.4 | Add a callback-style `EegSource` interface (start, stop, events: status, packet, optional stream statistics) to the core with no new dependency; coroutines stay in the heads. If OQ-23 rules for a Kotlin transport, also lift the session logic (find, activate, settle, RFCOMM, cleanup, best-effort disable; ordering from `Mw75Controller.kt`, status decoding from `Mw75BleActivator.kt`) behind a `Mw75Transport` interface with a scripted mock, the settle and command delays injected as a sleeper function so tests need no real time and the core still needs no coroutines | scripted-mock tests assert phase order, cleanup after drop and after activation failure | yes (JVM) |
| E0.5 | **Optional, owner-gated:** Android `:app` adopts `EegSource`/the shared session so Android and desktop share one lifecycle. Until then two lifecycles exist and Android-side parity is NDV | PR merged after E0.2 is green; behaviour on a phone is NDV | no |

### Linux desktop (P-LX)

| # | Step (placement) | Done when | Here? |
|---|---|---|---|
| L1 | `desktop/` Compose Desktop head, JVM compiled `--release 17`; compiles the zero-`android.*` files in place by source-directory include patterns (`StreamScreen.kt`, `Theme.kt`, `model/*`), no copies, no edits to `:app`; window host replaces `MainActivity`; a plain `StateFlow` replaces `AndroidViewModel`. The Compose Multiplatform release must be one compatible with Kotlin 2.1.0 (not verified here; pins follow OQ-17). Workflow `desktop-linux.yml` | `./gradlew -p desktop build` and the core tests pass on `ubuntu-latest`; window launch is not tested in the container | compile only: `CONTAINER-BUILD-ONLY` |
| L2 | `StreamerWebSocketSource` (desktop head): a client of `mw75_streamer.server` on loopback that maps `status`, `eeg_data`, `error` and `heartbeat` messages to `EegSource` events. It sends `connect` only after the user presses Start and `disconnect` on Stop, and never auto-connects. Limits to handle honestly, per the documented protocol (`docs-src/server.rst` in the streamer checkout, not re-verified against PR #1): the server reports one `connecting` state (the three Android sub-phases collapse), has a `reconnecting` state `StreamPhase` lacks, reports no invalid-packet count, and carries no device name, so the UI shows "not reported by this source", never 0% | adapter tests pass against recorded `SYNTHETIC` frames from E0.3; no live server in CI (R5); a loopback run against `mw75_streamer.server --mock` is an owner-local step recorded as NOV | yes (JVM; Python mock container-verifiable, not yet run) |
| L3 | Quality-sample storage behind a `QualitySampleStore` interface, summaries only (raw EEG stays off by default). Default: Room 2.7+ with the bundled SQLite driver so `ebbflow.db` v2 stays one schema; alternative SQLDelight; spike first. Path under `XDG_DATA_HOME` | schema parity with Android's `signal_quality_samples` asserted in a JVM test | yes |
| L4 | Sidecar pin in `streamer-linux/`: a file naming the `mbaliga/mw75-streamer` commit (submodule or pinned dependency, the open choice in `docs/streamer_port_plan.md`; mechanism per OQ-24), consumed by packaging scripts. The streamer is started by the user (documented) in phase one; phase two spawns it as a child after Start with an explicit loopback bind, owned by the app, no daemon | pin resolves in CI; loopback bind is explicit in the launch recipe (the server's default bind address was not verified here) | pin yes; spawn behaviour NDV |
| L5 | `packaging/linux/`: jpackage app-image tarball with `install.sh` (also the Redmagic-Edge path, which must start with no Bluetooth, Wayland or Vulkan), and a Flatpak manifest that consumes the prebuilt app-image (Flathub builds offline). Finish-args per the Linux brief: `--allow=bluetooth`, `--system-talk-name=org.bluez`, X11 sockets; a bundled sidecar should need no `--share=network` (`ASSUMPTION`: loopback inside one sandbox, to verify), a host-side streamer does and must be a named variant. App id left as a placeholder until OQ-25. Workflow `packaging-linux.yml`, package lanes on default branch/tags only, binaries only to a draft Release | manifest lints; unsigned tarball builds on `ubuntu-latest`; nothing published before OQ-12 | build yes; install and radio NDV |
| L6 | Owner validation on the Steam Deck with the MW75: pair with `bluetoothctl`, activate, stream; the exit criterion inherited from `docs/streamer_port_plan.md` is ten minutes at 500 Hz, 12 channels, no packet loss; plus the non-root RFCOMM spike (`CAP_NET_RAW` versus the BlueZ D-Bus Profile1 route) and the Flatpak sandbox check on SteamOS | `DEVICE_CHECKLIST_LINUX.md` rows recorded by the owner; until then every capture claim stays NDV | owner |
| L7 | **Only if OQ-23 rules for a Kotlin transport:** `jvmMain` Linux `Mw75Transport` (BlueZ over D-Bus for GATT; Panama FFI to `socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM)` for channel 25). It adds a second parser path that `PT:D-S` forbids unless ruled, so it needs an explicit ruling first | owner ruling, then tests against the E0.4 mock | NDV |

### Ubuntu Touch (reframe)

| # | Step (placement) | Done when | Here? |
|---|---|---|---|
| U1 | Gate: OQ-1 (or an explicit CI-only waiver), F7's webapp template, and a stream source on a LAN host (L2 and L4, or the macOS streamer) | gate recorded | owner |
| U2 | `ubuntu-touch/`: a webapp-container click over a small static HTML/JS viewer (Kotlin/Wasm is spike-gated on the 24.04-2.x engine and WasmGC, so not used here). `networking` group only (automated review). UI says "viewer", states LAN and cleartext, and treats suspension as normal: the OS freezes unfocused apps, so it reconnects on resume. Package name left a placeholder until OQ-25 (R11). Workflow `ubuntu-touch.yml` (Clickable in its digest-pinned image on `ubuntu-latest`, optional arm64 smoke) | click builds and passes click-review in CI | `CI (hosted VM)`/`CI-APPROX — NOT DEVICE EVIDENCE`; install and run NDV |
| U3 | Not planned: a native Qt/QML capture app (C++ port of the 168-line core, `QBluetoothSocket` to channel 25, reserved `bluetooth` group, manual review, open source only). Would require OQ-1, a licence (OQ-12), a ruling against `PT:D-S`, and a spike on whether a confined click may open the channel. The cheaper UT capture path is `mw75-streamer`'s own Python + QML click (its plan, P-UT a); apps cannot share a service on UT. Waydroid is not listed in OQ-21 for Ebbflow and Bluetooth passthrough into it is unverified | owner decision only | NDV |

### iOS / iPadOS (viewer only)

| # | Step (placement) | Done when | Here? |
|---|---|---|---|
| I1 | State the impossibility in the first screen and the README of `apple/`: "viewer, no capture on iOS" (R12); the BLE handshake alone yields no data | text lands with the head | yes |
| I2 | Extract `ui-shared/` as a KMP module (`jvm()`, `iosArm64`, `iosSimulatorArm64`, `androidTarget` conditional on `sdk.dir`): the screen, theme, state model and a small number formatter replacing `String.format`; `EegPacket.kt` and `SignalQualityEstimator.kt` compiled in place (already Kotlin-common), so `:core-eeg-community` and its gate are untouched. `desktop/` then switches from in-place mapping to `ui-shared/` | `desktop/` and `ui-shared` JVM tests green; `:app` untouched | yes (JVM) |
| I3 | `apple/`: XcodeGen head with a SwiftUI shell hosting the Compose controller (F9/F10 recipe); a WebSocket client for the LAN source; **no** `NSBluetoothAlwaysUsageDescription` (the viewer uses no Bluetooth); `NSLocalNetworkUsageDescription` and `PrivacyInfo.xcprivacy`; cleartext `ws://` to a LAN host may need an App Transport Security exception (`ASSUMPTION`, to verify) | head builds | `macos-latest` only |
| I4 | Workflow `ios-sim.yml` on `macos-latest`: simulator build with `CODE_SIGNING_ALLOWED=NO`, `iosSimulatorArm64Test`, no signing, no artifact upload | green run recorded | `SIMULATOR`; iPad Pro M4 NDV |
| I5 | UI copy: "LAN only, unencrypted, shows a stream from another machine"; App Review 4.2 minimum-functionality risk for a viewer of an external stream is general knowledge, not verified, so delivery is TestFlight or ad-hoc per OQ-2 | owner choice | NOV |

### macOS (rides Linux)

| # | Step (placement) | Done when | Here? |
|---|---|---|---|
| M1 | `packaging/macos/` and `desktop-macos.yml` on `macos-latest`: JVM tests, then a `jpackage` `.dmg`, **UNSIGNED — not for release**; `Info.plist` with `NSBluetoothAlwaysUsageDescription`; the entitlement set is the minimum that asom's S-M3 self-test establishes (program F10), never `jpackage`'s default sandbox plist; signing and notarisation exist only as disabled templates until OQ-3 | unsigned build and tests green; package lane on default branch/tags only | `CI (hosted VM)`; Gatekeeper and TCC NDV |
| M2 | Sidecar: bundle the macOS `mw75-streamer` helper (Briefcase `.app`, its own plan) and consume its WebSocket; fallback is a Swift helper over `IOBluetoothRFCOMMChannel` owned here, only if Python packaging proves too heavy | the bundled helper is present in the .app and `--help`/`--version` exits 0 in CI (no server started, R5); a mock-mode run is an owner-local NOV step | real capture NOV (no Mac) |

### Windows (rides Linux)

| # | Step (placement) | Done when | Here? |
|---|---|---|---|
| W1 | R3 path lint (reserved names, and the characters colon, less-than, greater-than, pipe, question mark, asterisk, double quote) lands before any Windows lane; `-text` on byte-exact fixtures (the repo has no `.gitattributes` today) | lint green | yes |
| W2 | `desktop-windows.yml` on `windows-2025`: core tests and `desktop/` tests on a Windows JDK first (R4; catches charset and CRLF traps), then `packaging/windows/` `jpackage` MSI, **UNSIGNED — not for release**; a winget manifest only after OQ-25 and OQ-3 | tests and unsigned MSI green on default branch/tags | `CI (hosted VM)`; install NDV |
| W3 | Sidecar: `mw75-streamer`'s Windows backend (PR #3, after OQ-13) bundled as a PyInstaller one-directory build, labelled experimental and unverified; no JNA or JVM-to-WinRT bridge | `--help`/`--version` exits 0 in CI; mock-mode run NOV, R5 | capture NOV; treat as defer-within-repo until Windows hardware exists |

## 7. Shared foundation this repo consumes or provides

**Consumes.** F5 (`kmp-conventions`: shared catalogue at the pin OQ-17 chooses, the `android.*` import ban
for common and jvm source sets, conditional Android inclusion, which matches `settings.gradle.kts`'s own
pattern); F9 (CI matrix template, SHA-pinned, package lanes on default branch/tags, R3 path lint, no artifact
uploads); F10 (jpackage and nFPM, the Flatpak manifest and the Flathub offline rule, DMG entitlements and
notarisation templates, XcodeGen head, clickable set); F11 (evidence scheme and the five
`DEVICE_CHECKLIST_*.md` templates, which each target's device rows here would use); F7's webapp-container
template for the UT viewer. F6 (`platform-ports`) names Ebbflow as a consumer for Bluetooth RFCOMM, app
directories and file and share seams: under the sidecar default only app directories are needed, and the
RFCOMM actual is needed only if OQ-23 rules for a Kotlin transport. F1 (Hyle tokens) applies only if
OQ-26 or a later decision makes Ebbflow a Hyle consumer; it is not one today. Not consumed: F2, F3, F4,
F8, F12 (Ebbflow holds no cloud keys, so OQ-22 touches it only through data-at-rest, Q10).

**Provides.** Nothing in F1 to F12. Offers: the `EegSource` seam and the `SYNTHETIC` parity fixtures, and
the finding that only `PacketParser.kt` stands between the core and Kotlin-common. **Gap against master
§6:** no F-item covers a versioned EEG stream contract for thin clients; this plan keeps it Ebbflow-local
and compatible with the streamer's documented server protocol rather than minting one. The private
`baseline` repo would need matching per-platform artefacts once a Baseline adapter seam is defined (Q9).

## 8. Open questions for the owner

Each says what it blocks. Proposals are labelled; none is a ruled decision.

1. **Licence (OQ-12).** `LICENSE` is missing: MIT, Apache-2.0 or AGPL-3.0 (`docs/phase_0_runbook.md`)? *Blocks* every distributable build, Flathub/AUR, any OpenStore listing, and contributing the Linux streamer port upstream to the MIT `arctop/mw75-streamer`. Packaging lanes stay compile-only until answered.
2. **Linux transport layer (OQ-23).** Python `mw75-streamer` sidecar over WebSocket (the letter of `PT:D-S`, and consistent with `docs/streamer_port_plan.md`) or a Kotlin `Mw75Transport` in Ebbflow (the Android exception generalised)? *Blocks* L2, L4, L7 and the macOS and Windows transports. *Proposal:* sidecar by default, with the E0.4 seam shaped so either can plug in; this plan is written that way.
3. **Hardware (OQ-5, OQ-1, OQ-2).** Do you own or plan a Mac, a Windows machine after the Dell moves to Linux, a Ubuntu Touch device? `build_plan.md` lists only Steam Deck, Dell, Android phone, iPad Pro M4. Is the Steam Deck an acceptable place to run device checklists? *Blocks* the device gates of macOS, Windows and UT; without answers they are CI-build-only.
4. **iOS.** Accept the reframe to a LAN viewer on the iPad Pro M4, or shelve iOS as `research_brief.md` §8.2 did? Do you know how the official MW75 Neuro iOS app receives EEG (BLE or MFi)? If BLE, native capture could be revisited. *Blocks* I1 to I5. Also needs OQ-2 for any device build.
5. **Android bring-up.** Has `:app` ever been built against SDK 35 or run with the MW75? May a compile-only `android-compile.yml` (E0.2) be added, and should every port wait for the E0.3 parity suite? *Proposal:* yes to both. *Blocks* E0.2 and E0.5.
6. **Identifiers (OQ-25).** The namespace and `applicationId` are `ai.ebbflow.baseline.app` while the core is `ai.ebbflow.eeg`; `NAMES.md` says `ai.ebbflow.*` / TBD. Rename before minting the Flatpak app id, iOS bundle id, click name, MSI GUID and winget id? *Blocks* every manifest (R11).
7. **Colour rule and Hyle (OQ-26).** Does the violet/cyan, shape-plus-label rule bind Ebbflow, whose UI uses Material 3 defaults including red error text? Does Ebbflow ever consume Hyle? *Blocks* port-UI copy review and F1 scope.
8. **Scope of the Linux port.** UI app only, or also the headless daemon on the Dell that `research_brief.md` §8.4 envisioned (a user systemd unit around the streamer's server mode, template only, disabled by default)? Ties to OQ-5 (does the Dell homelab plan still stand). *Blocks* L4 phase two and the LAN-viewer sources in Q11.
9. **Baseline adapter seam.** Define the optional engine interface (versioning, evidence-grade fields) in Ebbflow before ports, so each platform build has a documented no-engine mode? The private `baseline` repo would then need per-platform artefacts, and its consumption model (embed or LAN node) is its own question. *Blocks* nothing in §6 today; blocks any engine-aware build.
10. **Data at rest on desktop (OQ-22).** `build_plan.md` says durable data is encrypted at rest, but `:app` relies on the OS. On desktop, rely on OS disk encryption and say so in the UI, or add app-level encryption (which needs key custody)? *Blocks* L3's storage design.
11. **LAN viewer source and privacy.** Viewers (UT, iOS) can read the streamer's raw `eeg_data` (no new contract, but raw EEG in cleartext on the LAN, and each viewer needs the estimator) or a **derived-quality relay** from a desktop Ebbflow (summaries only, no raw µV, loopback by default and LAN only by explicit user action). *Proposal:* relay by default; raw LAN streaming only behind an explicit, labelled toggle. *Blocks* U2 and I3.
12. **Ubuntu Touch shape.** Thin viewer only (this plan), or also a native Qt/QML capture app, which needs a device, a licence and a ruling against `PT:D-S`? *Blocks* U3.
13. **Windows priority.** CI-build-only, or deferred entirely until Windows hardware exists, and "experimental, unverified" for streamer PR #3 (OQ-13)? Depends on OQ-3 for signing. *Blocks* W2 publication and W3.
14. **Default branch.** Rename the trunk to `main` before package lanes exist? There is no `main` today, so `ci.yml`'s `push: branches: [main]` trigger never fires. *Blocks* the push triggers of every proposed lane (L5, M1, W2, F9).

## 9. Sources read

Repo files (checkout `/home/user/Ebbflow`, 2026-10-06): `README.md`; `settings.gradle.kts`; `build.gradle.kts`;
`gradle.properties`; `gradle/libs.versions.toml`; `gradle/wrapper/gradle-wrapper.properties`; `.gitignore`;
`.github/workflows/ci.yml`; `.github/workflows/cleanup-artifacts.yml`; `app/README.md`; `app/build.gradle.kts`;
`app/proguard-rules.pro`; `app/src/main/AndroidManifest.xml`; under `app/src/main/kotlin/ai/ebbflow/baseline/app/`:
`bluetooth/{BluetoothPermissions,Mw75BleActivator,Mw75Controller,Mw75DeviceFinder,Mw75RfcommConnection}.kt`,
`data/{AppDatabase,FocusSample,FocusSampleDao}.kt`, `model/{StreamHub,StreamState}.kt`,
`service/Mw75StreamingService.kt`, `ui/{MainActivity,StreamScreen,StreamViewModel}.kt`, `ui/theme/Theme.kt`;
`app/src/main/res/values/{colors,strings,themes}.xml`; `core-eeg-community/build.gradle.kts`;
`core-eeg-community/src/main/kotlin/ai/ebbflow/eeg/{EegPacket,Mw75Constants,PacketParser,SignalQualityEstimator}.kt`;
`core-eeg-community/src/test/kotlin/ai/ebbflow/eeg/{PacketParserTest,SignalQualityEstimatorTest}.kt`;
`docs/session_handoff.md`; `docs/streamer_port_plan.md`; `docs/phase_0_runbook.md`; `build_plan.md` (reality
check, Phases 1, 2, 5 to 7, working agreements, cuts); `research_brief.md` (§2, §3, §6.5, §8, §11); `git log`
and `git show -s` for `f612972`, `d36d3eb`, `80544d8`, `19e650a`, `185a871` (read-only).

Outside this repo: `Personal-Tracker/PORTING_PROGRAM.md` (§0 to §3, §4, §5 Ebbflow row, §6 to §8);
`Personal-Tracker/DECISIONS.md` (`PT:D-S`) and `NAMES.md` (Ebbflow rows); the `mw75-streamer` checkout's
`README.md` and `docs-src/server.rst` (server-mode protocol: `connect`, `disconnect`, `status`,
`eeg_data`, `heartbeat`, `error`), read at its `main` (macOS-only v1.0.8); PR #1 and PR #3 contents were
not read for this plan.
