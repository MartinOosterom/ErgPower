# firmware-profiles Specification

## Purpose
TBD - created by archiving change add-firmware-profiles. Update Purpose after archive.
## Requirements
### Requirement: Firmware-specific interpretation is isolated in a profile
All firmware-dependent PM5 wire details — per-characteristic byte offsets, lengths, present/absent fields, scaling, and the force-curve packet format — SHALL live behind a single `FirmwareProfile` abstraction. The typed event model (`Pm5Event`) SHALL remain firmware-independent, and the decoder's cross-frame orchestration (force-curve reassembly, stroke↔curve correlation) SHALL NOT contain firmware-specific offsets.

#### Scenario: Adding a firmware requires no decoder edits
- **WHEN** a new PM5 firmware with a different characteristic layout must be supported
- **THEN** it is added as a new `FirmwareProfile` (overriding only the characteristics that differ)
- **AND** no change is made to `Pm5Decoder`'s orchestration or to the `Pm5Event` records

#### Scenario: Reference and current profiles both exist
- **WHEN** the system starts
- **THEN** a reference profile implementing the rev-1.30 spec layout is available
- **AND** a current-firmware profile is available that overrides only the drifted characteristics

### Requirement: Firmware auto-detection
On connect, the bridge SHALL read the C2 Firmware Revision characteristic (`0x0014`) and report the firmware string to the JVM before data frames are decoded. The system SHALL select a `FirmwareProfile` for the connection using, in order of precedence: an explicit configuration override; a match on the reported firmware version; a structural fingerprint of observed characteristic lengths.

#### Scenario: Profile selected from firmware version
- **WHEN** the bridge reports a firmware string that a registered profile claims
- **THEN** that profile is selected before the first data frame is decoded

#### Scenario: Fingerprint fallback for an uncatalogued firmware
- **WHEN** the reported firmware version matches no registered profile
- **THEN** the profile whose declared characteristic lengths match the observed frames is selected
- **AND** the firmware string and length fingerprint are logged so the firmware can be catalogued

#### Scenario: Configuration override wins
- **WHEN** `ergpower.ble.firmware.profile` names a specific profile
- **THEN** that profile is used regardless of the detected firmware

#### Scenario: Unknown firmware does not lose data
- **WHEN** no profile can be confidently selected
- **THEN** the newest profile is used with length-guarded reads, the situation is logged, and the raw frames are still persisted so the session can be re-decoded later

### Requirement: Captures record how they were decoded
Each session manifest (`session.json`) SHALL record the detected firmware string and the id of the `FirmwareProfile` used to decode it, so a stored session is self-describing and re-decodable.

#### Scenario: Manifest records firmware and profile
- **WHEN** a session is finalised
- **THEN** its `session.json` contains the firmware revision string and the selected profile id

### Requirement: Regression against real captures
Switching to profile-based decoding SHALL NOT change the decoded output for existing captures. The current firmware's captures (JustRow, 500&nbsp;m, 1:00) SHALL decode identically through the current-firmware profile.

#### Scenario: Existing fixtures decode identically
- **WHEN** a recorded capture is decoded via the current-firmware profile
- **THEN** the resulting events and stored files match the pre-refactor output

