# Baseline
## A Personal State Estimator with Ambient, Adaptive, and Metacognitive Surfaces

**Research Brief — v0.2**
**Date:** May 2026
**Primary hardware:** Master & Dynamic MW75 Neuro (12-channel temporal EEG, dry-electrode)
**Secondary sensors (optional, modality-dependent):** smartphone microphone (voice), webcam (face), wrist wearable (HRV)
**Actuators:** Home Assistant-integrated smart devices (smart bulb, smart switches), Linux desktop session, VLC / local music playback
**Software targets:** Linux daemon (Steam Deck → eventual homelab Dell), Android-native app with embedded web UI
**Methodology:** Single-subject longitudinal study with phased ABA/ABAB designs per surface
**Audience:** Internal research design document → foundation for a series of conference/journal submissions

---

## Abstract

This brief describes the design of *Baseline*, a personal state estimation system that uses consumer-grade EEG and optional multimodal sensors to model a single user's "normal" affective and cognitive state, detect meaningful deviations from that normal, and drive three categories of intervention: passive environmental adaptation, closed-loop music selection, and metacognitive self-observation. The system is grounded in three traditions: McEwen's allostatic-load theory of physiological adaptation, Picard's affective computing programme, and Russell's circumplex model of affect. The cultural anchor is the *Blade Runner 2049* "post-traumatic baseline test," reinterpreted as a self-administered metacognitive instrument rather than an instrument of institutional control. The work is structured as a shared sensing core feeding three distinct application surfaces, each of which can be developed, evaluated, and published independently. The intended outputs are open-source software (Linux daemon, Android app, contributed Linux port of the upstream streamer), a sequence of research papers covering protocol validation, multimodal probe design, closed-loop environmental adaptation, and patient-collected data as a therapeutic session aid, and a single-subject longitudinal dataset suitable for re-analysis by other researchers.

---

## 1. Conceptual Foundations

### 1.1 The Blade Runner Baseline Test as Cultural Anchor

The post-traumatic baseline test in *Blade Runner 2049* — in which Officer K rapidly recites fragments of Nabokov's *Pale Fire* while his physiological state is measured against a pre-established norm — descends from the original *Blade Runner*'s Voight-Kampff test, which monitored respiration, heart rate, eye movement, and blushing in response to provocative questions. The 2049 version inverts the original paradigm: rather than provoking emotion to detect it, the test demands emotional *stasis* under stress, and deviation from baseline constitutes failure.

Two aspects of the fictional test inform this project. First, the BR2049 test is administered through *standardised stimuli* — the same poem fragments, the same questions, every time — which is exactly what makes deviation interpretable rather than noisy. The same person reciting the same passage on consecutive days produces a controlled within-subject measurement that exposes affective and cognitive shifts the subject may not be able to articulate. Second, the original Voight-Kampff test measures *voice, face, and autonomic state*, not EEG. The genuine science behind the fictional instrument is multimodal behavioural and physiological measurement of response to standardised cues, not brainwave reading.

This project takes both of those insights seriously. It also explicitly inverts the framing: where the fictional baseline test is administered *to* the subject as an instrument of institutional control over their labour value, *Baseline* is self-administered and operates as a tool of self-knowledge, self-regulation, and — optionally — communication with the user's own clinician.

### 1.2 Scientific Foundations

**Allostatic load (McEwen).** The body has no single homeostatic setpoint; it adapts continuously through allostasis, and cumulative adaptive load produces measurable physiological signatures via the sympathetic-adrenal-medullary and hypothalamic-pituitary-adrenal axes. *Baseline* implements a real-time, single-subject analog of this measurement.

**Affective computing (Picard, 1997).** The foundational programme establishing that emotional and cognitive state are physiologically readable and, in principle, machine-regulatable. The closed-loop architecture in this project sits squarely in this tradition.

**Russell's circumplex model (1980).** Two-dimensional model placing affective state on orthogonal axes of valence (pleasant–unpleasant) and arousal (activated–deactivated). Almost all modern EEG-based and multimodal affect work operates in or around this 2D space.

**Digital phenotyping (Insel and colleagues, ~2017 onward).** The proposition that smartphone and wearable signals carry diagnostically meaningful information about mental health, particularly for depression and serious mental illness. *Baseline* is essentially a thoughtfully-designed digital phenotyping system with an active multimodal probe component layered on top of passive sensing.

