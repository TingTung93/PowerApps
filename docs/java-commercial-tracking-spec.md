# Portable Commercial Tracking Application

## Product and Technical Specification

Status: Draft for review  
Target application: Commercial Package Tracking  
Primary runtime: Java 8  
Storage transport: User-authenticated OneDrive synchronization of a Teams/SharePoint document library  
Deployment model: Portable desktop application; no installer, elevation, tenant app registration, or direct Microsoft 365 API access

Companion UI/UX specification: `java-commercial-tracking-ui-ux-spec.md`

## 1. Purpose

Replace the Commercial Tracking Power App with a streamlined, exceptionally easy-to-use receiving application optimized for keyboard-wedge barcode scanners while retaining Teams/SharePoint Online as the shared storage system.

The product's primary outcome is trustworthy package accountability with the least practical operator effort. Receiving a normal package must feel like a single-purpose appliance: select a location once, scan, receive an unmistakable result, and continue. The application captures the audit trail automatically rather than asking the operator to understand the storage or event model.

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
- Make the common receiving path obvious to a first-time operator with minimal training.
- Keep routine receiving free of administrative, reporting, and audit-model complexity.
- Create a complete, human-readable accountability trail automatically for every accepted package operation.
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
- Exposing every available field or administrative option in the routine receiving workflow.
- Requiring operators to understand event sourcing, OneDrive synchronization internals, or manifest audit mechanics.

## 3.1 Governing product principles

When requirements compete, use this priority order:

1. **Correct package and custody record.**
2. **Fast, unmistakable routine receiving.**
3. **Recoverable audit and accountability trail.**
4. **Supervisor control and reporting depth.**
5. **Customization.**

Product decisions must follow these principles:

- **Progressive disclosure:** routine operators see only what is needed for the current task. Advanced manifest, reporting, correction, and configuration controls live in their own workspaces.
- **Safe defaults:** location, scan mode, time display, and printer behavior are remembered when safe. A normal scan should not require repeated selections.
- **One clear next action:** each operational screen emphasizes one primary action and avoids competing button clusters.
- **Automatic audit:** actor/device, timestamps, location, source event, and synchronization state are captured without extra operator data entry.
- **Recognition over recall:** use plain labels, visible status, recent activity, and package summaries rather than codes the operator must remember.
- **Errors must be actionable:** state what happened, whether the package was saved, and exactly what the operator should do next.
- **Advanced capability without default complexity:** a feature does not belong on the scanner simply because it exists elsewhere in the application.
- **No silent uncertainty:** ambiguous barcodes, conflicts, pending synchronization, and destructive corrections require clear confirmation or escalation.

## 4. Operating assumptions

- Windows is the initial target platform.
- The organization supplies Java 8 on every workstation.
- Each user can synchronize the designated Teams/SharePoint library through OneDrive.
- Each operator has write access to the shared application folder.
- The OneDrive client starts independently of this application.
- Normal operations involve no more than two concurrent users.
- The two users generally work in separate receiving locations or streams.
- Keyboard-wedge scanners may send Enter, Tab, another configurable suffix, or no suffix at all.
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

#### 14.2.1 Scanner input framing

The input layer must not require a CR, Enter, or Tab suffix. It supports four completion signals:

1. A configured terminator such as Enter, CR, Tab, or another non-data key.
2. A paste operation containing a complete barcode payload.
3. A short configurable idle period after a scanner-speed burst.
4. Explicit operator submission for manually typed or edited input.

In **Automatic** mode, a suffix completes the scan immediately when present. Without a suffix, the client waits for a quiet interval after the last character and submits only when the input resembles a scanner burst and the parser considers the payload complete or plausibly complete. The quiet interval prevents a fixed-length barcode from being submitted while additional scanner characters are still arriving.

The initial suffixless defaults are:

- Idle completion delay: 120 milliseconds after the last character.
- Scanner-burst inter-character threshold: 50 milliseconds.
- Minimum automatic-submit length: 6 characters.

These values are pilot defaults, not hard-coded protocol facts. They must be adjustable per workstation within safe bounds and validated using the actual deployed scanner. The application must tolerate slower scanners, remote-desktop input, and long 2D payloads without truncation.

