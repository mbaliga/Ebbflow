# Ebbflow

Ebbflow is the source-available, local-first EEG acquisition application. It owns device connection,
signal-quality checks, explicit raw-data capture, EEG features, probes, and opt-in ambient,
music, and neurofeedback experiences.

The application builds and captures data without a proprietary dependency:

- `:core-eeg-community` provides documented packet parsing and acquisition-quality checks.
- `:app` owns Android Bluetooth, storage, and the Compose acquisition surface.
- Baseline is an optional evidence-engine integration. It may return versioned estimates or
  explanations, but Ebbflow remains authoritative for EEG observations and quality flags.

Ebbflow does not infer focus from a theta/beta ratio. The old bring-up placeholder was removed;
the default UI reports packet integrity, clipping, flatlining, and usable-channel coverage only.

## Build

```bash
./gradlew :core-eeg-community:test
```

With Android SDK 35 configured in `local.properties`:

```bash
./gradlew :app:assembleDebug
```

The app is offline-first. Raw EEG storage is disabled unless the user explicitly enables it.
Advanced Baseline features must be optional and must preserve source, model version, uncertainty,
and evidence grade.

The older `research_brief.md` and `build_plan.md` are retained as historical research inputs;
they are not the current cross-repository architecture.

## Licence

Source-available, free for noncommercial use under the [PolyForm Noncommercial License 1.0.0](LICENSE). If you make money with it, you need a commercial licence: see [COMMERCIAL-LICENSE.md](COMMERCIAL-LICENSE.md).
