## 1. Contract (leading artifact)

- [x] 1.1 Author `api/openapi.yaml` (OpenAPI 3.0.3, `/api/v1`): paths `connection`, `live/snapshot`, `live/stream`; schemas `ConnectionStatus`, `DeviceInfo`, `WorkoutState`, `LiveMetrics`, `StrokeSummary`, `ForceCurve`, `LiveSnapshot`, `Heartbeat`, `LiveEvent`, `Problem`. **Validated against openapi-spec-validator.**
- [x] 1.2 Validate the spec as a build gate. **`ApiContractTest` loads `api/openapi.yaml` via the OpenAPI validator (an invalid spec fails the build). Spectral *style* linting left as a future nicety.**

## 2. Codegen

- [x] 2.1 Add `openapi-generator-maven-plugin` (generator `spring`, `interfaceOnly=true`, delegate) generating API interfaces + models from `api/openapi.yaml`
- [x] 2.2 Add `spring-boot-starter-webflux`; confirm the app still builds/runs the existing CLI

## 3. Live state + server

- [x] 3.1 `LiveState` aggregator: merge `Pm5Event`s into a rolling current-state (connection, workout, metrics, last stroke/curve) and map to the API DTOs
- [x] 3.2 `serve` mode: long-running app owning a live `Pm5Source`, multicasting to storage + API
- [x] 3.3 Implement `GET /connection` and `GET /live/snapshot` delegates from `LiveState`
- [x] 3.4 Implement `GET /live/stream`: `Flux<Pm5Event>` → named `ServerSentEvent`s (connection/workout/metrics/stroke/forceCurve) + heartbeat
- [x] 3.5 Map PM5 workout/rowing state → `WorkoutPhase`; derive time-left/distance-left and projections

## 4. Verify

- [x] 4.1 Contract test: responses validate against the OpenAPI schemas. **`ApiContractTest` validates `/connection` + `/live/snapshot` bodies against the spec (caught + fixed a nullable-field mismatch).**
- [x] 4.2 Drive `serve` against the `ReplayPm5Source` and assert the SSE stream carries metrics + force curves; snapshot renders
- [x] 4.3 Manual: `curl -N /api/v1/live/stream` shows the event stream while replaying/rowing

## 5. Docs

- [x] 5.1 README: `serve` mode + the API (link the OpenAPI doc); note codegen + that generated sources aren't committed
