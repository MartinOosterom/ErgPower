## MODIFIED Requirements

### Requirement: Configurable widget dashboard
The user SHALL be able to compose the dashboard: add widgets from a palette, remove them, arrange (drag)
and resize them on a grid, and configure per-widget options. Dashboards SHALL be saved as **named
profiles**: the user can create (save-as), switch between, rename, duplicate, and delete profiles, each
holding its own set and arrangement of panels. Profiles SHALL be persisted **server-side as JSON** via
the `dashboard-storage` API (not browser local storage), so they are durable and shared across browsers
and devices using the same server. The **active** profile selection SHALL be remembered **per device**
(client-side), so different devices may show different active profiles. Built-in presets SHALL be
selectable as templates that seed a new profile (without overwriting others); edits SHALL be saved to
the active profile. A pre-existing browser-local single layout SHALL be migrated into a server-side
default profile so no layout is lost.

#### Scenario: Profiles are shared across devices
- **WHEN** a profile is saved from one browser and the app is opened in another browser against the same
  server
- **THEN** that profile is available to select

#### Scenario: Active selection is per-device
- **WHEN** two devices select different active profiles
- **THEN** each device keeps its own active selection while sharing the same stored profiles

#### Scenario: Preset seeds a new profile
- **WHEN** the user creates a new profile from a preset (e.g. "Minimal HUD")
- **THEN** a new named profile is created from that preset without overwriting existing profiles

#### Scenario: Existing local layout migrated
- **WHEN** a user who previously had a single browser-local dashboard loads the app after this change
- **THEN** their layout appears intact as a server-side default profile