**Measurement-based care.** A movement within psychiatry to administer validated brief instruments (PHQ-9, GAD-7) between sessions to give clinicians objective longitudinal data. The metacognitive surface of *Baseline* extends this tradition, replacing self-report questionnaires with richer multimodal signals while preserving the same goal: giving the therapeutic relationship better data to work with.

### 1.3 Why This Project Exists

The MW75 Neuro is a meaningful inflection point in consumer EEG: 12 channels at 500 Hz with 0–131 Hz bandwidth and True DC coupling, in a form factor users will wear all day. The arctop open-source streamer exposes the raw signal stream, removing dependence on Neurable's proprietary processing pipeline.

Critically, the headphones' own consumer app uses the EEG in a way the user — who is the developer of this project, the only subject of this study, and a real user of the device for its intended purpose — found counter-intuitive: music volume rises as focus rises, which makes the feedback itself a distractor and trains the user to perform for the system rather than to use the system as a passive observer. This project deliberately takes a different approach: the system *follows* the user's state and adapts the user's environment to it, rather than asking the user to chase a target state through audio feedback.

Three categories of intervention are explored, in order of increasing risk and complexity:

- **Ambient.** Lights, dark mode, do-not-disturb, smart appliances *follow* the user's state. The system confirms rather than steers.
- **Adaptive.** Music selection *modulates* the user's state. The system steers, carefully and with hysteresis.
- **Metacognitive.** Standardised daily probes — voice recitation, cognitive tasks, optionally face — produce longitudinal records of deviation that the user, and optionally the user's therapist, can review.

---

## 2. Hardware Reality and Honest Limitations

### 2.1 What the MW75 Neuro Provides

- 12 EEG channels via soft fabric dry electrodes embedded in the earcups
- 500 Hz sampling, µV precision, 0–131 Hz bandwidth, True DC coupling
- Bluetooth-based protocol: BLE handshake for activation, then RFCOMM (Bluetooth Classic) streaming on channel 25, delivering 63-byte packets with checksum validation
- ~10 hours of EEG + ANC battery life on the original; ~13 hours EEG-only on the LT variant

### 2.2 The Electrode Placement Constraint

Electrodes sit over the temporal lobes via the earcups, not over the frontal cortex. The principal consequence is that **Frontal Alpha Asymmetry (FAA)** — the most-cited EEG correlate of emotional valence in the literature — is not accessible on this hardware. This is not catastrophic for two reasons. First, recent multiverse analyses across multiple studies have found FAA's reliability as a valence marker is weaker than the literature suggests, with only a small minority of analytical pipelines producing significant effects. Second, temporal-site EEG carries its own validated emotion- and attention-relevant signatures, principally temporal theta asymmetry (linked to emotional regulation and anxiety), beta and low-gamma power (cortical arousal), and auditory-cortex alpha (relevant precisely because music is the actuator in one of the surfaces). The right framing for this hardware is *within-subject deviation in a temporally-derived spectral feature space* — not absolute emotion classification.

### 2.3 Other Honest Limitations

- Dry-electrode SNR is worse than wet/gel; movement artefacts (chewing, jaw clench, head turns) produce signals orders of magnitude larger than neural activity.
- Inter-session positioning of the headphones produces signal drift; per-session re-baselining is required.
- No frontal coverage means classical ERPs at Fz/Cz/Pz are not directly accessible.
- Single-subject inferences do not generalise; this is an N=1 design, by intention.

### 2.4 What Can Be Reliably Extracted

| Feature | Band | Interpretation |
|---|---|---|
| Temporal alpha power | 8–13 Hz | Cortical inhibition; inverse of local activation |
| Temporal theta asymmetry | 4–8 Hz | Emotional regulation; left > right associated with anxiety |
| Beta power | 13–30 Hz | Cortical arousal; sympathetic activation proxy |
| Low gamma | 30–50 Hz | Active cognitive engagement; binding |
| Theta/beta ratio | — | Attentional control |
| Individual Alpha Frequency (IAF) | — | Stable within-subject trait; per-session normalisation |
| Spectral entropy | — | Signal complexity; correlates with arousal and cognitive load |

