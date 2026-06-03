# Baseline — Phase 0 Plan (Foundations)

*Grounded in `build_plan.md` §0 and `research_brief.md` §3.6 / §4. This is the reviewable runbook for Phase 0; execution happens after you approve, one sub-phase at a time.*

## Context

The build plan gates **everything** behind Phase 0: no EEG, signal processing, or research work begins until the homelab and the project repo exist (`build_plan.md` lines 26, 32). This document turns build_plan §0 into a concrete, executable checklist, folds in the decisions we settled this session, and flags the one genuine unknown (Qubo local control) as an explicit decision-gate rather than a guess.

**Intended outcome:** the three Phase 0 exit gates met —
1. A 24/7, encrypted, headless Linux server on the Dell, reachable by SSH from the Steam Deck.
2. Home Assistant on the Dell controlling the two switches + bulb from a Steam Deck shell script, each command < 2 s.
3. The project repo scaffolded to the build plan's layout, README rewritten, first commit pushed.

## Execution model (read first)

This Claude Code session runs in a cloud container that has **only the git repo** — it cannot reach the Dell or the Steam Deck, and cannot install an OS. Therefore:

- **0.1 and 0.2** are runbooks *you* run on your hardware. I provide exact commands and verify the outputs you paste back. The physical install (BIOS/UEFI, USB media, partitioning) is hands-on at the Dell.
- **0.3** I execute directly in this container (git + files), then push and open a draft PR.
- We **stop after each numbered sub-phase** and confirm its exit criteria before starting the next.

## Decisions locked this session

