# Baseline — Build Plan v1
**Companion to:** `research_brief.md` (v0.2)
**Purpose:** Sequenced, practical engineering plan. The brief is the long-term vision; this is what to actually build, in what order, against what hardware reality.
**Format:** Phases with explicit prerequisites, deliverables, exit criteria, and Claude Code prompts where relevant.

---

## Reality Check

You have:
- MW75 Neuro headphones (the EEG sensor)
- Steam Deck with monitor/keyboard/mouse (daily-driver workstation, Arch Linux + KDE)
- Dell laptop (currently Windows, slated for Linux ASAP, will become homelab server)
- Android phone with substantial RAM
- iPad Pro M4 (will not run the EEG stack; useful for the metacognitive UI as a passive screen)
- Two smart switches (lamp, AC) and a smart LED bulb
- A therapist who will be consulted before the metacognitive surface enters use

You don't yet have:
- Linux on the Dell
- Home Assistant running anywhere
- The smart devices integrated into anything you control programmatically
- A working EEG stream on any platform you own (the upstream streamer is macOS-only)
- A wrist wearable (optional; HRV adds value but is not required for v0.1)

The single biggest gating constraint is that **before any of the interesting work happens, the homelab and the streamer port need to exist**. Those two are the prerequisites for everything else.

---

## Phase 0 — Foundations (target: 2–3 weekends)

Nothing about EEG, signal processing, or research happens until this phase is complete. This is the boring, well-understood plumbing that everything depends on.

### 0.1 — Linux on the Dell

Replace Windows with a Linux distribution suitable for a 24/7 homelab server. Recommended: Debian stable or Ubuntu Server LTS. Headless or minimal desktop. Set up SSH access from the Steam Deck. Verify it can run for a week without your attention.

**Exit criteria:** SSH into the Dell from the Steam Deck reliably. `uptime` shows multi-day uptime. Basic system monitoring (e.g., `cockpit` or just `htop` over ssh) accessible.

### 0.2 — Home Assistant on the Dell

Install Home Assistant (Container or OS edition; Container is fine for this server). Integrate the two smart switches and the smart bulb. Confirm you can toggle each device from the Home Assistant web UI and from a script via the REST API or MQTT.

**Exit criteria:** A shell script on the Steam Deck can turn the lamp on, turn the lamp off, change the bulb colour temperature, and turn the AC switch on/off. Each command takes under 2 seconds end-to-end.

**Why this is gated first:** The actuator chain is the boring part. You want it solid before any experimental signal-processing work points at it. If the lamp doesn't reliably respond to a curl command, no amount of EEG sophistication matters.

### 0.3 — Project repository

Create a git repository for the project. Single monorepo is fine for now (multiple language sub-projects are easier to manage in one repo at this scale than as separate repos). Initial structure:

```
baseline/
├── README.md
├── research_brief.md          (the v0.2 brief)
├── build_plan.md              (this document)
├── docs/
├── streamer-linux/            (Phase 1 deliverable)
├── core/                      (Phase 2 deliverable)
├── ambient-controller/        (Phase 3 deliverable)
├── music-controller/          (Phase 4 deliverable, optional/later)
├── probe-app/                 (Phase 5 deliverable, longest horizon)
├── streamer-android/          (Phase 6 deliverable)
└── data/                      (gitignored; durable local data)
```

Make the initial commit. Push to a private remote (GitHub, GitLab, or a Gitea instance on the Dell — your call).

**Exit criteria:** Repo exists, layout is in place, README explains the project in 3 paragraphs and links to the brief.

---

## Phase 1 — Linux streamer port (target: 1–2 weekends)

Port the macOS-only upstream `arctop/mw75-streamer` to Linux so you can stream EEG on the Steam Deck.

### Approach

The upstream streamer is Python. The macOS-specific code uses PyObjC to talk to Apple's IOBluetooth framework for the RFCOMM connection. The BLE handshake, packet parsing, checksum validation, ADC-to-µV conversion, and WebSocket/LSL output are all platform-neutral Python and can be reused as-is. What needs to change is the RFCOMM connection layer, which on Linux uses BlueZ.

The path: fork the upstream repo, identify the macOS-specific module(s), write a Linux equivalent using `pybluez` or direct AF_BLUETOOTH sockets, conditionally select the right backend at runtime based on platform, test against the mock mode first, then test against real hardware.

### Claude Code prompt for Phase 1

