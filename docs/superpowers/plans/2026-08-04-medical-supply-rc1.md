# Medical Supply Java RC1 Implementation Plan

**Source:** `docs/superpowers/specs/2026-08-04-medical-supply-rc1-scope-design.md`  
**Target:** `medical-supply-java`  
**Release:** `0.2.0-rc1`

## Spec critique and resolved interpretation

The scope is a useful program contract: it makes adoption requirements visible, sequences integrity
before convenience, and correctly treats identity and incomplete replay as release blockers. It is
not, by itself, an implementation-ready design. It says each work-stream still needs a spec while
also asking the definition of done to cover all five, and several requirements do not define their
data model, failure policy, or acceptance test.

The most important gaps are:

1. **Completeness has no precise boundary.** RC1 defines completeness as: every artifact discovered
   below `events/` that could represent an event (`*.json` or `*.partial`) has either been recovered
   and validated or is represented by a visible load error. A load error makes the projection
   incomplete. Reports are blocked while incomplete; they are not allowed to imply completeness.
2. **Hash chaining conflicts with independent writers.** A single linear previous-hash field cannot
   be assigned safely by offline/concurrent workstations. RC1 retains per-event canonical content
   hashes and duplicate-ID conflict detection, documents their limits, and defers a cryptographically
   coherent multi-writer chain until an audit stakeholder chooses a scheme. Pretending a racy chain
   is tamper-proof would be worse than documenting the actual guarantee.
3. **Identity is authenticated only to the workstation session.** Windows account name is OS-derived,
   immutable in the app, and attached to every event. It does not distinguish two people sharing one
   Windows login; deployment documentation must forbid shared logins for attributable use.
4. **Backdating lacks a workflow.** `recordedUtc` becomes creation time and `occurredUtc` can be a
   supplied business time only in audited correction operations. Ordinary operations use the same
   current instant. No general-purpose editable timestamp is exposed.
5. **Archive/retire/distro semantics are absent.** Restore is append-only `STOCK_RESTORED`; product
   retirement is append-only `PRODUCT_RETIRED`; distro changes are append-only replacement events.
   Zero-quantity auto-archive is emitted after a successful removal, never inferred only in the UI.
6. **The definition of done mixes priorities.** It says all red/orange items are required, but later
   includes some yellow items. This plan treats the enumerated definition-of-done list as binding,
   implements cheap yellow hardening, and documents pagination/tamper-chain limits if unfinished.
7. **Qualification cannot be self-signed.** Automated build/self-test and a prepared smoke protocol
   are implementation outputs. A named human must execute and sign browser evidence before release.

## Phase 1 — regulated core and security

- Derive actor from the current OS session on every load; remove actor from persisted/editable
  settings. Keep device ID editable because it identifies the workstation, not the operator.
- Save client configuration via same-directory temporary file, forced write, and atomic replace.
- Recover valid shared `.partial` files; report invalid/ambiguous partials as completeness failures.
- Retry buffered events on store open, before reload, after writes, and on the server refresh timer.
- Expose `trailComplete`, load-error count, pending count, and retry errors in state; show persistent
  alerts in the shell and diagnostics.
- Reject picks and absolute adjustments below zero. Auto-archive a lot after a removal reaches zero.
- Cap JSON nesting depth; encode GUDID query values and distinguish unavailable from not-found.
- Add CSV formula neutralization, locale-independent numeric output, and robust HTML escaping.
- Extend unit tests and `SelfTest` for corrupt/duplicate artifacts, orphan partial recovery, negative
  inventory, and incomplete projections.

## Phase 2 — parity and production UI, implemented screen-by-screen

- Add `STOCK_RESTORED`, `PRODUCT_RETIRED`, and distro configuration events/projections.
- Add Archive browse/search/restore and bulk archive-expired workflows.
- Add catalog edit and retire dialogs, manufacturer/category inventory filters.
- Replace Pick/Adjust/Archive prompts with accessible dialogs and focus restoration.
- Add scanner auto-focus, sound, auto-submit, default quantity, and two-scan capture settings.
- Add distro-list management (data only; no notification sender).
- Add per-action busy states, navigation/draft guards, visible GUDID errors, an error boundary, queued
  severity notifications, and bounded table rendering/pagination.
- Wire the existing shutdown endpoint to an explicit Quit action and remove unused configure calls
  from the browser client.

## Phase 3 — release and qualification

- Ensure every exported report states the trail status and refuse export when incomplete.
- Update onboarding, backup/retention, audit identity, and known-limitation documentation.
- Complete all machine-verifiable smoke evidence; leave the operator name/date/signature fields for
  the human qualification run.
- Set `java-release-track.json` to `0.2.0-rc1`, candidate channel; add release notes.
- Run `build.ps1 -Version 0.2.0-rc1`, all Java tests, `--self-test`, frontend production build, and
  `git diff --check`. Do not commit unrelated `commercial-tracking-java` changes.

## Acceptance criteria

- No editable actor control or actor setting API input; new events always carry non-empty OS actor
  and device ID.
- Any bad event artifact produces a persistent incomplete-trail banner and blocks report export.
- Pending writes retry automatically and remain visibly counted until published.
- A workstation cannot knowingly remove above freshly replayed on-hand stock; any negative balance
  produced by simultaneous offline workstations marks the trail incomplete and blocks writes/reports.
- Archive/restore, bulk expiry archive, catalog maintenance, filters, scanner settings/two-scan, and
  distro maintenance are operable without `window.prompt`.
- All write controls prevent duplicate submission and unsaved scanner/count/registration work is
  guarded.
- Release build and self-test pass; human browser qualification remains visibly unsigned until run.
