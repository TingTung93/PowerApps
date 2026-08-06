# Manifest QR codes, DOCX documents, void/custody + date-scoping fixes, and settings/time-display cleanup

**Date:** 2026-08-04
**Component:** `apps/commercial-tracking-java`
**Status:** Approved (design), pending implementation plans

## Summary

Refined through brainstorming into six work areas plus a document-format change:

1. **QR encoder** — a self-contained, dependency-free QR encoder in pure Java (no ZXing available).
2. **DOCX documents** — replace the hand-rolled PDF output with `.docx` (OOXML via `java.util.zip`) for **both manifests and reports**. Manifests keep their on-screen HTML view; reports keep CSV + HTML. `PortablePdf` is retired.
3. **QR codes on manifests** — a manifest-level QR (manifest ID) and a per-package QR (tracking number) on both the inbound and custody manifests, embedded as **PNG** in the DOCX (via `javax.imageio.ImageIO`) and as inline **SVG** in the HTML.
4. **Void-eligibility fix** — voided packages must never appear on or be added to any manifest.
5. **Custody-eligibility fix + date-scoped, recipient-grouped manifests** — released (`PICKED_UP`) packages become eligible for a recipient manifest; both `READY_FOR_PICKUP` and `PICKED_UP` are eligible. Every manifest is bounded to a single day (received date, host machine time zone): inbound = (date + location); custody = a date's items grouped into recipient sections, optional single-recipient filter.
6. **Settings & time display** — remove the `operationalTimeZone` shared setting (use host machine time everywhere); add a shared `timeFormat` toggle (`12h`/`24h`); drop seconds from displayed timestamps; replace the pipe-delimited locations text field with an add/remove chip editor.

## Constraints (existing codebase realities)

- Pure JDK build: `build.ps1` runs `javac --release 8`, no Maven/Gradle, no third-party jars. QR encoding, DOCX generation (`java.util.zip`), and QR-to-PNG (`javax.imageio.ImageIO`) are all implemented on the JDK.
- Manifests and reports are generated server-side. `ManifestWriter` and `ReportWriter` both currently call `PortablePdf`; both switch to a new shared `DocxWriter`. After the switch, `PortablePdf` and `PortablePdfTest` are unused and are deleted (build.ps1 line removed).
- Tests are plain `main()` classes invoked in sequence by `build.ps1` (not JUnit). New Java test classes are wired into `build.ps1`. Frontend tests are plain Node scripts (`node test/*.js` via `npm test`); pure logic is extracted into importable modules and tested that way.
- `PackageState` tracks only `lastEventUtc`; date-scoping needs a received timestamp captured in `Projection`.
- Shared config is a flat string map in `configuration/application.json`; `SharedConfigManager.validate` gates it. Manifest types are exactly `inbound` and `custody` ("outbound"/"recipient" = `custody`).
- `BrowserServer` reads `operationalTimeZone` at the manifest and report call sites (≈535, ≈583/634) and requires it in `saveSharedSettings` (≈692); all of these change to host machine time.

---

## Plan decomposition

The work splits into four sequential, independently testable plans:

- **Plan 1 — Document core:** `QrCode` encoder (+ PNG rendering) and `DocxWriter`. Foundational; no product behavior yet, fully unit-tested.
- **Plan 2 — Manifests:** `ManifestWriter` (DOCX + HTML, QR, date header, custody recipient grouping, host-time/12-24), `BrowserServer.manifest()` (void guard, custody eligibility, date scoping), and the frontend `ManifestWorkspace` (date picker, grouping, void exclusion). Depends on Plan 1.
- **Plan 3 — Reports:** `ReportWriter` PDF→DOCX, `BrowserServer.report()` response, frontend `ReportsWorkspace`. Depends on Plan 1.
- **Plan 4 — Settings & time display:** `Projection.receivedUtc` + package-map `receivedDate`, `SharedConfigManager` (`timeFormat`, drop `operationalTimeZone`), host-time removal across `BrowserServer`, `format.js` 12/24, locations chip editor, settings toggle. Independent of the document core; sequence before or alongside Plans 2–3 since they consume `receivedDate` and host-time.

