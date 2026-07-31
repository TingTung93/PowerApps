# Commercial Tracking RC

Portable Java 8-compatible desktop release candidate for package receiving and release using a OneDrive-synchronized Teams/SharePoint folder as an immutable event store.

## Build

Requirements:

- JDK 9 or newer to compile with `--release 8`.
- PowerShell.
- No Maven, Gradle, or downloaded dependencies.

```powershell
.\build.ps1
```

Output:

```text
dist/
├── CommercialTracking-RC.jar
├── run-commercial-tracking.cmd
└── TESTING.md
```

The produced JAR targets Java 8 bytecode and uses only Java SE APIs.

## Run

Copy the complete `dist` folder to a workstation and launch:

```text
run-commercial-tracking.cmd
```

On first run, select a locally synchronized empty pilot folder. Do not point the release candidate at production package records.

## RC scope

Included:

- Inbound and outbound scanner modes.
- Four receiving locations.
- UPS, USPS, FedEx, DHL, Amazon, ANSI MH10 `31Z`, GS1, generic, and application-reference parsing.
- Field confidence and confirmation for ambiguous values.
- Immutable JSON event writing.
- Cross-client event replay.
- Duplicate receive and competing release visibility.
- Current-session ledger.
- Derived package history.
- Recipient assignment and audited package voiding from history.
- Printable inbound session manifest.
- Offline/local pending protection during event commit.
- Malformed-event diagnostics.

Not included:

- Direct SharePoint or carrier API calls.
- Verified Microsoft identity.
- Strong cross-device locking.
- Automated recipient reconciliation editor.
- PDF generation independent of the system browser.
- Automated event retention or archival.

## Data locations

Shared events and manifests are written below the operator-selected synchronized root.

Per-user settings and temporary pending files are stored under:

```text
%LOCALAPPDATA%\CommercialTrackingRC
```

The shared store contains no API credentials.