### 2.5 The Multimodal Sensors

The metacognitive surface depends on standardised probes whose signal extraction is well-established outside EEG:

- **Voice features** during key-phrase recitation: F0 mean and variability, jitter, shimmer, harmonics-to-noise ratio, speech rate, MFCCs (openSMILE or parselmouth).
- **Facial action units** during probe sessions: FACS AUs via py-feat or OpenFace at 5–10 Hz.
- **HRV** from any wrist wearable with an SDK exposing inter-beat intervals: validated against ECG in published comparisons (concordance correlation coefficients above 0.98 for Apple Watch against Polar H7 in lab conditions, though real-world accuracy degrades).
- **Behavioural** measures from cognitive probes: simple reaction time, Stroop interference, n-back accuracy and RT variability.

---

## 3. System Architecture: Shared Core, Three Surfaces

### 3.1 Architecture Overview

```
                  ┌────────────────────────────────────────────┐
                  │         Personal State Core                │
                  │                                            │
                  │   ┌─────────────────┐   ┌──────────────┐  │
                  │   │ Streaming layer │──▶│  Feature     │  │
                  │   │ (MW75, HRV,     │   │  extractor   │  │
                  │   │  voice, face)   │   └──────┬───────┘  │
                  │   └─────────────────┘          │           │
                  │                                ▼           │
                  │   ┌─────────────────┐   ┌──────────────┐  │
                  │   │ Baseline store  │◀──│  Deviation   │  │
                  │   │ (per context,   │   │  scorer      │  │
                  │   │  per session)   │   │  d(t),       │  │
                  │   └─────────────────┘   │  vec(t),     │  │
                  │                         │  state(t)    │  │
                  │                         └──────┬───────┘  │
                  │                                │           │
                  │   ┌──────────────────────────────────────┐│
                  │   │   Logging & event store              ││
                  │   │   (all features, all events,         ││
                  │   │    all interventions, durable)       ││
                  │   └──────────────────────────────────────┘│
                  └────────────────┬───────────────────────────┘
                                   │ state(t), events, summaries
                ┌──────────────────┼───────────────────┐
                ▼                  ▼                   ▼
       ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
       │  AMBIENT       │ │  ADAPTIVE      │ │  METACOGNITIVE │
       │  CONTROLLER    │ │  MUSIC         │ │  INSTRUMENT    │
       │                │ │  CONTROLLER    │ │                │
       │  Home Assist   │ │  VLC HTTP      │ │  Probe runner  │
       │  Linux session │ │  Library tags  │ │  Weekly report │
       │  Phone DND     │ │  (Spotify/     │ │  Therapist     │
       │                │ │   librosa)     │ │  export        │
       └────────────────┘ └────────────────┘ └────────────────┘
        (environment-       (state-changing    (longitudinal,
         following,         intervention,      reflective,
         low risk)          higher risk)       high care)
```

The core is one system. The surfaces are independent consumers of the core's outputs. Each surface can be developed, evaluated, and published independently. The reason for this separation is that the surfaces have very different risk profiles, evaluation methods, and design constraints, and bundling them at the application layer would force compromises on all three.

### 3.2 Streaming Layer

The upstream Python streamer (arctop/mw75-streamer) provides BLE activation handshake, RFCOMM packet parsing, checksum validation, ADC→µV conversion, and output via WebSocket JSON, LSL, CSV, or in-process callbacks. The original is macOS-only because RFCOMM is implemented via PyObjC against Apple's IOBluetooth framework. The protocol itself is documented and platform-neutral.

This project ports the streamer to Linux first (BlueZ-based, contained engineering work, contribution-back to upstream) and to Android second (Kotlin native, larger but produces a deployable mobile streamer). iOS/iPadOS is explicitly out of scope: Apple does not allow third-party apps to open Bluetooth Classic RFCOMM connections to non-MFi-certified devices, and the MW75 is not MFi-certified for Classic data channels. A future iOS path requires Apple cooperation or Master & Dynamic's MFi certification of the EEG channel.

### 3.3 Feature Extraction

Standard real-time EEG pipeline with 4-second windows and 87.5% overlap (0.5 s hop):