> Open the cloned fork of `arctop/mw75-streamer`. Read the README and the source tree to identify (a) the BLE activation handshake code, (b) the RFCOMM streaming code, (c) the packet parsing and output code. The first two are platform-specific (macOS via PyObjC); the third is platform-neutral.
>
> Our goal is to add Linux support without breaking macOS support. Propose a refactor that introduces a small backend abstraction for the Bluetooth I/O: a single interface with two implementations (a macOS implementation that wraps the existing PyObjC code, and a new Linux implementation using BlueZ via `pybluez` or direct AF_BLUETOOTH sockets). The platform-neutral code calls the abstraction.
>
> Before writing any code, save the proposed file structure and the abstraction interface as a docs file at `docs/streamer_port_plan.md` and stop. I will review it before you implement.
>
> Notes:
> - We are on Arch Linux (Steam Deck). BlueZ is available via the standard package manager. Use `pybluez` if it works cleanly; if not, use direct sockets.
> - The MW75 protocol uses BLE for activation, then Bluetooth Classic RFCOMM channel 25 for streaming. Both are needed.
> - Preserve the existing WebSocket JSON output schema exactly — downstream components depend on it.
> - I want the mock mode to keep working everywhere, since we'll develop downstream against the mock before connecting real hardware.

### Exit criteria