- **Disk:** LUKS2 full-disk encryption + TPM2 auto-unlock, **plus a recovery passphrase that we always keep** (non-negotiable fallback). Trade-off accepted: a powered-on stolen machine is readable; a powered-off box or a pulled drive is not.
- **Distro:** Debian stable, headless. Why: boring, multi-year support, no snaps, solid `systemd-cryptenroll` TPM tooling. Arch's rolling model is the wrong fit for an unattended 24/7 server. *Confirm the current stable version/point release at install — don't trust memory across the knowledge cutoff.*
- **Home Assistant:** Container (Docker). Why: it coexists with the Baseline core, ambient controller, and later the probe web app as one service among several; HA OS wants to own the whole machine.
- **Smart-home devices — revised this session (portable / lean / local):** you may move home soon and want a travel-friendly kit, so the rule is now **plug-in or screw-in only — no in-wall switches**, all **Matter-capable Wi-Fi** (no hub to carry, local control, future-proof). The only *definite* new buy is **1× E27 smart bulb** for the IKEA uplighter (Tapo L530E, Matter, colour — *confirm the lamp's socket is E27*). Qubo's loads are plug-in appliances, so **try LocalTuya on the existing Qubo first (₹0)**; replace with **Tapo P110M** 16 A Matter smart plugs only if that fails. Full kit, prices, the AC caveat, and the travel reality in 0.2. *(Supersedes the earlier B22 two-bulb pick — the actual fixture is the E27 uplighter.)*

**Parked (not Phase 0 work):** audio↔EEG confound → the core will *log what audio is playing / silence* from Phase 2 so we can quantify it on real data; music surface → Phase 4.

---

## 0.1 — Debian on the Dell (encrypted, headless)

**Goal:** replace Windows with an encrypted, headless Debian server that survives a week untouched and is reachable from the Steam Deck.

### Install
1. Download current Debian stable netinst ISO; verify checksum; write to USB.
2. In the Dell firmware: enable **UEFI**, enable **TPM 2.0**, set boot order to USB. Note whether Secure Boot is on (affects which TPM PCR we bind to).
3. Boot the installer. Choose a **minimal/headless** install (no desktop; "SSH server" + "standard system utilities" only).
4. Partitioning — use the installer's **"Guided — use entire disk and set up encrypted LVM"**, which produces:
   - ESP (FAT32, ~512 MB–1 GB) → `/boot/efi`, unencrypted
   - `/boot` (ext4, ~1 GB), unencrypted
   - **LUKS2** container over the remainder → **LVM** volume group → `root` (ext4) + `swap` (encrypted)
   - ext4 throughout for v0.1 (boring/robust). *Optional later: carve a separate `data` logical volume for the EEG corpus — LVM makes this easy; not needed now.*
   - Set a strong LUKS passphrase — **this becomes the recovery passphrase; record it in your password manager.**

### TPM2 auto-unlock (post-install, over SSH)
*Verify exact device path and current `systemd` guidance at the time of install.*
1. Identify the LUKS partition: `lsblk` / `sudo blkid` (e.g. `/dev/nvme0n1p3`).
2. Enrol the TPM, binding to a stable PCR (PCR 7 = Secure Boot state is the usual low-churn choice):
   `sudo systemd-cryptenroll --tpm2-device=auto --tpm2-pcrs=7 /dev/nvme0n1pX`
3. Add `tpm2-device=auto` to that volume's line in `/etc/crypttab`, then `sudo update-initramfs -u`.
4. **The original passphrase keyslot stays** — that's the recovery path.

> **TPM caveat (important):** a firmware/Secure-Boot/kernel change can alter the measured PCRs and make the TPM refuse to auto-unlock. That is exactly when the recovery passphrase saves you. Auto-unlock with no PIN also means a powered-on box needs no secret — the trade-off you accepted.

### Hardening (homelab role)
- **SSH:** copy your Steam Deck key first (`ssh-copy-id`), then set `PasswordAuthentication no` and `PermitRootLogin no` in `sshd_config`; restart sshd. Confirm key login works *before* disabling passwords.
- **Firewall:** `ufw default deny incoming` / `allow outgoing`; `allow` SSH (and later HA `8123`) from the LAN subnet only.
- **Auto security updates:** install `unattended-upgrades` (security pocket only). Reboots are safe to automate *because* TPM auto-unlock brings the box back unattended — schedule a maintenance window or reboot manually.
- **Time sync:** ensure `systemd-timesyncd` (or chrony) is active — needed for TLS now and EEG timestamping later.
- **Monitoring:** install **Cockpit** (`https://<dell-ip>:9090`) — beginner-friendly web dashboard; satisfies the exit criterion. `htop` over SSH as the minimal fallback.

### Exit criteria (build_plan §0.1)
- [ ] SSH from the Steam Deck to the Dell, reliably, key-only.
- [ ] `uptime` shows multi-day uptime (verify by leaving it running).
- [ ] Monitoring reachable (Cockpit or htop).
- [ ] **Added:** reboot remotely and confirm it comes back **without** a passphrase prompt (TPM works) **and** separately confirm the **recovery passphrase still unlocks** the disk.

---

## 0.2 — Home Assistant + smart devices

**Goal:** a Steam Deck shell script toggles the lamp, the AC switch, and the bulb's colour temperature, each in < 2 s.

### Home Assistant (Container)
1. Install Docker Engine + Compose plugin on the Dell.
2. Run HA as a container (host networking is needed for device discovery), e.g.:
   ```yaml
   services:
     homeassistant:
       image: ghcr.io/home-assistant/home-assistant:stable
       container_name: homeassistant
       network_mode: host
       volumes:
         - ./config:/config
         - /etc/localtime:/etc/localtime:ro
       restart: unless-stopped
   ```
3. Complete onboarding at `http://<dell-ip>:8123`. Create a **long-lived access token** (Profile → Security) for scripting — store it in a secrets file / env var, **never in the repo**.

### Device strategy (portable / lean / local)

Constraints from this session: you may move home soon and want a kit that travels, on a minimal budget, but **fully local** (Matter where possible). Three rules follow:
- **Plug-in / screw-in only — no in-wall switches.** Everything unplugs and comes with you; nothing wired into a wall.
- **Matter-capable Wi-Fi** devices — no hub to carry, local control, and they work across HA / phone / any ecosystem (future-proof).
- **Reuse before buying.** The Qubo loads are plug-in appliances, so try local control on what you already own before spending.

**Reality — your hub doesn't travel.** Home Assistant lives on the Dell. *At home* it's the brain (local, fast, runs Baseline's automations). *Travelling*, you control the devices you bring from the **vendor app or a Matter controller on your phone**, on whatever Wi-Fi you're on — HA isn't in the loop. So every device must (a) re-pair easily to a new **2.4 GHz** network and (b) be usable standalone. Travel gotcha: many hotel networks (captive portals, client isolation, 5 GHz-only) block smart gear — it works best when you control the router (a cheap **travel router** that makes your own Wi-Fi bubble is the pro move).

**Shopping list (Amazon.in / Flipkart — verify live prices):**

| Need | Pick | ~Price | Why |
|---|---|---|---|
| E27 bulb for the IKEA uplighter | **Tapo L530E** (E27, colour, Matter) | ₹1,000–1,500 | Screws into the uplighter; colour + tunable white; Matter = local + future-proof. *Confirm the lamp is E27.* Alt: **WiZ E27** — its HA integration is fully local (no vendor cloud even for auth). |
| Smart plug ×1–2 (replace Qubo) | **Tapo P110M** (16 A, Matter, energy monitoring) | ₹900–1,100 ea | 16 A handles an AC/geyser; Matter = local; unplug-and-go. **Buy only if Qubo local control fails.** |
| AC control (optional, better) | **BroadLink RM4 Mini** IR blaster | ₹1,800–2,500 | A plug only cuts power (and a compressor shouldn't be rapidly power-cycled); the IR blaster mimics the remote (temp/mode/on-off), is **local in HA** (Broadlink integration), and doubles as a travel TV/AC remote. |

**Qubo — try free first, then replace:**
1. Attempt **LocalTuya** on the existing Qubo (link via the Smart Life / Tuya IoT path, pull each device's local key + ID). Local <2 s control → keep them, ₹0 spent; since the loads are plug-in, Qubo is portable enough *if* it works locally.
2. If the local key is blocked (the branded-Tuya trap) → replace with **Tapo P110M** plugs. Do **not** settle for Qubo *cloud* — it fights the <2 s budget and the local-first rule.

**Add Tapo to HA via Matter, not the TP-Link cloud integration** — that bypasses TP-Link's cloud-for-auth entirely and keeps control purely local. (WiZ needs no such workaround.)

### Scriptable control + the < 2 s test
Provide a Steam Deck bash script that calls HA's REST API with the long-lived token (or MQTT), timing each call:
- `light.turn_on` / `light.turn_off` + a `color_temp` change on the bulb
- `switch.turn_on` / `switch.turn_off` on the lamp and the AC
- wrap each in `time` (or date-diff) and assert end-to-end < 2 s.

### Exit criteria (build_plan §0.2)
- [ ] Script turns the lamp on, lamp off, changes bulb colour temperature, toggles the AC switch.
- [ ] Each command < 2 s end-to-end.
- [ ] (If the gate forced cloud control, this gate is where we'd see the 2 s budget at risk — flagged.)

---

## 0.3 — Project repository  *(I execute this in-container)*

**Goal:** repo scaffolded to the build plan's layout, README rewritten, first commit pushed, draft PR opened. Work happens on branch `claude/tender-planck-pcYiy` → draft PR into `main`. (Remote is already the GitHub repo `mbaliga/baseline`, settling build_plan's "your call" on hosting; a Gitea-on-Dell move stays possible later.)

1. **Rewrite `README.md`** (currently the stale "ebbflow" stub): project name *Baseline*, ~3 paragraphs (what it is, the shared-core/three-surfaces architecture, the N=1 longitudinal/open-source intent), and a link to `research_brief.md`. (Satisfies §0.3 exit criteria.)
2. **Commit the planning docs at root** with the canonical names the build plan references: `research_brief.md` and `build_plan.md` (from the uploaded files).
3. **Create the directory skeleton** from build_plan lines 52–65, each empty dir carrying a `.gitkeep`:
   `docs/  streamer-linux/  core/  ambient-controller/  music-controller/  probe-app/  streamer-android/  data/`
4. **Add `.gitignore`:** `data/*` (keep `!data/.gitkeep`); secrets (`*.env`, `.env`, `secrets.yaml`, `*token*`); Python (`__pycache__/`, `*.pyc`, `.venv/`, `venv/`); Node (`node_modules/`, `dist/`); OS junk (`.DS_Store`).
5. **First commit** (descriptive message, e.g. "Establish Baseline project structure and documentation"), then `git push -u origin claude/tender-planck-pcYiy` (retry with backoff on network error) and open a **draft PR** into `main`.

### Exit criteria (build_plan §0.3)
- [ ] Repo layout in place.
- [ ] README explains the project in ~3 paragraphs and links to the brief.
- [ ] First commit pushed; draft PR open.

---

## Open items (your call — not blocking)

- **License.** The brief states open-source intent but names no license. Options when you're ready: permissive (MIT / Apache-2.0) vs. copyleft (GPL-3.0 / **AGPL-3.0**). AGPL is worth considering for a research tool you want others to build on while keeping derivatives open. Can add post-first-commit.
- **Qubo fallback** — only if 0.2 Step 1 is blocked (replace vs. accept cloud).

## Verification summary (the three gates)

| Sub-phase | Pass when… |
|---|---|
| 0.1 | SSH key-only from Deck; multi-day uptime; Cockpit reachable; reboot auto-unlocks via TPM **and** recovery passphrase verified |
| 0.2 | Deck script does lamp on/off + bulb colour-temp + AC on/off, each < 2 s |
| 0.3 | Repo matches the build plan layout; README rewritten + links brief; first commit pushed, draft PR open |

*(0.3 is complete — committed `cd7b5e0`, draft PR [#1](https://github.com/mbaliga/baseline/pull/1). 0.1/0.2 run on your hardware. The section below runs in parallel.)*

---