1. Bandpass 1–45 Hz; notch filter at line frequency.
2. ICA-based artefact rejection for ocular and muscular components.
3. Welch's method for PSD per channel.
4. Per-channel band powers (delta, theta, alpha, beta, low gamma).
5. Cross-channel features: hemispheric asymmetry, theta/beta ratio, spectral entropy.
6. Output: ~80–100 dimensional feature vector per 0.5 s.

For HRV: 5-minute rolling windows of RMSSD, SDNN, pNN50, LF/HF.
For voice (probe only): full openSMILE eGeMAPS feature set per recitation.
For face (probe only): py-feat AU intensities per frame, summarised per recitation.

### 3.4 Baseline Capture and Storage

Baselines are *contextual* rather than universal. The user accumulates multiple baselines over time — "focused morning work," "winding down evening," "calm weekend" — each with a stored mean μ, covariance Σ, and rich context metadata (time of day, sleep duration, caffeine, prior activity, ambient noise). The system selects the active baseline by context match rather than treating "feel good" as a single point.

Each baseline capture runs the same protocol: 3 minutes eyes-open, 2 minutes eyes-closed, optional 2-minute cognitive probe battery. IAF is computed and stored per session for normalisation against headphone-repositioning drift.

### 3.5 Deviation Scoring and State Estimation

For each window: Mahalanobis distance d(x) against the active baseline distribution, plus the direction vector preserving information about *which* features deviate. d(t) is smoothed with a 10–15 s exponential moving average to filter transient artefacts.

The "state" exposed to downstream surfaces is not a continuous scalar — it's a discrete categorical estimate over a small set of modes (deep work, light work, transition, winding down, off-baseline, unknown). Continuous outputs are available to surfaces that want them, but the categorical estimate is the default interface. The reason: discrete modes are vastly easier to live with for the ambient surface (lights cycling smoothly across a continuum is annoying; lights snapping between three or four states with hysteresis is liveable). The discrete classifier is trained on user-labelled baselines plus opportunistic on-line labels from explicit user mode declarations.

### 3.6 Logging

Everything is logged, durably, locally, encrypted at rest. Raw features (downsampled), state estimates, baselines, interventions, user labels, probe results. No cloud sync by default. The user owns the data; the user controls the export. This is non-negotiable both for ethical reasons (mental-state data is among the most sensitive a person can produce) and for research reasons (the corpus is the basis for all four papers in the publication plan).

---

## 4. Surface 1: Ambient Controller

### 4.1 Purpose

The ambient controller adapts the user's environment to follow their state. It does not attempt to change state. It confirms.

### 4.2 Actuators (v0.1, given current hardware)

- Smart bulb: colour temperature, intensity
- Smart switch (lamp): on/off
- Smart switch (AC): on/off
- Linux desktop (Steam Deck workstation, later homelab Dell): GTK/KDE colour scheme, terminal theme, notification daemon
- Phone: Do Not Disturb state, ringer mode

### 4.3 Modes (v0.1)

A small finite-state machine, not a continuous mapping. Approximately:

- **Deep work:** bulb cool white at high intensity, lamp off, AC on if ambient temperature outside comfort band, desktop dark mode, phone DND.
- **Light work / transition:** bulb neutral, lamp on low, desktop default, phone normal.
- **Winding down:** bulb warm and dim, lamp warm, AC off, desktop light mode (or warmer dark theme), phone normal.
- **Off-baseline (sustained):** no environmental action; surface only logs and optionally prompts a probe session.

### 4.4 Design Constraints

- **Hysteresis.** Sustained state changes (>2 minutes) trigger transitions; transient changes do not.
- **Minimum dwell time.** Once a mode is active, it cannot change again for at least 5 minutes. The room must not flicker.
- **User override.** Any actuator change can be overridden manually and the override sticks for a configurable duration (default 1 hour). The system learns from sustained overrides.
- **Slowness as a feature.** The environment should follow state with a comfortable lag, not chase it in real time. Minute-scale, not second-scale.

### 4.5 Evaluation

A controlled phased single-case design (ABA) over six weeks. Outcomes: time spent in self-reported "right environment for the task" state, frequency of manual environmental adjustments, sleep quality (self-report and wearable), and unstructured weekly subjective evaluation. The ambient surface is the easiest to evaluate because the actuator outputs are observable in the home automation logs and the user's "did the environment match my state" judgement is reasonably retrievable in retrospect.

