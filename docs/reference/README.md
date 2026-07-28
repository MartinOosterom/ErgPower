# Reference specifications

Vendor and standards specification documents used while designing ErgPower, kept locally so the
design and decoder work can be checked against the source of truth without re-fetching.

**Policy: latest editions only.** Every file here must be the newest available version of its
document. When a newer edition is published, replace the file (and its date in the tables below).
The "Refresh" URLs are *living* links that always serve the current revision — re-pull them
periodically. Verified current as of **2026-07-28**.

> Third-party documents reproduced for reference only; copyright remains with Concept2 / Bluetooth SIG.

```
docs/reference/
├── concept2/         Concept2 PM5-specific specs (what ErgPower actually talks to)
└── bluetooth-sig/    Standard BLE fitness/trainer profiles + registries (companions)
```

## concept2/

| File | Document | Revision | Date | Status |
|------|----------|----------|------|--------|
| `Concept2-PM5-Bluetooth-Smart-Interface-Definition-rev1.30.pdf` | PM Bluetooth Smart Interface Definition | **1.30** | 2022-03-02 | ⚠️ **latest *public* copy, but stale** — see below |
| `Concept2-PM-CSAFE-Communication-Definition-2026-03.pdf` | PM CSAFE Communication Definition | **0.34** | 2025-07-17 | ✅ latest |

- CSAFE refresh: https://cms.concept2.com/sites/default/files/2026-03/Concept2%20PM%20CSAFE%20Communication%20Definition.pdf
- BLE def source: `http://www.concept2.co.in/files/pdf/us/monitors/PM5_BluetoothSmartInterfaceDefinition.pdf` (mirror). Landing: https://www.concept2.com/support/software-development

> ⚠️ **The PM5 BLE definition is not current.** rev 1.30 (2022) is the newest *publicly downloadable*
> copy, but it predates the **Sept-2025 firmware** that added a new force-curve BLE format. Both
> public Concept2 SDK downloads are the "outdated" ones (Mac SDK = 2010/USB, pre-BLE; Windows =
> InstallShield bundle). **The current definition must be requested from Concept2** — see
> "Getting the current PM5 BLE definition" below. Until then, treat force-curve decode as versioned
> and confirm against firmware.

## bluetooth-sig/

Standard Bluetooth SIG documents a "smart trainer"/fitness machine may expose — companions to
Concept2's proprietary profile, and the reference for any HR-strap / bike-sensor capture later.

| File | Document | Version | Date | Notes |
|------|----------|---------|------|-------|
| `Bluetooth-SIG-GATT-Specification-Supplement-2026-02-05.pdf` | GATT Specification Supplement (GSS) | **2026-02-05** | 2026-02-05 | ✅ **Primary reference** — byte formats for all characteristics: FTMS Rower/Indoor-Bike Data, HR Measurement, CP/CSC Measurement. Living doc. |
| `Bluetooth-SIG-Assigned-Numbers-2026-07-22.pdf` | Assigned Numbers | **2026-07-22** | 2026-07-22 | ✅ latest — all 16-bit service/characteristic UUIDs. Living doc. |
| `Bluetooth-SIG-Fitness-Machine-Service-FTMS-v1.0.1.pdf` | Fitness Machine Service (FTMS) | **v1.0.1** | 2024-10-01 | ✅ latest revision of the service. |
| `Bluetooth-SIG-Heart-Rate-Service-HRS-v1.0.pdf` | Heart Rate Service (`0x180D`) | **v1.0** | 2011-07-12 | ✅ latest — HRS has never been revised past 1.0. |
| `Bluetooth-SIG-Heart-Rate-Profile-HRP-v1.0.pdf` | Heart Rate Profile | **v1.0** | 2011-07-12 | ✅ latest — not superseded. |
| `Bluetooth-SIG-Cycling-Speed-and-Cadence-Service-CSCS-v1.0.pdf` | Cycling Speed & Cadence Service (`0x1816`) | **v1.0** | 2012-08-21 | ✅ latest — not superseded. |
| `Bluetooth-SIG-Cycling-Speed-and-Cadence-Profile-CSCP-v1.0.pdf` | Cycling Speed & Cadence Profile | **v1.0** | 2012-08-21 | ✅ latest — not superseded. |

The 2011/2012 dates are not stale: the SIG never issued newer versions of those services, and their
current **characteristic byte formats live in the GSS above** (the living doc), which is current.

### Refresh (living URLs — always the latest)
- GSS: https://btprodspecificationrefs.blob.core.windows.net/gatt-specification-supplement/GATT_Specification_Supplement.pdf
- Assigned Numbers: https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Assigned_Numbers/out/en/Assigned_Numbers.pdf
- FTMS: https://www.bluetooth.com/specifications/specs/fitness-machine-service-1-0/ (mirror used: `https://www.fitmonster.club/SDK/FTMS_v101.pdf`)
- HRS/HRP: `bluetooth.org/docman` doc_id 239866 / 239865 · CSCS/CSCP: doc_id 261450 / 261449

### Not stored (gated / not needed here)
- **Cycling Power Service (CPS, `0x1818`)** clean spec — old docman links retired, current copy HTML-only. Its CP Measurement byte format is in the **GSS** (stored). Landing: https://www.bluetooth.com/specifications/specs/cycling-power-service-1-1/

## Getting the current PM5 BLE definition

The post-Sept-2025 interface definition is not on the public site. Request it via, in order of likelihood:
1. **Email `rowing@concept2.com`** (Concept2 handles developer/SDK requests here) — ask for the *current* PM Bluetooth Smart Interface Definition, specifically the updated force-curve format. **Ready-to-send email + forum drafts:** `concept2/REQUEST-latest-BLE-def.md`.
2. **Concept2 developer forum** — https://www.c2forum.com (the "Ergs, Erg Accessories..." / developer threads; C2 interface authors respond there).
3. **Concept2 Software Development page** — https://www.concept2.com/support/software-development (watch for an updated SDK/PDF).

## Related project docs
- `openspec/changes/add-pm5-capture-storage/design.md` — "Verified PM5 wire facts (rev 1.30)" cites `concept2/`.
