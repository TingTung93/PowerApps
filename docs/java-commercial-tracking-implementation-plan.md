# Portable Java Commercial Tracking Implementation Plan

Status: Draft for execution
Branch: `feature/java-commercial-tracking`
Product specification: `docs/java-commercial-tracking-spec.md`
UI/UX specification: `docs/java-commercial-tracking-ui-ux-spec.md`

## 1. Delivery objective

Deliver a portable Java 8 application that makes routine commercial-package receiving exceptionally easy while automatically creating a durable audit and accountability trail in a OneDrive-synchronized Teams/SharePoint document library.

The plan uses gated vertical phases. Each phase must leave the application testable and must not compromise the normal one-scan receiving path.

No phase may introduce:

- Workstation installation or administrator privileges.
- A Node.js or other runtime requirement on the target workstation.
- Direct Microsoft 365 authentication or API access.
- A shared mutable database file.
- Carrier credentials in the desktop client.
- Dependence on immediate OneDrive propagation.
- Editing or replacing finalized event files.

## 2. Starting point

The current prototype already proves several useful concepts:

- Java 8-compatible JAR packaging.
- Bundled React/MUI browser assets served from localhost.
- Swing compatibility fallback.
- User-selected synchronized folder.
- Immutable JSON package events.
- Basic UPS, FedEx, USPS, DHL, Amazon, ANSI MH10, and GS1 parsing.
- Inbound/outbound operations, recipient assignment, voiding, history, and printable HTML.
- One current-state session row per package plus separate event activity.

The prototype is a validation baseline, not the finished architecture. Production work should preserve working behavior while reorganizing code around explicit domain, storage, projection, scanner, reporting, and UI contracts.

## 3. Delivery principles

1. Correctness and recoverability precede visual completeness.
2. Routine receiving remains usable throughout development.
3. Shared data contracts are versioned before UI features depend on them.
4. Every accepted operation is durably local before success is shown.
5. The local cache is always disposable and rebuildable.
6. Advanced controls stay outside the scanner workspace.
7. Actual scanners and OneDrive clients are tested early, not only before release.
8. Optional carrier enrichment cannot block core receiving.

## 4. Target implementation shape

```text
apps/commercial-tracking-java/
├── build.ps1
├── README.md
├── frontend/
│   ├── package.json
│   ├── package-lock.json
│   └── src/
├── src/
│   ├── main/
│   │   ├── java/org/commercialtracking/
│   │   │   ├── app/
│   │   │   ├── config/
│   │   │   ├── domain/
│   │   │   ├── events/
│   │   │   ├── filesystem/
│   │   │   ├── index/
│   │   │   ├── manifests/
│   │   │   ├── parsing/
│   │   │   ├── projection/
│   │   │   ├── recovery/
│   │   │   ├── reports/
│   │   │   └── server/
│   │   └── resources/web/
│   └── test/
│       ├── java/
│       └── resources/
│           ├── barcode-fixtures/
│           ├── event-fixtures/
│           └── report-fixtures/
├── packaging/
├── releases/
└── scripts/
```

Development machines may use Node.js to compile the frontend. The built frontend is embedded into the JAR. Target workstations receive only the portable Java package.

## 5. Phase 0 — baseline stabilization and decision records

### Goal

Turn the current prototype into a reproducible baseline before adding major workflows.

### Work

- Inventory current behavior against both specifications.
- Preserve the current session-row consolidation and local-time display fixes.
- Document build prerequisites and exact offline deployment contents.
- Make `build.ps1` reproducible from a clean checkout.
- Confirm all generated frontend assets are embedded in the JAR.
- Record architecture decisions for:
  - JSON parser and validation strategy.
  - Disposable local index.
  - Bundled Java 8 PDF library.
  - Localhost security model.
  - Event and configuration schema evolution.
  - Logging and redaction.
- Add a release version source used by the JAR manifest, UI, and release notes.
- Establish test folders and synthetic data that cannot be confused with production.

