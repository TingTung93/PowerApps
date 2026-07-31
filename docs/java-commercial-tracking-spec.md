# Portable Commercial Tracking Application

## Product and Technical Specification

Status: Draft for review  
Target application: Commercial Package Tracking  
Primary runtime: Java 8  
Storage transport: User-authenticated OneDrive synchronization of a Teams/SharePoint document library  
Deployment model: Portable desktop application; no installer, elevation, tenant app registration, or direct Microsoft 365 API access

## 1. Purpose

Replace the Commercial Tracking Power App with a responsive desktop application optimized for keyboard-wedge barcode scanners while retaining Teams/SharePoint Online as the shared storage system.

The application will access only ordinary files in a locally synchronized folder. The installed OneDrive client remains responsible for Microsoft authentication, authorization, upload, download, retry, and offline synchronization.

The first release targets at most two concurrent operators working primarily in separate receiving streams. It accepts eventual consistency between workstations and makes rare conflicting operations visible and recoverable.

## 2. Goals

- Start and operate without local administrator privileges.
- Run on the Java 8 runtime already present on managed workstations.
- Require no Entra application registration or Microsoft Graph permission.
- Preserve inbound receiving, outbound release, history, reconciliation, voiding, and manifest workflows.
- Replace ad hoc barcode substring parsing with a tested standards-first parsing pipeline.
- Keep an immutable audit history.
- Continue operating temporarily when SharePoint or the network is unavailable.
- Avoid corruptible shared database files and concurrent file editing.
- Provide materially faster scanning, searching, navigation, and printing than the canvas app.
- Make local persistence and synchronization state clear to the operator.
- Allow a later migration to a server API without changing the user-facing workflows or historical event format.

## 3. Non-goals

- Strong real-time consistency across workstations.
- Distributed file locking.
- Guaranteed prevention of two simultaneous operations on the same package.
- Direct calls to SharePoint, Teams, Graph, Dataverse, or Power Automate.
- Requiring a carrier API for normal receiving or release.
- Embedding carrier API secrets in a portable client.
- Reliably deriving the authenticated Microsoft identity inside Java.
- Supporting arbitrary numbers of simultaneous high-volume scanners.
- Treating OneDrive synchronization as a conventional network filesystem.
- Editing finalized transaction files.

## 4. Operating assumptions

- Windows is the initial target platform.
- The organization supplies Java 8 on every workstation.
- Each user can synchronize the designated Teams/SharePoint library through OneDrive.
- Each operator has write access to the shared application folder.
- The OneDrive client starts independently of this application.
- Normal operations involve no more than two concurrent users.
- The two users generally work in separate receiving locations or streams.
- Keyboard-wedge scanners send an Enter/Return suffix.
- Occasional synchronization delays of seconds or minutes are operationally acceptable.
- A rare conflicting package operation may be resolved by a supervisor.

## 5. Architecture

```text
+------------------------+
| Java desktop client    |
|                        |
| Swing UI               |
| barcode parser         |
| business rules         |
| event projector        |
| manifest generator     |
+-----------+------------+
            |
            +---- local cache/index (not synchronized)
            |
            +---- immutable JSON events
                          |
                          v
+--------------------------------------------------+
| Locally synchronized OneDrive/Teams folder      |
+-------------------------+------------------------+
                          |
                          v
+--------------------------------------------------+
| SharePoint Online document library              |
| Authentication and transport owned by OneDrive  |
+--------------------------------------------------+
```

The synchronized directory is an immutable event store and document repository. It is not a shared database. Each client derives current package state by replaying the event stream into a private, rebuildable local index.

## 6. Shared folder layout

```text
CommercialTracking/
├── configuration/
│   ├── application.json
│   ├── locations.json
│   └── carriers.json
├── events/
│   ├── 2026/
│   │   ├── 07/
│   │   └── 08/
├── manifests/
│   ├── 2026/
│   │   ├── inbound/
│   │   └── custody/
├── snapshots/
├── reconciliation/
└── diagnostics/
```

Event directories are partitioned by UTC year and month to prevent one directory from accumulating an unbounded number of files. Workstation-specific subdirectories may be added if OneDrive performance testing shows a benefit, but correctness must not depend on directory ownership.

