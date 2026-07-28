#!/usr/bin/env python3
"""
ErgPower BLE bridge — the machine-facing transport.

Connects to a Concept2 PM5 over BLE and forwards **raw** notification frames as NDJSON on stdout,
one per line; all logs go to stderr. It does NO decoding — the JVM (`BlePm5Source`) parses everything.

Robustness for long pieces:
  - auto-reconnect with exponential backoff if the PM5 drops (the erg keeps its own workout clock,
    so pmTime stays continuous across a reconnect);
  - a stdin command channel (one JSON command per line) to set the sample rate or write CSAFE.

stdout lines:
  frame : {"hostTime": ISO, "mono": seconds, "uuid": full-uuid, "bytes": hex}   (same as saved captures)
  meta  : {"meta": "device", "name": ..., "address": ...}
          {"meta": "state",  "state": searching|connected|disconnected|reconnecting}

stdin commands (from the JVM):
  {"cmd": "sample_rate", "ms": 500}                 # write PM5 0x0034 (1000|500|250|100)
  {"cmd": "write", "uuid": "<uuid>", "hex": "<..>"} # generic write, e.g. CSAFE to 0x0021

Usage:
    uv run python bridge.py [--name "PM5 …"] [--seconds N] [--sample-rate-ms 500] [--no-reconnect]
    uv run python bridge.py --scan
"""

from __future__ import annotations

import argparse
import asyncio
import json
import sys
import time
from datetime import datetime, timezone

from bleak import BleakClient, BleakScanner

C2_BASE = "ce06{:04x}-43e5-11e4-916c-0800200c9a66"
SAMPLE_RATE_UUID = C2_BASE.format(0x0034)
FIRMWARE_UUID = C2_BASE.format(0x0014)   # C2 firmware revision string (device info)

# Subscribe to ALL C2 data characteristics (incl. HR-belt 0x3B and summary-2 0x3C); the JVM decides
# what to decode. Nothing is dropped at the transport.
DATA_CHARS = [C2_BASE.format(cid) for cid in
              (0x0031, 0x0032, 0x0033, 0x0035, 0x0036, 0x0037, 0x0038,
               0x0039, 0x003A, 0x003B, 0x003C, 0x003D)]

# 0x0034 sample-rate byte per interface definition rev 1.30.
RATE_BYTE = {1000: 0, 500: 1, 250: 2, 100: 3}


def log(*a):
    print(*a, file=sys.stderr, flush=True)


def out(obj: dict):
    sys.stdout.write(json.dumps(obj) + "\n")
    sys.stdout.flush()


def state(s: str):
    out({"meta": "state", "state": s})


def emit(uuid: str, data: bytearray, start_mono: float):
    out({
        "hostTime": datetime.now(timezone.utc).isoformat(),
        "mono": round(time.perf_counter() - start_mono, 6),
        "uuid": uuid,
        "bytes": bytes(data).hex(),
    })


async def find_pm5(name: str | None, timeout: float):
    log(f"scanning {timeout:.0f}s for a PM5{f' named {name!r}' if name else ''}...")
    devices = await BleakScanner.discover(timeout=timeout, return_adv=True)
    for address, (dev, adv) in devices.items():
        adv_name = adv.local_name or (dev.name if dev else None) or ""
        if (name and adv_name == name) or (not name and adv_name.upper().startswith("PM5")):
            return dev, adv_name
    return None, None


async def scan():
    devices = await BleakScanner.discover(timeout=8.0, return_adv=True)
    for address, (dev, adv) in devices.items():
        nm = adv.local_name or (dev.name if dev else None) or "?"
        if str(nm).upper().startswith("PM5"):
            log(f"PM5: {nm}  {address}  rssi={adv.rssi}")


async def write_sample_rate(client, ms: int):
    b = RATE_BYTE.get(ms)
    if b is None:
        log(f"unsupported sample rate {ms}ms (use 1000/500/250/100); leaving PM5 default")
        return
    try:
        await client.write_gatt_char(SAMPLE_RATE_UUID, bytes([b]), response=True)
        log(f"sample rate set to {ms}ms (0x0034={b})")
    except Exception as e:  # noqa: BLE001
        log(f"failed to set sample rate: {e}")


async def read_commands(get_client, rate_ref):
    """Read one JSON command per line from stdin and act on it against the live client."""
    loop = asyncio.get_running_loop()
    reader = asyncio.StreamReader()
    try:
        await loop.connect_read_pipe(lambda: asyncio.StreamReaderProtocol(reader), sys.stdin)
    except Exception as e:  # noqa: BLE001 - some environments have no usable stdin pipe
        log(f"stdin command channel unavailable: {e}")
        return
    while True:
        raw = await reader.readline()
        if not raw:
            return
        line = raw.decode(errors="replace").strip()
        if not line:
            continue
        try:
            cmd = json.loads(line)
        except Exception:
            log(f"bad command: {line}")
            continue
        c = cmd.get("cmd")
        client = get_client()
        if c == "sample_rate":
            rate_ref[0] = int(cmd.get("ms", 500))
            if client:
                await write_sample_rate(client, rate_ref[0])
        elif c == "write" and cmd.get("uuid") and cmd.get("hex"):
            if client:
                try:
                    await client.write_gatt_char(cmd["uuid"], bytes.fromhex(cmd["hex"]), response=True)
                    log(f"wrote {cmd['hex']} -> {cmd['uuid']}")
                except Exception as e:  # noqa: BLE001
                    log(f"write failed: {e}")
        else:
            log(f"ignored command: {line}")


