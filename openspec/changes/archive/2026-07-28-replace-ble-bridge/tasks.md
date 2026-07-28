## 0. Spike (de-risk before committing)

- [x] 0.1 Prove `btleplug` connects to the real PM5 on macOS: scan → connect → discover the Concept2
      service → subscribe → print a few notifications; confirm bytes match the current bridge's frames
      — DONE: connected to PM5 432234859; 0x0031/0x0032/0x0033 byte-lengths (19/17/20) match the
      bleak fixture exactly, so the existing decoder is unchanged.

## 1. Native bridge (Rust / btleplug)

- [x] 1.1 Scaffold `bridge/` (cargo) with `btleplug`; CLI mirroring `bridge.py` (`--name`,
      `--sample-rate-ms`, `--no-reconnect`, `--backoff-min/max`, `--scan`)
- [x] 1.2 Scan → connect → discover the Concept2 service → subscribe to all data characteristics →
      forward NDJSON frames (`hostTime`, `mono`, `uuid`, hex `bytes`) byte-identical to `bridge.py`
- [x] 1.3 Meta lines: connect meta (name/address/firmware read from `0x0014`) + connection-state
      transitions (searching/connected/disconnected/reconnecting)
- [x] 1.4 stdin command channel (`sample_rate` write to `0x0034`; generic `write` uuid/hex);
      auto-reconnect with backoff
- [x] 1.5 `--scan` mode → one JSON device per line on stdout (`name`, `address`, `rssi`)

## 2. JVM integration (keep the seam)

- [x] 2.1 `BlePm5Source` launches the bundled native binary instead of `uv run python bridge.py`;
      stdout reader / decoder path unchanged
- [x] 2.2 `ErgPowerBleProperties`: drop uv/python knobs (`bridge.dir`, `bridge.uvCommand`); add a binary
      path/override; `SourceManager.scanDevices` uses the native `--scan`
- [x] 2.3 Binary resolution: pick `native/<os>-<arch>/ergpower-bridge[.exe]` by `os.name`/`os.arch`,
      extract from the jar to a temp dir, mark executable, exec; clear error on an unsupported platform

## 3. Build + packaging

- [x] 3.1 Cargo build of the Rust bridge bound into the **single Maven build** (like the frontend step,
      at `prepare-package` so `test` stays fast) → one `./mvnw package` builds Java + web + bridge;
      `-Dnative.skip` reuses prebuilt binaries for offline builds
- [x] 3.2 Bundle the **macOS arm64/x64** binaries in the jar under `native/<os>-<arch>/`; keep the layout
      + os/arch selection generic so adding Linux/Windows later is a CI-target addition (no code change)

## 4. Retire Python

- [x] 4.1 Delete `ble-bridge/` (bridge.py, pyproject, uv) outright — **no fallback** — once the native
      bridge is validated on macOS; move the reference `captures/` fixtures to `src/test/resources/`
      (update the test paths that load them)
- [x] 4.2 Docs: README requirements (runtime = JVM only, no Python/uv; macOS now, extensible later; build
      needs a Rust toolchain, skippable); update project layout

## 5. Verify

- [x] 5.1 Frame-parity: the native bridge's output decodes to the same typed events as the reference
      fixture (decoder + tests unchanged)
- [x] 5.2 Live smoke on macOS (arm64) against the real PM5 (Linux/Windows deferred to when those targets
      are added)
- [x] 5.3 Confirm a packaged run needs only the JVM — no Python/`uv` on `PATH`