### Verification

- Clean build produces one Java 8-compatible application package.
- Application launches without network access.
- Target launch requires neither Node.js nor installation.
- Parser and event-store tests pass.
- Packaged self-test passes.
- Browser console contains no startup errors.

### Exit gate

The team can reproduce, launch, and verify the baseline from a clean workspace.

## 6. Phase 1 — shared domain and storage foundation

### Goal

Create stable contracts for every later workflow.

### Work

- Split domain logic from browser-server and Swing presentation code.
- Define versioned schemas for:
  - Common event envelope.
  - Package events.
  - Manifest events and membership.
  - Configuration revisions.
  - Report metadata.
  - Optional enrichment requests/results.
- Populate device ID from normalized Windows computer name.
- Populate actor context from the current Windows account.
- Implement bounded JSON parsing, field validation, and schema-version checks.
- Implement safe pending write, flush, `.partial`, and finalized rename.
- Implement SHA-256 hashing and duplicate event-ID handling.
- Implement deterministic event ordering and package projection.
- Introduce a disposable local index abstraction.
- Implement complete cache deletion and replay.
- Add invalid-file quarantine metadata without moving or deleting shared files.
- Define extension points reusable by a later inventory application.

### Tests

- Valid and invalid schema fixtures.
- Interrupted and partially copied files.
- Duplicate event ID with identical and different content.
- Out-of-order discovery and clock skew.
- Cache deletion and deterministic rebuild.
- Oversized, deeply nested, malformed, and unknown-version JSON.

### Exit gate

The same event set always produces the same package state, and no incomplete shared file can be interpreted as finalized data.

## 7. Phase 2 — application shell and design system

### Goal

Build the final navigation and interaction foundation before expanding features.

### Work

- Replace the prototype dashboard layout with the specified application shell.
- Add a persistent workstation navigation rail:
  - Receive Packages.
  - Release Packages.
  - Current Session.
  - Package History.
  - Recipients.
  - Manifests.
  - Reports.
  - Attention.
  - Settings.
  - Diagnostics.
- Make Receive Packages the startup destination.
- Define shared MUI theme tokens for color, typography, spacing, focus, tables, side panels, and dialogs.
- Replace technical result labels with operational language.
- Add common components:
  - Page header.
  - Storage-state indicator.
  - Scan result card.
  - Package table.
  - Package detail side panel.
  - Activity timeline.
  - Filter panel.
  - Confirmation dialog.
  - Empty, loading, and failure states.
- Keep device ID, shared path, event totals, and raw diagnostics out of the scanner surface.
- Add accessible live regions and focus restoration.

### Verification

- Layout works at 1366×768 and 1920×1080 with Windows scaling through 150%.
- Keyboard navigation order matches visual order.
- Every icon action has a label or tooltip and accessible name.
- Receive screen shows scanner, location, last result, and recent packages without scrolling at the minimum target size.

### Exit gate

Stakeholders approve the shell and Receive workspace wireframe before feature workflows are built into it.

## 8. Phase 3 — scanner capture and calibration

### Goal

Reliably turn one physical scanner action into exactly one complete input across scanner configurations.

### Work

- Create a scanner-input state machine independent of barcode parsing.
- Support:
  - Enter/CR terminator.
  - Tab terminator.
  - Configurable terminator.
  - Suffixless idle completion.
  - Paste.
  - Manual typing and explicit processing.
- Preserve ASCII group, record, and file separators.
- Treat CR/LF as one terminator.
- Debounce duplicate terminators and repeat submission.
- Implement automatic-mode timing:
  - Inter-character burst measurement.
  - Configurable idle delay.
  - Minimum automatic-submit length.
  - Parser completeness check after quiet interval.
- Cancel pending auto-submit when the operator edits captured input.
- Add visible `Reading scanner…` feedback.
- Build the three-scan calibration workflow without retaining test values.
- Store scanner settings per workstation.
- Add timing-fixture playback for automated tests.

