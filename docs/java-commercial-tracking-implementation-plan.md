# Portable Java Commercial Tracking Implementation Plan

Status: Draft  
Branch: `feature/java-commercial-tracking`  
Companion specification: `docs/java-commercial-tracking-spec.md`

## 1. Delivery strategy

Build vertical slices, proving OneDrive synchronization and immutable-event correctness before investing in the full interface. Java 8 and Swing are the initial targets. Event, parser, and enrichment contracts remain language-neutral so a self-contained .NET client could use the same data later.

No phase may introduce direct Microsoft 365 authentication, a shared mutable database file, carrier credentials in the desktop client, or a dependency on immediate OneDrive propagation.

## 2. Proposed project structure

```text
commercial-tracking-java/
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/.../commercialtracking/
│   │   │   ├── app/
│   │   │   ├── config/
│   │   │   ├── domain/
│   │   │   ├── events/
│   │   │   ├── filesystem/
│   │   │   ├── index/
│   │   │   ├── manifests/
│   │   │   ├── parsing/
│   │   │   │   ├── gs1/
│   │   │   │   └── carriers/
│   │   │   ├── projection/
│   │   │   ├── recovery/
│   │   │   └── ui/
│   │   └── resources/
│   └── test/
│       ├── java/
│       └── resources/
│           ├── barcode-fixtures/
│           └── event-fixtures/
├── packaging/
└── scripts/
```

Before implementation, record short decisions for Maven versus Gradle, JSON library, disposable local index, printable HTML versus PDF, logging, launcher format, and update distribution.

Recommended defaults are Maven, a constrained Jackson configuration without polymorphic default typing, H2 or SQLite only for the disposable cache, printable HTML for the pilot, and a command launcher before any native wrapper.

## 3. Milestone 0 — synchronization spike

### Objective

Prove that two managed workstations can safely exchange immutable event files through the synchronized Teams library.

### Work

- Select a non-production synchronized test root.
- Build a small Java 8 event writer and scanner.
- Implement local pending, `.partial`, flush/close, and final rename.
- Generate UUID filenames and SHA-256 checksums.
- Produce 1,000 unique events from each of two clients.
- Create events offline, reconnect, and observe convergence.
- Measure propagation time in both directions.
- Inspect conflict-copy and SharePoint Created By behavior.
- Test antivirus delay, locked files, long paths, and interrupted writes.

### Exit criteria

- Zero lost or corrupted unique events.
- Both clients converge on the same checksums.
- Partial files are never parsed.
- Propagation delay is operationally acceptable.

## 4. Milestone 1 — domain and filesystem core

### Objective

Create a runnable application shell with durable commit, validation, deterministic replay, and recovery.

### Work

- Establish the Java 8 build and test framework.
- Add the Swing application shell and background executor.
- Add first-run synchronized-folder selection and validation.
- Define configuration and common event schemas.
- Implement bounded canonical JSON serialization.
- Implement pending retry and finalized event discovery.
- Add `WatchService` plus periodic reconciliation scanning.
- Implement checksums, quarantine diagnostics, and redacted logging.
- Implement deterministic ordering, package projection, and revisions.
- Add a disposable local index.

### Exit criteria

- A headless test writes, discovers, validates, indexes, and replays events.
- Cache deletion and replay reproduce identical state.
- Interrupted writes recover without exposing incomplete JSON.
- The Swing event-dispatch thread performs no blocking filesystem work.

## 5. Milestone 2 — barcode parsing foundation

### Objective

Replace brute-force parsing with a deterministic, fixture-tested pipeline.

### Work

- Define scan, candidate, parsed-field, confidence, and provenance types.
- Preserve ASCII group separators during normalization.
- Implement application package-reference parsing.
- Implement a GS1 AI tokenizer with explicit fixed/variable-length definitions.
- Support relevant SSCC, GTIN, shipment, consignment, routing, postal, weight, and dimension identifiers.
- Validate supported check digits.
- Implement conservative generic tracking recognition.
- Implement deterministic arbitration and ambiguity reporting.
- Create synthetic structurally valid fixtures.
- Define a controlled method for anonymizing real scanner captures.

### Tests

- Fixed- and variable-length GS1 elements.
- Multiple FNC1-separated elements.
- Missing, repeated, and misplaced separators.
- Valid and invalid check digits.
- Truncated and oversized scans.
- Equal-confidence disagreements and unknown formats.
- Bounded fuzz/property testing.

### Exit criteria

- Every supported fixture has an explicit expected result.
- Low-confidence values cannot be committed without confirmation.
- No parser failure can terminate scanner operation.

## 6. Milestone 3 — inbound receiving MVP

### Objective

Deliver a pilot-capable inbound workflow.

### Work

