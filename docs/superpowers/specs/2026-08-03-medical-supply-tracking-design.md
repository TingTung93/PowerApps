# Medical Supply Tracking — Design Spec

**Date:** 2026-08-03
**Status:** Approved (design), pending spec review
**Author:** danangboy13@gmail.com (with Claude)

## 1. Purpose & context

Re-create the existing PowerApps medical supply / inventory tracker (originally the
"MAMC TS and Tissue Bank" supply tracker, Dataverse tables `cr31c_*`) as a portable,
Java 8-compatible desktop/browser application. The production environment supports
Java 8 only and has **no shared database**: data is synchronized by having every
operator sync a shared Teams/SharePoint folder to their workstation via OneDrive, then
running the app locally pointed at that folder. Multiple operators run the app
independently and concurrently from their own workstations.

This app is the medical-supply counterpart to the existing `commercial-tracking-java`
project and reuses its portability blueprint and storage engine wherever possible.

### Goals
- Mirror the core workflows of the PowerApp: scan-to-stock, batch scan, browse/search
  with expiry alerts, product registration, and printable QR labels.
- Back product registration with the **FDA AccessGUDID** device database, while
  remaining fully usable offline.
- Run on Java 8 with no Maven/Gradle, distributed as a single JAR + launcher, using a
  OneDrive-synced folder as the shared store.

### Non-goals (carried from commercial-tracking-java)
- No strong cross-device locking (the event-sourced model makes it unnecessary).
- No direct SharePoint / Microsoft Graph / Dataverse API calls; filesystem only.
- No verified Microsoft identity (actor is a configured display name).
- GUDID is **never required** to operate — it is best-effort enrichment only.
- No automated event retention/archival (events are immutable).

## 2. Source-of-truth data model (from the PowerApp)

Two Dataverse entities in the original app, both reproduced as **projections** here:

**Product catalog** (`GTIN_Lookups`)
| Field | Notes |
|---|---|
| GTIN (`cr31c_gtin`) | key, required |
| ItemName (`cr31c_name`) | required |
| ItemManufacturer (`cr31c_itemmanufacturer`) | |
| UnitPrice (`cr31c_unitprice`) | currency |
| ItemCategory (`cr31c_itemcategory`) | multi-select choice |
| PAR (`cr31c_par`) | number; reorder/par level |
| Notes (`cr31c_notes`) | |

**Inventory / stock** (`Inventories`)
| Field | Notes |
|---|---|
| Barcode (`cr31c_barcode`) | full scanned GS1 string |
| ItemID (`cr31c_itemid`) | GTIN (links to catalog) |
| Name (`cr31c_name`) | denormalized product name |
| Manufacturer_Lookup (`cr31c_manufacturer_lookup`) | denormalized |
| Batch (`cr31c_batch`) | lot number (GS1 AI 10) |
| Expiration (`cr31c_expiration`) | datetime (GS1 AI 17) |
| Quantity (`cr31c_quantity`) | running total (case/bx/sleeve) |
| CleanedBarcode (`cr31c_cleanedbarcode`) | GTIN + Batch |
| Notes/Comments (`cr31c_notescomments`) | |
| Modified On / Modified By | system audit |

Derived UI states from the PowerApp we must preserve:
- **Expiring soon**: `Expiration <= today+7` → red; `<= today+30` → yellow.
- **Expired**: `Expiration < today`.
- **Low quantity**: `Quantity < 1` → red.
- **Stale**: `Modified On` older than 7/14 days → caution/alert tint.

## 3. Architecture: event-sourced store on OneDrive

### 3.1 Reused engine
Port/reuse from `commercial-tracking-java` with minimal changes:
`EventStore` (atomic append: local `pending/<id>.tmp` → `fsync` → shared
`<file>.partial` → atomic move; dedup by `eventId` + content hash; malformed-event
diagnostics), `LocalEventIndex` (rebuildable local cache), `AppConfig` +
`SharedConfigManager` (per-user `%LOCALAPPDATA%\MedicalSupply`, shared settings with
rollback), `BrowserServer` (loopback SPA host), `PortablePdf`, background 15s refresh,
offline `retryPending`.

