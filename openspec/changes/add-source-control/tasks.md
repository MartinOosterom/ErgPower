## 1. Contract

- [x] 1.1 Extend `api/openapi.yaml`: `GET /sessions`, `GET|POST|DELETE /source`, `GET /devices`; schemas `SessionSummary`, `SourceRequest` (oneOf ble|replay), `SourceStatus`, `DiscoveredDevice`
- [x] 1.2 Update the contract test / validator for the new paths

## 2. Session catalog

- [x] 2.1 `SessionCatalog`: list `sessions/*/` reading `session.json` + `summary.json`; flag replayable (has `raw.ndjson`)
- [x] 2.2 `GET /sessions` controller (implements generated interface)

## 3. Source control

- [x] 3.1 `SourceManager`: own the single active source; start (`ble` → +storage; `replay` → live only) and stop; reset `LiveState` on switch; push a source/connection event
- [x] 3.2 `serve` starts idle (remove auto-connect; optional configured default)
- [x] 3.3 `POST /source` (ble|replay), `DELETE /source`, `GET /source` controllers
- [x] 3.4 `GET /devices` — scan via the bridge (`--scan` → JSON)

## 4. Timed replay

- [x] 4.1 `ReplayPm5Source` paced mode: emit honouring inter-frame `mono` deltas × `speed`; keep the fast mode for tests
- [x] 4.2 Guard non-replayable sessions (no `raw.ndjson`) with a clear error

## 5. Verify

- [x] 5.1 Test: `GET /sessions` lists the fixture-derived session(s)
- [x] 5.2 Test: `POST /source {replay}` a recorded session → SSE carries metrics + force curves (timed); `DELETE /source` stops it
- [x] 5.3 Contract test: new responses validate against the spec

## 6. Docs

- [x] 6.1 README: `serve` is idle; source selection (connect/replay) via the API; timed replay for hardware-free demos
