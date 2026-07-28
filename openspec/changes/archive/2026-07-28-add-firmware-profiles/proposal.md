## Why

The PM5's BLE wire format is **firmware-dependent** — proven on real hardware: characteristics `0x33/0x35/0x36/0x38` drift in length and field position between the 2022 interface definition (rev 1.30) and the current firmware, and the force-curve format changed again in Sept 2025. Today `Pm5Decoder` hard-codes one firmware's offsets, so supporting another firmware (a user updating, or a different erg) means editing core decode logic. We want firmware-specific details isolated behind a swappable, auto-selected abstraction.

## What Changes

- Introduce a **`FirmwareProfile`** abstraction that owns **all** firmware-specific wire interpretation: per-characteristic byte offsets, lengths, present/absent fields, scaling, and the force-curve packet format. The typed `Pm5Event` records (the semantic contract) stay firmware-independent.
- Refactor `Pm5Decoder` into a thin **orchestrator**: it keeps only the firmware-independent cross-frame state (force-curve reassembly loop, stroke↔curve correlation) and delegates byte interpretation to the active profile. **BREAKING** internal API (decoder construction), no change to `Pm5Event` or stored output.
- Ship two profiles: **`ReferenceRev130`** (abstract base implementing the rev-1.30 spec) and **`CurrentPm5`** (extends it, overriding only the drifted characteristics — the current behaviour).
- **Auto-detect firmware:** the bridge reads the C2 Firmware Revision characteristic (`0x0014`) on connect and reports it; a **`FirmwareProfileRegistry`** selects a profile by firmware version, with a **characteristic-length fingerprint** fallback and a **config override** (`ergpower.ble.firmware.profile`).
- Record the **firmware string + selected profile id** in `session.json`, so every capture is self-describing and re-decodable (with `raw.ndjson`).

Non-goals: cataloguing firmwares we haven't observed; the Sept-2025 force-curve format (added later as a profile override); decoding still-raw characteristics (summary `0x39/3A/3C`, HR-belt `0x3B`).

## Capabilities

### New Capabilities
- `firmware-profiles`: A pluggable `FirmwareProfile` that encapsulates all firmware-specific PM5 wire interpretation, selected per connection by auto-detected firmware version (with fingerprint fallback and config override), and recorded in each session's manifest.

### Modified Capabilities
<!-- The related `pm5-ble-capture` capability lives in the unarchived sibling change
     `add-pm5-capture-storage`; the interaction (decode goes through the profile, bridge reports
     firmware) is described in design.md rather than as a delta here. -->


## Impact

- **Refactor** of `Pm5Decoder` (orchestration vs. interpretation split); new `firmware/` package (`FirmwareProfile`, `ReferenceRev130`, `CurrentPm5`, `FirmwareProfileRegistry`). No change to `Pm5Event` records or on-disk formats.
- **Bridge**: reads `0x0014` on connect, adds firmware to the device meta line.
- **Config**: new `ergpower.ble.firmware.profile` (auto | reference | current | <id>).
- **Manifest**: `session.json` gains `firmware` (now populated) + `profileId`.
- **Tests**: existing real-hardware fixtures (JustRow, 500m, 1:00) must keep decoding identically through the `CurrentPm5` profile (regression guard).