Shared root layout (created on first run):
```
<shared-root>/
  events/YYYY/MM/*.json    # immutable event files, one per event
  reports/
  configuration/
  diagnostics/
```

### 3.2 Event envelope
Same shape as `TrackingEvent`: `schemaVersion`, `eventId` (UUID), `eventType`,
`occurredUtc`, `recordedUtc`, `deviceId`, `sessionId`, `actor`, plus type-specific
payload fields. Files named `<utc>_<device>_<eventId>_<type>.json`.

### 3.3 Event types
Catalog:
- `PRODUCT_REGISTERED` — `gtin, name, manufacturer, category[], unitPrice, par, notes, source(GUDID|MANUAL)`
- `PRODUCT_UPDATED` — same payload; edits an existing catalog record

Stock (payload carries `itemKey`, `gtin`, `lot`, `expiration`, `barcode`):
- `STOCK_RECEIVED` — `quantityDelta` (+N)
- `STOCK_PICKED` — `quantityDelta` (−N)
- `STOCK_ADJUSTED` — `quantityAbsolute` (SET; manual quantity edit)
- `STOCK_ARCHIVED` — `reason`
- `STOCK_VOIDED` — `reason`

### 3.4 Item identity
`itemKey = gtin + "|" + lot + "|" + expiration(yyyymmdd)`. A different lot or
expiration is a distinct stock line — this matches the PowerApp keying `Inventories`
by the full barcode (which encodes GTIN + lot + expiry). Empty lot/expiry are allowed
and normalized to empty strings.

### 3.5 Projections (`Projection`)
- **Catalog**: for each GTIN, the latest `PRODUCT_*` event (by `occurredUtc` then
  `recordedUtc`) wins.
- **Inventory**: group stock events by `itemKey`, replay in order; `STOCK_ADJUSTED`
  sets the running quantity, `RECEIVED`/`PICKED` apply deltas, `ARCHIVED`/`VOIDED`
  mark the line inactive. Enrich each line with catalog data by GTIN (name,
  manufacturer, category, PAR). Conflicting/duplicate-id events surface in diagnostics.

## 4. GS1 barcode parser (`Gs1Parser`)

Decode GS1-128 / DataMatrix payloads used on medical device labels:
- AI 01 → GTIN-14
- AI 17 → expiration (YYMMDD)
- AI 10 → lot/batch (variable length)
- AI 21 → serial, AI 30 → count (captured, optional)

**Implementation:** a proper Application-Identifier table parse that honors the FNC1 /
GS group separator (ASCII 29) that scanners emit to terminate variable-length fields.
Falls back to the PowerApp's positional `Find`-based heuristic when no GS is present.
This is deliberately more robust than the PowerApp, whose `Find("17")`/`Find("10")`
logic mis-parses when a lot value contains those digits. Strips `()` AI delimiters.
Ambiguous/low-confidence parses return a result flagged for user confirmation
(mirroring `ParseResult` in commercial tracking).

## 5. FDA GUDID integration (`GudidClient`)

**API:** `GET https://accessgudid.nlm.nih.gov/api/v3/devices/lookup.json?di={GTIN}`
(public AccessGUDID Device Lookup API v3; no key/auth).

**Field mapping → catalog:**
- `brandName` → ItemName (fallback `deviceDescription`)
- `companyName` → ItemManufacturer
- `gmdnTerms[].gmdnPTName` → ItemCategory (joined)
- `versionModelNumber` / `catalogNumber` → Notes

**Transport:** Java 8 `HttpsURLConnection` (TLS 1.2 default on 8u121+), ~4s connect/read
timeouts, `User-Agent` set, no external HTTP/JSON libraries. Requires a small
**nested-JSON reader** (object/array aware) extending the existing minimal JSON code
(the current `EventJson`/`JsonFlat` handles flat structures only).

**Behavior — offline-first, best-effort:**
1. Unknown GTIN scanned → Registration screen fires a background GUDID lookup.
2. Success → prefill name/manufacturer/category; the operator reviews and may override.
3. Offline / timeout / not-found → operator fills fields manually (today's PowerApp
   behavior). Manual registration always works.