### Tests

- Enter, Tab, CR/LF, and no-suffix scanners.
- Variable-length 1D and long 2D payloads.
- Meaningful embedded control separators.
- Slow scanner and remote-desktop timing.
- Human typing with pauses.
- Duplicate suffixes and rapid repeated physical scans.
- Paste and manual correction.

### Exit gate

Representative scanners configured with Enter, Tab, and no suffix each create exactly one untruncated scan, while normal human typing does not submit prematurely.

## 9. Phase 4 — streamlined inbound receiving

### Goal

Deliver the primary operator workflow at production interaction quality.

### Work

- Implement the dedicated Receive Packages workspace.
- Require and remember a workstation receiving location.
- Keep scanner focus without repeated operator action.
- Run the parser pipeline off the UI thread.
- Complete and fixture-test carrier and standards parsers needed for pilot labels.
- Show `Package received` only after durable local save.
- Display carrier, tracking, location, local time, and recipient state.
- Keep the last result until the next scan.
- Add optional sound with distinct success and attention cues.
- Implement duplicate receive handling:
  - Keep existing record.
  - Record location change.
- Implement plain-language ambiguous-barcode confirmation.
- Keep parser internals and raw payloads out of the routine result.
- Add current-session recent rows with one row per package.
- Show pending/shared-folder state without claiming SharePoint upload.

### Verification

- Operator receives ten supported packages without mouse input.
- Each visible success maps to one durable event.
- Duplicate and ambiguity paths do not create silent duplicate packages.
- Offline receiving remains locally durable and visible.
- New operator completes a normal receive with only location confirmation and a scan.

### Exit gate

Inbound receiving is suitable for supervised pilot use before the remaining accountability features are added.

## 10. Phase 5 — sessions, history, and recipient reconciliation

### Goal

Make received packages findable and correctable without confusing packages with audit events.

### Work

- Persist session membership across restart.
- Implement Current Session package view.
- Implement separate Session Activity event timeline.
- Add package detail side panel with contextual actions.
- Implement indexed Package History search across tracking, manifest ID, carrier, recipient, location, status, and notes.
- Add advanced filters and exact barcode lookup.
- Implement complete package audit timeline with expandable UTC/event details.
- Implement unassigned-recipient queue.
- Add single and reviewed bulk recipient assignment.
- Record one immutable assignment event per affected package.
- Implement finish-session workflow and unmanifested-package warning.
- Never delete shared events when a session is closed.

### Tests

- Assignment changes do not create duplicate package rows.
- Session survives application restart.
- Search returns correct projections after cache rebuild.
- Bulk assignment is atomic per event and reports partial failure safely.
- Package count and event count remain visually and semantically distinct.

### Exit gate

Operators can account for every package received in a session and supervisors can reconstruct its full history.

## 11. Phase 6 — outbound release, corrections, and conflicts

### Goal

Complete custody transfer and exceptional accountability workflows.

### Work

- Implement separate Release Packages workspace.
- Require package lookup and verification before release.
- Show recipient prominently.
- Require explicit **Confirm release** for the initial release design.
- Block or route unassigned, voided, conflicted, and already released packages.
- Implement reasoned package corrections.
- Implement two-step void.
- Record Windows account, workstation, prior value, new value, and reason.
- Detect stale revisions and competing releases.
- Build the Attention queue ordered by severity.
- Implement deterministic conflict-resolution events.
- Allow corrective workflows to users with write access; do not add a separate application allowlist.

### Tests

- Concurrent and offline releases.
- Release after recipient reassignment.
- Stale correction and stale location change.
- Void and attempted post-void release.
- Same event set yields the same conflict state on two clients.

### Exit gate

No release, correction, void, or conflict resolution can erase or silently replace prior accountability history.

## 12. Phase 7 — audited manifests and PDF generation

### Goal

Produce immutable, reprintable accountability documents tied to exact package events or revisions.