Configuration files are exceptional mutable documents. Changes to them are administrative and infrequent. Clients retain the last valid configuration if a synchronized configuration file is temporarily unavailable or malformed.

## 7. Local folder layout

Local state must be outside the synchronized library:

```text
%LOCALAPPDATA%\CommercialTracking\
├── config/
│   └── client.json
├── cache/
│   ├── event-index.db
│   └── projections.db
├── pending/
├── logs/
└── recovery/
```

The application must remain functional if the cache is deleted. It rebuilds the cache by replaying shared events plus any local pending events.

No OAuth tokens, Microsoft passwords, SharePoint cookies, or OneDrive credentials are stored.

## 8. Event storage rules

1. Every business operation creates a new immutable event.
2. Every event has a UUID generated by the originating client.
3. Finalized events are never changed or deleted by the application.
4. Corrections, voids, and reversals are additional events referencing earlier events.
5. Filenames are globally unique and contain no recipient or tracking information.
6. Business timestamps use UTC ISO-8601 with millisecond precision.
7. Display timestamps are converted to local time by the UI.
8. Unknown fields must be ignored to permit forward-compatible schema changes.
9. A duplicate `eventId` represents the same event and is processed only once.
10. Invalid files are quarantined logically and reported; clients do not delete them.

### 8.1 Filename

```text
<utc-basic-time>_<device-id>_<event-id>_<event-type>.json
```

Example:

```text
20260731T001530284Z_WS017_a18f79a9-058c-4f63-86a4-3a8f11480e13_PACKAGE_RECEIVED.json
```

Device identifiers must contain only uppercase letters, digits, and hyphens. The event ID, rather than the timestamp or device ID, provides uniqueness.

### 8.2 Safe commit procedure

1. Construct and validate the complete event in memory.
2. Write it to `%LOCALAPPDATA%\CommercialTracking\pending\<event-id>.tmp`.
3. Flush and close the file.
4. Copy it into the target synchronized directory using a temporary `.partial` name.
5. Flush and close the synchronized temporary file.
6. Atomically rename `.partial` to `.json` when supported.
7. Retain the local pending copy until the final file remains observable in the synchronized directory.
8. Never parse `.tmp` or `.partial` files as events.

If an atomic move is not supported by the synchronized directory, the application must copy to `.partial`, close it, and rename it in the same directory. A finalized JSON file must never be exposed while it is still being written.

## 9. Common event envelope

```json
{
  "schemaVersion": 1,
  "eventId": "a18f79a9-058c-4f63-86a4-3a8f11480e13",
  "eventType": "PACKAGE_RECEIVED",
  "occurredUtc": "2026-07-31T00:15:30.284Z",
  "recordedUtc": "2026-07-31T00:15:30.301Z",
  "deviceId": "WS017",
  "sessionId": "bb779d91-c110-443d-9170-92b2b81d8375",
  "streamId": "MAIN-RECEIVING",
  "actor": {
    "windowsAccount": "DOMAIN\\username",
    "displayName": "Optional operator-entered/display value"
  },
  "packageKey": "TRACKING:1Z9999999999999999",
  "observedRevision": 4,
  "payload": {}
}
```

`actor` is audit context, not a cryptographically verified Microsoft identity. SharePoint's Created By file metadata remains available outside the application if an authoritative uploader identity is needed during investigation.

`packageKey` is created from the normalized tracking number. It must not be used as a filename because tracking numbers may be operationally sensitive.

`observedRevision` records the package revision visible to the client when the action was initiated. It enables conflict detection but is not a distributed lock.

## 10. Event types

### 10.1 `PACKAGE_RECEIVED`

Required payload:

```json
{
  "trackingNumber": "1Z9999999999999999",
  "carrier": "UPS",
  "location": "Main Receiving",
  "receivedAtUtc": "2026-07-31T00:15:30.284Z",
  "displayAddressee": "",
  "displayAddressBlock": "",
  "displayWeight": "",
  "displayPackageType": "",
  "scanNotes": "",
  "rawBarcodeHash": "sha256:..."
}
```

Raw label data should not be retained unless operationally required. Prefer storing parsed fields and a hash of the raw input.

### 10.2 `PACKAGE_LOCATION_CHANGED`

References an active package and records old and new locations.

