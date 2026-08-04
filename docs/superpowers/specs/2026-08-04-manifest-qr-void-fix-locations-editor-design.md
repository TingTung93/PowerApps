# Inbound manifest QR codes, void-eligibility fix, and friendly locations editor

**Date:** 2026-08-04
**Component:** `commercial-tracking-java`
**Status:** Approved (design), pending implementation plan

## Summary

Three independent changes to the commercial-tracking application:

1. **QR codes on the inbound receiving manifest** — encode the manifest ID (manifest-level) and each tracking number (per-package) as QR codes so staff can scan a manifest to look up packages and their status.
2. **Void-eligibility bug fix** — voided packages currently remain selected on the Prepare-manifest screen and can be pushed onto a manifest. Voided tracking numbers must never appear on or be added to a manifest.
3. **Friendly locations editor** — replace the raw pipe-delimited (`|`) text field in shared operational settings with an add/remove chip list suitable for non-technical staff.

## Constraints (existing codebase realities)

- The build is **pure JDK**: `build.ps1` runs `javac --release 8` with **no Maven/Gradle and no third-party jars**. No QR library (e.g. ZXing) is available; a QR encoder must be implemented in pure Java.
- The inbound manifest is generated **server-side** in `ManifestWriter`, producing both an HTML file and a PDF via the hand-rolled `PortablePdf` (which today emits only text lines, paginated 48 lines/page).
- Tests are plain classes with a `main()` method invoked in sequence by `build.ps1` (not JUnit). New test classes must be wired into `build.ps1`.
- Shared locations are persisted as a single pipe-joined string under `locations` in `configuration/application.json`. `SharedConfigManager.validate` enforces the value is non-empty and ≤500 characters.

---

## 1. QR codes on the inbound manifest

### 1.1 QR encoder (`QrCode.java`, new)

A self-contained, dependency-free QR encoder:

- **Mode:** byte mode (ISO-8859-1), so any tracking number or manifest ID encodes without character-set restrictions.
- **Error correction:** level M.
- **Version:** smallest version that fits the payload, selected automatically.
- **Masking:** evaluate the eight mask patterns and choose the lowest-penalty mask per the QR spec.
- **Output:** a boolean module matrix (`true` = dark) plus its size, consumed by the renderers below.

This is the highest-risk component and is built **test-first**:

- Reed–Solomon error correction verified against the QR specification's worked-example data/ECC codeword vector (hard-coded expected bytes).
- Structural assertions: correct matrix size for a chosen version, the three finder patterns present at the correct corners, timing patterns, and the dark module.

### 1.2 Rendering (`ManifestWriter` + `PortablePdf` extension)

- **Manifest-level QR** in the manifest header, encoding the **manifest ID** (`MNF-…`).
- **Per-package QR** beside each tracking number, encoding the **plain tracking number** (identical to the package barcode payload, so it scans into the app's existing scan/search box).

Rendering targets — both the on-screen HTML manifest and the archived/printed PDF:

- **HTML:** each QR rendered as a crisp inline **SVG** (vector, printable, no external assets or data URIs).
- **PDF:** each QR rendered as **vector square modules**. `PortablePdf` is extended with the ability to place a QR matrix (filled rectangles via `re`/`f` content-stream operators) at a given position/size on a page, in addition to its existing text lines.

### 1.3 Density rule (accepted)

- **Detailed layout (≤20 packages):** header manifest QR **and** a per-package QR on each row (sized ~1 inch, scannable).
- **High-density layout (>20 packages, up to 100):** header manifest QR **only**; per-package QRs are **omitted** because sub-inch QRs at that density are not reliably scannable. The manifest notes that per-package QRs are omitted for high-density manifests.

---

## 2. Voided package blocking Prepare-manifest

**Root cause:** eligibility for an inbound manifest is computed only from `!manifestId && matching location`, ignoring package status. A voided package keeps an empty `manifestId`, so it stays in the eligible list, remains auto-selected, and can be finalized onto a manifest.

**Fix — two lines of defense:**

- **Frontend** (`frontend/src/main.jsx`, inbound `eligible` list): exclude voided packages —
  `!item.manifestId && item.location === manifestLocation && item.status !== 'VOIDED'`.
  This clears the stuck auto-selected package from the Prepare-manifest screen.
- **Backend** (`BrowserServer.java`):
  - Auto-select path: exclude `VOIDED` when gathering session targets.
  - `manifest()` validation: add a hard guard that rejects any `VOIDED` package even when its tracking number is supplied explicitly, so the rule is enforced server-side, not merely hidden in the UI.

Scope is limited to `VOIDED` status, matching the reported problem; other statuses are left unchanged.

---

## 3. Friendly locations editor

Replace the raw `Locations (separate with |)` text field in the shared-settings section of `frontend/src/main.jsx` with a **chip/list editor**:

- A "type a location" input with an **Add** button; pressing **Enter** also adds.
- Each location shown as a **removable chip**.
- Serializes to/from the existing pipe-joined `locations` string on load/save, so `SharedConfigManager` and its validation are **unchanged**.
- Editor-level rules for non-technical staff: trim whitespace, block empty entries, block duplicates, forbid the `|` delimiter character itself, and keep within the existing ≥1-location / ≤500-character backend limits with inline feedback.

The backend contract is unchanged; only the presentation layer changes.

---

## Testing & verification

- **`QrCodeTest`** (new `main()`-based test class, wired into `build.ps1`): Reed–Solomon vector and structural matrix assertions.
- **`ManifestWriterTest`** (extended): assert QR presence in both HTML (SVG) and PDF output, for both the header manifest QR and per-package QRs on a detailed manifest; assert per-package QRs are omitted on a high-density manifest.
- **Void guard:** focused backend check that a voided tracking number is rejected from `manifest()` and excluded from auto-select.
- **Locations editor & void UI fix:** frontend build plus manual verification (Prepare-manifest screen no longer shows the voided package; locations editor adds/removes chips and round-trips through save).

## Out of scope

- Custody-manifest QR codes (only the inbound receiving manifest is in scope).
- Lookup-URL QR payloads (plain tracking number / manifest ID chosen).
- Changing statuses other than `VOIDED` in manifest eligibility.
- Any change to the persisted `locations` storage format.
