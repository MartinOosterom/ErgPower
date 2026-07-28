## 1. Phase 1 — Extract the FirmwareProfile abstraction (pure refactor)

- [x] 1.1 Create `pm5/firmware/FirmwareProfile` (abstract): read helpers (u8/u16/u24), per-characteristic decode methods with rev-1.30 implementations, a `parseForceCurvePacket` hook, and `expectedLength(charId)` / `id()`
- [x] 1.2 Create `ReferenceRev130` (the base as-is) and `CurrentPm5` overriding only 0x33/0x35/0x36/0x38 (current validated offsets)
- [x] 1.3 Refactor `Pm5Decoder` to hold a `FirmwareProfile` + cross-frame state (reassembly, correlation) and delegate byte interpretation
- [x] 1.4 Default the decoder to `CurrentPm5`; verify all existing fixtures (JustRow / 500m / 1:00) decode byte-identically (regression)
- [x] 1.5 Add a unit test asserting `ReferenceRev130` vs `CurrentPm5` differ exactly on the drifted characteristics

## 2. Phase 2 — Firmware detection + selection

- [x] 2.1 Bridge: read the C2 Firmware Revision characteristic (0x0014) on connect; add `firmware` to the device meta line
- [x] 2.2 `BlePm5Source`: capture the reported firmware string; expose it
- [x] 2.3 `FirmwareProfileRegistry`: `select(firmwareString)` via each profile's `claims(...)`; wire the selected profile into the decoder before first data frame
- [x] 2.4 Record `firmware` + `profileId` in `session.json`

## 3. Phase 3 — Fingerprint fallback + config override

- [x] 3.1 Length-fingerprint matcher: pick the profile whose `expectedLength` map matches observed frame lengths; log firmware + fingerprint when falling back
- [x] 3.2 Add `ergpower.ble.firmware.profile` (auto | reference | current | <id>) config; override selection
- [~] 3.3 Self-check to flag a mis-selected profile. **Length-fingerprint self-check done (`checkFingerprint` warns + records `profileNote` when observed lengths disagree with the active profile). Cross-field invariant check (force-curve peak ≈ peak drive force) still pending.**

## 4. Docs

- [ ] 4.1 Document the profile model + how to add a firmware in `docs/reference` / module README