### 10.3 `PACKAGE_RECIPIENT_ASSIGNED`

Records the normalized recipient plus optional display form. The previous recipient remains available through history.

### 10.4 `PACKAGE_RELEASED`

Required payload includes recipient, release time, release method, and the receive event or package revision observed by the operator.

### 10.5 `PACKAGE_VOIDED`

References the event being voided and includes a reason. A void never removes the original event.

### 10.6 `PACKAGE_CORRECTED`

Contains an explicit map of corrected fields, previous observed values, and a required correction reason. Audit fields such as original event ID, event time, and device cannot be corrected.

### 10.7 `MANIFEST_PREPARED`

Contains:

- Manifest ID.
- Manifest type.
- Stream/location or recipient.
- Exact ordered event/package references included.
- Preparing user context.
- Preparation time.

### 10.8 `MANIFEST_PRINTED`

References `MANIFEST_PREPARED`, records output filename and checksum, and indicates that the application invoked the print action. It must not claim that paper physically printed successfully.

### 10.9 `CONFLICT_RESOLVED`

References every competing event, names the accepted outcome, and contains the supervisor's reason.

### 10.10 `PACKAGE_ENRICHED`

Records optional metadata returned by an external carrier integration. It references the package and enrichment request, names the provider, includes retrieval time and field provenance, and must not silently replace operator-confirmed fields.

Enrichment events are additive. Failure or absence of enrichment does not invalidate receiving, release, manifest, or history operations.

## 11. Derived package state

Clients sort events deterministically by:

1. `occurredUtc`
2. `recordedUtc`
3. `deviceId`
4. `eventId`

This provides identical replay results on every client after all events synchronize. Ordering does not make a conflicting physical operation valid; the conflict detector operates separately.

Derived states:

- `RECEIVED`
- `AWAITING_RECIPIENT`
- `READY_FOR_PICKUP`
- `PICKED_UP`
- `VOIDED`
- `CONFLICT`

Every accepted state transition increments the derived revision. Invalid or conflicting events remain in history but do not silently replace a previously accepted result.

## 12. Conflict policy

Given the expected two-user, separate-stream deployment, conflict handling favors detection and recovery over distributed coordination.

### 12.1 Duplicate inbound scan

- Same tracking number in the same stream and session: warn and do not create another receive event unless the operator confirms a legitimate rescan.
- Same active tracking number received by another stream: preserve both events and flag `POSSIBLE_DUPLICATE_RECEIVE`.
- A confirmed rescan may generate `PACKAGE_LOCATION_CHANGED` instead of another receive.

### 12.2 Concurrent release

If two release events reference the same active package revision:

- The package becomes `CONFLICT`.
- Both release events remain visible.
- Neither is hidden or deleted.
- The UI displays the actors, devices, and times.
- A supervisor records `CONFLICT_RESOLVED`.

### 12.3 Stale update

If `observedRevision` is older than the current derived revision:

- Harmless additive information may be accepted.
- State transitions and corrections require review.
- The UI must not silently apply a stale destructive transition.

### 12.4 Determinism

Clients must reach the same derived state from the same event set. Conflict resolution cannot depend on filesystem enumeration order or the workstation's local time zone.

## 13. Synchronization awareness

The Java application cannot reliably determine OneDrive server state through ordinary filesystem APIs. It therefore reports application-observable states:

- `LOCAL_PENDING`: event is safely stored locally but not finalized in the sync folder.
- `SUBMITTED_TO_SYNC_FOLDER`: finalized event exists in the local synchronized folder.
- `OBSERVED_AFTER_RESCAN`: event was rediscovered during a later full/delta scan.
- `SYNC_ATTENTION`: pending or partial state exceeded the configured threshold.
- `CONFLICT`: competing business events were observed.

The UI must not label an event "uploaded to SharePoint" without an authoritative server acknowledgement, which this architecture does not have.

OneDrive shell overlay icons may be useful to the operator, but the application must not scrape or depend on them.

## 14. Functional requirements

### 14.1 First run

- Prompt the user to select the synchronized `CommercialTracking` root.
- Validate the expected configuration and folder structure.
- Confirm the root is writable using a harmless unique probe file.
- Let the user choose or create a device ID.
- Load receiving locations.
- Perform an initial event scan and cache rebuild.
- Display any malformed files or unresolved conflicts before normal operation.

