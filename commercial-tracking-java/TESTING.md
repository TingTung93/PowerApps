# Commercial Tracking RC Test Instructions

Version: 0.1.0-rc1

## Important

Use an empty, non-production Teams/SharePoint synchronized folder. This release candidate creates immutable JSON files and printable manifests. It does not update the existing SharePoint `Tracking` list.

## Setup

1. Synchronize the same pilot folder on both test workstations.
2. Copy the complete `dist` folder to each workstation.
3. Run `run-commercial-tracking.cmd`.
4. Select the synchronized pilot root when prompted.
5. Confirm each workstation displays a different device ID in its generated events.

Before opening the interface, the packaged binary can be verified from Command Prompt:

```text
java -jar CommercialTracking-RC.jar --self-test
```

Continue only if it reports `CommercialTracking RC self-test: PASS`.

## Core tests

### Inbound

1. Select `Inbound` and `Main Receiving`.
2. Scan a known UPS `1Z` barcode.
3. Confirm `SUCCESS` appears and the event is in Current Session.
4. Confirm the other workstation observes the package in Package History.
5. Repeat the scan at `Loading Dock`.
6. Confirm a warning indicates that the active package was retained and its location changed.

### Outbound

1. Wait until the receiving event is visible on the second workstation.
2. Select `Outbound`.
3. Scan the same package.
4. Confirm the package becomes `PICKED_UP`.
5. Scan it again and confirm the client refuses a normal second release.

### Offline

1. Pause OneDrive or disconnect the test workstation.
2. Receive a unique package.
3. Confirm the event appears in the local synchronized folder.
4. Resume OneDrive.
5. Confirm the other workstation eventually observes the event.

### Parsing

Test available examples of:

- UPS 1Z
- USPS IMpb
- FedEx numeric
- DHL 10-digit
- Amazon TBA/TBC/TBM
- FedEx/ANSI `[)>` 2D label
- GS1 label

Record the raw scanner output only if information-protection policy permits it. Prefer a redacted structural example. Verify carrier, tracking number, weight, package count, and addressee where present.

### Conflict

1. Pause synchronization on both workstations.
2. Receive the same synthetic tracking number on both.
3. Resume synchronization.
4. Confirm both clients display the duplicate receive in Conflicts after refresh.

### Manifest

1. Receive several synthetic packages in one session.
2. Select `Print inbound session manifest`.
3. Confirm a browser opens the generated HTML.
4. Verify location, package count, tracking values, timestamps, and device IDs.
5. Print or save to PDF using the browser.

## Failure reporting

Capture:

- The visible error text.
- The Diagnostics tab.
- Workstation/device name.
- Approximate time.
- Whether OneDrive was running.
- A redacted description of the barcode format.

Do not email raw recipient, address, tracking, or barcode data unless the approved support process permits it.
