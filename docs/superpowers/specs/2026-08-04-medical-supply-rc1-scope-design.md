# Medical Supply Java — Release Candidate 1 Scope

**Date:** 2026-08-04
**Component:** `medical-supply-java`
**Status:** Program-level scope, approved. Decomposed into five work-streams; each gets its own spec → plan → implementation cycle.

## 1. Context

`medical-supply-java` is an event-sourced Java 8 replacement for the PowerApps "Inventory Tracker"
(`Inventory_Tracker/*.pa.yaml`). It writes immutable, per-event JSON files into a
OneDrive/SharePoint-synchronized folder shared across workstations, and serves a precompiled
React/MUI SPA over a loopback-only HTTP endpoint. Per-user settings live in
`%LOCALAPPDATA%\MedicalSupply`.

The core architecture is sound: unique per-event filenames (timestamp + device + `eventId`) mean two
workstations cannot clobber each other, and replay is idempotent (dedup by `eventId` + content hash).
The applet is currently **behind its own sibling** (`commercial-tracking-java`, which already drains
buffered events and surfaces sync state) and **behind the PowerApp** on several user-facing workflows.

This document defines the complete set of features to implement or change to reach Release
Candidate 1 (`x.y.z-rc1`) and real end-user adoption.

## 2. Scope decisions

These four decisions were made with the product owner and shape everything below.

| Decision | Answer | Consequence |
|---|---|---|
| Concurrency profile | **Mostly single-user** per site | Heavy conflict-resolution deferred; a *hard* negative-inventory guard is safe and is kept. |
| PowerApp parity | **Full parity** required | All parity features (Archive/restore, bulk archive, catalog editing, filters, scanner UX, two-scan) are in scope. |
| Data migration | **Start fresh** | No importer. Day-one catalog is empty; first scan of each product forces registration (GUDID offline-first makes this tolerable). |
| Audit/identity bar | **Regulated audit trail** | Self-declared editable identity is disqualifying. Trustworthy identity + trail-completeness guarantee become RC1 blockers. Reports must state their own completeness. |

## 3. Out of scope for RC1 (deferred, documented limitations)

- **Concurrent-edit conflict resolution (A2)** — `STOCK_ADJUSTED` (absolute set) and `PRODUCT_UPDATED`
  (full replace) remain last-writer-wins. Acceptable under a single active user per site. Documented
  as a known limitation.
- **Live cross-workstation refresh (A6)** — no file-watch/push. The existing 15s poll absorbs synced
  remote events on the next tick, which is adequate single-user.
- **Notification send mechanism (B7 send)** — the PowerApp modeled a staff/distro list but *never had a
  send action*. The distro **data-management surface** is rebuilt for parity; actually emailing expiry
  alerts is a new feature deferred past RC1.
- **Data-migration importer (E1)** — not needed; start fresh.

## 4. Cross-cutting requirement: regulated audit trail

Because this deploys to military medical facilities under a regulated audit bar, the following are
hard constraints on every work-stream, not a single feature:

1. **Trustworthy attribution.** The event `actor` must come from the OS-authenticated Windows user and
   must not be user-editable. `deviceId` and `actor` are recorded on every event.
2. **Trail completeness.** The system must never silently compute on an incomplete event set. Any
   unreadable / online-only-placeholder / corrupt event file must raise a hard, visible "trail may be
   incomplete" state, and any exported report must state whether the underlying trail was complete.
3. **Append-only integrity.** No destructive deletes. Archive and restore are new events, never file
   removal. (The event model already gives this; the UI must honor it.)
4. **Tamper-evidence (should-have).** A hash chain over the ordered event log so post-hoc edits are
   detectable.

## 5. Work-streams

Severity legend: 🔴 blocker · 🟠 must-have · 🟡 should-have · ⚪ deferred.

### WS1 — Regulated audit & data-integrity core (build first)

| ID | Item | Current state → change |
|---|---|---|
| A-ID 🔴 | Trustworthy, non-editable identity | `actor` defaults to `USERDOMAIN\user` (`AppConfig.defaultActor`) but is freely editable via `/api/settings`. Remove `actor` from editable settings; derive from OS auth on each launch; record device + user on every event. |
| A-COMPLETE 🔴 | Trail-completeness guarantee | `EventStore.loadAll` collects per-file errors into a list the UI barely shows. Promote to a first-class "projection incomplete: N events unreadable" state; block/label reports accordingly; attempt to hydrate online-only files or warn. |
| A3 🔴 | Drain buffered events | `EventStore.retryPending()` exists but is **never called**. Call on startup, on a timer, and after operations; surface `pendingCount` as an alert (port `commercial-tracking-java` pattern). |
| A1 🔴 | Negative-inventory hard guard | `AppService.pick` blindly decrements; `Projection.apply` has no floor. Reject a pick that exceeds on-hand. |
| A4 🟠 | Orphan `.partial` recovery/cleanup | `finalizeShared` can leave `<name>.json.partial` if the process dies mid-move; nothing ever scans shared `.partial`. Add recovery/cleanup on load. |
| A8 🟠 | Crash-safe config save | `AppConfig.save` writes `client.json` without temp+atomic move (unlike `EventStore`/`LocalEventIndex`). Make it crash-safe. |
| A7 🟡 | Separate `occurredUtc` / `recordedUtc` | `SupplyEvents.base` sets them equal (dead tiebreaker, no backdating). Separate to support audited backdated corrections and meaningful merge ordering. |
| A-HASH 🟡 | Tamper-evidence hash chain | Add a chained hash over the ordered log; verify on load; surface breaks. |
| E3 🟠 | Expand SelfTest | No coverage for orphan-recovery, corrupt/duplicate-event, negative-quantity, or incomplete-projection. Add these cases. |

