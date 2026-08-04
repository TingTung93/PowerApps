# Manifest QR codes, void/custody-eligibility fixes, date-scoped recipient-grouped manifests, and friendly locations editor

**Date:** 2026-08-04
**Component:** `commercial-tracking-java`
**Status:** Approved (design), pending implementation plan

## Summary

Changes to the commercial-tracking application, refined through brainstorming:

1. **QR codes on both manifest types** — encode the manifest ID (manifest-level) and each tracking number (per-package) as QR codes on the **inbound receiving** and **recipient custody** manifests, so staff can scan a manifest to look up packages and their status.
2. **Void-eligibility bug fix** — voided packages currently stay selected on the Prepare-manifest screen and can be pushed onto a manifest. Voided tracking numbers must never appear on or be added to any manifest.
3. **Custody-eligibility fix** — releasing a package to a recipient flips it to `PICKED_UP`, which today makes it ineligible for that recipient's manifest. Both `READY_FOR_PICKUP` (assigned, not yet released) and `PICKED_UP` (released) packages must be eligible for a recipient manifest.
4. **Date-scoped, recipient-grouped manifests** — every manifest is bounded to a single day (received date, host machine time zone). The inbound manifest is scoped to (date + location); the recipient/custody manifest is scoped to a date and lists all items received that day grouped by recipient, with an optional single-recipient filter.
5. **Friendly locations editor** — replace the raw pipe-delimited (`|`) text field in shared operational settings with an add/remove chip list suitable for non-technical staff.
6. **Host-time, legible timestamps** — all date/time display uses the **host machine time zone**; the `operationalTimeZone` shared setting is removed. A shared **12-hour / 24-hour** toggle controls time style, and displayed timestamps drop seconds (legible at a glance; the immutable event log keeps full precision).

## Constraints (existing codebase realities)

- The build is **pure JDK**: `build.ps1` runs `javac --release 8` with **no Maven/Gradle and no third-party jars**. No QR library (e.g. ZXing) is available; a QR encoder must be implemented in pure Java.
- Both manifests are generated **server-side** in `ManifestWriter`, producing an HTML file and a PDF via the hand-rolled `PortablePdf` (which today emits only text lines, paginated 48 lines/page).
- Tests are plain classes with a `main()` method invoked in sequence by `build.ps1` (not JUnit). New test classes must be wired into `build.ps1`.
- Package status is derived in `Projection`: `PACKAGE_RECEIVED` → `READY_FOR_PICKUP`; `PACKAGE_RELEASED` → `PICKED_UP`; `PACKAGE_VOIDED` → `VOIDED`. `PackageState` currently tracks only `lastEventUtc`, **not** a received date.
- Shared locations are persisted as one pipe-joined string under `locations` in `configuration/application.json`; `SharedConfigManager.validate` enforces non-empty and ≤500 characters. `operationalTimeZone` is a validated shared setting today and is passed into `ManifestWriter`; this design removes it.
- The frontend already formats displayed timestamps in the host zone with no seconds (`format.js` via `Intl.DateTimeFormat`, default zone); it does not yet honor a 12/24 preference. `ManifestWriter` prints a host-local "Prepared" line and a second-precision "Prepared UTC" line using the configured operational zone.
- Manifest types are exactly `inbound` and `custody`. "Outbound" / "recipient" / "custody" all refer to the `custody` manifest.

---

## 0. Supporting change: capture received date

Date-scoping requires each package's received date.

- **`PackageState`**: add `receivedUtc` (String, ISO instant) and carry it in `copy()`.
- **`Projection.apply`**: on the first `PACKAGE_RECEIVED` event for a package, set `receivedUtc` if empty (do not overwrite on later events).
- **Package maps** (`sessionPackageMaps` and `packageMaps` in `BrowserServer`): expose `receivedUtc` and a derived `receivedDate` — the received instant formatted as `yyyy-MM-dd` in the **host machine time zone** (`ZoneId.systemDefault()`). The frontend filters by string-equality on `receivedDate`; the backend recomputes and validates it independently.

---

## 1. QR codes on manifests (inbound and custody)

### 1.1 QR encoder (`QrCode.java`, new)

A self-contained, dependency-free QR encoder:

- **Mode:** byte mode (ISO-8859-1), so any tracking number or manifest ID encodes without character-set restrictions.
- **Error correction:** level M.
- **Version:** smallest version that fits the payload, selected automatically.
- **Masking:** evaluate the eight mask patterns and choose the lowest-penalty mask per the QR spec.
- **Output:** a boolean module matrix (`true` = dark) plus its size, consumed by the renderers below.

Built **test-first** (highest-risk component):

