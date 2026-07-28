## ADDED Requirements

### Requirement: Configurable device selection
The system SHALL allow configuration of which PM5 to connect to via a selection strategy: by advertised name/serial, by a cached CoreBluetooth peripheral UUID, or by first PM5 found (matched on the Concept2 rowing service). Configuration SHALL be externalised (Spring configuration properties). This change scopes configuration to a **single** device (no multi-device profiles).

#### Scenario: Match by name/serial
- **WHEN** the device selection strategy is `name` and a name/serial is configured
- **THEN** the bridge connects to the advertised PM5 whose name matches, and ignores other PM5s

#### Scenario: First-found selection
- **WHEN** the device selection strategy is `first`
- **THEN** the bridge connects to the first PM5 discovered that advertises the Concept2 rowing service

#### Scenario: Peripheral UUID is cached for fast reconnect
- **WHEN** a device is resolved by name for the first time on this Mac
- **THEN** its CoreBluetooth peripheral UUID is cached so subsequent connections may skip a full scan
- **AND** if the cached UUID no longer resolves, the system falls back to the configured selection strategy

### Requirement: Configurable connection behaviour
The system SHALL allow configuration of connection behaviour: scan timeout, connect timeout, whether to auto-reconnect on drop, and reconnect backoff bounds. BLE-level reconnection SHALL be owned by the bridge; JVM-level restart of the bridge process SHALL be governed by the same auto-reconnect intent.

#### Scenario: Auto-reconnect on BLE drop
- **WHEN** auto-reconnect is enabled and the PM5 disconnects mid-session
- **THEN** the bridge attempts to reconnect using the configured backoff, and reports connection state changes to the JVM

#### Scenario: Connect timeout honoured
- **WHEN** a connection attempt exceeds the configured connect timeout
- **THEN** the attempt fails, the failure is surfaced, and behaviour follows the auto-reconnect configuration

### Requirement: Configurable capture parameters
The system SHALL allow configuration of the PM5 status sample rate (written to the PM5 sample-rate characteristic) and whether the per-stroke force curve is captured. "Faithful mode" (subscribe to all data characteristics) SHALL be the default.

#### Scenario: Sample rate applied
- **WHEN** a status sample rate is configured
- **THEN** the bridge writes it to the PM5 sample-rate characteristic on connect, so status notifications arrive at the configured cadence

#### Scenario: Force curve disabled
- **WHEN** force-curve capture is disabled in configuration
- **THEN** the system does not subscribe to / does not reassemble the force-curve characteristic, and no force-curve events or files are produced

### Requirement: Device discovery mode
The bridge SHALL provide a discovery mode that lists nearby PM5 devices with the identifiers usable in configuration (advertised name/serial and peripheral UUID), so a user can populate device configuration.

#### Scenario: Scan lists nearby PM5s
- **WHEN** the bridge is run in discovery/scan mode
- **THEN** it lists each nearby PM5 with its advertised name/serial and CoreBluetooth peripheral UUID
- **AND** it exits without connecting or capturing