Detailed per-plan design follows.

---

## 0. Supporting change: capture received date (Plan 4)

- **`PackageState`**: add `receivedUtc` (ISO instant), carried in `copy()`.
- **`Projection.apply`**: on the first `PACKAGE_RECEIVED` for a package, set `receivedUtc` if empty (never overwrite).
- **Package maps** (`sessionPackageMaps`, `packageMaps`): expose `receivedUtc` and derived `receivedDate` = the received instant as `yyyy-MM-dd` in the **host machine time zone** (`ZoneId.systemDefault()`). Frontend filters by string-equality on `receivedDate`; backend recomputes and validates independently.

## 1. QR encoder (Plan 1)

`QrCode.java`, dependency-free:

- Byte mode (ISO-8859-1); error-correction level M; versions 1–3 (single ECC block: 21/25/29 modules, 16/28/44 data codewords, 10/16/26 ECC codewords); smallest fitting version auto-selected; throws `IllegalArgumentException` if the payload exceeds version-3 capacity (well beyond any tracking number/manifest ID, ~41 bytes).
- Full mask evaluation (8 patterns, 4 penalty rules), lowest-penalty mask chosen.
- Output: a `boolean[][] modules` (`true` = dark) and `size`.
- `toPng(int scale, int quietModules)` → PNG bytes via `BufferedImage` + `ImageIO`, for DOCX embedding.

Built test-first: `generatorPolynomial(10)` equals the spec's documented α-exponents `{0,251,67,46,61,118,70,64,94,32,45}`; encoded size matches the auto-selected version; the three finder patterns are present; over-length input throws; `toPng` yields a valid PNG (`\x89PNG` header) of the expected pixel dimensions.

## 2. DOCX generator (Plan 1)

`DocxWriter.java`, dependency-free (`java.util.zip`), emitting a minimal valid OOXML package: `[Content_Types].xml`, `_rels/.rels`, `word/document.xml`, `word/_rels/document.xml.rels`, and `word/media/imageN.png` parts. Builder API: `heading(text)`, `paragraph(text)`, `table(rows)` where a cell holds text or an embedded image (PNG bytes + display size), and `save(Path)`. Inline images use `<w:drawing>` with EMU extents and a relationship-referenced blip. Built test-first: saved file is a valid ZIP whose entries include the required parts; `document.xml` contains supplied text (XML-escaped); an image cell adds a `word/media/*.png` entry and a matching relationship; opens in Word (manual check).

## 3. Manifests — QR + DOCX + HTML (Plan 2)

`ManifestWriter` produces an HTML file (on-screen) and a DOCX file (archived/printable/signable); the DOCX replaces the PDF and carries the checksum. Both formats render:

- **Manifest-level QR** in the header (manifest ID); **per-package QR** per row (tracking number) in the detailed layout (≤20 packages). HTML uses inline SVG; DOCX uses embedded PNG (~1 inch). At high density (>20, up to 100), the manifest-level QR is kept and per-package QRs are omitted (noted on the document).
- The header shows the scoped **date** (§5) in host-local, no-seconds, 12/24 style.
- Custody manifests group packages into **recipient sections** (recipients alphabetical, `Unassigned` last); numbering and the density threshold use the total package count.
- `ManifestWriter` drops the `timeZone` parameter and uses `ZoneId.systemDefault()`; a time-format preference (`12h`/`24h`) is passed in for the header/recorded times.

`ManifestWriterTest` extended: QR present in HTML (SVG) and DOCX (PNG media part) for header + per-package on a ≤20 manifest; per-package omitted at >20; custody DOCX/HTML grouped into recipient sections incl. Unassigned; header carries the date; the "Prepared" line honors 12h/24h with no seconds; checksum is over the DOCX and stable.

## 4. Manifest eligibility — void, custody, date scoping (Plan 2)

`BrowserServer.manifest()` and the frontend `ManifestWorkspace`:

