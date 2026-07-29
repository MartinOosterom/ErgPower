## 1. Contract (spec-first)

- [x] 1.1 Extend `api/openapi.yaml`: `GET /dashboards` (list), `GET|PUT|DELETE /dashboards/{name}`;
      schemas `DashboardSummary {name}` and `Dashboard {name, config}` where `config` is an **opaque
      object** (`additionalProperties: true`, no widget schema); 404 on unknown; regenerate interfaces
- [x] 1.2 Update the contract test/validator for the new paths

## 2. Backend (server-side JSON)

- [x] 2.1 `DashboardStore`: list / read / write / delete `dashboards/<name>.json` under a configurable
      dir; pass the `config` JSON through opaquely; **sanitize/reject unsafe names** (path separators,
      `..`) so nothing is written outside the dir
- [x] 2.2 Controller implementing the generated Dashboards API (list/get/put/delete), 404 on unknown,
      400 on an unsafe name

## 3. Frontend

- [x] 3.1 Read/write profile bodies via the API (list/get/put/delete) instead of localStorage; keep the
      **active** selection in localStorage
- [x] 3.2 Named-profile ops (create/switch/rename/duplicate/delete) through the API; presets → "New from
      preset"; toolbar profile picker/menu
- [x] 3.3 Migration: on first load, if the server has no profiles and a legacy browser-local single
      dashboard exists, upload it as `"Default"` (else seed a Default from the default preset)

## 4. Verify

- [x] 4.1 A profile saved in one browser appears in another against the same server; the active
      selection stays per-device; an existing browser-local layout migrates intact to "Default";
      unsafe names are rejected
- [x] 4.2 Contract test for `/dashboards`; `web/README` note; typecheck + builds green (`./mvnw test`,
      `npm run build`)