### 14.2 Barcode parsing

Barcode interpretation uses a deterministic parser pipeline rather than one monolithic expression or an unstructured sequence of substring guesses.

Parsing order:

1. Normalize scanner framing without discarding meaningful control separators.
2. Recognize application-owned `PKGID-<id>` package references.
3. Decode standards-based formats.
4. Run carrier-specific parsers.
5. Run conservative generic tracking-number recognition.
6. Ask the operator to confirm or enter missing required values.

Standards support:

- GS1 Application Identifiers.
- GS1-128 and FNC1/group-separator handling.
- GS1 DataMatrix scanner output.
- SSCC, GTIN, shipment, consignment, routing, postal code, weight, and dimension fields relevant to logistics.
- ANSI MH10.8.2/ASC data identifiers when representative labels require them.
- MaxiCode decoded scanner output when supported by the deployed scanner.

The parser must retain the difference between a field explicitly encoded in the barcode, validated by carrier structure, inferred heuristically, confirmed by an operator, or returned later by an external provider.

Every parsed field has a value, confidence, source, and confirmation requirement:

```json
{
  "value": "1Z9999999999999999",
  "confidence": "HIGH",
  "source": "UPS_1Z_CHECK_DIGIT",
  "requiresConfirmation": false
}
```

Confidence values:

- `VERIFIED`: standards decoding plus validation/check digit where applicable.
- `HIGH`: carrier structure and validation strongly identify the field.
- `MEDIUM`: plausible inference that should be visible to the operator.
- `LOW`: suggestion only; never committed without confirmation.
- `NONE`: not available.

Parser interface:

```java
public interface BarcodeParser {
    ParseCandidate parse(ScanPayload payload, ParseContext context);
}
```

Initial parser modules:

- `ApplicationPackageReferenceParser`
- `Gs1Parser`
- `UpsLabelParser`
- `FedExLabelParser`
- `UspsLabelParser`
- `DhlLabelParser`
- `GenericTrackingParser`

The orchestrator combines candidates by explicit precedence and confidence rules. Parser registration order must not resolve equal-confidence disagreement silently. Ambiguous candidates are shown to the operator.

Parser development uses an anonymized fixture corpus containing representative raw scanner strings and expected fields. Fixtures preserve meaningful separators while replacing names, addresses, identifiers, and tracking numbers with structurally valid synthetic values.

### 14.3 Inbound receiving

- Require a receiving location/stream.
- Keep scanner focus after every completed action.
- Normalize the scanned barcode.
- Parse the barcode through the standards-first parser pipeline.
- Display fields that require operator confirmation.
- Resolve supported package-reference barcodes.
- Infer known carriers where possible.
- Detect locally known active packages.
- Write a `PACKAGE_RECEIVED` or appropriate rescan/location event.
- Add the scan to the current session ledger immediately after durable local commit.
- Display persistent success, warning, or error text.
- Play distinct supplemental sounds.

### 14.4 Outbound release

- Locate the latest derived package state.
- Display tracking number, recipient, location, and received time prominently.
- Require explicit confirmation when the package is stale, unassigned, conflicted, or not ready.
- Create `PACKAGE_RELEASED`.
- Never hide a later-arriving conflicting release.

### 14.5 Session ledger

- Show only operations from the current client session by default.
- Search tracking, carrier, recipient, location, label details, and notes.
- Retain session membership across an application restart until closed explicitly.
- Warn before clearing a session containing unmanifested inbound records.
- Clearing a local session view does not remove shared events.

### 14.6 History

- Search the local derived index without rescanning every file.
- Support tracking number, manifest ID, carrier, recipient, location, status, and notes.
- Show event-level audit history for a selected package.
- Clearly distinguish accepted, duplicate, stale, voided, and conflicting events.

### 14.7 Recipient reconciliation

- List active packages without an assigned recipient.
- Allow assignment through `PACKAGE_RECIPIENT_ASSIGNED`.
- Preserve the prior value and actor in history.

### 14.8 Voiding and correction