- **Void:** exclude `VOIDED` from eligibility (frontend + backend auto-select) and hard-reject any explicitly supplied `VOIDED` tracking number.
- **Custody eligibility:** `READY_FOR_PICKUP` **or** `PICKED_UP` (never `VOIDED`/`CONFLICT`).
- **Date scoping:** a `date` parameter (`yyyy-MM-dd`, default today in host zone). Eligibility is drawn from **all packages** whose `receivedDate` equals the date (not just the scan session), minus packages already on a manifest of that type. Inbound = date + single location (existing single-location rule kept). Custody = date + optional recipient, grouped by recipient. The explicit checkbox selection path still works. The per-type "already manifested" guard (matching `MANIFEST_PREPARED` with the same `parserSource`) is retained.
- The frontend gains a **date picker** (default today), derives eligibility from `packages` via `receivedDate`/`status`/`location`/`recipient`, groups custody rows by recipient with an Unassigned group, and passes `date` on finalize.

## 5. Reports — PDF→DOCX (Plan 3)

`ReportWriter` keeps CSV + HTML and replaces the PDF with a DOCX (via `DocxWriter`); `Result.pdf` becomes `Result.docx`. `BrowserServer.report()` returns `docxFile` instead of `pdfFile` and passes host zone (`ZoneId.systemDefault().getId()`) instead of a configured `operationalTimeZone`. The frontend `ReportsWorkspace` relabels "Create PDF / Print" → "Create DOCX", sends the host time zone (`Intl.DateTimeFormat().resolvedOptions().timeZone`) for range math, and drops the `operationalZone` prop. Report timestamp **columns** remain full UTC (data export, not human display). `ReportWriterTest` extended: DOCX produced and is a valid ZIP with `word/document.xml`; CSV/HTML unchanged.

## 6. Settings & time display (Plan 4)

- **`SharedConfigManager`**: add `timeFormat` (`12h` default, validate ∈ {`12h`,`24h`}); `validate` no longer requires/validates `operationalTimeZone` (ignored if present in old files); `defaults()` drops it. `saveSharedSettings` stops requiring `operationalTimeZone` and persists `timeFormat`.
- **Host time**: all `operationalTimeZone` reads in `BrowserServer` (manifest, report) use `ZoneId.systemDefault()`.
- **`format.js`**: keep host-zone, no-seconds formatting; honor `timeFormat` by setting `hour12`; expose a configure function called when settings load (default `12h`); `formatDate` stays the single entry point.
- **Locations chip editor**: replace the `Locations (separate with |)` field with a chip list (type + Add, Enter adds, removable chips) that serializes to/from the pipe-joined `locations` string; rules: trim, block empty/duplicate, forbid `|`, keep ≥1 / ≤500 chars with inline feedback. Pure parse/serialize logic extracted to a testable module.
- **Settings UI**: add the 12/24 toggle; remove the operational-time-zone field and its review copy.

## Testing & verification

- New: `QrCodeTest`, `DocxWriterTest` (wired into `build.ps1`); `PortablePdfTest` line removed.
- Extended: `ManifestWriterTest`, `ReportWriterTest`, `SharedConfigManagerTest` (timeFormat accepted; config lacking `operationalTimeZone` validates), plus a `ProjectionTest` for `receivedUtc`.
- Frontend Node tests for the locations parse/serialize helper, the manifest-eligibility helper, and `format.js` 12/24 behavior.
- Manual: manifests/reports open in Word; scan a manifest QR to confirm it decodes; Prepare-manifest hides voided packages; releasing then preparing that day's recipient manifest includes the package in the right group; date picker bounds both types; 12/24 toggle changes on-screen and document times; locations chips round-trip through save.

## Out of scope

- Lookup-URL QR payloads; QR on report documents.
- Manifest eligibility for `CONFLICT` (remains ineligible) or other statuses.
- Changing the `locations` storage format or the event-log schema beyond the additive `receivedUtc` projection field.
- Report timestamp columns' format (remain full UTC data export).
- Cross-day or multi-location inbound manifests.
