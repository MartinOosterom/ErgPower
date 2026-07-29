## Context

The dashboard persists a single `DashboardConfig` in `localStorage["ergpower.dashboard"]`
(`persistence.ts`), and presets overwrite it. This change makes dashboards plural and named, and moves
their storage **server-side as JSON** via a small REST API (the app is served by the same Java process,
so the API is always co-located — no offline concern). The active selection stays per-device.

## Goals / Non-Goals

**Goals:** multiple named profiles; create/switch/rename/duplicate/delete; server-side JSON persistence
(one file per profile); active selection per-device; presets seed profiles; migrate the existing local
layout; a stable contract that doesn't churn as widgets change.

**Non-Goals:** auth / multi-user; import/export or public sharing; validating widget internals on the
server; the metric/graph registry (separate `metric-graph-panels`).

## Decisions

### D1: One JSON file per profile, server-side
Persist each profile at `dashboards/<name>.json` under a configurable dir (default `dashboards/`). This
maps cleanly to REST resources (`/dashboards/{name}`), is git-friendly and hand-editable, and avoids
rewriting a whole document on every save.

### D2: The server treats config as an **opaque** JSON object
A dashboard's `config` (widgets + layout + arbitrary per-widget config) is stored and returned verbatim;
the contract types it as an open object (`additionalProperties`), **not** a widget schema. The frontend
owns the shape, so adding widgets/config fields (incl. `metric-graph-panels`) needs **no API change**.
- Rejected: fully modelling `WidgetInstance`/`DashboardConfig` in OpenAPI — type-safe but couples the
  API to the widget model; every widget change becomes a contract change.

### D3: Active profile is per-device (client-side)
Profiles are shared/durable on the server; *which* one is active is a device preference, kept in
localStorage. Two devices can show different active profiles against the same server. (No server-side
"active" field in v1.)

### D4: REST shape (spec-first)
```
   GET    /dashboards          → list of { name } (+ maybe metadata)
   GET    /dashboards/{name}   → { name, config }        404 if unknown
   PUT    /dashboards/{name}   → upsert (body = config)  → { name, config }
   DELETE /dashboards/{name}   → delete                  404 if unknown
```
Added to `api/openapi.yaml`; Spring interfaces generated; a controller implements them (same pattern as
`source-control`). `live-api`'s read-only requirement is broadened to permit these mutations.

### D5: Migration
On first load: if `GET /dashboards` is empty and a legacy `localStorage["ergpower.dashboard"]` exists,
`PUT /dashboards/Default` with it and set the local active = `Default`. Otherwise seed a `Default` from
the default preset (client PUTs it). No data loss.

### D6: Filename safety
`{name}` maps to a filename, so the backend MUST reject/sanitize path separators and traversal
(`..`, `/`, `\`) — store under a fixed dir with a sanitized, bounded name; reject anything unsafe with a
clear 400. This is the single most important backend correctness point.

## Risks / Trade-offs

- **Path traversal via `{name}`** → strict sanitization/allow-listing (D6).
- **Concurrent writes** → last-write-wins is fine for single-user localhost; no locking needed in v1.
- **No auth** → acceptable for a personal localhost tool; explicitly a non-goal.

## Migration Plan

Additive. First load migrates the browser-local single dashboard to a server-side `Default` profile
(D5). No server state existed before, so nothing to convert on disk.

## Open Questions

- Dashboards dir: top-level `dashboards/` (sibling of `sessions/`) vs a `config/` subdir — cosmetic.
- Do we ever want a server-side "active" too (e.g. a shared default for new devices)? Deferred.
- Exact name-sanitization scheme (slug vs reject-invalid) — decide at implementation.