- Require a two-step confirmation for void.
- Require a reason for correction or conflict resolution.
- Never delete an event file.
- Restrict supervisor functions through an application-configured allowlist only as a workflow aid; without verified identity, this is not a security boundary.

### 14.9 Manifests

- Generate inbound receiving manifests from exact event IDs in the current session.
- Generate recipient custody manifests from exact active package revisions.
- Assign a UUID-based manifest ID.
- Produce PDF when a suitable bundled Java PDF library is approved.
- Otherwise produce printable HTML and open the system browser/print dialog.
- Store the finalized document under `manifests/<year>/<type>/`.
- Store a SHA-256 checksum in `MANIFEST_PRINTED`.
- Preserve detailed and high-density layouts comparable to the Power App.

### 14.10 Optional carrier enrichment

Carrier APIs are optional, asynchronous metadata providers. They are not barcode decoders and are not part of the critical scan-commit path.

Potential enrichment fields include carrier confirmation, shipment status, service level, estimated delivery, latest carrier event, package count, and weight or dimensions when available. Recipient name and full address must not be assumed to be available.

The portable client must not contain carrier or third-party API secrets. If enrichment is approved later, it uses a language-neutral request/result transport:

```text
Java client
    -> immutable enrichment request file
    -> OneDrive/SharePoint synchronization
    -> approved Power Automate flow or credentialed worker
    -> immutable enrichment result file
    -> Java client
```

Requirements:

- Receiving succeeds while enrichment is unavailable.
- Timeouts and rate limits never block scanner focus.
- Results are cached only as permitted by provider terms.
- Retry is bounded and honors throttling.
- Enrichment never overwrites an operator-confirmed field silently.
- Network calls occur only in the approved external worker or flow.
- Provider secrets never enter synchronized storage.
- Raw provider responses are not retained unless specifically approved.
- Responses retain provider and field-level provenance.

## 15. User interface

Initial implementation uses Java Swing with the system look and feel.

Primary views:

1. Receive and Release
2. Current Session
3. Package History
4. Reconcile Recipients
5. Manifests
6. Conflicts and Recovery
7. Settings and Diagnostics

Scanner workflow requirements:

- A scan must be processable without mouse input.
- Enter submits.
- Focus returns to the scan field.
- Scan processing must never block the Swing event-dispatch thread.
- Duplicate rapid Enter events must be debounced.
- The visible result remains until the next scan.
- Color is never the only indication of status.
- An operator can copy the last tracking number and error details.

## 16. Local index

The index is an implementation detail and not authoritative.

Acceptable implementations:

- H2 version compatible with Java 8.
- SQLite through a bundled Java 8-compatible JDBC driver.
- Custom immutable index plus serialized projections for the small expected data volume.

Required index capabilities:

- Event ID deduplication.
- File path, size, modification time, and checksum tracking.
- Package projection.
- Manifest projection.
- Session projection.
- Full-text-like normalized search fields.
- Conflict and invalid-event queues.

The index must use one local writer and must never be placed inside OneDrive.

## 17. Filesystem monitoring

- Use `WatchService` for prompt notification.
- Treat notifications only as hints because events may be coalesced or dropped.
- Run a periodic reconciliation scan.
- Run a full validation scan at startup.
- Ignore temporary and hidden files.
- Wait for file size and modification time to stabilize before parsing an unexpected `.json` file.
- Retry transient access-denied and sharing violations with bounded exponential backoff.
- Never retry a malformed finalized event indefinitely.

## 18. Configuration

`application.json`:

```json
{
  "schemaVersion": 1,
  "applicationName": "Commercial Tracking",
  "eventScanSeconds": 15,
  "pendingWarningSeconds": 120,
  "defaultTimeZone": "America/Los_Angeles",
  "historyRetentionYears": 7,
  "maximumDetailedManifestItems": 20,
  "maximumManifestItems": 100
}
```

`locations.json`:

```json
{
  "schemaVersion": 1,
  "locations": [
    {"id": "MAIN-RECEIVING", "name": "Main Receiving", "enabled": true},
    {"id": "LOADING-DOCK", "name": "Loading Dock", "enabled": true},
    {"id": "MAILROOM", "name": "Mailroom", "enabled": true},
    {"id": "WAREHOUSE", "name": "Warehouse", "enabled": true}
  ]
}
```

