#!/usr/bin/env python3
"""
PM5 BLE spike — de-risks the project's #1 assumption:
    "Does `bleak` connect to the PM5 over CoreBluetooth on macOS and stream notifications?"

This is a throwaway validation tool (task 1 of add-pm5-capture-storage), but it lives in
`ble-bridge/` because it seeds the real bridge: scan -> connect -> subscribe -> log raw frames.
It performs NO real decoding beyond a sanity read of general-status elapsed-time/distance so you
can see numbers move while you row.

Usage (from ble-bridge/):
    uv run python spike.py --scan                 # list nearby BLE devices; find your "PM5 <serial>"
    uv run python spike.py                         # auto-connect to the first PM5 found, log for 60s
    uv run python spike.py --name "PM5 430123456"  # connect to a specific PM5 by advertised name
    uv run python spike.py --seconds 120 --out capture.ndjson   # log longer + save raw frames

Prerequisites:
  - PM5 powered on and NOT already connected to another app/phone (it advertises when idle).
  - macOS Bluetooth ON, and the TERMINAL APP running this must be granted Bluetooth permission
    (System Settings -> Privacy & Security -> Bluetooth). The first run usually triggers a prompt;
    if you see zero devices, that permission is the most likely cause.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import time
from datetime import datetime, timezone

from bleak import BleakScanner, BleakClient
from bleak.backends.characteristic import BleakGATTCharacteristic

# Concept2 PM base UUID: CE06XXXX-43E5-11E4-916C-0800200C9A66 (rev 1.30).
C2_BASE = "ce06{:04x}-43e5-11e4-916c-0800200c9a66"


def c2(short: int) -> str:
    return C2_BASE.format(short)


# Data characteristics we care about for the spike (all stable in rev 1.30).
DATA_CHARS = {
    c2(0x0031): "general-status(0x31)",
    c2(0x0032): "additional-status1(0x32)",
    c2(0x0033): "additional-status2(0x33)",
    c2(0x0035): "stroke-data(0x35)",
    c2(0x0036): "additional-stroke(0x36)",
    c2(0x0037): "split-data(0x37)",
    c2(0x0038): "additional-split(0x38)",
    c2(0x0039): "workout-summary(0x39)",
    c2(0x003A): "additional-summary(0x3A)",
    c2(0x003D): "force-curve(0x3D)",
}


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


async def scan(timeout: float = 8.0) -> None:
    print(f"Scanning {timeout:.0f}s for BLE devices...\n")
    devices = await BleakScanner.discover(timeout=timeout, return_adv=True)
    if not devices:
        print("No BLE devices found. If your Mac's Bluetooth is on, this is almost certainly the")
        print("Bluetooth permission for your terminal app (Privacy & Security -> Bluetooth).")
        return
    rows = []
    for address, (dev, adv) in devices.items():
        name = adv.local_name or (dev.name if dev else None) or "?"
        pm5 = "  <-- PM5" if str(name).upper().startswith("PM5") else ""
        rows.append((str(name), address, adv.rssi, pm5))
    rows.sort(key=lambda r: (not r[3], r[0]))
    print(f"{'NAME':<24} {'ADDRESS (CoreBluetooth UUID)':<40} RSSI")
    print("-" * 78)
    for name, address, rssi, pm5 in rows:
        print(f"{name:<24} {address:<40} {rssi!s:>4}{pm5}")


async def find_pm5(name: str | None, timeout: float = 10.0):
    print(f"Scanning {timeout:.0f}s for a PM5{f' named {name!r}' if name else ''}...")
    devices = await BleakScanner.discover(timeout=timeout, return_adv=True)
    for address, (dev, adv) in devices.items():
        adv_name = adv.local_name or (dev.name if dev else None) or ""
        if name:
            if adv_name == name:
                return dev, adv_name
        elif adv_name.upper().startswith("PM5"):
            return dev, adv_name
    return None, None


def make_handler(out):
    counters: dict[str, int] = {}

    def handler(sender: BleakGATTCharacteristic, data: bytearray):
        uuid = str(sender.uuid).lower()
        label = DATA_CHARS.get(uuid, f"unknown({uuid})")
        counters[label] = counters.get(label, 0) + 1
        raw = bytes(data)

        # Sanity decode of general status so you can see real movement while rowing.
        extra = ""
        if uuid == c2(0x0031) and len(raw) >= 6:
            elapsed = int.from_bytes(raw[0:3], "little") / 100.0   # 0.01 s
            dist = int.from_bytes(raw[3:6], "little") / 10.0        # 0.1 m
            extra = f"  t={elapsed:7.2f}s  dist={dist:8.1f}m"

        print(f"[{counters[label]:4d}] {label:<24} {len(raw):3d}B  {raw.hex()}{extra}")

        if out is not None:
            out.write(json.dumps({
                "hostTime": now_iso(),
                "mono": round(time.perf_counter(), 6),
                "uuid": uuid,
                "char": label,
                "bytes": raw.hex(),
            }) + "\n")
            out.flush()

    return handler


async def run(name: str | None, seconds: float, out_path: str | None) -> None:
    dev, adv_name = await find_pm5(name, timeout=10.0)
    if dev is None:
        print("No PM5 found. Is it on, idle (not connected elsewhere), and in range?")
        print("Try `uv run python spike.py --scan` to see what's advertising.")
        return

    print(f"Found {adv_name} @ {dev.address}. Connecting...")
    out = open(out_path, "w") if out_path else None
    try:
        async with BleakClient(dev) as client:
            print(f"Connected: {client.is_connected}. Subscribing to data characteristics...")
            subscribed = []
            for uuid, label in DATA_CHARS.items():
                try:
                    await client.start_notify(uuid, make_handler(out))
                    subscribed.append(label)
                except Exception as e:  # noqa: BLE001 - spike: report and continue
                    print(f"  (skip {label}: {e})")
            print(f"Subscribed to: {', '.join(subscribed)}")
            print(f"\n>>> ROW NOW <<<  logging for {seconds:.0f}s (Ctrl-C to stop early)\n")
            try:
                await asyncio.sleep(seconds)
            except asyncio.CancelledError:
                pass
    finally:
        if out is not None:
            out.close()
            print(f"\nRaw frames written to {out_path}")


def main() -> None:
    p = argparse.ArgumentParser(description="PM5 BLE spike")
    p.add_argument("--scan", action="store_true", help="list nearby BLE devices and exit")
    p.add_argument("--name", default=None, help="connect to a specific PM5 advertised name")
    p.add_argument("--seconds", type=float, default=60.0, help="how long to log (default 60)")
    p.add_argument("--out", default=None, help="write raw frames to this NDJSON file")
    args = p.parse_args()

    try:
        if args.scan:
            asyncio.run(scan())
        else:
            asyncio.run(run(args.name, args.seconds, args.out))
    except KeyboardInterrupt:
        print("\nStopped.")


if __name__ == "__main__":
    main()