### Work

- Select and license a bundled Java 8-compatible PDF library.
- Implement manifest domain and membership projection.
- Build **Prepare manifest** and **Manifest register** workspaces.
- Support:
  - Inbound receiving manifests.
  - Recipient custody manifests.
- Default inbound membership to eligible unmanifested packages in the current session.
- Add explicit inclusion/exclusion review.
- Generate human-readable collision-resistant manifest IDs.
- Implement:
  - Detailed layout for 1–20 packages.
  - Four-column high-density layout for 21–100.
  - Blocking/splitting guidance above 100.
- Add package-reference barcodes where permitted.
- Include local display time and exact UTC audit metadata.
- Write finalized document, checksum, and audit event before reporting success.
- Store PDFs under synchronized `manifests/<year>/<type>/`.
- Retain printable self-contained HTML fallback.
- Implement immutable register lookup and reprint using original ID/membership.
- Add finish-session transition into manifest preparation.

### Tests

- Golden layout fixtures at 1, 20, 21, 50, 100, and 101 packages.
- Long tracking numbers, recipient names, and locations.
- Page numbering, signatures, certification text, and barcode scan tests.
- Failure between document creation and audit commit.
- Reprint checksum and membership stability.
- Two workstations preparing overlapping manifests.

### Exit gate

Every finalized manifest can be traced to exact immutable membership, reprinted without changing identity, and validated by checksum.

## 13. Phase 8 — on-demand reports

### Goal

Provide useful daily, weekly, monthly, and custom accountability views without mutating audit state.

### Work

- Build the Reports workspace.
- Implement Receiving Activity and Outbound/Custody Activity.
- Add Day, Week, Month, and Custom range presets in the operational time zone.
- Implement specified filters, visible columns, grouping, sorting, and summaries.
- Make the recommended Receiving Activity Report the default:
  - Group by location.
  - Sort by received time.
  - Include operational and reconciliation fields.
  - Summarize by location, carrier, assignment state, and status.
- Label all output `Reporting Extract — not an audited manifest`.
- Generate PDF/print output on demand.
- Generate CSV with displayed fields plus UTC and immutable identifiers.
- Add **Open audited manifest** when all selected rows share one manifest ID.
- Save explicit copies under synchronized `reports/<year>/<period>/`.
- Do not schedule or automatically distribute reports.

### Tests

- Date boundaries across daylight-saving transitions.
- Day/week/month/custom range equivalence.
- CSV escaping and spreadsheet-safe output.
- Recipient and Unicode text.
- Reports above 100 packages.
- Report generation creates no package or manifest events.

### Exit gate

Operators can produce and save accurate on-demand extracts while clearly distinguishing them from audited manifests.

## 14. Phase 9 — settings, diagnostics, and recovery

### Goal

Expose necessary control and recovery without adding complexity to normal receiving.

### Work

- Build Settings categories:
  - Workstation.
  - Scanner.
  - Receiving defaults.
  - Date and time.
  - Printing and exports.
  - Feedback and accessibility.
  - Shared operational settings.
- Implement synchronized-root validation and health test.
- Implement local preference persistence.
- Implement shared JSON configuration validation, audit, prior-version retention, and rollback.
- Do not require application-level signatures.
- Add effective-setting source display.
- Build Diagnostics:
  - Runtime/application version.
  - Windows account and workstation.
  - Shared root.
  - Cache and event health.
  - Pending counts.
  - Last rescan.
  - Redacted export.
- Complete Attention/recovery actions:
  - Locally pending retry.
  - Overdue synchronization attention.
  - Malformed file detail.
  - Cache rebuild.
- Add one-year retention visibility and approved SharePoint disposition guidance; do not let the client partially delete event history.

### Exit gate

A workstation custodian can configure and diagnose the application without exposing technical controls in routine receiving, and invalid shared settings cannot replace the last valid configuration.

## 15. Phase 10 — synchronization and two-workstation qualification

### Goal