### 4.6 Risks

- **Goodhart's Law of self-tracking.** The user starts performing for the system (noticing the lights warm and inferring something about their state). Mitigation: keep changes slow, keep the ambient surface running in the background rather than visible.
- **Annoyance fatigue.** Wrong mode at the wrong time becomes irritating. Mitigation: easy override, sustained overrides feed back into the mode classifier.
- **Home Assistant downtime.** A core dependency. Mitigation: graceful degradation; the streamer and core run regardless of whether actuators are reachable.

---

## 5. Surface 2: Adaptive Music Controller

### 5.1 Purpose

Closed-loop selection of music intended to modulate the user's state toward baseline when deviation is detected. This is a state-changing intervention with a higher risk profile than the ambient surface.

### 5.2 Actuator

VLC HTTP interface for local playback. Library tagged with Spotify Audio Features (valence, energy, danceability, tempo, acousticness, instrumentalness) where available, or with locally-computed equivalents via librosa.

### 5.3 Control Policy (v0.1)

Rule-based, intentionally simple. Examples: high-arousal deviation triggers a search for lower-energy lower-tempo tracks; sustained off-baseline beyond a configurable threshold switches to a user-designated "rescue playlist"; the policy does not change tracks more than once every several minutes regardless of signal.

The control policy is a deliberate placeholder. A core empirical question is whether the policy should remain hand-engineered, learn via contextual bandits from the user's manual interventions, or learn via offline imitation. v0.1 establishes the infrastructure; later versions explore policy learning.

### 5.4 Design Constraints

- **Conservative defaults.** The system intervenes rarely. False positives (intervening when the user is fine) are worse than false negatives (failing to intervene when the user is off-baseline) because the former undermine user trust quickly.
- **Transparent action log.** Every intervention is logged with reason; the user can review at any time.
- **Manual override is sacred.** If the user skips a track the system selected, that skip is a strong negative signal; the system does not immediately re-select something similar.
- **Off-by-default.** The music controller does not activate unless explicitly enabled per session.

### 5.5 Evaluation

ABA single-case design as in the original brief. Primary outcomes: return-to-baseline latency, user-rated appropriateness of interventions, manual-skip rate of system selections vs. user selections. Secondary: self-reported affect (PANAS/SAM), comparison to surface 1 (does adding music modulation improve outcomes beyond ambient adaptation alone?).

### 5.6 Risks

- **Subtle self-manipulation.** The user has built a system that nudges their own affect using their own music library. The user may not consciously notice direction or magnitude of this nudging. Mitigation: full transparency in the intervention log; periodic audit; conservative thresholds; sustained user-controlled off switches.
- **Auditory cortex confound.** The actuator (music) is delivered through the same headphones the sensor (EEG) is on. Auditory-cortex channels respond directly to the stimulus. Mitigation: document explicitly; analyse auditory-cortex channels separately; treat music-onset windows as covariate-controlled.
- **Music ruins music.** Over-instrumented music listening may degrade the user's relationship with music. Mitigation: surface is opt-in per session; "library safe mode" excludes specific artists/playlists from algorithmic selection.

---

## 6. Surface 3: Metacognitive Instrument

### 6.1 Purpose

A daily standardised probe protocol that captures the user's response to fixed stimuli across multiple modalities, with longitudinal review and optional therapist export. This is the surface most directly inspired by the Voight-Kampff / BR2049 baseline test, and it operates at a different timescale and risk profile from the ambient and adaptive surfaces.

### 6.2 The Probe Protocol

Approximately 5–10 minutes, run once per day at a consistent time. Components:

- **Key-phrase recitation.** A fixed passage of approximately 30–60 seconds spoken at conversational pace. Default suggestion: a fragment of Nabokov's *Pale Fire* ("cells interlinked within cells interlinked") as direct homage; user-selectable. Voice features extracted in full. EEG passively co-recorded.
- **Stroop task (short version).** ~60 seconds. Measures attentional control.
- **Simple reaction time.** ~60 seconds. Measures processing speed and RT variability.
- **Optional: emotional images.** Standardised set (e.g., a subset of the IAPS or OASIS image databases used in affect research). Face AUs recorded via webcam; EEG co-recorded.
- **Optional: free voice journal.** 60-second unstructured spoken reflection. Voice features extracted; transcript optionally generated locally with Whisper. The free journal is *not* analysed for content by default; the user retains sole access to the transcript unless they choose to export.

