# Release Notes

## 0.2.0-rc1

- Enforces OS-derived, non-editable Windows attribution and records user/device on events.
- Detects incomplete audit replay, visibly warns operators, and blocks incomplete reports.
- Automatically retries buffered writes and recovers valid orphan `.partial` event files.
- Reloads before removals, rejects locally unsafe picks, atomically archives lots depleted to zero,
  and marks any cross-workstation negative replay as an incomplete trail requiring audit review.
- Adds archive browse/restore, bulk expired-lot archive, catalog edit/retire, and inventory filters.
- Replaces prompt-based inventory actions with accessible dialogs and adds exact-lot history.
- Adds scanner workflow settings, optional two-scan production-data capture, and GUDID failure details.
- Adds append-only expiry distribution-list management; notification sending remains deferred.
- Hardens JSON nesting, GUDID URL construction, configuration saves, HTML escaping, CSV exports,
  and locale-independent report numbers.

Known limitations: absolute adjustments and catalog replacements are last-writer-wins; remote events
appear on the 15-second refresh rather than by push; notification sending and a multi-writer
cryptographic hash chain are deferred. OS attribution requires distinct Windows logins—shared Windows
accounts do not provide individual operator attribution. OneDrive is not a distributed locking
service: two genuinely simultaneous offline removals can conflict; replay detects a resulting negative
balance, blocks further writes and reports, and requires an audited correction.

## 0.1.0
- Event-sourced medical-supply tracker on a OneDrive-synchronized folder (Java 8, no Maven/Gradle).
- GS1 barcode decoding (AI 01/17/10/21/30) with FNC1 handling.
- Catalog + inventory projections; expiry color coding; PAR + consumption reorder advisor.
- FDA GUDID-backed registration (offline-first, best-effort, cached in the shared catalog).
- Management dashboard and exportable HTML/CSV/PDF report.
- React/MUI browser UI with QR label printing; Swing `--classic-ui` fallback.
