# Ebbflow device checklist: macOS

Plan rows M1 and M2 in [porting_plan.md](porting_plan.md). Nothing here has been run.

**No Mac is on record** (OQ-31: a Mac mini chosen, not yet bought), so every row is
`NEEDS-DEVICE-VALIDATION` and no macOS claim may be made from CI alone. A hosted macOS runner can show
that something builds. It cannot show Gatekeeper, TCC or Bluetooth behaviour.

**Scope note.** Ebbflow reports acquisition integrity only. It does not estimate focus or mental state.

| ID | Check | Pass when | Status |
|---|---|---|---|
| DV-MAC-01 | Open an unsigned dmg (plan M1; not built yet) | Gatekeeper blocks it as expected and the documented override (right-click Open, or System Settings) works | NEEDS-DEVICE-VALIDATION |
| DV-MAC-02 | TCC Bluetooth prompt is attributed to the parent app, with the `NSBluetoothAlwaysUsageDescription` text shown | the prompt names Ebbflow, not a helper or Terminal; denying it is handled in the UI | NEEDS-DEVICE-VALIDATION |
| DV-MAC-03 | IOBluetooth RFCOMM through the bundled macOS helper (plan M2; not built yet) | a stream starts after Start is pressed and packets arrive | NEEDS-DEVICE-VALIDATION |
| DV-MAC-04 | Start-button-only streaming | nothing pairs, activates or streams until Start is pressed | NEEDS-DEVICE-VALIDATION |
| DV-MAC-05 | Menu bar: standard App menu items (About, Quit, Hide) work and Cmd-Q stops the stream and the helper | no orphan helper process remains | NEEDS-DEVICE-VALIDATION |
| DV-MAC-06 | Uninstall by dragging the app to Trash | no leftover launch agents or helpers; local data location is stated | NEEDS-DEVICE-VALIDATION |
