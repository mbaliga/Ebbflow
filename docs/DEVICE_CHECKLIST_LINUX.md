# Ebbflow device checklist: Linux (Steam Deck)

Plan row L6 in [porting_plan.md](porting_plan.md). Written from the plan and `app/README.md`; nothing
here has been run. Every row is `NEEDS-DEVICE-VALIDATION` until the owner records a result.

**Device.** Steam Deck (Desktop Mode; Gaming Mode optional) with the owner's MW75. The Dell counts only
if the owner rules it Linux (OQ-5). Redmagic-Edge cannot reach a radio (no BlueZ under proot).

**Scope note.** Ebbflow reports acquisition integrity (usable, clipped and flatlined channels). It does
not estimate focus, mood or any mental state, and no row below asks for such a claim.

**How to record a result.** Replace the status with `PASS <date> <build id>` or `FAIL <date> <note>`
in the owner's copy. Until then every capture claim stays unproven.

| ID | Check | Pass when | Status |
|---|---|---|---|
| DV-LX-01 | Pair the MW75 from the shell with `bluetoothctl` (scan, pair, trust, connect) | headphones show Paired and Connected; no GUI helper needed | NEEDS-DEVICE-VALIDATION |
| DV-LX-02 | Activation: the BLE handshake switches EEG mode on. Needs the Linux desktop head and streamer sidecar (plan L1/L2), which do not exist yet | activation succeeds with no manual step beyond pressing Start | NEEDS-DEVICE-VALIDATION |
| DV-LX-03 | Ten minutes at 500 Hz, 12 channels, with no packet loss (exit criterion from `docs/streamer_port_plan.md`). Needs the streamer sidecar | 10 min stream; packet loss count is 0; cadence stays 500 Hz | NEEDS-DEVICE-VALIDATION |
| DV-LX-04 | Non-root RFCOMM spike: `CAP_NET_RAW` versus the BlueZ D-Bus Profile1 route, run as an unprivileged user. Needs a spike command that does not exist yet | one route works without root and the choice is written down | NEEDS-DEVICE-VALIDATION |
| DV-LX-05 | Flatpak sandbox on SteamOS: the packaged app can reach BlueZ and RFCOMM inside its permissions. Needs a Flatpak package that does not exist yet | stream starts from the sandboxed app, or the missing permission is named | NEEDS-DEVICE-VALIDATION |
| DV-LX-06 | Reconnect: switch the headphones off and on, and walk out of range and back, during a stream | the app shows the disconnect, reconnects or offers a Start press, and never claims data it did not receive | NEEDS-DEVICE-VALIDATION |
| DV-LX-07 | Cleanup after Bluetooth loss: turn Bluetooth off mid-stream | no crash; sockets closed; the status shows stopped; a later Start works | NEEDS-DEVICE-VALIDATION |
| DV-LX-08 | Start-button-only streaming: launch, resume from sleep and reconnect without pressing Start | no pairing, no EEG mode, no stream begins until Start is pressed | NEEDS-DEVICE-VALIDATION |
| DV-LX-09 | Wording audit of the running UI | signal quality is labelled acquisition integrity only; the app does not estimate focus or mental state anywhere | NEEDS-DEVICE-VALIDATION |
| DV-LX-10 | Window behaviour on the Deck screen (1280x800) and a docked external display: resize without restarting the stream | text stays readable, stream continues across resize | NEEDS-DEVICE-VALIDATION |