- Reed–Solomon error correction verified against the QR specification's worked-example data/ECC codeword vector (hard-coded expected bytes).
- Structural assertions: correct matrix size for a chosen version; the three finder patterns at the correct corners; timing patterns; the dark module.

### 1.2 Rendering (`ManifestWriter` + `PortablePdf` extension)

- **Manifest-level QR** in the manifest header, encoding the **manifest ID** (`MNF-…`).
- **Per-package QR** beside each tracking number, encoding the **plain tracking number** (identical to the package barcode payload, so it scans into the app's existing scan/search box).

Both applied to **inbound and custody** manifests, in **both** output formats:

- **HTML:** each QR rendered as a crisp inline **SVG** (vector, printable, no external assets or data URIs).
- **PDF:** each QR rendered as **vector square modules**. `PortablePdf` is extended to place a QR matrix (filled rectangles via `re`/`f` content-stream operators) at a given position/size on a page, alongside its existing text lines.

### 1.3 Density rule (accepted)

Based on total package count on the manifest:

- **≤20 packages:** header manifest QR **and** a per-package QR on each row (~1 inch, scannable).
- **>20 packages (up to 100):** header manifest QR **only**; per-package QRs are **omitted** (sub-inch QRs are not reliably scannable). The manifest notes that per-package QRs are omitted at high density.

---

## 2. Voided package blocking manifests

**Root cause:** manifest eligibility ignores package status, so a voided package (which keeps an empty `manifestId`) stays eligible, auto-selected, and finalizable.

**Fix — two lines of defense (applies to both manifest types):**

- **Frontend** (`frontend/src/main.jsx` eligible lists): exclude `status === 'VOIDED'`.
- **Backend** (`BrowserServer.java`): exclude `VOIDED` from the auto-select path, **and** add a hard guard in `manifest()` that rejects any `VOIDED` package even when its tracking number is supplied explicitly.

---

## 3. Custody eligibility: released packages must be eligible

**Root cause:** custody eligibility requires `status == 'READY_FOR_PICKUP'` (frontend `main.jsx:503`, backend `BrowserServer.java:508`), but `PACKAGE_RELEASED` sets `PICKED_UP`, so a released package is excluded.

**Fix (accepted "both eligible"):** a package is eligible for a recipient/custody manifest when its status is **`READY_FOR_PICKUP` or `PICKED_UP`** (never `VOIDED`/`CONFLICT`). Update the frontend eligible filter and the backend custody validation accordingly. This combines with the date + grouping rules in §4.

---

## 4. Date-scoped, recipient-grouped manifests

Every manifest is bounded to a single day; the scoping day is the package's **received date** in the **host machine time zone**.

### 4.1 Selection model

- The Prepare-manifest screen gains a **date picker**, defaulting to **today** (host machine time zone). Same-day workflows are unchanged in feel.
- Eligibility is computed from **all packages** whose `receivedDate` equals the selected date (not just the current scan session), minus packages already on a manifest of that type and minus `VOIDED`.
  - **Inbound:** date + location. Eligible = received on the date, at the selected location, status not `VOIDED`, not already on an inbound manifest.
  - **Custody:** date (+ optional recipient). Eligible = received on the date, status `READY_FOR_PICKUP` or `PICKED_UP`, not already on a custody manifest. If a specific recipient is chosen, filter to that recipient; otherwise include all.
- The explicit checkbox selection path (operator ticks specific rows) still works and is still honored.

### 4.2 Custody document layout — grouped by recipient

- The custody manifest lists the day's packages in **sections grouped by recipient**, each section headed by the recipient name; packages with no recipient fall under an **"Unassigned"** section.
- Header shows the **date** prominently; when narrowed to one recipient, the recipient is shown too.
- `ManifestWriter` groups the membership events by recipient for `custody` type (events already carry `recipient`/`addressee`). Group order: recipients alphabetically, `Unassigned` last. Package numbering and the density rule (§1.3) are based on the **total** package count across all sections.
- The inbound manifest layout is unchanged except the header now shows the scoped **date** alongside the location.

### 4.3 Backend `manifest()` changes

- Accept a `date` parameter (`yyyy-MM-dd`); default to today in the host machine time zone; validate format and reject packages whose `receivedDate` ≠ the selected date.
- **Inbound:** gather eligible packages by (date + location); keep the existing single-location rule.
- **Custody:** replace the "all packages share one recipient" rule with the date-scoped, recipient-grouped rule; allow multiple recipients in one manifest; accept `READY_FOR_PICKUP` or `PICKED_UP`; reject `VOIDED`.
- Per-package `MANIFEST_PREPARED` events keep `addressee` = the package's recipient. The manifest register scope/label (`manifestMaps`) shows date + location (inbound) or date + recipient(s) (custody).
- The per-type "already on a manifest" guard (matching `MANIFEST_PREPARED` with the same `parserSource`) is retained, so a package may appear on one inbound and one custody manifest but not two of a kind.

### 4.4 Frontend `ManifestWorkspace` changes

- Add the date picker (default today). Derive eligible lists from `packages` using `receivedDate`, `status`, `location`/`recipient`, and existing manifest membership — replacing the session-only inbound filter and the `READY_FOR_PICKUP`-only custody filter.
- Custody view groups the eligible rows by recipient (with an Unassigned group) and supports an optional recipient filter; the finalize call passes the selected `date`.

---

## 5. Shared operational settings

### 5.1 Friendly locations editor

Replace the raw `Locations (separate with |)` text field in the shared-settings section of `frontend/src/main.jsx` with a **chip/list editor**:

- A "type a location" input with an **Add** button; **Enter** also adds.
- Each location shown as a **removable chip**.
- Serializes to/from the existing pipe-joined `locations` string on load/save, so the `locations` persistence format is **unchanged**.
- Editor-level rules for non-technical staff: trim whitespace, block empty entries, block duplicates, forbid the `|` delimiter character itself, and keep within the existing ≥1-location / ≤500-character backend limits with inline feedback.

### 5.2 Time-format toggle (new shared setting)

- Add a shared setting **`timeFormat`** with values `12h` (default) or `24h`, edited via a toggle/radio in the shared-settings screen.
- `SharedConfigManager`: add to `defaults()`; `validate` accepts only `12h`/`24h`.

### 5.3 Remove the operational time zone

- Remove the operational-time-zone field from the settings screen.
- `SharedConfigManager.validate` **no longer requires or validates** `operationalTimeZone` (an existing key in old config files is ignored, not rejected); `defaults()` drops it.
- `ManifestWriter` and all date-scoping use `ZoneId.systemDefault()` instead of a configured zone (see §6).

---

## 6. Host-time, legible timestamps

All date/time display uses the **host machine time zone** and drops seconds; the immutable event log retains full-precision `occurredUtc` (unchanged).

- **Frontend `format.js`**: keep host-zone, no-seconds formatting; honor the shared `timeFormat` by setting `hour12` (`true` for `12h`, `false` for `24h`). Since the formatters are module-level, expose a small configure function that the app calls when settings load (default `12h` until known); `formatDate` continues to be the single entry point callers use.
- **`ManifestWriter`**: drop the `timeZone` parameter; render the "Prepared" line in host-local time with no seconds, choosing `MMM d, uuuu h:mm a` (12h) or `MMM d, uuuu HH:mm` (24h) from a passed time-format preference. Reduce the "Prepared UTC" line to minute precision (e.g. `2026-08-04 14:32 UTC`) so an at-a-glance UTC reference remains without second-level clutter. Recorded-time columns in the manifest tables display host-local, no-seconds times in the same style.
- The manifest header also shows the scoped **date** (§4) in the same legible style.

---

## Testing & verification

- **`QrCodeTest`** (new `main()`-based test class, wired into `build.ps1`): Reed–Solomon vector and structural matrix assertions.
- **`ManifestWriterTest`** (extended): QR presence in HTML (SVG) and PDF for header + per-package QRs on a ≤20 manifest; per-package QRs omitted on a >20 manifest; custody output grouped into recipient sections with an Unassigned group; header carries the scoped date.
- **`Projection` / eligibility checks**: `receivedUtc` set on first receive; `receivedDate` computed in the host machine time zone; voided packages excluded and hard-rejected; released (`PICKED_UP`) packages eligible for custody; eligibility bounded to the selected date.
- **`SharedConfigManagerTest`** (extended): `timeFormat` accepts only `12h`/`24h`; a config lacking `operationalTimeZone` validates (setting is no longer required); an old config containing it is not rejected.
- **`ManifestWriterTest`** (extended): the "Prepared" line honors 12h vs 24h and carries no seconds; timestamps render in the host zone.
- **Manual verification**: Prepare-manifest screen no longer shows the voided package; releasing a package then preparing that day's recipient manifest includes it in the right recipient group; date picker bounds both manifest types; locations editor adds/removes chips and round-trips through save; the 12/24 toggle changes both on-screen and printed times; scan a printed manifest QR to confirm it decodes to the tracking number / manifest ID.

## Out of scope

- Lookup-URL QR payloads (plain tracking number / manifest ID chosen).
- Manifest eligibility for statuses other than `READY_FOR_PICKUP`, `PICKED_UP` (custody) and the location-scoped active set (inbound); `CONFLICT` packages remain ineligible.
- Any change to the persisted `locations` storage format or the event-log schema beyond the additive `receivedUtc` projection field.
- Cross-day manifests or manifests spanning multiple locations (inbound).