async def _sleep_backoff(backoff: float, deadline: float | None) -> bool:
    """Sleep up to `backoff`; return False if the overall deadline has passed."""
    if deadline is not None:
        remaining = deadline - time.perf_counter()
        if remaining <= 0:
            return False
        await asyncio.sleep(min(backoff, remaining))
        return time.perf_counter() < deadline
    await asyncio.sleep(backoff)
    return True


async def _wait_disconnect_or_deadline(disconnected: asyncio.Event, deadline: float | None):
    if deadline is not None:
        remaining = deadline - time.perf_counter()
        if remaining <= 0:
            return
        try:
            await asyncio.wait_for(disconnected.wait(), timeout=remaining)
        except asyncio.TimeoutError:
            pass
    else:
        await disconnected.wait()


async def run(name, seconds, sample_rate_ms, reconnect, backoff_min, backoff_max):
    start_mono = time.perf_counter()
    deadline = (start_mono + seconds) if seconds > 0 else None
    rate_ref = [sample_rate_ms]
    holder = {"client": None}
    cmd_task = asyncio.create_task(read_commands(lambda: holder["client"], rate_ref))
    backoff = backoff_min
    try:
        while True:
            if deadline is not None and time.perf_counter() >= deadline:
                break
            state("searching")
            scan_to = 10.0 if deadline is None else max(1.0, min(10.0, deadline - time.perf_counter()))
            dev, adv_name = await find_pm5(name, timeout=scan_to)
            if dev is None:
                log("no PM5 found (on? idle? in range?)")
                if not reconnect:
                    break
                state("reconnecting")
                if not await _sleep_backoff(backoff, deadline):
                    break
                backoff = min(backoff * 2, backoff_max)
                continue

            disconnected = asyncio.Event()

            def on_disconnect(_client):
                log("PM5 disconnected")
                state("disconnected")
                disconnected.set()

            try:
                async with BleakClient(dev, disconnected_callback=on_disconnect) as client:
                    holder["client"] = client
                    firmware = None
                    try:
                        raw = await client.read_gatt_char(FIRMWARE_UUID)
                        firmware = raw.decode("utf-8", errors="replace").replace("\x00", "").strip() or None
                    except Exception as e:  # noqa: BLE001
                        log(f"could not read firmware: {e}")
                    out({"meta": "device", "name": adv_name, "address": str(dev.address), "firmware": firmware})
                    state("connected")
                    log(f"connected to {adv_name} @ {dev.address}")
                    if rate_ref[0] is not None:
                        await write_sample_rate(client, rate_ref[0])
                    for uuid in DATA_CHARS:
                        try:
                            await client.start_notify(uuid, lambda s, d: emit(str(s.uuid), d, start_mono))
                        except Exception as e:  # noqa: BLE001
                            log(f"skip {uuid}: {e}")
                    log("streaming frames on stdout")
                    backoff = backoff_min  # reset after a healthy connection
                    await _wait_disconnect_or_deadline(disconnected, deadline)
            except Exception as e:  # noqa: BLE001
                log(f"connection error: {e}")
            finally:
                holder["client"] = None

            if not reconnect:
                break
            if deadline is not None and time.perf_counter() >= deadline:
                break
            state("reconnecting")
            if not await _sleep_backoff(backoff, deadline):
                break
            backoff = min(backoff * 2, backoff_max)
    finally:
        cmd_task.cancel()
    log("bridge stopping")


def main():
    p = argparse.ArgumentParser(description="ErgPower PM5 BLE bridge")
    p.add_argument("--name", default=None, help="advertised PM5 name to connect to")
    p.add_argument("--seconds", type=float, default=0.0, help="stop after N seconds (0 = until killed)")
    p.add_argument("--sample-rate-ms", type=int, default=None, help="status sample rate: 1000|500|250|100")
    p.add_argument("--no-reconnect", action="store_true", help="do not auto-reconnect on disconnect")
    p.add_argument("--backoff-min", type=float, default=2.0, help="reconnect backoff floor (s)")
    p.add_argument("--backoff-max", type=float, default=30.0, help="reconnect backoff ceiling (s)")
    p.add_argument("--scan", action="store_true", help="list nearby PM5s and exit")
    args = p.parse_args()
    try:
        if args.scan:
            asyncio.run(scan())
        else:
            asyncio.run(run(args.name, args.seconds, args.sample_rate_ms,
                            not args.no_reconnect, args.backoff_min, args.backoff_max))
    except KeyboardInterrupt:
        log("interrupted")


if __name__ == "__main__":
    main()
