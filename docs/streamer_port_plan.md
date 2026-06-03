# MW75 Streamer — Linux Port Design

Design document for porting [`arctop/mw75-streamer`](https://github.com/arctop/mw75-streamer)
to Linux, so the MW75 EEG headphones can stream on the Steam Deck (Arch) and on the
Dell homelab server. This is the Phase 1 deliverable called for in
[`build_plan.md`](../build_plan.md) §1, and the streaming foundation the rest of the
system depends on (see [`research_brief.md`](../research_brief.md) §3 / §4).

Per the build plan, this design is reviewed **before** any code is written.

## Goal and scope

- **In scope:** make a real MW75 stream EEG over Bluetooth on Linux, producing the same
  output (WebSocket / LSL / CSV) the macOS version produces today.
- **Out of scope (unchanged):** the BLE activation handshake, packet parsing, the output
  servers, the WebSocket JSON schema, and mock mode. The port must not alter any of these.
- **Contribution intent:** the Linux support is developed in a fork and offered back to
  upstream as a pull request, so it benefits other users and we avoid carrying a private
  patch set indefinitely.

## Upstream architecture (inspected 2026-05-24, `main`)

`mw75-streamer` is an MIT-licensed Python package (`requires-python >= 3.9`). The CLI entry
point is `mw75_streamer.main:cli_main` (`mw75-streamer`). Relevant dependencies:

- `bleak >= 0.20` — cross-platform BLE (uses BlueZ on Linux).
- `pyobjc` / `pyobjc-framework-IOBluetooth` — **gated on `sys_platform == 'darwin'`**.
- Optional extras: `websocket` (websocket-client), `lsl` (pylsl), `testing` (websockets).

The device layer lives in `mw75_streamer/device/`:

| Module | Role | Platform |
| --- | --- | --- |
| `mw75_device.py` | `MW75Device` — coordinator: composes BLE + RFCOMM managers | **neutral** |
| `ble_manager.py` | BLE discovery + activation handshake (via `bleak`) | **cross-platform** |
| `rfcomm_manager.py` | RFCOMM (Bluetooth Classic) channel 25 via PyObjC/IOBluetooth | **macOS only** |
| `mock_rfcomm_manager.py` | Mock RFCOMM, mirrors the manager interface | **cross-platform** |

**Key finding:** the only macOS-specific code is in `rfcomm_manager.py`. `MW75Device` contains
no PyObjC — it just orchestrates a BLE manager and an RFCOMM manager and forwards a data
callback. BLE is already cross-platform via `bleak`. A working mock RFCOMM manager already
exists and mirrors the interface, which proves the RFCOMM module is the correct seam and that
a third (Linux) implementation drops in cleanly.

`MW75Device.connect_and_stream()` runs the lifecycle: BLE discover + activate →
disconnect BLE (flagged "macOS compatibility") → construct the RFCOMM manager →
`run_until_stopped()` streams, delivering raw bytes to the callback.

## The seam: the RFCOMM manager interface

Both the macOS and mock managers implement this implicit interface, and the Linux
implementation must satisfy it exactly:

```text
__init__(self, device_name: str, data_callback: Callable[[bytes], None])
connect(self) -> bool          # find the paired device, open RFCOMM channel 25
run_until_stopped(self) -> None # blocking read loop; calls data_callback(bytes) per chunk
stop(self) -> None             # signal the loop to end
close(self) -> None            # tear down the channel/socket
```

Framing into 63-byte packets and parsing happen **downstream** of this callback and are
platform-neutral — the manager only needs to deliver raw bytes as they arrive.

## Design

The port is three small, isolated changes. New Linux code stays in clearly-named files so the
upstream pull request is a clean addition rather than a rewrite.

### 1. `device/rfcomm_manager_linux.py` (new)

A `LinuxRFCOMMManager` implementing the interface above using the Python **standard library**:

```python
import socket
sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
sock.connect((bd_addr, 25))
# run_until_stopped: loop sock.recv(...) -> data_callback(chunk) until stop()
```

- **Why stdlib, not `pybluez`:** `socket.AF_BLUETOOTH` / `BTPROTO_RFCOMM` is built into CPython
  on Linux and needs no third party package; `pybluez` is largely unmaintained. Fall back to
  `pybluez` only if the stdlib path proves insufficient.
- **Address resolution:** find the paired device's Classic `bd_addr` via **BlueZ over D-Bus**,
  mirroring the macOS `IOBluetoothDevice.pairedDevices()` lookup by name. Exact mechanism
  (a small D-Bus query vs. reusing what `bleak` discovered) is finalized during implementation;
  see open questions.
- **Concurrency:** the existing interface is synchronous (`run_until_stopped` blocks), so the
  Linux version mirrors that with a blocking `recv` loop and a `stop()` flag / socket shutdown.

### 2. `device/rfcomm_backend.py` (new) — platform factory

A `create_rfcomm_manager(device_name, data_callback)` factory selecting the implementation by
`sys.platform`, importing the platform module **lazily** so PyObjC is never imported on Linux
(and the Linux module is never imported on macOS):

```text
darwin  -> rfcomm_manager.RFCOMMManager
linux   -> rfcomm_manager_linux.LinuxRFCOMMManager
other   -> raise (clear "unsupported platform" error)
```

`mw75_device.py` calls this factory instead of importing the macOS class directly.

### 3. `main.py` guard fix

The CLI currently disables the real device on any non-macOS platform (real hardware only works
on macOS; elsewhere only `--mock` runs). Update that guard so Linux is a supported real-device
platform, leaving the unsupported-platform error for everything else.

### Untouched

BLE handshake, packet parsing, the `server/` output (WebSocket/LSL/CSV), mock mode, and the
WebSocket JSON schema are not modified. Downstream Baseline components depend on that schema.

## Repository and contribution workflow

- The port is developed in a fork, **`mbaliga/mw75-streamer`**, on a `linux-port` branch, and
  offered upstream as a pull request to `arctop/mw75-streamer`.
- This `baseline` repository references the fork from `streamer-linux/` — either a git submodule
  pinned to the `linux-port` commit, or a pinned dependency
  (`mw75-streamer @ git+https://github.com/mbaliga/mw75-streamer@linux-port`) — plus any
  Baseline-specific launch/systemd glue. Submodule vs. pin is locked when Phase 2's core is
  wired to the live stream.

## Build and verification plan (mock-first)

1. Branch `linux-port` in the fork; add the factory and the Linux RFCOMM skeleton.
2. **Mock parity on Linux:** run `--mock` on Linux, connect a WebSocket client, and confirm the
   emitted JSON matches what the macOS build emits. No hardware required (works in a container
   and on the Steam Deck).
3. Implement the Linux RFCOMM connect + read loop.
4. **Hardware test on the Steam Deck:** pair the MW75 (`bluetoothctl`), run the `bleak`
   activation handshake, then stream continuously for 10 minutes at 500 Hz / 12 channels with
   no packet loss.
5. Add `README_LINUX.md` (pairing + run instructions); open the upstream pull request.

### Exit criteria (build_plan §1)

- [ ] Mock mode on Linux produces the same WebSocket JSON as the macOS build.
- [ ] A real MW75 streams on the Steam Deck: activation handshake succeeds, then 10 minutes of
      lossless 500 Hz / 12-channel data.
- [ ] `README_LINUX.md` written; pull request to `arctop/mw75-streamer` open.

## Risks and open questions

- **BLE handshake portability.** `bleak` should make activation work on Linux, but the
  `disconnect_after_activation` step is flagged "macOS compatibility" in the source. Verify on
  hardware and document any Linux divergence. (The build plan already anticipates BlueZ/RFCOMM
  quirks here.)
- **Classic vs. BLE address.** Confirm whether the MW75's BLE address equals its Classic
  `bd_addr`, or whether the Classic address must be resolved from BlueZ paired devices.
- **Pairing is a manual prerequisite** on Linux (`bluetoothctl`), as it is on macOS.
- **Upstream divergence.** This design reflects upstream `main` as of 2026-05-24; re-check the
  module layout before implementing if upstream has moved.

## References

- Upstream: <https://github.com/arctop/mw75-streamer> (MIT)
- [`build_plan.md`](../build_plan.md) §1 — Streamer port
- [`research_brief.md`](../research_brief.md) §3, §4 — hardware and architecture