Every configuration document must be validated before replacing the last known valid configuration.

## 19. Security and information protection

- Store only operationally required package and recipient information.
- Do not retain raw barcode payloads by default.
- Permit parser diagnostic capture only through an explicit redacted/anonymized workflow.
- Sanitize all filenames and generated HTML.
- Parse JSON with bounded sizes and nesting depth.
- Treat synchronized files as untrusted input even though the library is access-controlled.
- Treat external carrier responses as untrusted, size-bound input.
- Do not deserialize Java objects from shared storage.
- Do not execute scripts or load classes from the synchronized folder.
- Redact recipient and tracking details from routine logs.
- Make diagnostic export an explicit operator action.
- Use SharePoint/Teams permissions and managed-device controls as the actual access boundary.
- Record the local Windows account for audit context while clearly documenting that it is not a verified Microsoft identity.

## 20. Reliability and recovery

- A scan is acknowledged as locally saved only after the local pending event is flushed.
- Failure to copy into the sync folder leaves the event in `pending` and visible in Recovery.
- Pending events are retried automatically.
- The operator can export pending events to removable or approved recovery storage only when policy permits.
- Cache corruption triggers quarantine and a replay rebuild.
- A malformed shared event cannot prevent other events from loading.
- Clock skew must not discard events; it should generate a diagnostic warning.
- Event replay and conflict detection must be covered by deterministic tests.
- Parser behavior must be covered by synthetic and anonymized fixture tests.

## 21. Performance targets

On a representative managed workstation:

- Application usable within 3 seconds when cache is current.
- Local scan acknowledgement within 250 ms, excluding manifest generation.
- Search results within 200 ms for 100,000 cached events.
- Incremental event ingestion without freezing the UI.
- Initial rebuild progress visible for more than 2 seconds of work.
- Memory target below 256 MB for normal operation.

OneDrive propagation time is measured during the pilot but is not included in local UI latency targets.

## 22. Packaging and deployment

### 22.1 Java 8 baseline

```text
CommercialTracking/
├── CommercialTracking.exe or CommercialTracking.cmd
├── app/
│   ├── commercial-tracking.jar
│   └── lib/
├── config/
└── VERSION.txt
```

- Compile for Java 8 bytecode.
- Use Swing, avoiding a separate JavaFX dependency.
- Bundle all third-party JARs.
- Prefer a launcher that checks the Java version and produces a readable error.
- Do not write beside the executable after startup.
- Support execution from a read-only deployment folder.

### 22.2 Optional private Java 21 runtime

A later build may ship a `jlink` runtime:

```text
CommercialTracking/
├── runtime/
├── app/
└── CommercialTracking.exe
```

This improves runtime control but increases package size and patching responsibility. The Java 8-compatible application should remain the initial deployment unless Java 8 behavior or vendor support proves insufficient.

## 23. Portable implementation alternative

### Recommended alternative: self-contained .NET desktop application

A Windows .NET 8 WPF application can publish as a self-contained folder or single-file executable. It does not require a machine-wide .NET installation or administrator privileges.

Advantages over Java 8:

- Better Windows accessibility and high-DPI behavior.
- Stronger integration with printing, file dialogs, notifications, and Windows identity.
- Straightforward native launcher and version information.
- Modern supported runtime shipped with the application.
- Good filesystem monitoring and JSON support in the platform.
- Easier generation of polished Windows operator interfaces.

Disadvantages:

- Larger deployment than using the existing Java 8 runtime.
- Windows-only.
- The team owns distribution of runtime security updates.
- Application-control policy may require signing or approval.

The synchronized event protocol in this specification is language-neutral. A .NET client can use exactly the same files and coexist with a Java client during migration testing.

### Other alternatives considered

| Technology | Portability | Suitability |
|---|---|---|
| .NET 8 WPF, self-contained | Excellent on Windows | Best alternative |
| Java 8 Swing | Excellent in this environment | Best smallest deployment |
| Java 21 with bundled JavaFX | Good | Polished UI, but larger and more packaging work |
| Go desktop application | Single binary possible | Good runtime story; desktop UI ecosystem less mature |
| Python embedded + Qt | Portable folder | Large dependency surface and weaker controlled deployment |
| Electron | Portable folder | Very large footprint for a scanner/data-entry application |
| Tauri/Rust | Small backend; WebView dependency | Strong but higher implementation complexity |
| Browser-only local application | No install | Browser filesystem and background monitoring constraints make it unsuitable |

