## Context

`Pm5Decoder` currently hard-codes one firmware's byte layout. We have proven the PM5 wire format is firmware-dependent: on the current firmware `0x33`/`0x35`/`0x38` are +2/+2/+1 bytes vs rev 1.30 and `0x36` is −2, with fields inserted/removed; the force-curve format also changed in a Sept-2025 firmware. Field offsets were reverse-engineered and validated against real captures (JustRow, 500 m, 1:00). We want to support more than one firmware without editing the decoder, and to pick the right layout automatically.

The PM5 exposes its firmware via the C2 Device Information Service characteristic `0x0014` (Firmware Revision string, 20 bytes) — a clean detection point the bridge can read on connect, before any data frames.

## Goals / Non-Goals

**Goals:**
- Isolate every firmware-specific detail behind one swappable `FirmwareProfile`.
- Auto-select the profile per connection; make captures self-describing (firmware + profile id in the manifest).
- Keep the `Pm5Event` contract and on-disk formats unchanged; keep existing fixtures green.

**Non-Goals:**
- Cataloguing firmwares we have not observed.
- Implementing the Sept-2025 force-curve format (a later profile override).
- Decoding the still-raw characteristics (summary `0x39/3A/3C`, HR-belt `0x3B`).

## Decisions

### D1: Split orchestration from interpretation
`Pm5Decoder` keeps only firmware-independent cross-frame state — the force-curve reassembly loop and stroke↔curve correlation — and delegates per-characteristic byte interpretation to the active `FirmwareProfile`. The force-curve *packet* parsing (nibble meaning + sample encoding) is a profile hook (so the Sept-2025 format becomes an override), but the *reassembly across notifications* stays in the decoder.
- **Alternative:** declarative offset tables (field→offset maps) instead of code. Rejected for now: drift includes present/absent fields and shifted semantics, which template-method overrides express more clearly than a data table; revisit if profiles proliferate.

### D2: Template-method profiles
`FirmwareProfile` is an abstract base implementing every characteristic at rev-1.30 offsets (that is the `ReferenceRev130` profile). A firmware profile extends it and **overrides only the drifted characteristics**. `CurrentPm5` overrides `0x33`, `0x35`, `0x36`, `0x38` (and inherits `0x31`, `0x32`, `0x37`, force curve). Each profile also declares `expectedLength(charId)` for fingerprinting and self-checks.
- **Why:** minimises duplication — a new firmware is one small subclass. Matches how Concept2 actually evolves the format (additive/positional drift).

### D3: Three-layer profile selection (most specific wins)
1. **Config override** `ergpower.ble.firmware.profile = auto | reference | current | <id>`.
2. **Firmware version match** — `FirmwareProfileRegistry` asks each profile `claims(firmwareString)`.
3. **Structural fingerprint** — match observed characteristic lengths against each profile's declared lengths. Robust for uncatalogued firmwares that share a layout; also a sanity check on the version match.
If none is confident: use the newest profile, log firmware + fingerprint, rely on length-guarded reads + `raw.ndjson` (never lose data). Firmware is known before the first data frame (bridge reads `0x0014` and emits it in the device meta line first), so the profile is fixed before decoding.

### D4: Self-describing captures
`session.json` records the firmware string and the profile id. With `raw.ndjson` (already implemented), any session is re-decodable by a corrected/added profile — validated in practice today.

## Risks / Trade-offs

- **A firmware update silently shifts offsets** → detected by the length fingerprint (mismatch vs the version-selected profile) and by cross-field invariants (force-curve peak ≈ peak drive force); logged, and re-decodable from `raw.ndjson`.
- **`0x0014` unreadable / bridge can't fetch firmware** → fall back to fingerprint-only selection; record `firmware: unknown`.
- **Refactor regresses decoding** → the real-hardware fixtures (JustRow/500 m/1:00) are the guard; Phase 1 must keep them byte-identical.
- **Over-abstraction** → keep it template-method + one registry; avoid a config-driven field-table engine until a third or fourth firmware justifies it.

## Migration Plan

Staged, each phase independently shippable and covered by existing fixtures:
1. **Extract** — `FirmwareProfile` (base = rev 1.30) + `CurrentPm5`; `Pm5Decoder` delegates. Pure refactor; fixtures stay green.
2. **Detect** — bridge reads `0x0014`, reports firmware; registry selects by version; firmware + profile id into `session.json`.
3. **Fallback + override** — length-fingerprint matcher + `ergpower.ble.firmware.profile`.
4. **Grow** — add profiles as firmwares/specs appear (incl. Sept-2025 force curve).

## Open Questions

- Exact form of the firmware string from `0x0014` on this firmware (e.g. "999 build 12") — read it live to define the `claims(...)` match.
- Whether hardware revision (`0x0013`) is also needed to disambiguate layouts, or firmware alone suffices.