Prove the full product under its real OneDrive/SharePoint concurrency model.

### Work

- Run two actual managed workstations against a non-production synchronized library.
- Exercise separate receiving streams concurrently.
- Measure normal propagation against the 30-second expectation.
- Test offline creation, reconnect, reordering, and duplicates.
- Generate overlapping package operations, manifests, and configuration revisions.
- Test 1,000 rapid small-file additions.
- Test antivirus delays, file locks, long paths, OneDrive conflict copies, and interrupted shutdown.
- Confirm SharePoint Created By and version-history behavior.
- Verify saved reports and PDFs synchronize and remain readable.
- Document operator response when propagation exceeds 30 seconds.

### Exit gate

- No unique event is lost or overwritten.
- Both clients converge after synchronization.
- Conflicts remain visible and resolvable.
- Normal connected events are usually visible cross-workstation within 30 seconds.
- Offline work recovers without manual file editing.

## 16. Phase 11 — release-candidate hardening

### Goal

Produce the package used for formal release-candidate testing.

### Work

- Complete accessibility review against WCAG 2.1 AA expectations.
- Test keyboard-only and representative assistive technology.
- Test Windows display scaling, browser zoom, and supported screen sizes.
- Profile startup, scan acknowledgement, search, replay, PDF, and memory.
- Run dependency, license, and security review.
- Verify localhost token, origin checks, random port binding, and shutdown.
- Confirm no network-loaded UI assets.
- Validate execution from read-only and space-containing paths.
- Test on the actual Java 8 workstation image.
- Produce:
  - Versioned ZIP.
  - SHA-256 checksum.
  - Release notes.
  - Operator quick-start.
  - Workstation setup guide.
  - Recovery guide.
  - Known limitations.
- Run clean-folder upgrade and rollback exercises.

### Exit gate

All specification acceptance criteria pass, all critical defects are closed, and the versioned ZIP runs on a representative managed workstation without installation, elevation, Node.js, or internet access.

## 17. Phase 12 — operational pilot and release

### Goal

Validate usability and accountability with real operators before replacing the Power App.

### Work

- Train pilot operators using the one-sentence receiving instruction.
- Run the Java app in parallel with the existing process for an agreed sample.
- Measure:
  - First-attempt receive success.
  - Scan-to-result time.
  - Duplicate/truncated scans.
  - Manifest/report accuracy.
  - Pending/conflict frequency.
  - Operator recovery success.
- Reconcile sample records and manifests between old and new processes.
- Collect structured usability feedback.
- Fix release-blocking findings and repeat affected tests.
- Obtain operational-owner approval for cutover, retention, support, and rollback.

### Exit gate

- No unexplained scan losses.
- No duplicate commits from one physical scan.
- At least 95% first-attempt success for routine receiving.
- Operators correctly distinguish package count, event activity, audited manifests, and reporting extracts.
- Operational owner approves production use.

## 18. Phase 13 — optional carrier enrichment

### Preconditions

- Approved carrier developer account and terms.
- Confirmed endpoint reachability from an approved worker or Power Automate.
- Approved credential storage and data-retention rules.

### Work

- Finalize versioned enrichment request/result schemas.
- Emit immutable requests from the Java client.
- Process requests in an approved credentialed environment.
- Integrate one carrier as a bounded pilot.
- Preserve provider and field-level provenance.
- Apply bounded retry, timeout, throttling, and caching outside the client.
- Display enrichment as supplemental metadata.
- Never silently overwrite operator-confirmed values.
- Measure whether returned data reduces manual work.

### Exit gate

- No provider credential reaches the client or synchronized storage.
- Core receiving works normally during enrichment failure.
- The selected provider delivers enough operational value to justify continued support.

## 19. Cross-cutting engineering work

### 19.1 Automated testing