Human typing must not be mistaken for a completed scan merely because the operator paused. Input that does not meet the scanner-burst rule remains visible and awaits Enter or the explicit **Process** action. Editing an automatically captured value cancels pending auto-submission until another qualifying burst or explicit submission occurs.

The input layer must:

- Preserve meaningful ASCII group, record, and file separators delivered by 2D scanners.
- Remove only configured framing characters; never strip data merely because it is non-printing.
- Treat CR/LF pairs as one terminator and suppress a trailing Tab/Enter from creating a second submission.
- Debounce duplicate suffixes and prevent the same buffered payload from being committed twice.
- Keep the buffer intact and show a recoverable message when parsing or saving fails.
- Display a subtle “capturing scan” state during the idle window without distracting the operator.
- Provide a scanner test panel that reports character count, terminator detected, elapsed duration, inter-character timing summary, and chosen completion reason. Raw payload display or export follows the diagnostic redaction policy.

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

- Provide a dedicated **Manifests** workspace; manifest creation, review, reprint, and export must not be hidden behind a scanner-screen button.
- Support two audited manifest types:
  - **Inbound receiving manifest**, assembled from exact unmanifested inbound event IDs in one or more explicitly selected local sessions.
  - **Recipient custody manifest**, assembled from exact active package revisions for one explicitly selected recipient.
- Show a review step before finalization with manifest type, location or recipient, package count, included packages, excluded packages, prepared time, and the proposed manifest ID.
- Permit operators to include or exclude individual eligible packages before finalization. Exclusions do not modify or void package records.
- Prevent a package revision from being silently assigned to two audited manifests of the same operational type. A reprint retains the original manifest ID and package membership.
- Assign a collision-resistant, human-readable manifest ID such as `MNF-YYYYMMDD-HHMMSS-XXXXXX`.
- Record the exact event IDs or package revisions, manifest type, creator/device, local prepared time, UTC prepared time, finalized time, output format, and document checksum.
- Write the audit event and finalized document before reporting success. If the print dialog is canceled, the manifest remains finalized and is available for reprint.
- Provide a searchable manifest register with filters for manifest ID, type, date range, location, recipient, creator/device, and package tracking number.
- Allow preview, reprint, and approved-format export from the register. Reprinting must not create a new manifest or alter membership.
- Produce PDF when a suitable bundled Java PDF library is approved.
- Otherwise produce self-contained printable HTML and open the system browser/print dialog.
- Store finalized documents under `manifests/<year>/<type>/`.
- Store a SHA-256 checksum in `MANIFEST_PRINTED`.
- Preserve layouts comparable to the Power App:
  - 1–20 packages use a detailed layout with one package per row and a compact scannable package reference where supported.
  - 21–100 packages use a four-column high-density accountability index and omit individual barcodes.
  - More than 100 packages cannot be finalized as one audited manifest; the UI must help split the selection into batches of 100 or fewer.
- Include the manifest ID, document type, location or recipient, prepared date/time, package count, page numbering, and signature/certification blocks.
- Render user-facing dates in the workstation's configured local time and retain exact UTC timestamps in audit metadata.

### 14.10 Daily packing lists and reporting extracts

- Provide a dedicated **Daily Lists** workspace for operational reporting that does not alter package or manifest audit state.
- Support a **Daily Receiving** extract filtered by local calendar date, optional location, carrier, recipient assignment state, and package status.
- Support an **Outbound/Custody** extract filtered by local calendar date, recipient, location, and release status.
- Allow an operator to choose visible columns, sort order, grouping, and whether summary totals are included.
- Provide an on-screen preview and printable HTML output; CSV export may be enabled by configuration.
- Clearly label an ad-hoc daily list as **Reporting Extract**, never **Audited Manifest**.
- A reporting extract must not assign a manifest ID, mark packages manifested, or emit `MANIFEST_PRINTED`.
- If every selected row belongs to the same existing audited manifest, the UI may offer **Open audited manifest** instead of presenting the extract as authoritative.
- Date boundaries use the configured operational time zone. The preview must show that time zone and the exact inclusive date/time range used.
- Daily extracts may span more than 100 packages and paginate normally; the audited-manifest 100-package limit does not apply.
- Saved extract preferences are local settings. Generated extract files are temporary unless the operator explicitly selects **Save copy**.

