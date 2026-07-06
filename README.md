# Ebbflow — the open EEG app

> **Constellation (D-S).** Ebbflow is the **open-source EEG application**: it captures EEG
> (from the MW75 via the [`mw75-streamer`](https://github.com/mbaliga/mw75-streamer) layer, or
> any future hardware), stores it locally, and drives the ambient / adaptive-music /
> metacognitive surfaces. It **licenses** the **Baseline engine** — the *proprietary*
> deviation-from-optimum + state-inference core (EEG parse + `FocusEstimator`), which lives in
> the private [`baseline`](https://github.com/mbaliga/baseline) repo, **not here**. `:app`
> consumes that engine as a licensed binary; until that seam is published, `:app` intentionally
> does not compile (the open app cannot ship the paid engine — see `settings.gradle.kts`).
> The vision docs below (`research_brief.md` / `build_plan.md`) describe the whole product and
> still use the pre-D-S "Baseline" framing; they will be split (engine → `baseline`).

**A personal state estimator with ambient, adaptive, and metacognitive surfaces.**

Baseline uses consumer-grade EEG (the Master & Dynamic MW75 Neuro — 12-channel temporal-site, dry-electrode) together with optional multimodal sensors (voice, face, HRV) to model one user's "normal" cognitive and affective state, detect meaningful deviations from that normal, and drive interventions in response. Its cultural anchor is the *Blade Runner 2049* post-traumatic baseline test — here deliberately inverted from an instrument of institutional control into a self-administered tool for self-knowledge, self-regulation, and, optionally, communication with the user's own clinician.

The system is built as one **shared sensing core** (streaming → feature extraction → contextual baselines → deviation scoring → a discrete state estimate) feeding three **independent surfaces**, each with its own risk profile, evaluation method, and development timeline: an **ambient controller** that lets the environment *follow* the user's state (lights, switches, desktop, do-not-disturb — low risk), an **adaptive music controller** that *modulates* state toward baseline (higher risk, opt-in), and a **metacognitive instrument** that runs standardised daily probes for longitudinal self-review and optional therapist export (slow cadence, high care). Because the surfaces are separate consumers of the core's output, each can be developed, evaluated, and published on its own.

This is a single-subject (N=1) longitudinal study run with phased ABA single-case designs per surface. It is **local-first** — all data stays on the user's own machines, encrypted at rest, with no cloud sync — and **open-source**, including the Linux daemon, the Android app, and a contributed Linux port of the upstream `arctop/mw75-streamer`. See **[`research_brief.md`](research_brief.md)** for the full design, scientific grounding, and ethics, and **[`build_plan.md`](build_plan.md)** for the sequenced engineering plan.
