# Commercial Tracking

Portable Java 8-compatible release candidate for package receiving and release using a OneDrive-synchronized Teams/SharePoint folder as an immutable event store.

The default interface is a precompiled React/Material UI application served by Java over a loopback-only HTTP endpoint. React, MUI, fonts, icons, CSS, and JavaScript are embedded in the JAR. Test workstations need only Java 8 and an existing modern browser; Node and npm are development-time tools only.

## Build

Requirements:

- JDK 9 or newer to compile with `--release 8`.
- Node and npm on the development computer to compile the MUI frontend.
- PowerShell.
- No Maven or Gradle.

```powershell
.\build.ps1

# Build beside a running/locked release
.\build.ps1 -OutputDirectory dist-review
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

On first run, select the synchronized `CommercialTracking` root. The application validates and probes the folder before saving it.

The default launcher opens the modern MUI interface in the system browser. The local service binds only to `127.0.0.1` with an ephemeral port and random session token.

Classic Swing fallback:

```text
run-commercial-tracking.cmd --classic-ui
```

## Implemented scope

Included:

- Offline bundled Material UI workspaces for receiving, release, current session,
  history, recipient reconciliation, manifests, reports, attention/recovery,
  settings, and diagnostics.
- Automatic, terminator, paste, and manual scanner completion with guided
  calibration and workstation-specific timing.
- Configurable shared receiving locations.
- UPS, USPS, FedEx, DHL, Amazon, ANSI MH10 `31Z`, GS1, generic, and application-reference parsing.
- Field confidence and confirmation for ambiguous values.
- Immutable JSON event writing.
- Cross-client event replay.
- Duplicate receive and competing release visibility.
- Current-session ledger.
- Derived package history.
- Recipient assignment and audited package voiding from history.
- Audited inbound and custody manifests with immutable membership, checksums,
  PDF/HTML output, register, and reprint.
- Event-derived day/week/month/custom receiving and custody reports with
  PDF/HTML/CSV output.
- Offline/local pending protection during event commit.
- Local rebuildable event index, background folder monitoring, malformed-event
  diagnostics, conflict resolution, and shared-settings rollback.

Architecture boundaries:

- Direct SharePoint or carrier API calls.
- Verified Microsoft identity.
- Strong cross-device locking.
- Authoritative proof of OneDrive upload completion.
- Automated event retention or archival (events are immutable).
- Carrier enrichment unless an approved provider is configured later.

## Data locations

Shared events and manifests are written below the operator-selected synchronized root.

Per-user settings and temporary pending files are stored under:

```text
%LOCALAPPDATA%\CommercialTracking
```

The shared store contains no API credentials.