### 14.11 Optional carrier enrichment

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

The default interface is a modern browser UI embedded in the Java 8 application and served only on a random localhost port. All web assets are bundled in the application; the workstation does not require Node.js, an installed web server, or internet access. Java Swing remains an emergency compatibility fallback.

The information architecture has three levels:

1. **Operations:** Receive/Release and the current session. This is the default workspace and remains visually dominant.
2. **Accountability:** Package History, Recipient Reconciliation, Manifests, Daily Lists, and Conflicts/Recovery.
3. **Administration:** Settings and Diagnostics.

Advanced workspaces must not add fields or persistent action bars to the scanner. They are reached through simple top-level navigation and return the operator to a scan-ready state when closed.

### 15.1 Primary receiving journey

For a recognized, non-duplicate inbound barcode with a previously selected location:

1. The scan field already has focus.
2. The operator scans once; a configured suffix or suffixless idle detection submits it.
3. The application parses, validates, and durably saves the event.
4. A large success result shows tracking number, carrier, location, local received time, and whether a recipient is assigned.
5. Focus returns to the scan field automatically.

No additional click, modal confirmation, recipient selection, manifest selection, or audit-field entry is allowed on this happy path. Confirmation appears only for ambiguity, duplicates requiring a decision, conflicts, or policy-controlled exceptions.

The main screen shows one current-state row per tracking number. Event-level changes such as recipient assignment appear in a separate **Session Activity** audit view so operators do not mistake multiple events for multiple packages.

Scanner workflow requirements:

- A scan must be processable without mouse input.
- Enter and Tab can submit when configured, but neither is required.
- Suffixless scanner bursts submit automatically after the configured quiet interval.
- Manual typing remains available and requires Enter or the explicit **Process** action unless it independently meets the scanner-burst rule.
- Focus returns to the scan field.
- Scan processing must never freeze the UI.
- Duplicate suffix events and repeated submissions of the same input buffer must be debounced.
- The visible result remains until the next scan.
- Color is never the only indication of status.
- An operator can copy the last tracking number and error details.
- User-facing timestamps use a compact local date/time format; exact UTC is available in record details and on hover where practical.
- Routine success messages use plain operational language and do not expose event IDs, filenames, or synchronization implementation details.
- Pending local upload is visible but does not falsely present a locally durable scan as lost.

### 15.2 Usability acceptance

- A new operator can receive a normal package after being told only: “Confirm the location, then scan the barcode.”
- After initial setup, routine inbound receiving requires zero mouse clicks per recognized package.
- The normal one-scan workflow works with scanners configured with Enter, Tab, or no suffix.
- A returning operator can begin scanning without reopening settings.
- The operator can tell within one second whether the last scan succeeded, needs attention, or was not saved.
- The current-session package count cannot be confused with the number of audit events.
- Manifest and daily-list controls are discoverable from navigation but do not appear as competing primary actions beside the scan field.
- Destructive or exceptional actions use plain-language confirmation and state their audit effect.

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

The browser UI provides a dedicated **Settings** workspace. Settings are divided by scope so operators can tell whether a change affects only their workstation or every synchronized client.

### 18.1 Workstation settings

Workstation settings are stored under the current user's profile and never synchronized unless exported intentionally. The operator can configure:

- Synchronized data-root folder and a read/write/latency health check.
- Default scan mode, receiving location, and whether scanner focus is restored automatically.
- Scanner completion mode (`Automatic`, `Terminator`, or `Manual`), accepted terminators, suffixless idle delay, burst timing threshold, and minimum automatic-submit length.
- A guided scanner test that recommends workstation timing values without retaining package data.
- Operational time zone, date/time display style, and 12-hour or 24-hour clock.
- Default printer behavior, print-preview preference, paper size, and manifest scale guidance.
- Sound and visual acknowledgement preferences.
- History page size, default filters, and remembered table columns.
- Optional CSV export enablement and the default local export folder.
- Diagnostic verbosity and redacted diagnostic export.