The probe is the scientific heart of this surface and arguably of the whole project. Its standardisation is what makes longitudinal deviation interpretable.

### 6.3 Longitudinal Review

Weekly summary, not daily firehose. The default cadence is one summary per week, available to the user as a dashboard and as an exportable PDF/markdown report. The summary reports:

- Trend in within-subject deviation across modalities.
- Voice-feature deviations from voice baseline.
- Cognitive probe performance trends (RT, Stroop interference, n-back accuracy).
- Notable correlations across modalities for that week.
- Free-form events the user logged during the week.

Deliberately absent: any claim about what these patterns "mean" diagnostically, any score or grade, any alert. The instrument reports; it does not interpret. Interpretation is for the user and the user's therapist together.

### 6.4 Therapist Export

A dedicated export format designed for clinician use:

- One-page summary at the front.
- Plain-language descriptions of any deviations, with the user's own annotations.
- No raw signal, no spectral plots, no jargon — those are available in a technical appendix the user can include or exclude.
- The user controls what each export contains; nothing is shared without explicit per-export selection.

The therapist export is co-designed with at least one clinician before it ships, ideally the user's own. The clinician's input determines what is and isn't useful, what would help session planning vs. what would distract from the therapeutic relationship.

### 6.5 Design Constraints

- **Slow cadence by design.** No real-time alerts. No daily score. The metacognitive surface is for reflection, not monitoring.
- **User-owned data, fully.** Nothing leaves the device without explicit per-export action.
- **No diagnostic claims.** The system describes deviations; it never names conditions.
- **Therapist veto.** Features that interact with the therapeutic relationship are designed with clinician input, and the clinician can veto specific features.
- **Opt-in everything.** The image probe, the free voice journal, the webcam capture, the cognitive probes — each is independently optional. The minimum viable probe is just the key-phrase recitation plus a Stroop, both of which require only the microphone and screen.

### 6.6 Evaluation

The metacognitive surface is the hardest to evaluate empirically because its outcomes are subjective and long-horizon. The evaluation strategy is mixed-methods:

- **Within-subject test-retest reliability** of probe features across days, weeks, and months — establishes that the instrument is measuring something stable enough to detect deviation.
- **Qualitative interview with the therapist** about whether the exported summaries change session content, planning, or outcomes. Conducted at three months and again at six months. This is the closest the project can come to a clinical efficacy claim without a controlled trial.
- **User reflective journal** kept separately about whether the instrument changed self-awareness, journaling practice, or other self-observation habits.

### 6.7 Risks

- **Self-monitoring intensifies rumination.** The clinical literature on self-tracking in depression and anxiety is mixed; for some users, increased pattern awareness helps, for others it deepens rumination and avoidance. Mitigation: weekly summary only, not daily; therapist consulted before the instrument is used in the context of active therapy; user has an easy "pause for two weeks" option.
- **The instrument replaces articulation.** The therapeutic relationship is built on the client's own articulation. A device that reports state could substitute for the work of putting feelings into words. Mitigation: the instrument is positioned explicitly as a session aid, never as a replacement for self-report; the free voice journal is content-private and exists to *encourage* articulation, not bypass it.
- **The developer is the subject.** Self-experimentation in mental health requires special care. Mitigation: pre-registered protocol, therapist involvement from before any probe data is captured, no public release of personal data, willingness to abandon the surface or the whole project if it produces harm.

---

## 7. Experimental Protocol

### 7.1 Design

Single-subject (N=1) longitudinal study with phased ABA single-case designs per surface. The three surfaces are evaluated independently and on different timescales:

- Ambient controller: 6 weeks per arm (A baseline → B intervention → A withdrawal).
- Adaptive music controller: 6 weeks per arm, started only after the ambient evaluation is complete and the core's stability is established.
- Metacognitive instrument: continuous daily probes from the start of the project, but formal evaluation milestones at 3 and 6 months. Probes also serve as a stable measurement substrate for evaluating the other surfaces.

### 7.2 Pre-Registration