### WS2 — PowerApp parity features

| ID | Item | Notes |
|---|---|---|
| B1 🔴 | Archive browse + restore screen | Java writes archive events but has no way to view or restore them. Add a restore event type + an Archive screen (search + one-click restore), mirroring the PowerApp `Expireds` screen. |
| B2 🟠 | Auto-archive on zero-qty + bulk "Archive Expired Items" | Parity with Inventory/ItemList bulk-archive; auto-archive zero-quantity on removal. |
| B3 🟠 | Catalog edit / retire in UI | Management "Catalog controls" is read-only. Add edit (price/PAR/category/notes) and retire, mirroring PowerApp Registration CRUD. |
| B4 🟠 | Manufacturer + category filters | Inventory currently has plain text search only; add manufacturer multi-select + category filter. |
| B5 🟡 | Scanner UX settings | Add auto-focus, scan sound, auto-submit-on-scan, default quantity (PowerApp had all four; Java has only `scannerMinimumLength`). |
| B6 🟡 | Two-scan capture | Support GTIN scan then Lot/Expiry scan (PowerApp QuickPick behavior). |
| B7 🟠 | Distro data-management surface | Rebuild the staff/distro list management for parity. **Send mechanism deferred** (§3). |

### WS3 — Frontend production-hardening (interleave with WS2, per screen)

| ID | Item |
|---|---|
| C1 🔴 | Replace `window.prompt` Pick/Adjust/Archive with real dialogs (scanner-friendly, accessible, testable). |
| C2 🟠 | Wire or remove dead `/api/configure` & `/api/shutdown`; add an exit/quit path. |
| C3 🟠 | Add loading/disabled states to report export, settings save, folder chooser, inventory mutations (prevent double-submit). |
| C4 🟠 | Guard uncommitted-work loss in Scan/Count/Registration and the batch queue (nav confirm; the 15s auto-refresh amplifies this). |
| C5 🟡 | Surface GUDID lookup failures in Registration instead of silently swallowing them. |
| C6 🟡 | Add an error boundary; upgrade the single-slot toast to a queued, severity-aware notifier. |
| C7 🟡 | Add pagination/virtualization to Inventory/Count/Labels/Management full-table renders. |
| C8 🟡 | Accessibility: non-color status (esp. the B/W printed ledger), ARIA/table semantics, focus management, client-side scanner-length validation. |

### WS4 — Security hardening (small, cheap, do early)

| ID | Item |
|---|---|
| D1 🟠 | Add a recursion-depth cap to `Json.Parser` (a `StackOverflowError` is an `Error`, so it escapes the `catch(Exception)` guards in `GudidClient.lookup` and `EventStore.loadAll`). |
| D2 🟠 | URL-encode the GTIN in `GudidClient.lookup`; distinguish "lookup failed" from "not found." |
| D3 🟡 | CSV formula-injection defense + locale-safe number formatting + robust HTML attribute escaping in `ManagementReport`. |

### WS5 — Release & qualification (last)

| ID | Item |
|---|---|
| E2 🟠 | Complete a signed-off browser-smoke qualification run (`qualification/browser-smoke-evidence.md` is a blank template). |
| E4 🟠 | Bump `java-release-track.json` to `x.y.z-rc1` (channel `candidate`) + release notes. |
| E-DOCS 🟡 | Deployment / onboarding / backup-retention documentation for end users. |

## 6. Sequencing & decomposition

**Order:** WS1 → WS4 → (WS2 ⇄ WS3 interleaved by screen) → WS5.

- WS1 is the blocker (regulated-audit constraints + durability) and everything else assumes it.
- WS4 is small and cheap; clear it early.
- WS2 and WS3 both heavily touch the frontend; do them **per screen together** (e.g. build the Archive
  screen with its real dialogs, loading states, and a11y in one pass) to avoid rework.
- WS5 closes out with qualification and the RC1 track bump.

Each work-stream is its own spec → plan → implementation cycle. **WS1 is brainstormed in detail next.**

## 7. RC1 definition of done

RC1 is ready when:

1. All 🔴 and 🟠 items above are implemented and covered by tests (`build.ps1` green: every `*Test`
   PASS; `--self-test` PASS including the new WS1 cases).
2. Identity is OS-derived and non-editable; every event carries device + authenticated user.
3. An incomplete/corrupt/placeholder event set produces a visible warning, and reports state their
   completeness.
4. Buffered events are drained automatically and their count is surfaced.
5. Full PowerApp parity for the in-scope features (Archive/restore, bulk archive, catalog editing,
   filters, scanner UX, two-scan, distro management) is demonstrable against the PowerApp screens.
6. No `window.prompt`-driven data entry remains; no dead endpoints; write actions have loading/disabled
   states and uncommitted-work guards.
7. Security items D1/D2 are fixed.
8. A signed-off qualification run exists and `java-release-track.json` reads `x.y.z-rc1`.

Deferred items (§3) are documented as known limitations in the release notes.

## 8. Risks & open questions

- **Identity source on non-domain / shared-login workstations.** If workstations use a shared Windows
  login, OS-derived `actor` won't distinguish operators. Confirm the login model per site; may need a
  lightweight per-session operator prompt that is still not freely editable after the fact.
- **Tamper-evidence depth (A-HASH).** Full hash-chaining vs. relying on immutable synced files +
  existing content hashes — decide the acceptable bar with the audit stakeholder during WS1.
- **Notification send (B7).** Confirm it stays deferred; if alerting is required for adoption, it
  becomes its own post-RC1 spec.
- **"Start fresh" cutover.** With an empty day-one catalog, the first weeks are registration-heavy.
  Confirm operators accept this vs. a minimal catalog seed.
