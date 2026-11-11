# Ebbflow device checklist: Windows

Plan rows W1 to W3 in [porting_plan.md](porting_plan.md). Nothing here has been run. Every row is
`NEEDS-DEVICE-VALIDATION`.

**Evidence default.** The only Windows machine on record, the Dell, may become Linux (OQ-5, OQ-33). Until
the owner has a Windows machine, the evidence for this platform is `CI (hosted VM)` only: a build and
tests on a hosted runner prove nothing about install, Bluetooth or the screen. Those rows stay open.

**Scope note.** Ebbflow reports acquisition integrity only. It does not estimate focus or mental state.

| ID | Check | Pass when | Status |
|---|---|---|---|
| DV-WIN-01 | Install the unsigned MSI (plan W2; the package does not exist yet) | installs for the current user, app launches from the Start menu | NEEDS-DEVICE-VALIDATION |
| DV-WIN-02 | SmartScreen on an unsigned build | the warning appears, the owner can proceed via More info, and the README says to expect it | NEEDS-DEVICE-VALIDATION |
| DV-WIN-03 | Bluetooth RFCOMM capture through the bundled streamer, only if W3 lands (streamer PR #3, OQ-13). Experimental | a stream starts after Start is pressed and packets arrive; otherwise this row stays deferred | NEEDS-DEVICE-VALIDATION |
| DV-WIN-04 | Firewall prompt: the app and any loopback sidecar | no inbound prompt for non-loopback access; the app binds loopback only | NEEDS-DEVICE-VALIDATION |
| DV-WIN-05 | DPI scaling at 100%, 150% and 200% | layout and text stay usable at each scale | NEEDS-DEVICE-VALIDATION |
| DV-WIN-06 | Start-button-only streaming | nothing pairs, activates or streams until Start is pressed | NEEDS-DEVICE-VALIDATION |
| DV-WIN-07 | Uninstall | the app and any sidecar are removed; no leftover services or startup entries; local data handling is stated | NEEDS-DEVICE-VALIDATION |
