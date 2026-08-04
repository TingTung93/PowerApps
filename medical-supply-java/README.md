# Medical Supply Tracking

Portable Java 8 medical-supply inventory tracker using a OneDrive-synchronized Teams/SharePoint
folder as an immutable event store. Mirrors the original PowerApps inventory tracker: scan-to-stock,
batch receiving, browse/search with expiry alerts, FDA GUDID-backed product registration, a
management dashboard with PAR/consumption reorder alerts, and printable QR labels.

The default interface is a precompiled React/MUI app served by Java over a loopback-only HTTP
endpoint; the SPA (React, MUI, fonts, QR generation) is embedded in the JAR. Workstations need only
Java 8 and a browser. Node/npm are development-time tools only.

## Build
Requires JDK 9+ (to compile `--release 8`), Node/npm (frontend), and PowerShell. No Maven/Gradle.

    .\build.ps1                 # full build (frontend + Java + tests + jar)
    .\build.ps1 -SkipFrontend   # Java only, reuse existing web assets

Output: `dist/MedicalSupply-RC.jar`, `run-medical-supply.cmd`, docs, `qualification/`.

## Run
Copy `dist` to a workstation and launch `run-medical-supply.cmd`. On first run, select the
synchronized folder. The service binds `127.0.0.1` with an ephemeral port and a random session token.

Classic Swing fallback: `run-medical-supply.cmd --classic-ui`.

## FDA GUDID
Unknown GTINs are looked up best-effort against the AccessGUDID Device Lookup API
(`https://accessgudid.nlm.nih.gov/api/v3/devices/lookup.json`). Lookups are optional; registration
works fully offline, and confirmed products are cached in the shared catalog for all workstations.
Disable lookups in per-user settings for air-gapped sites.

## Data
Shared events under the selected root; per-user settings and pending files under
`%LOCALAPPDATA%\MedicalSupply`. The shared store contains no credentials.
