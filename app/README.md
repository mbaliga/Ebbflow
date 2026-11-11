# Ebbflow Android app

The Android app performs MW75 discovery, BLE activation, RFCOMM capture, packet parsing,
signal-quality analysis, local persistence, and a Compose acquisition surface.

```text
Mw75StreamingService
  -> Mw75Controller
     -> BLE activation / RFCOMM transport
     -> :core-eeg-community PacketParser
     -> SignalQualityEstimator
     -> StreamHub + Room signal_quality_samples
```

Signal quality describes acquisition integrity only. It is not a focus, mood, diagnostic,
or clinical measurement. Advanced state estimates may later arrive through an optional Baseline
adapter and must remain separately labelled and versioned.

Build with Android SDK 35 configured in `local.properties`:

```bash
./gradlew :app:assembleDebug
```

Hardware validation still requires a paired MW75 and a real Android device. Verify activation,
reconnect, packet error rate, clipping, flatlining, sample cadence, permission revocation, and
cleanup after Bluetooth loss before treating capture as production-ready.

Porting beyond Android (plan only, nothing built): see [docs/porting_plan.md](../docs/porting_plan.md).