- The streamer's mock mode runs on the Steam Deck and produces the same WebSocket JSON output as the upstream macOS version (verified by connecting a WebSocket client and inspecting messages).
- Real hardware mode connects to the MW75, completes the activation handshake, and streams 500 Hz / 12 channels of data to the WebSocket output without packet loss for at least 10 minutes continuous.
- A small `README_LINUX.md` documents how to set up and run on Linux.
- A pull request back to the upstream `arctop/mw75-streamer` repo is open (whether or not it's accepted, the contribution-back gesture is appropriate).

### Risks for Phase 1

- BlueZ + RFCOMM has historically been quirky. If `pybluez` is unmaintained or broken on current Arch, fall back to direct sockets — Linux's `socket.AF_BLUETOOTH` supports RFCOMM natively in Python's standard library.
- The MW75 may have undocumented protocol quirks that didn't surface on macOS. If real-device testing reveals these, document them clearly; they're likely valuable to the upstream maintainers.

---

## Phase 2 — Sensing core (target: 4–6 weekends)

The shared signal-processing daemon that consumes the streamer's WebSocket output and produces baselines, deviations, and discrete state estimates. Everything downstream depends on this.

### 2.1 — Skeleton + mock-stream development

Build the core entirely against the streamer's mock mode first. Real-hardware integration is the last step of Phase 2, not the first. The reason is scientific hygiene: if the deviation scorer reports "off-baseline" on pure mock noise, the scorer is wrong, not the user.

### 2.2 — Modules to build (in order)

1. **Streamer client.** Connects to the streamer's WebSocket; reconnects on disconnect; logs raw samples to durable local storage (gzipped Parquet or similar — not CSV, this volume of data needs compressed columnar storage).
2. **Feature extractor.** 4-second windows, 87.5% overlap, bandpass + notch filter, ICA artefact rejection, Welch PSD, band powers, asymmetries, theta/beta ratio, spectral entropy. Output: ~100-dim feature vector per 0.5s.
3. **Baseline capture flow.** A CLI or simple web UI that walks through "3 min eyes-open, 2 min eyes-closed" capture, computes μ, Σ, IAF, stores with context metadata.
4. **Deviation scorer.** Mahalanobis distance against the active baseline; direction vector; smoothing.
5. **State estimator.** Small discrete classifier (start with rule-based; later, a small per-user trained model) over modes: deep work, light work, transition, winding down, off-baseline, unknown.
6. **Output API.** WebSocket and HTTP endpoints exposing current state, recent deviations, and historical summaries. This is what the surfaces consume.
7. **Logging.** Durable, encrypted-at-rest local storage of all features, states, baselines, and events. No cloud sync.

### Claude Code prompt for Phase 2

> We are building the sensing core for project Baseline. Read `research_brief.md` §2 and §3 fully before starting.
>
> Phase 2.1: Propose a Python project structure under `core/` for the modules listed in build_plan.md §2.2. Identify which Python libraries we'll use for each module (signal processing, ICA, storage, WebSocket I/O). Justify any non-obvious choices in one line each. Save as `docs/core_design.md` and stop.
>
> After I approve the design, build modules 1 through 4 in order, against the streamer's mock mode. Do not attempt module 5 (state estimator) until 1–4 work end-to-end against mock data and produce sensible (i.e., non-explosive, no false positives) outputs on pure noise.
>
> Commit after each module. Use descriptive commit messages. Show me the proposed test cases for each module before writing them.
>
> A few constraints:
> - Local-first. No cloud anything. All data stays on the Steam Deck (and later the Dell).
> - Durable storage in Parquet (or compressed Feather), not CSV. We'll have gigabytes of features eventually.
> - The WebSocket schema between core and surfaces is part of the public interface of this project. Define it in `docs/core_api.md` early and version it.
> - The feature extractor's output should be exactly reproducible given the same input. Determinism matters for the methodology papers.

### Exit criteria

- Mock stream → feature extractor → baseline capture → deviation scorer → state estimator all working end-to-end on the Steam Deck.
- On pure mock noise, deviation scores remain low and state stays in "unknown" — confirming no false positives.
- Real hardware integration verified: a 30-minute live capture from the MW75 produces stable feature output, a successful baseline capture, and sensible deviation behaviour over the session.
- `docs/core_api.md` is written and locked.
- All features, states, and events for the 30-minute test capture are persisted to local storage and queryable.

---

## Phase 3 — Ambient controller (target: 3–4 weekends)

The first user-facing surface. The smallest, lowest-risk, most-bounded application of the core.

### 3.1 — Mode → action mapping

Define the finite-state machine described in research_brief.md §4.3 as a configuration file (YAML or TOML). Modes are keys; actions are lists of Home Assistant API calls. Keep this user-editable; the user will tune it over weeks.

### 3.2 — Controller service

Subscribes to the core's state WebSocket; applies hysteresis and dwell-time rules; executes Home Assistant API calls when transitions occur; logs every action with reason.

### 3.3 — Override handling

The lamp can be toggled manually via its physical switch. The Home Assistant state will reflect that. The controller must detect "user override happened" and respect it for a configurable period. Sustained overrides feed back into a manual log the user can review weekly.

### 3.4 — Evaluation infrastructure

Twice-daily prompt (push notification or web UI) asking "did the room match your state?" with 5-point response. Stored alongside system logs for later ABA analysis.

### Claude Code prompt for Phase 3

> We're building the ambient controller for project Baseline. Read research_brief.md §4 and build_plan.md §3 fully before starting.
>
> Propose the controller's architecture as a single document at `docs/ambient_design.md`. Specifically I want to see (a) how the controller subscribes to core state, (b) the format of the mode→action configuration, (c) the hysteresis and dwell-time logic, (d) the override-detection logic, (e) the evaluation prompt mechanism. Stop after the design.
>
> Constraints:
> - The controller is a separate process from the core. They communicate over the core's WebSocket and HTTP API only.
> - Home Assistant integration uses the long-lived access token model. Store the token in an env var or a local secrets file, never in the repo.
> - The mode→action configuration must be hot-reloadable. I will tune it constantly during evaluation; restarting the service every time is unacceptable.
> - All actions, transitions, overrides, and prompt responses are logged to the core's event store via its HTTP API.

### Exit criteria

- The ambient controller runs as a service on the Dell (via systemd) and reliably executes mode transitions over a 7-day continuous test.
- Mode transitions respect dwell-time and hysteresis rules; no flicker observed in subjective use.
- User overrides are detected and respected; sustained overrides accumulate in a review log.
- At least 14 days of "did the room match my state?" responses are collected.
- A weekly summary report (markdown) can be generated showing modes, transitions, overrides, and user responses.

### What's deliberately not in Phase 3

- The ABA evaluation itself. That comes in Phase 7, after both the core and the controller have demonstrated week-long stability. Phase 3 just builds the infrastructure.
- Any machine learning of the mode classifier. v0.1 is hand-tuned. Learning waits for data.

---

## Phase 4 — Adaptive music controller (target: 3–4 weekends, deferred)

**Do not start Phase 4 until Phase 3 has been running stably for at least one month and you've decided the ambient surface is worth the maintenance overhead.** The music controller is the riskier surface, both technically and ethically, and the marginal value over the ambient surface alone is unclear until you have lived with the ambient surface for a while.

When you do start it:

- VLC HTTP interface for playback control.
- Local music library tagged with audio features (extract via `librosa`, or fetch Spotify Audio Features for tracks that exist in their catalogue).
- Rule-based control policy as the v0.1 (no learning).
- Conservative defaults: at most one intervention per 5 minutes; full transparency log; manual skip is a strong negative signal.
- Off-by-default; opt-in per session.

### Exit criteria

Same shape as Phase 3 — week-long stability, transparent action log, user-rated appropriateness data collected.

---

## Phase 5 — Metacognitive instrument (longest horizon)

Different timescale from the other surfaces. **Probe collection can start early** — even during Phase 2 — because daily probes are useful baseline data for evaluating everything else. But the **summary and therapist export features** wait until you have months of data and an actual conversation with the therapist about what would be useful.

### 5.1 — The probe app

A web app served by the core's HTTP endpoint. Accessible from any device on LAN. Walks through the daily probe sequence: key-phrase recitation (records voice), Stroop (records RTs), simple RT (records RTs), optional emotional images (records face AUs via webcam), optional free voice journal. ~5–10 minutes total.

### 5.2 — Probe runs early

Start running daily probes during Phase 2, even before the core's state estimator works, because the longitudinal probe data is independently valuable and produces a corpus for paper 1's reliability analysis.

### 5.3 — Therapist conversation before any summary work

Before building the weekly summary or therapist export, sit with your therapist (ideally in a session, or in a separate explicit "I want to talk to you about this tool" meeting). Show her the brief. Ask: what would be useful for her to see? What would be distracting? What concerns does she have about you using this kind of tool?

Whatever she says shapes the design. Do not build the summary feature against an imagined therapist's preferences; build it against your actual therapist's actual input. If she has reservations about the whole concept, take them seriously — she has clinical judgment you don't.

### 5.4 — Then build the summary

Weekly cadence. Markdown and PDF output. One-page front summary in plain language. Technical appendix optional. User selects what each export contains.

### Exit criteria

- Daily probe completion rate above 80% over 60 consecutive days (a real test of whether you'll actually use it).
- Within-subject test-retest reliability characterised for each modality (paper 1 data).
- At least one therapist conversation completed, documented, and reflected in the summary design.
- Weekly summary export works and has been used in at least three therapy sessions.

---

## Phase 6 — Android streamer port (deferred, optional)

Only undertaken if the Linux-on-Steam-Deck + Linux-on-Dell setup proves insufficient. The original motivation was "use anywhere"; in practice, your daily-driver is the Steam Deck and the Dell is always-on, so the Android port may not actually be needed.

If undertaken: Kotlin native, using `BluetoothSocket.createRfcommSocketToServiceRecord`, exposing the same WebSocket JSON schema. Packaged as an APK. Web UI is the same React code embedded via WebView.

---

## Phase 7 — Formal evaluations and paper drafting

Once the ambient controller has been running stably for at least a month and the core has demonstrated reliability, begin the formal pre-registered ABA evaluations described in research_brief.md §7. These are scheduled, pre-registered, and produce data for papers 1 and 3.

Paper 1 (sensing core methodology) is drafted in parallel with Phase 2 — the writing sharpens the design.
Paper 2 (probe protocol) is drafted in parallel with Phase 5 — same reason.
Paper 3 (ambient controller evaluation) is written after the Phase 7 evaluation completes.
Paper 4 (therapist integration) is the latest and requires the most external dependencies; treat it as a 18–24 month horizon paper.

---

## Working agreements with Claude Code

Carry these from the original prompt into every Claude Code session for this project:

- Read the brief at the start of every new session. The design is in the brief, not in chat history.
- Plan mode for design discussions; code mode only after a plan is agreed.
- Commit working states frequently, with descriptive messages.
- Never make a unilateral choice on a scientific or research-methodology question. Ask. Architecture choices (which Kotlin coroutine library, which Parquet engine) — your call, just tell me why in one line.
- Honest uncertainty over invented confidence. Bluetooth stacks and dry-electrode EEG both have undocumented quirks; expect them and surface them rather than papering over.
- Hardware safety: never auto-pair, never auto-enable EEG mode, never auto-set volume above a configurable safe ceiling, without an explicit user toggle.

---

## What gets cut if life intervenes

If the project loses momentum and you need to choose what to keep:

1. **Keep:** the homelab, Home Assistant, and the Linux streamer port. These have standalone value regardless of the rest of the project.
2. **Keep:** the daily probe protocol, if started early. The corpus alone is valuable to you and to a future paper 1.
3. **Cut first:** the adaptive music controller. It's the riskiest surface and the one with the closest existing-product competition.
4. **Cut second:** the Android port. Use the Steam Deck and Dell.
5. **Never cut without therapist consultation:** the metacognitive surface, once it's been introduced into the therapeutic relationship. Either commit to it or wind it down deliberately, but don't let it lapse silently.

---

*End of build plan v1.*