Each surface's evaluation phase is pre-registered on OSF before its B phase begins. The methodology papers for surfaces 1 and 2 (see §9) are drafted before the empirical work, so that the protocol is locked and reviewed before data collection produces results that might tempt unprincipled analysis decisions.

### 7.3 Outcome Measures

Surface 1 (ambient):
- Primary: user-rated appropriateness of environmental state ("did the room match my state") on a 5-point scale, sampled twice daily.
- Secondary: frequency of manual override; sleep quality (self-report); wearable-based sleep metrics if available.

Surface 2 (adaptive music):
- Primary: return-to-baseline latency after off-baseline events.
- Secondary: skip rate of system selections vs. user selections; user-rated intervention appropriateness; SAM ratings of affect.

Surface 3 (metacognitive):
- Primary: within-subject test-retest reliability of probe features (ICC).
- Secondary: cross-modal concordance; therapist-reported usefulness of summaries (qualitative); user-reported impact on self-awareness.

### 7.4 Analysis

Single-case designs use specialised statistics: visual analysis with phase boundaries marked, Tau-U non-overlap with baseline trend correction, multilevel models with time-of-day covariates if data density permits, permutation tests for phase comparisons. Methodology papers establish the analytical plan before B phases begin.

### 7.5 Ethics and Self-Experimentation

- Pre-registered protocol per surface.
- Transparent reporting of negative results, including any decisions to abandon a surface.
- Therapist consulted from before the metacognitive surface enters use.
- No public release of raw personal signals; only derived analyses and aggregate statistics.
- Willingness to halt the project if the metacognitive surface produces measurable harm in the therapeutic context.
- Acknowledgement of the developer-as-subject conflict; mitigation through pre-registration and external review of methods papers before empirical work.

---

## 8. Cross-Platform Engineering

### 8.1 Target Platforms

- **Linux (Steam Deck, then homelab Dell):** primary daemon target. The Steam Deck is the developer's daily-driver workstation; the Dell is the future always-on homelab server.
- **Android:** mobile streaming and probe app. Kotlin-native.
- **Web UI:** React/Vue, embedded in the Android app via WebView, and reachable from any device on the LAN pointing at the daemon's WebSocket and HTTP endpoints.

### 8.2 What's Not Supported

- **iOS / iPadOS:** Apple prohibits third-party Bluetooth Classic RFCOMM to non-MFi-certified devices. The MW75 is not MFi-certified for the EEG channel. No viable path without vendor cooperation.
- **Pure web (Web Bluetooth):** Web Bluetooth supports only BLE GATT, not Bluetooth Classic RFCOMM. The MW75 EEG stream is RFCOMM. A pure-browser implementation is not possible.

### 8.3 Streamer Port

The upstream `arctop/mw75-streamer` is Python with macOS-specific Bluetooth Classic implementation via PyObjC. Port plan:

- **Linux port (Python + BlueZ):** rewrite the macOS-specific connection block using BlueZ via `pybluez` or direct sockets. Contained engineering work, contribution-back to upstream. Estimated effort: small.
- **Android port (Kotlin):** Android's `BluetoothSocket.createRfcommSocketToServiceRecord(UUID)` provides native RFCOMM. Reuse the documented protocol byte layout. Expose the same WebSocket JSON schema as the upstream Python streamer for downstream compatibility. Estimated effort: moderate.

### 8.4 Deployment

The shared sensing core runs as a Linux daemon, initially on the Steam Deck workstation, eventually on the homelab Dell. Surfaces are implemented as separate services that subscribe to the core's outputs:

- Ambient controller: Python service, calls Home Assistant REST/MQTT.
- Adaptive music controller: Python service, calls VLC HTTP.
- Metacognitive instrument: web UI (React) served by the core's HTTP endpoint, accessible from any device on LAN; Android app embeds the same UI in a WebView.

---

## 9. Publication Strategy

A sequence of focused papers, each defensible on its own, cumulatively building a body of work.

### Paper 1 — Methodology: within-subject baseline estimation from temporal-site consumer EEG
Establishes the sensing core's reliability. Small N (developer + 2–3 collaborators with matching hardware). Pre-registration, test-retest, characterisation of artefact regime. Foundation for everything downstream. Target venue: *Frontiers in Human Neuroscience* (BCI section) or similar.

