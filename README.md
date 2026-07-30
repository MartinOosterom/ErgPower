# ErgPower

**Capture everything a Concept2 PM5 emits over Bluetooth, store it faithfully, and turn it into
insight — from raw per-stroke force curves to AI rowing coaching.**

ErgPower connects to a PM5 (RowErg / SkiErg / BikeErg) over Bluetooth Low Energy, decodes every stream
the monitor broadcasts, and records each session to disk as newline-delimited JSON in **metric / SI
units** — power, pace, stroke rate, heart rate, splits, calories, drive metrics, and the reassembled
per-stroke **force curve**. A single self-contained jar then serves a live browser dashboard and a
REST/SSE API — with **no Python, no Node, and no package manager at runtime**.

## Features

- **Full-fidelity capture** — every PM5 characteristic decoded to typed events and stored per-stream,
  plus the exact raw frames so any session can be re-decoded later.
- **Per-stroke force curves** — the reassembled drive-force curve in Newtons, the data most tools drop.
- **Live dashboard + API** — one process, one port: a configurable React dashboard at `/` and a
  `/api/v1` REST + SSE stream, fed identically by a live PM5 or a replayed session.
- **Technique analysis** — a deterministic, Kleshnev-grounded scorecard (catch gradient, peak position,
  finish plateau…), a mean±band curve, drift trends, a per-stroke heatmap, and fault flags. No model needed.
- **Optional AI coach** — plug in an LLM (local Ollama by default, or OpenAI / Anthropic) to turn that
  analysis into grounded, plain-language coaching. Disabled unless you configure it.
- **.FIT export** — download any session as a data-rich rowing activity for Strava / Garmin Connect /
  Concept2 Logbook.
- **Hardware-free replay** — replay stored sessions through the exact same pipeline, so you can develop,
  demo, and test without a PM5.
- **Self-contained** — the native BLE bridge (Rust) and the web UI are compiled into the jar; running it
  needs only a JVM.
- **Firmware-resilient decode** — wire-format differences between firmware revisions are isolated behind
  pluggable firmware profiles, selected automatically per connection.

## How it works

