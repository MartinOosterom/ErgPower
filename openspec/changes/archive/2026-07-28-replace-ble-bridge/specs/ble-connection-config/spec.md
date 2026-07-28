## MODIFIED Requirements

### Requirement: Configurable device selection
The system SHALL allow configuration of which PM5 to connect to via a selection strategy: by advertised
name/serial, by a cached **platform device handle**, or by first PM5 found (matched on the Concept2
rowing service). Configuration SHALL be externalised (Spring configuration properties). This change
scopes configuration to a **single** device (no multi-device profiles).

#### Scenario: Match by name/serial
- **WHEN** the device selection strategy is `name` and a name/serial is configured
- **THEN** the bridge connects to the advertised PM5 whose name matches, and ignores other PM5s

#### Scenario: First-found selection
- **WHEN** the device selection strategy is `first`
- **THEN** the bridge connects to the first PM5 discovered that advertises the Concept2 rowing service

#### Scenario: Platform device handle is cached for fast reconnect
- **WHEN** a device is resolved by name for the first time on this host
- **THEN** its platform device handle (the CoreBluetooth peripheral UUID on macOS, or the platform's
  stable device identifier elsewhere) is cached so subsequent connections may skip a full scan
- **AND** if the cached handle no longer resolves, the system falls back to the configured selection
  strategy

### Requirement: Device discovery mode
The bridge SHALL provide a discovery mode that lists nearby PM5 devices with the identifiers usable in
configuration (advertised name/serial and the platform device handle for that host), so a user can
populate device configuration.

#### Scenario: Scan lists nearby PM5s
- **WHEN** the bridge is run in discovery/scan mode
- **THEN** it lists each nearby PM5 with its advertised name/serial and the platform device handle
  usable for configuration on that host
- **AND** it exits without connecting or capturing
