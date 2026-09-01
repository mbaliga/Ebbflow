# Baseline — Session Handoff

> Historical only. The placeholder-focus bring-up below is superseded by the
> `:core-eeg-community` signal-quality pipeline on the canonical Ebbflow branch.

*Generated 2026-06-03. The next session (or you, weeks from now) can resume from this.*

## Update 2026-06-03 — Android `:app` bring-up

The native Android focus app (Phase 6 territory, "ebbflow") now has its **`:app`
module**, built on the `:core-eeg` parser scaffold from PR #2. Work is on branch
`claude/android-app-bring-up-ZI7yd` (merged PR #2's scaffold in, then added `:app`).

- **`:core-eeg` (pure JVM, CI-tested):** added `FocusEstimator` + `BandPower` — a
  **placeholder** theta/beta → 0..1 focus index, recomputed at ~2 Hz. `./gradlew
  :core-eeg:test` → **13 tests pass** (7 parser + 6 focus). Verified here.
- **`:app` (Android, NOT build-verified — no SDK here, no hardware):** faithful port
  of the Python device layer — `Mw75DeviceFinder` (bonded/scan), `Mw75BleActivator`
  (BLE handshake), `Mw75RfcommConnection` (RFCOMM ch.25 via reflection + SPP
  fallbacks), `Mw75Controller` (lifecycle), `Mw75StreamingService` (foreground,
  type=connectedDevice), Room (`FocusSample`), and a Compose UI (`MainActivity`,
  `StreamScreen`, `StreamViewModel`, `StreamHub`).
- **Build wiring:** `:app` is included in `settings.gradle.kts` **only when an Android
  SDK is present**, so SDK-less CI still configures and builds `:core-eeg` alone.
  Versions pinned in `gradle/libs.versions.toml` (AGP 8.7.3 / Gradle 8.14.3).
- **Docs:** full module README at `app/README.md` (architecture, build, manual
  hardware verification, known risks).
- **Next:** build `:app` against the SDK and fix any compile fallout; pair an MW75 +
  phone and run the manual verification in `app/README.md`; confirm the sample rate
  empirically; later, replace the placeholder focus metric with the Phase 2 core.

---

## TL;DR
- All work to date is on branch `claude/tender-planck-pcYiy`; **PR #1 is open in draft** at https://github.com/mbaliga/baseline/pull/1, awaiting review/merge. **Nothing on `main` yet.**
- Two commits were ahead of `main` before this one:
  - `cd7b5e0` — Phase 0.3 scaffold (README, planning docs, repo layout, `.gitignore`).
  - `7a54a81` — Phase 1 streamer-port design (`docs/streamer_port_plan.md`).
- PR #1's title/body describe Phase 0.3 only; the Phase 1 design commit (and now this handoff + the runbook) was added afterwards. Expand PR #1's description, or split things into separate PRs — your call.
- Detailed Phase 0 runbook: `docs/phase_0_runbook.md`. Phase 1 design: `docs/streamer_port_plan.md`.

## What's in the repo now
- `README.md` — Baseline overview (replaces the old "ebbflow" stub).
- `research_brief.md` (v0.2), `build_plan.md` — canonical planning docs at root.
- Monorepo layout (per build_plan §0.3): `core/`, `ambient-controller/`, `music-controller/`, `probe-app/`, `streamer-linux/`, `streamer-android/`, `docs/`, `data/` (gitignored, `.gitkeep`-anchored).
- `.gitignore` covering `data/`, secrets, and Python/Node/OS artifacts.
- `docs/phase_0_runbook.md` — detailed Phase 0 runbook (0.1 install, 0.2 HA + smart devices, 0.3 repo scaffold, the <2 s test, exit criteria).
- `docs/streamer_port_plan.md` — Phase 1 Linux streamer port design.
- `docs/session_handoff.md` — this file.

## Phase status

| Sub-phase | What | Status |
|---|---|---|
| 0.1 | Encrypted headless Debian on the Dell (LUKS2 + TPM2 + recovery passphrase) | **Not started** — hardware-side; see `docs/phase_0_runbook.md` §0.1 |
| 0.2 | Home Assistant in Docker + smart devices controllable in <2 s | **Not started** — device strategy revised this session; see `docs/phase_0_runbook.md` §0.2 |
| 0.3 | Repo scaffold + README + planning docs | **Done** — in PR #1 (draft, awaiting merge) |
| 1   | Linux port of `arctop/mw75-streamer` (MW75 EEG over AF_BLUETOOTH) | **Design captured in `docs/streamer_port_plan.md`** (commit `7a54a81`); implementation not started |

## Key decisions locked
1. **Disk:** LUKS2 full-disk + TPM2 auto-unlock bound to PCR 7 + **recovery passphrase keyslot kept** (mandatory fallback).
2. **Distro:** Debian stable, headless install (verify current point release at install time).
3. **Home Assistant:** Docker container on the Dell (NOT HA OS — coexists with the Baseline core and ambient controller).
4. **Smart-home strategy (portable / lean / local):**
   - Plug-in / screw-in only; **no in-wall switches** (kit must travel).
   - **Matter-capable Wi-Fi** devices (no hub to carry; local & future-proof).
   - Reuse before buying: **try LocalTuya on the existing Qubo first (₹0)** before purchasing plugs.
5. **Shopping list:**
   - **Definite buy:** 1× **Tapo L530E** (E27, colour, Matter) ~₹1,000–1,500 — for the IKEA floor uplighter. *Confirm the lamp's socket is E27 first.* Alt: WiZ E27 (HA integration is fully local).
   - **Conditional:** 1–2× **Tapo P110M** (16 A, Matter) ~₹900 ea — only if LocalTuya on Qubo fails.
   - **Optional / recommended for the AC:** **BroadLink RM4 Mini** IR blaster ~₹1,800–2,500 — local in HA; doubles as a travel TV/AC remote.
6. **Add Tapo devices to HA via Matter**, not the legacy TP-Link cloud integration — keeps control purely local with no TP-Link login.
7. **Phase 1 streamer port (design only so far):** new stdlib AF_BLUETOOTH backend + platform factory; reuse the bleak BLE handshake, packet parsing, output servers, and WebSocket schema unchanged. Mock-first verification, then hardware on the Steam Deck. Fork-and-upstream workflow to `arctop/mw75-streamer`. Full detail in `docs/streamer_port_plan.md`.

## Critical caveats (don't lose these)
- **HA on the Dell does not travel.** At home it's the brain; on the road, control devices via vendor app / phone Matter controller on local Wi-Fi.
- **Smart devices need 2.4 GHz Wi-Fi.** Hotel networks (captive portals, client isolation, 5 GHz-only) often block them — a cheap travel router that broadcasts your own Wi-Fi bubble is the fix.
- **AC via smart plug = on/off only** and a compressor must not be rapidly power-cycled. Use an **IR blaster** for real control.
- **Match plug amperage:** an AC needs a **16 A** plug (P110M), not 6/10 A.
- **Tapo cloud-for-auth:** the legacy TP-Link HA integration uses cloud creds for auth even though control is local. **Adding Tapo via Matter bypasses this entirely.**
- **TPM auto-unlock can break** on firmware / Secure Boot / kernel changes (PCR drift). The **recovery passphrase** is what saves you — store it in a password manager and verify it still unlocks after enrolling the TPM.
- **Qubo / branded-Tuya:** the cloud "partition" may block local-key extraction; the LocalTuya gate is in Phase 0.2.
- **Audio↔EEG confound** is parked but tracked — the core will log audio state from Phase 2 so it can be quantified on real data.
- **No secrets in repo.** HA long-lived tokens and keys go to env / secrets files, never committed.

## Pending actions

**User-side:**
1. Review PR #1; decide whether to expand its description to cover everything on the branch (Phase 0.3 scaffold + Phase 1 design + this handoff + the runbook) or split things; then merge.
2. Confirm the IKEA floor uplighter is **E27** and order it + 1× **Tapo L530E**.
3. Run the **LocalTuya gate** on the existing Qubo (Tuya IoT Developer account → link Smart Life devices → pull local key + device ID → LocalTuya in HA) **before** buying plugs.
4. Decide AC approach (16 A plug vs BroadLink RM4 Mini) closer to actual use.
5. Begin Phase 0.1 (Debian install on the Dell) when ready; full steps in `docs/phase_0_runbook.md` §0.1.

**Claude-side (next session):**
- No code changes pending until 0.1/0.2 produce outputs or you choose to start the Phase 1 streamer implementation.
- If asked to resume: read this file → `research_brief.md` → `build_plan.md` → `docs/phase_0_runbook.md` → `docs/streamer_port_plan.md`. Pull `claude/tender-planck-pcYiy`. **Respect the harness branch policy — never push to `main` without explicit user permission.**

## How the user prefers to work
- Beginner Linux/HA, sharp learner — default to teaching-while-doing.
- Values local-first, control, and understanding over magic.
- Budget-conscious; "try free first" before purchases.
- Portability matters (may move home; travels often).

## Useful URLs (sources from this session)
- Tapo P110 16 A — TP-Link India: https://www.tp-link.com/in/home-networking/smart-plug/tapo-p110/
- Tapo P110 price history (Amazon.in): https://pricehistory.app/p/tapo-p110-16a-wi-fi-smart-plug-By2E5dG5
- Tapo P110M Matter + HA discussion: https://community.home-assistant.io/t/tapo-p110m-energy-monitoring/736975
- Tapo L530E E27 (Flipkart India): https://www.flipkart.com/tp-link-tapo-l530e-60w-e27-base-multicolor-wi-fi-smart-bulb/p/itm52ef5bd69921e
- BroadLink RM4 Mini (Amazon India): https://www.amazon.in/BroadLink-Hub-WiFi-Automation-Compatible-RM4/dp/B086VD96LH
- Home Assistant Broadlink integration (local): https://www.home-assistant.io/integrations/broadlink/