No platform has a usable pure-JVM Bluetooth stack, so BLE runs in a tiny **native bridge** — a small
Rust binary (using [`btleplug`](https://github.com/deviceplug/btleplug): CoreBluetooth on macOS,
BlueZ/WinRT elsewhere) that forwards *raw* notification frames. It's compiled per platform and
**bundled inside the jar**, so at runtime there's no interpreter to install. All the interesting logic
— decoding, session lifecycle, storage — lives in a **Spring Boot (Java 21)** app.

```
   Concept2 PM5 ──BLE──▶ ergpower-bridge (Rust) ──raw NDJSON frames (stdout)──▶ JVM app
   (CoreBluetooth/…)     (btleplug, dumb pipe)                                 │
                                                                              ▼
                                            FirmwareProfile decode → typed events (Flux)
                                                                              │
                                                          ┌───────────────────┴──────────────┐
                                                          ▼                                   ▼
                                                   SessionStorage                       live viewer (web/)
                                                   per-characteristic NDJSON
                                                   + session.json + summary.json + raw.ndjson
```

The same typed event stream can be produced from a live PM5 (`BlePm5Source`) or by replaying a
recorded capture (`ReplayPm5Source`) — so the whole pipeline is testable without hardware.

## Requirements

**To run** the built jar you need only:

- **JDK 21 (LTS) or newer** — the project targets Java 21, so it also runs on 24 / 25 / 26. If your
  default `java` isn't 21+, select one with `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
- For a **live PM5**: **macOS** (v1's bundled bridge target) with Bluetooth permission granted, and a
  Concept2 **PM5** monitor. Replaying stored sessions needs none of this.

That's it — **no Python, no Node, no package manager at runtime.** The native BLE bridge and the browser
dashboard are both compiled into the jar and used by the Java app directly.

**To build**, additionally: a JDK + Maven (bundled `./mvnw`), a **Rust toolchain** for the bridge
(install via [rustup](https://rustup.rs) — the build finds `~/.cargo/bin/cargo` automatically, so it
works even from an IDE that didn't source your shell profile; for a non-rustup install pass
`-Dcargo.executable=/path/to/cargo`), and — for the web UI — a private Node the `package` build
downloads itself. The Rust/Node builds can be skipped (`-Dnative.skip=true` / `-Dfrontend.skip=true`)
to reuse existing artifacts for an offline build.

## Build

```sh
./mvnw -q package                 # → target/ErgPower-0.0.1-SNAPSHOT.jar (Java + web UI + native bridge)
./mvnw test                       # run the Java test suite (does NOT build web/bridge — stays fast)
./mvnw -q package -Dnative.skip=true -Dfrontend.skip=true   # reuse existing bridge/web (offline)
```

One `./mvnw package` builds everything into a single self-contained jar: the Java app, the `web/` React
dashboard (bundled into `static/`), and the `bridge/` Rust binary (bundled into
`native/<os>-<arch>/`). The bridge builds with `cargo`; the first web build fetches Node from nodejs.org.

## Usage

The jar is self-contained. Run it from the repo root so relative paths (`sessions/`) resolve.

```sh
# Live-capture: turn on the PM5, pick/row a piece — a session folder appears and closes itself.
java -jar target/ErgPower-0.0.1-SNAPSHOT.jar capture

# Decode a previously saved raw capture into a session folder.
java -jar target/ErgPower-0.0.1-SNAPSHOT.jar replay <capture-or-raw.ndjson>

# Serve the browser dashboard + REST/SSE API on http://localhost:8080 (one process, one port).
# Open http://localhost:8080 and pick a source (a live PM5 or a stored-session replay). Starts idle.
java -jar target/ErgPower-0.0.1-SNAPSHOT.jar serve

# Usage / help.
java -jar target/ErgPower-0.0.1-SNAPSHOT.jar
```

## Live API (serve mode)

`serve` runs one reactive HTTP server that hosts **both** the browser dashboard (at `/`, bundled into
the jar) and the API (at `/api/v1`), on the same origin/port — no separate web server, proxy, or CORS.
The API contract is the leading OpenAPI document [`api/openapi.yaml`](api/openapi.yaml) (Spring server
interfaces and the browser's TS types are both generated from it). The dashboard is the primary UI;
you can also drive the API directly:

```sh
curl -s  http://localhost:8080/api/v1/connection       # PM5 connection status + firmware/profile
curl -s  http://localhost:8080/api/v1/live/snapshot     # full current state, for initial render
curl -N  http://localhost:8080/api/v1/live/stream       # multiplexed SSE: metrics, stroke, forceCurve, …
```

The SSE stream carries named events (`connection`, `workout`, `metrics`, `stroke`, `forceCurve`,
`heartbeat`); `metrics` is a full snapshot at the PM5 sample cadence. v1's live view is read-only.
Storage and the live API are independent subscribers to the same source, so a live source both
streams **and** records.

### Choosing a source

`serve` boots **idle** — no PM5 is contacted until you pick a source, so it starts with no hardware
present. The browser (or `curl`) selects one at runtime; both a live PM5 and a stored-session replay
feed the exact same live view.

```sh
# List stored, replayable sessions (those that kept raw frames).
curl -s  http://localhost:8080/api/v1/sessions

# Scan for nearby PM5s (for the connect picker).
curl -s  http://localhost:8080/api/v1/devices

# Connect to a live PM5 (optional {"device":"PM5 …"} to target one) — this also records.
curl -s -X POST http://localhost:8080/api/v1/source -H 'content-type: application/json' -d '{"type":"ble"}'

# Replay a stored session into the live view — no PM5 needed. speed is a real-time multiplier
# (1.0 = as recorded; omit for real time). Great for developing/demoing the viewer hardware-free.
curl -s -X POST http://localhost:8080/api/v1/source -H 'content-type: application/json' \
     -d '{"type":"replay","sessionId":"session-2026-07-28T10-00-00","speed":2}'

curl -s  http://localhost:8080/api/v1/source            # what's currently feeding the view
curl -s -X DELETE http://localhost:8080/api/v1/source    # stop the active source

# Download a stored session as a data-rich Garmin .FIT rowing activity — records (distance/speed/power/
# heart rate/stroke rate/stroke distance), rich per-split laps, a session summary, device/firmware, and
# rowing extras (drag, per-stroke drive force/time) as FIT developer fields. Upload to Strava / Garmin
# Connect / Concept2 Logbook. The viewer also shows a ".fit" link per session.
curl -s -o session.fit  http://localhost:8080/api/v1/sessions/<id>/export.fit

# Deterministic technique analysis of a session's force curves — Kleshnev-grounded scorecard (catch
# gradient, peak position, finish plateau, drive smoothness, recovery:drive rhythm…), a mean±band curve,
# drift trends including a per-quartile (Q1–Q4) progression that locates where technique drifts, a
# per-stroke heatmap, and fault flags. No model needed. The viewer shows an "Analyze" button per session.
curl -s  http://localhost:8080/api/v1/sessions/<id>/analysis

# Optional LLM coach (see "AI coach" below). Is a provider configured?
curl -s  http://localhost:8080/api/v1/integrations/llm            # {"configured":true,"provider":"ollama",...}
# Grounded natural-language coaching for a session (200 when configured; 409 when not).
curl -s  http://localhost:8080/api/v1/sessions/<id>/coach

# Cross-session index: every session with its cached technique scores, filterable by workout
# target type, distance band, and date range (see "Cross-session analysis" below).
curl -s  "http://localhost:8080/api/v1/sessions/index?targetType=distance&distanceMin=1500"
# A metric over time across sessions (technique metrics span the log; scope performance metrics).
curl -s  "http://localhost:8080/api/v1/trends?metric=catchGradient"
```

For a live source, sessions **start and stop automatically** from the PM5 workout state — just row.
Each piece is written to `sessions/session-<timestamp>/`.

The native bridge can also be run on its own (after `cargo build --release`):
```sh
./bridge/target/release/ergpower-bridge --scan   # list nearby PM5s (JSON on stdout)
```

## Configuration

All settings live under `ergpower.ble.*` (see `src/main/resources/application.properties`) and can
be overridden on the command line, e.g.:

```sh
java -jar target/ErgPower-0.0.1-SNAPSHOT.jar capture \
  --ergpower.ble.device.match=name \
  --ergpower.ble.device.name="PM5 432234859 Row" \
  --ergpower.ble.capture.sample-rate=250ms \
  --ergpower.ble.storage.dir=sessions
```

| Property | Default | Purpose |
|---|---|---|
| `device.match` | `first` | `first` \| `name` \| `peripheral-id` |
| `device.name` | – | advertised PM5 name/serial when `match=name` |
| `capture.sample-rate` | `500ms` | status sample rate (`1000ms`/`500ms`/`250ms`/`100ms`) |
| `capture.auto-session` | `true` | open/close a session folder from PM5 workout state |
| `connect.auto-reconnect` | `true` | survive PM5 drops mid-piece (exponential backoff) |
| `firmware.profile` | `auto` | `auto` \| `current` \| `reference` \| `<profile id>` |
| `storage.dir` | `sessions` | where session folders are written |

### AI coach (optional)

The technique analysis above is fully deterministic and needs no model. If — and only if — you
configure an LLM under `ergpower.llm.*`, the analysis view gains an **"AI Coach"** panel that turns
those numbers into grounded, plain-language coaching on demand. The coach consumes the *structured
analysis* (scorecard, feature stats, drift trends, fault flags) plus a distilled *session context* —
workout target, distance/time, average/peak power, pace, stroke rate, drag factor, a per-split summary,
and heart rate (average + drift) when a belt was worn — and a Kleshnev rubric. It never sees the raw
force curves or per-sample series, and is told to keep the **force-curve technique** as the subject,
using the context only to explain *why* the curve behaved as it did (pacing, fatigue, drag, effort).
With no provider set (`provider=none`, the default) the coach is disabled and nothing about the analysis
changes.

The panel has a **This session / Progress** toggle. *Progress* (`?mode=progress`) additionally grounds
your recent **same-type** sessions from the cross-session index — the most recent comparable pieces of
the same workout type within a distance/time band — and narrates what has improved, plateaued, or
regressed versus your own baseline. It needs a few comparable sessions; with too little history it falls
back to single-session coaching.

The AI layer runs on **[Spring AI](https://docs.spring.io/spring-ai/reference/) 2.0** (on Spring Boot
4.1); providers are selected with `spring.ai.*`. No chat model is active by default (`spring.ai.model.chat=none`),
so the coach is off until you configure one.

Set `ergpower.ai.language` to a natural-language name (e.g. `Dutch`) to have the coach and agent answer in
that language — only the prose is translated; metric names and numbers are kept as given. The **agent
formats its answers as Markdown** (headings/tables), rendered in the chat; the **coach stays plain prose**.

An optional **athlete profile** (`ergpower.athlete.*`: `weightKg`, `age`, `hrMax`, `goal`, …) unlocks
**watts/kg** (power ÷ weight), **HR zones** (Z1–Z5 from `hrMax`, else `220 − age`), and **goal-aware**
framing — fed to the coach and agent. Technique targets are unchanged (they're body-independent). Unset
fields simply omit their derived values.

**Ollama-first for privacy:** point it at a local [Ollama](https://ollama.com) and nothing leaves the
machine. Cloud providers (OpenAI-compatible or Anthropic) receive only the numeric analysis, and only
when you opt in by configuring one. Put settings — especially any API key — in the **git-ignored**
`./config/ergpower.local.properties` (auto-loaded via `spring.config.import`), not in the committed
`application.properties`:

```properties
# ./config/ergpower.local.properties  (git-ignored)
spring.ai.model.chat=ollama                        # none | ollama | openai | anthropic
spring.ai.ollama.base-url=http://localhost:11434   # a local or remote Ollama host
spring.ai.ollama.chat.options.model=llama3.1
# or a cloud provider (api key stays in this git-ignored file):
# spring.ai.model.chat=openai
# spring.ai.openai.api-key=...
# spring.ai.openai.chat.options.model=gpt-4o-mini
```

| Property | Default | Purpose |
|---|---|---|
| `spring.ai.model.chat` | `none` | `none` \| `ollama` \| `openai` \| `anthropic`; `none` disables the coach |
| `spring.ai.<provider>.chat.options.model` | – | e.g. `llama3.1`, `gpt-4o-mini`, `claude-sonnet-4-5` |
| `spring.ai.ollama.base-url` | `http://localhost:11434` | local or remote Ollama host |
| `spring.ai.<cloud>.api-key` | – | credential for `openai`/`anthropic` (keep in the local file) |

### AI agent (chat, optional)

When a provider is configured, the analysis view also gains an **"Ask about this session"** chat. It's an
interactive agent — the same Spring AI stack as the coach, but with **read-only tools** it calls on demand:
a session's `overview` and technique `analysis`, a `metrics` window, the `strokes` in a window, a single
stroke's `forceCurve`, plus cross-session `listSessions` / `compareSessions`. So it answers by time,
interval, stroke, or *across* sessions ("how does my catch compare to my last 2k?") — pulling exactly the
data a question needs rather than a fixed summary. Answers stream token-by-token.

Grounding and trust: the agent is told to answer from the tools (not invent numbers) and to use any web
content only as background. Tools are read-only and confined to the session store. The **conversation is
held in the browser** and sent each turn — nothing is persisted server-side, so the live API stays
read-only. As with the coach, a local Ollama keeps everything on your machine; a multi-turn chat may send
more of a session to a cloud provider than the one-shot coach, so opt in accordingly.

```sh
# Ask the agent (SSE stream of token/done events); the last message is the new question.
curl -N -X POST http://localhost:8080/api/v1/sessions/<id>/chat \
  -H 'content-type: application/json' \
  -d '{"messages":[{"role":"user","content":"How did my catch hold up in the second half?"}]}'
```

## Cross-session analysis

Each session's deterministic technique analysis is **computed once and cached** as `analysis.json` in the
session folder (stamped with an analyzer version; recomputed when the analysis logic changes). A
lightweight, rebuildable **rollup** (`sessions-index.json`) then indexes every session — start time,
workout target type/value, distance, duration, power, and the technique scores — so listing and trending
hundreds of sessions never re-runs the analysis (`GET /sessions/index`, `GET /trends`). Both files are
derived: delete them and the next read reproduces them.

Comparison uses **two lenses**, because the log is heterogeneous (varied distances and fixed times):

- **Technique-shape metrics** (catch gradient, peak position, finish plateau, mean/max ratio) are
  normalized as a percentage of the drive, so they compare honestly across *any* pieces — a
  `catchGradient` trend spans your whole log.
- **Performance metrics** (power, pace) are only comparable *within a workout type*, so scope them with
  the filters (`targetType`, `distanceMin/Max`, `from`/`to`).

The viewer splits the two mental modes into two screens. The per-session **Analysis** view stays focused
on one piece (coach and agent scoped to that session). A separate **Progress dashboard** (reached from
"View progress across sessions") lets you **pick a set of sessions**, see technique trends across them,
and get **progress coaching over the chosen set** (`POST /coach/progress`) plus a **set-scoped agent**
(`POST /chat`) — "which of these had my best finish?", "am I improving my catch?".

## What a session looks like

```
sessions/session-2026-07-28T15-33-35/
├── session.json              device, firmware, decoder profile, char→file map, provenance
├── summary.json              strokes, distance, duration, avg/peak power, force-curve count
├── status-general.ndjson     0x31: distance, states, workout target (time/distance), drag
├── status-additional1.ndjson 0x32: speed, stroke rate, heart rate, current/avg pace
├── status-additional2.ndjson 0x33: interval count, calories, split avg pace/power
├── stroke.ndjson             0x35: drive length/time, recovery, peak/avg drive force
├── stroke-additional.ndjson  0x36: power, stroke count, calories, projected time/distance
├── split.ndjson              0x37: per-split time/distance/type
├── split-additional.ndjson   0x38: per-split pace/power/spm/hr/speed
├── force-curve.ndjson         0x3D: reassembled per-stroke force curve (Newtons)
└── raw.ndjson                exact bridge frames — lets any session be re-decoded later
```

Every record carries the join keys `pmTime` (PM5 elapsed clock) + `hostTime`, and per-stroke records
carry `strokeCount` — so any subset of files recombines by time and/or stroke. **Units are SI**:
metres, seconds, watts, **Newtons** (force), bpm.

## Firmware profiles

The PM5 wire format is **firmware-dependent** (characteristic lengths/offsets drift between firmware
revisions). All firmware-specific interpretation is isolated behind a pluggable `FirmwareProfile`;
the correct one is selected per connection from the firmware revision (read from characteristic
`0x0014`), with a characteristic-length fingerprint fallback and a config override. `session.json`
records the firmware string and the profile used, and `raw.ndjson` makes captures re-decodable if a
profile is later corrected. Supporting a new firmware = adding one small profile class.

## Project layout

```
src/main/java/work/zing/ergpower/pm5/
├── event/       typed event model (Pm5Event sealed hierarchy)  — the stable contract
├── firmware/    FirmwareProfile + ReferenceRev130 / CurrentPm5 + registry
├── decode/      Pm5Decoder (orchestration: reassembly + correlation)
├── source/      Pm5Source seam: BlePm5Source (live), ReplayPm5Source (fast + timed)
├── storage/     SessionStorageWriter, SessionStorage
├── capture/     CaptureService, SessionManager (auto start/stop)
├── api/         REST + SSE: LiveState, SourceManager, SessionCatalog, controllers
├── config/      ErgPowerBleProperties (@ConfigurationProperties)
└── cli/         CaptureCommand (the CLI entry point)
bridge/          Rust BLE bridge (btleplug) — the native transport; built + bundled by `mvn package`
web/             React + Vite browser dashboard (configurable widgets; types generated from the spec)
openspec/        spec-driven change proposals + specs
docs/reference/  spec index + how to fetch the (git-ignored) vendor PDFs
src/test/resources/captures/  reference capture (real 289 m / 28-stroke row) anchoring the tests
```

## Development

- Tests decode a real captured row and assert exact values — the regression anchor for the decoder,
  force-curve reassembly, storage recombination, auto start/stop, and firmware-profile divergence.
- Targets **Java 21 (LTS)**; runs on 21 and newer. The BLE bridge is a Rust (`cargo`) subproject in
  `bridge/`, cross-platform via `btleplug`; v1 bundles the macOS binary (Linux/Windows are a
  binary-only add later).

## Reference specs

`docs/reference/README.md` indexes the Concept2 and Bluetooth SIG specifications used, with source
URLs. The PDFs themselves are **not committed** (third-party copyright) — fetch them from the listed
sources.

## Roadmap

- Typed decode of the workout **summary** characteristics (`0x39/3A/3C`) and HR-belt (`0x3B`), which
  would light up the dashboard's registered-but-disabled splits/summary widgets.
- Firmware `claims(...)` matching once the current firmware string is catalogued.
- Server-persisted / shareable dashboards (the viewer persists layouts to localStorage today).

## License / attribution

Concept2 and Bluetooth SIG specifications are the property of their respective owners; ErgPower is an
independent project and is not affiliated with or endorsed by Concept2.