4. Confirmed registration writes `PRODUCT_REGISTERED` (source=GUDID) to the shared
   store, so **every other workstation gets that product offline thereafter** — the
   shared catalog is the durable team-wide cache.
5. A per-user raw-response cache under `%LOCALAPPDATA%\MedicalSupply\gudid-cache\`
   avoids repeat calls and allows offline re-view.
6. Settings toggle disables lookups entirely for air-gapped sites; endpoint is
   configurable.

Tests use a saved JSON fixture — **no live network call in the build.**

## 6. User interface

Primary UI is a precompiled React/MUI SPA embedded in the JAR and served by
`BrowserServer` on `127.0.0.1` with an ephemeral port and random session token
(identical model to commercial tracking). `--classic-ui` launches a Swing fallback.
Node/npm are development-time only.

Workspaces:
1. **Scan (Quick Pick)** — single scan → decode → resolve product (catalog or GUDID) →
   set quantity → `STOCK_RECEIVED`. Existing item routes to quantity edit; unknown GTIN
   routes to inline registration.
2. **Rapid Scan** — batch session list showing previous vs scanned quantity per item;
   adjust; "Submit All" emits `STOCK_RECEIVED` events; "Clear Session" discards.
3. **Inventory** — search + manufacturer/category filter, expiry color coding
   (≤7d red / ≤30d yellow / expired), edit quantity (`STOCK_ADJUSTED`), pick
   (`STOCK_PICKED`), archive expired (`STOCK_ARCHIVED`).
4. **Registration / Catalog** — search catalog; register new product (GUDID-backed);
   edit product (name, manufacturer, category, PAR, unit price, notes).
5. **Labels (Inventory Tool)** — printable QR/label sheet, category filter, auto-fit
   grid layout, browser print. **QR codes generated locally** by a small bundled JS QR
   library — not the external `api.qrserver.com` URL the PowerApp used, which would
   break the offline model.
6. **Diagnostics / Settings** — shared folder selection & probe, GUDID enable/disable +
   endpoint, malformed-event list, local index rebuild, pending-event retry.

## 7. Testing, build & qualification

- No test framework. Plain `main()`-method test classes run from `build.ps1`, mirroring
  commercial tracking: `Gs1ParserTest`, `ProjectionTest` (catalog + inventory replay),
  `GudidClientTest` (parses saved fixture, offline), `EventStoreTest`, JSON tests,
  `PerformanceSmokeTest`. A `--self-test` entry point runs a smoke check.
- `build.ps1` adapted: npm build of the MUI frontend (skippable), `javac --release 8`,
  run tests, package `MedicalSupply-RC.jar` with `run-medical-supply.cmd`, README,
  TESTING.md, RELEASE_NOTES.md, and a `qualification/` folder.
- Produce `dist-review/qualification` browser-smoke evidence in the same form as
  `commercial-tracking-java/dist-review/qualification/browser-smoke-evidence.md`.

## 8. Reuse map (commercial-tracking-java → medical-supply-java)

| Component | Disposition |
|---|---|
| `EventStore`, `LocalEventIndex`, `EventJson` | Reuse; add medical event types & nested-JSON reader |
| `AppConfig`, `SharedConfigManager` | Reuse; rename config root, add GUDID settings |
| `BrowserServer` | Reuse; new SPA bundle & JSON API routes |
| `PortablePdf`, `ReportWriter`, `ManifestWriter` | Reuse where reports/labels overlap |
| `BarcodeParserChain` (carrier) | Replace with `Gs1Parser` (GS1 medical) |
| `Projection` (package state) | Rewrite for catalog + inventory projections |
| `build.ps1`, launchers, test harness | Adapt |

## 9. Open items for spec review
- Confirm ItemCategory should be free-text-from-GMDN vs a fixed choice list (PowerApp
  used a fixed multi-select choice; GUDID GMDN terms are free-form). Current plan:
  store the GMDN preferred term(s) as the category value.
- Confirm whether UnitPrice/PAR are in scope for v1 UI (present in catalog model; not in
  the four selected workflows explicitly). Current plan: carried in the catalog record
  and shown in Registration, not surfaced elsewhere in v1.