- Unit tests: parsers, validation, projection, conflict rules, configuration, scanner state machine, report selection.
- Integration tests: real filesystem writes, pending recovery, replay, manifests, reports, and shared configuration.
- Contract tests: compatibility fixtures for every shared schema version.
- UI tests: critical receive, release, session, manifest, report, and settings paths.
- Golden-file tests: printable HTML, PDFs, CSVs, and checksums.
- Fuzz/boundary tests: barcode input, JSON, filenames, HTML, CSV, and report text.

### 19.2 Manual hardware testing

- Each deployed scanner model.
- Enter, Tab, and suffixless configurations.
- Code 128, Data Matrix, supported 2D formats, and control separators.
- Actual printers and 100% scale.
- Actual managed Java 8 workstation image.
- Actual OneDrive client and synchronized Teams library.

### 19.3 Security and privacy

- Bind only to `127.0.0.1` on a random port.
- Require a per-launch session token for state-changing API calls.
- Validate Origin and request size.
- Sanitize generated HTML, PDF, filenames, and CSV.
- Keep raw payload and recipient data out of routine logs.
- Treat every synchronized file as untrusted input.
- Inventory and ship third-party licenses.

### 19.4 Documentation

Update the PRD, UI/UX specification, schemas, test plan, and operator documentation in the same change that alters behavior.

## 20. Phase dependency map

```text
0 Baseline
  └─ 1 Domain/storage
      ├─ 2 Shell/design
      │   └─ 3 Scanner capture
      │       └─ 4 Inbound receiving
      │           ├─ 5 Session/history/recipients
      │           │   ├─ 6 Release/corrections/conflicts
      │           │   └─ 7 Manifests
      │           │       └─ 8 Reports
      │           └─ 9 Settings/diagnostics/recovery
      └────────────── 10 Two-workstation qualification
                         └─ 11 RC hardening
                             └─ 12 Pilot/release

13 Carrier enrichment begins only after its external preconditions are met.
```

Phase 2 may begin once the Phase 1 API contracts are stable. Scanner timing work can proceed alongside shell implementation, but it cannot be accepted until integrated into the real Receive workspace. Settings infrastructure should be introduced early enough to support scanner configuration, then completed in Phase 9.

## 21. Recommended release checkpoints

### Internal build A — receiving foundation

Phases 0–4:

- Reproducible portable build.
- Final shell.
- Suffix-independent scanner capture.
- Streamlined inbound receiving.

### Internal build B — accountability

Phases 5–7:

- Sessions/history/recipients.
- Release/corrections/conflicts.
- Audited PDF manifests.

### Internal build C — operational completeness

Phases 8–10:

- On-demand reports.
- Settings/diagnostics/recovery.
- Two-workstation qualification.

### Release candidate

Phases 11–12:

- Hardened package.
- Formal workstation and usability testing.
- Pilot approval and production decision.

## 22. Definition of done for every phase

- Java production code compiles to Java 8 bytecode.
- Target package requires no runtime other than installed Java 8 and an existing browser.
- Automated tests cover new business rules and failure paths.
- Blocking filesystem work stays off UI request/interaction paths.
- Every success state corresponds to durable local data.
- Shared contracts remain versioned and backward-readable.
- Cache/index state remains disposable.
- User-facing failures state whether work was saved and what to do next.
- Routine logs contain no raw barcode, recipient, tracking, or credential data.
- Accessibility and keyboard behavior are preserved.
- Product, UI/UX, schema, and operating documentation remain current.

## 23. Immediate next implementation sequence

1. Complete Phase 0 by committing the current session/timestamp fixes and producing a clean reproducible baseline.
2. Refactor the event store and projection behind stable Phase 1 interfaces without changing operator behavior.
3. Implement the Phase 2 navigation shell and focused Receive workspace.
4. Build and hardware-test the Phase 3 scanner state machine.
5. Integrate it into the Phase 4 inbound vertical slice.
6. Conduct a short operator review before proceeding to accountability and reporting features.

This sequence addresses the highest usability and data-integrity risks before investing in the broader administrative surface.