- Implement location and receiving-stream selection.
- Implement scanner-focused entry, Enter submission, and debouncing.
- Add UPS, FedEx, USPS, DHL, and generic parser modules driven by fixtures.
- Implement receive, rescan, and location-change rules.
- Detect duplicates against current local knowledge.
- Acknowledge success only after durable local commit.
- Add persistent text results and supplemental tones.
- Implement current-session ledger, search, and session recovery.
- Implement pending and sync-attention indicators.
- Implement indexed local history.

### Exit criteria

- Operators complete a representative batch without a mouse.
- Every success corresponds to a durable local event.
- Two-workstation inbound testing converges without overwrites.
- Offline work submits successfully after reconnecting.

## 7. Milestone 4 — manifests

### Objective

Generate auditable receiving manifests from exact immutable event IDs.

### Work

- Implement manifest preparation and print events.
- Implement detailed layouts for 1–20 items.
- Implement high-density layouts for 21–100 items.
- Generate application package-reference Code 39 barcodes where required.
- Produce printable HTML for the pilot.
- Record output SHA-256 and copy finalized documents into the synchronized manifest folder.
- Warn before clearing unmanifested session records.

### Exit criteria

- Layout tests pass for 1, 20, 21, 50, and 100 items.
- Every manifest traces to exact event IDs and revisions.
- Print cancellation does not claim physical print success.

## 8. Milestone 5 — parity workflows

### Objective

Complete recipient and custody functionality.

### Work

- Implement recipient assignment and reconciliation.
- Implement outbound scan and package confirmation.
- Implement release events and custody manifests.
- Implement two-step void and reasoned corrections.
- Implement package history with field provenance.
- Implement stale-revision detection.

### Exit criteria

- Core workflows in the Commercial Tracking user guide are represented.
- No void or correction mutates historical files.
- Recipient-specific manifests exclude unrelated packages.

## 9. Milestone 6 — conflicts and recovery

### Objective

Make eventual-consistency risks visible and recoverable.

### Work

- Classify duplicate receives and competing releases.
- Add conflict detail and supervisor resolution.
- Add pending, malformed-event, and cache-rebuild recovery screens.
- Add integrity verification and redacted diagnostic export.
- Document manual recovery.

### Tests

- Simultaneous offline receive and release.
- Out-of-order arrival and clock skew.
- Duplicate event ID with same and different content.
- Malformed and oversized events.
- Cache corruption and rebuild.

### Exit criteria

- Conflicts cannot disappear because of replay order.
- Clients derive the same outcome from the same event set.
- Every simulated conflict has a documented resolution path.

## 10. Milestone 7 — production hardening

### Work

- Accessibility, keyboard, high-DPI, and display testing.
- Performance profiling at the expected retention volume.
- Dependency, license, and security review.
- Backup, retention, and SharePoint versioning review.
- Packaging and launcher validation.
- Application signing if policy requires it.
- Operator, supervisor, rollback, and cutover documentation.
- Parallel-run comparison with the Power App.

### Exit criteria

- Specification acceptance criteria pass.
- Pilot users approve scan speed and recovery behavior.
- The operational owner accepts support and retention procedures.

## 11. Milestone 8 — optional carrier enrichment

### Preconditions

- Approved carrier developer account.
- Approved external-network execution environment.
- Power Automate or a credentialed worker that can protect secrets.
- Provider terms reviewed for permitted use, storage, and caching.

### Work

- Define versioned enrichment request/result schemas.
- Implement request creation and result ingestion in the client.
- Integrate one carrier in the external processor.
- Normalize metadata while retaining provider provenance.
- Handle token caching, throttling, timeout, and retry outside the client.
- Compare returned metadata with local decoding and manual entry.

### Exit criteria

- No provider secret reaches a client or synchronized file.
- Receiving remains functional during provider failure.
- The provider measurably reduces manual work or improves decisions.
- Each additional provider requires a separate value review.

## 12. Cross-cutting test strategy

- Unit tests cover event validation, projection, conflicts, parsing, normalization, and configuration.
- Integration tests use real filesystem operations for safe writes, watches, indexing, pending recovery, and manifests.
- A two-client simulator controls file visibility to reproduce delay, reordering, duplication, and offline work.
- Two actual managed workstations and the production OneDrive client are required for pilot validation.
- Shared JSON contracts receive compatibility fixtures before schema changes merge.

## 13. Definition of done

For every milestone:

- Code runs on Java 8.
- Automated tests cover new business rules.
- Blocking I/O stays off the Swing event-dispatch thread.
- Logs contain no unapproved raw label, recipient, or credential data.
- Shared contracts are versioned and documented.
- Cache state remains disposable.
- Every user-facing failure offers a recovery action.
- Specification and operating documentation remain current.

## 14. Initial work sequence

1. Create the Java module and build.
2. Implement the event envelope and validation.
3. Implement safe filesystem commit.
4. Implement deterministic headless replay.
5. Run the two-workstation synchronization spike.
6. Confirm that measured synchronization supports proceeding.
7. Implement the parser model and fixture harness.
8. Implement the inbound Swing vertical slice.

This sequence retires the largest architectural risk before investing in feature parity.
