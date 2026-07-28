# Requesting the current PM5 BLE interface definition

The newest public copy of the *PM Bluetooth Smart Communication Interface Definition* is
**rev 1.30 (2022-03-02)** — stored in this folder. It predates the **September 2025 PM5 firmware**
that added a new force-curve BLE data format. Use the drafts below to obtain the current revision.

Before sending, fill in `<PM5 FIRMWARE VERSION>`
(PM5 → **Menu → More Options → Utilities → Product ID / Firmware Version**).

---

## Option A — Email to rowing@concept2.com

**To:** rowing@concept2.com
**Subject:** Request: current PM5 Bluetooth Smart Interface Definition (updated force-curve format)

Hello,

I'm developing an application that connects to a PM5 over Bluetooth Low Energy to record
rowing-session data, including the per-stroke force curve.

The most recent public copy of the *PM Bluetooth Smart Communication Interface Definition* I can
find is **revision 1.30 (2022-03-02)**. Your PM5 firmware timeline notes that a **new force-curve
data format for BLE was added in a September 2025 firmware release**, which rev 1.30 predates.

Could you please send me the **current** revision of the interface definition, or point me to where
the up-to-date version is published? I'm specifically after the updated **force-curve characteristic
(0x003D)** byte layout, but the complete current document would be ideal.

For reference, my PM5 is on firmware version **<PM5 FIRMWARE VERSION>**.

Thank you very much,
Martin Oosterom
w.m.oosterom@doubleforge.com

---

## Option B — Short post for c2forum.com (developer thread)

**Title:** Current PM5 BLE interface definition — updated force-curve (0x003D) format?

Hi all — I'm reading PM5 rowing data over BLE, including the per-stroke force curve. The newest
public *PM Bluetooth Smart Interface Definition* I have is **rev 1.30 (2022)**, but the firmware
timeline says a **new force-curve BLE data format** landed in the **Sept 2025** firmware.

Is there an updated interface definition available, and does the new force-curve format extend the
existing `0x003D` characteristic or add a new one? My PM5 is on firmware **<PM5 FIRMWARE VERSION>**.
Any pointer to the current byte layout would be hugely appreciated. Thanks!

---

## When the reply arrives
1. Save the new PDF into `docs/reference/concept2/` named with its revision
   (e.g. `Concept2-PM5-Bluetooth-Smart-Interface-Definition-rev1.3X.pdf`) and **remove rev 1.30**
   (latest-only policy — see `../README.md`).
2. Update `openspec/changes/add-pm5-capture-storage/design.md` → "Verified PM5 wire facts" and
   "Open Questions" with the confirmed force-curve format + version.
3. Bump the force-curve decoder version and record the format version in `session.json`.