## 24. Recommended implementation choice

Start with Java 8 Swing if:

- The existing Java 8 runtime is approved and consistently patched.
- Minimal deployment size is important.
- The development team is comfortable producing an accessible Swing interface.

Choose self-contained .NET 8 WPF if:

- All target devices are Windows.
- A larger portable folder is acceptable.
- UI polish, printing, and long-term framework support outweigh package size.

Do not choose based on storage compatibility; both implementations use the same event protocol.

## 25. Delivery phases

### Phase 0: synchronization spike

- Validate two clients writing unique immutable files.
- Measure propagation in both directions.
- Test offline creation and reconnect.
- Test 1,000 rapid small-file additions.
- Confirm SharePoint Created By metadata.
- Confirm OneDrive library and filename restrictions.

Exit criterion: no corruption or lost unique files, and propagation is operationally acceptable.

### Phase 1: technical prototype

- Folder selection and validation.
- Event writer and reader.
- Local index and deterministic replay.
- Scanner input.
- Parser pipeline, GS1 separator preservation, and fixture harness.
- Two-workstation live test.
- Pending and recovery views.

### Phase 2: inbound minimum viable product

- Receiving streams and locations.
- GS1 parsing and carrier/tracking normalization.
- UPS, FedEx, USPS, DHL, and generic parser fixtures.
- Operator confirmation for ambiguous or low-confidence fields.
- Session ledger.
- Duplicate detection.
- History search.
- Inbound manifest.

### Phase 3: complete Commercial Tracking parity

- Outbound release.
- Recipient reconciliation.
- Custody manifests.
- Void/correction workflows.
- Conflict resolution.
- Operational documentation.

### Phase 4: production hardening

- Accessibility review.
- Security review.
- Load and soak testing.
- Backup/retention review.
- Signed distribution, if required.
- Pilot, parallel operation, and cutover.

### Phase 5: optional carrier enrichment

- Confirm external-network and provider-account approval.
- Select Power Automate or an approved credentialed worker.
- Implement immutable request/result contracts.
- Integrate one carrier as a bounded pilot.
- Measure latency, reliability, rate limits, and metadata usefulness.
- Add providers only when returned data justifies credential and maintenance cost.

## 26. Acceptance criteria

- Two workstations can receive separate packages simultaneously without overwriting data.
- Both converge on the same history after synchronization.
- A duplicate same-package receive is detected after synchronization.
- Concurrent releases produce a visible conflict rather than silent data loss.
- No finalized event is edited or deleted during ordinary operation.
- Cache deletion and full replay reproduce the same package and manifest states.
- Offline events are retained and submitted after reconnecting.
- A malformed file does not stop application startup or event ingestion.
- Manifest content references exact immutable event IDs.
- Scanner operation requires no mouse use during a normal batch.
- Standards-encoded fixtures parse deterministically and preserve FNC1/group separators.
- Ambiguous carrier results require confirmation rather than a silent choice.
- The application remains fully usable with carrier enrichment disabled.
- No carrier credential is present in the client package, cache, event store, or logs.
- The application runs without installation or elevation on a representative managed workstation.
- No direct Microsoft API authentication is required.

## 27. Open decisions

1. Java 8 Swing or self-contained .NET 8 WPF for the pilot.
2. Whether PDF generation libraries are acceptable or printable HTML is required.
3. Required retention period.
4. Whether recipient names are permitted in synchronized event bodies.
5. Whether raw carrier barcode payloads may be retained.
6. How workstation/device IDs are assigned.
7. Who may perform corrections and how that workflow is operationally enforced.
8. Acceptable OneDrive propagation delay.
9. Whether configuration changes require signed files or only SharePoint permissions/version history.
10. Whether the inventory application will reuse the same event-store library in a later phase.
11. Which scanner symbologies and control-character transmission modes are enabled.
12. Whether a redacted sample-label corpus may be retained for regression testing.
13. Whether external carrier endpoints are reachable from an approved worker.
14. Whether carrier enrollment and terms permit this receiving use case.