Changing the synchronized data root requires validation and an explicit confirmation. The UI must explain that it changes where this workstation reads and writes shared records; it does not migrate existing data.

### 18.2 Shared operational settings

Shared settings live below `config/` in the synchronized root and include:

- Enabled receiving locations and stable location IDs.
- Manifest numbering prefix and layout thresholds.
- Enabled carrier parsers and confirmation thresholds.
- Retention/display policy metadata.
- Optional supervisor workflow allowlist.
- Feature switches for custody manifests, daily extracts, CSV export, and carrier enrichment.

Shared settings changes must use immutable versioned configuration documents, identify the authoring device, retain the previous valid version, and produce a configuration audit event. Because the client cannot verify Microsoft 365 identity, an application allowlist is a workflow control rather than a security boundary.

The settings UI must support previewing a proposed change, validation, and rollback to a prior valid version. Ordinary operators may view effective shared settings even when editing is disabled.

### 18.3 Configuration precedence

Configuration resolves in this order:

1. Built-in safe defaults.
2. Latest valid shared operational configuration.
3. Workstation-specific preferences for fields explicitly allowed to vary locally.

The Diagnostics view shows the effective value and source for each setting. An invalid shared configuration is quarantined and the last known valid configuration remains active.

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
  "maximumManifestItems": 100,
  "manifestIdPrefix": "MNF",
  "dailyCsvExportEnabled": false,
  "custodyManifestsEnabled": true
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
- Audited manifest preview supports explicit package membership review before finalization.
- Reprinting an audited manifest preserves its ID, membership, and original audit record.
- A daily packing list is visibly labeled as a reporting extract and does not mutate package or manifest state.
- Daily date filters use the configured operational time zone and show their effective range.
- Workstation settings survive restart without modifying shared settings.
- Invalid shared configuration leaves the last known valid configuration active and visible in Diagnostics.
- A first-time operator can complete a normal receive with only location confirmation and one barcode scan.
- After setup, a recognized normal inbound package requires no mouse interaction or audit-field entry.
- Representative scanners configured with Enter, Tab, and no suffix each complete exactly one scan without truncation or duplicate submission.
- A suffixless 2D scan containing meaningful separators remains byte-for-byte intact through input framing.
- Normal human typing and pauses do not cause premature automatic submission.
- Slow-scanner and remote-desktop timing fixtures can be accommodated through workstation settings without code changes.
- The last-scan result unambiguously states success, attention required, or not saved.
- The default scanner workspace does not expose manifest configuration, daily-report configuration, or shared administration controls.
- Current Session displays one package row per tracking number while Session Activity preserves every audit event.
- Scanner operation requires no mouse use during a normal batch.
- Standards-encoded fixtures parse deterministically and preserve FNC1/group separators.
- Ambiguous carrier results require confirmation rather than a silent choice.
- The application remains fully usable with carrier enrichment disabled.
- No carrier credential is present in the client package, cache, event store, or logs.
- The application runs without installation or elevation on a representative managed workstation.
- No direct Microsoft API authentication is required.

## 27. Open decisions

1. Whether PDF generation libraries are acceptable or printable HTML is required.
2. Required retention period.
3. Whether recipient names are permitted in synchronized event bodies.
4. Whether raw carrier barcode payloads may be retained.
5. How workstation/device IDs are assigned.
6. Who may perform corrections and shared-settings changes, and how those workflows are operationally enforced.
7. Acceptable OneDrive propagation delay.
8. Whether shared configuration changes require signed files or only SharePoint permissions/version history.
9. Whether daily CSV export is permitted and which columns may contain recipient information.
10. Required daily packing-list variants, grouping, columns, and certification language.
11. Whether saved reporting extracts belong in synchronized storage or only an operator-selected local folder.
12. Whether the inventory application will reuse the same event-store library in a later phase.
13. Which scanner symbologies and control-character transmission modes are enabled.
14. Whether a redacted sample-label corpus may be retained for regression testing.
15. Whether external carrier endpoints are reachable from an approved worker.
16. Whether carrier enrollment and terms permit this receiving use case.
