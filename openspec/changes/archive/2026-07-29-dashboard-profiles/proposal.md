## Why

Today there is exactly **one** implicit dashboard, and picking a preset **overwrites** it. Different
situations want different panels — a race HUD, a technique view, a long-row view — so the user should
keep several **named profiles** and switch between them.

And per the storage decision, profiles should live **server-side as JSON**, not in browser local
storage: durable, shared across every browser/device hitting the same server, and editable/versionable
on disk. The one genuinely per-device thing — *which* profile is active — stays client-side.

## What Changes

- **Server-side profile storage, one JSON file per profile** at `dashboards/<name>.json` (configurable
  dir). The server stores and returns a profile's configuration **opaquely** — it never parses the
  widget internals — so the browser's widget model can evolve with **no contract churn**.
- **A small REST CRUD** under `/api/v1/dashboards` (list / get / create-or-replace / delete),
  **spec-first** in `api/openapi.yaml` → generated Spring interfaces (same pattern as `source-control`).
- **Named-profile operations** (create/switch/rename/duplicate/delete) go through the API; **presets
  seed new profiles** rather than overwriting.
- **The active profile is remembered per-device** (localStorage), so different devices may show
  different active profiles while sharing the same server-stored profiles.
- **Migration:** on first load, if the server has no profiles and a legacy browser-local single
  dashboard exists, it is uploaded as a `"Default"` profile — nothing lost.

Out of scope: authentication / multi-user (single-user localhost — anyone hitting the server shares the
profiles); import/export; shareable public links.

## Capabilities

### New Capabilities
- `dashboard-storage`: server-side JSON persistence of dashboard profiles (one file per profile, opaque
  config) exposed as a small REST CRUD API.

### Modified Capabilities
- `web-viewer`: the dashboard becomes **named profiles** persisted **server-side via the API**, with the
  **active** selection kept per-device and migration of an existing browser-local layout.
- `live-api`: the read-only requirement broadens — `dashboard-storage` mutations join `source-control`
  as permitted mutating operations (still no endpoint modifies stored session data).

## Impact

- Spans **contract + backend + frontend** (this is the change the earlier localStorage-only sketch was
  not): `api/openapi.yaml` gains `/dashboards` paths + a `Dashboard` schema whose `config` is an opaque
  object; a `DashboardStore` reads/writes `dashboards/<name>.json`; a controller implements the
  generated interface; the frontend reads/writes profiles through the API and keeps `active` local.
- Storage is trivial JSON; the API is tiny. **Filename safety** (reject path traversal in `{name}`) is
  the one thing to get right on the backend.
- Composes with `metric-graph-panels`: richer panels make multiple profiles more useful; the two remain
  independent.