### Paper 2 — Methodology: a multimodal Voight-Kampff-inspired daily probe for personal state monitoring
Defines the probe protocol, demonstrates within-subject reliability across modalities, characterises the cross-modal information structure. Cultural framing here lands well — the BR2049 reference is honest and useful for situating the work. Target venue: *IEEE Transactions on Affective Computing* or ACII proceedings.

### Paper 3 — Applied: closed-loop environmental adaptation driven by personal state estimation: a single-case study
The ambient controller as a fully evaluated intervention. ABA design, six months of data, formal single-case statistics. The most empirically substantive paper. Target venue: *PLOS One* or *Journal of Medical Internet Research*.

### Paper 4 — Mixed-methods: patient-collected multimodal data as a therapeutic session aid
Requires the therapist as collaborator and IRB review. Qualitative interviews plus quantitative session-content analysis. Highest-impact paper, longest timeline, most external dependencies. Target venue: a digital-mental-health journal such as *JMIR Mental Health*.

The adaptive music controller is treated as a product feature rather than a paper unless the personalisation methodology proves novel enough to merit its own writeup.

Methodology papers (1 and 2) are written *before* the empirical evaluations (3 and 4), so that analytical plans are reviewed and locked before results exist to bias interpretation.

---

## 10. Key References

- Ehrlich, Agres, Guan, Cheng (2019). Closed-loop music-based BCI for emotion mediation. *PLOS One*. — Closest methodological precedent for the adaptive music surface.
- Picard, R. (1997). *Affective Computing*. MIT Press. — Foundational text.
- Russell, J. A. (1980). A circumplex model of affect. *Journal of Personality and Social Psychology*. — Valence-arousal frame.
- McEwen, B. S. (1998). Stress, adaptation, and disease: Allostasis and allostatic load. *Annals NYAS*. — Theoretical frame.
- Smith, Reznik, Stewart, Allen (2017). Assessing and conceptualizing frontal EEG asymmetry. *International Journal of Psychophysiology*. — Honest treatment of FAA's limitations.
- Insel, T. R. (2017). Digital phenotyping: technology for a new science of behavior. *JAMA*. — Foundational digital phenotyping piece.
- Onnela, J.-P. (2021). Opportunities and challenges in the collection and analysis of digital phenotyping data. *Neuropsychopharmacology*. — Recent state of the field.
- Fortney, J. C., et al. (2017). A tipping point for measurement-based care. *Psychiatric Services*. — Measurement-based care frame for paper 4.
- Trull, T. J., & Ebner-Priemer, U. W. (2013). Ambulatory assessment. *Annual Review of Clinical Psychology*. — Methodological foundation.
- Hernando, D., et al. (2018). Validation of the Apple Watch for heart rate variability. *PMC*. — HRV sensor validation reference.
- Apicella, et al. (2024). Music in the loop: systematic review of neurofeedback methodologies using music. *Frontiers in Neuroscience*. — Recent review.
- Kazdin, A. E. (2019). Single-case experimental designs. — Methodological reference for the evaluation strategy.

---

## 11. Open Questions

1. **The discrete vs. continuous state interface.** v0.1 exposes a small categorical state estimate to surfaces. Is this the right primary interface or should surfaces consume continuous deviations directly? Probably mode-dependent.

2. **Where does "baseline" actually live?** A single point, a distribution, a manifold of acceptable states, or a trajectory that varies across the day? The contextual-baselines design hedges; the data will clarify.

3. **Does adding EEG to a multimodal probe improve anything?** A genuinely open empirical question, and the comparative analysis in paper 2 is its answer. A "no" result would be valuable in itself.

4. **Goodhart's Law of self-tracking.** Long-running self-administered systems risk the user performing for the measurement. Multiple mitigations are designed in; the project's longitudinal viability is the test of whether they work.

5. **The relationship between the three surfaces.** Run all three at once or stage them? Current plan is staged (ambient first, music second, metacognitive throughout). Whether the surfaces interfere with each other in evaluation is an empirical question for Phase 1.

6. **Therapist co-design.** What does the therapist actually want from this? The whole metacognitive surface depends on her input. That conversation precedes any code.

---

*End of brief v0.2.*
