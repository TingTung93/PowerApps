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

Events are append-only; archive, restore, catalog retirement, and distribution-list changes create
new audit events. The signed-in Windows account supplies immutable operator attribution. Deployments
that require individual attribution must provision a distinct Windows login for every operator.

If any discovered event cannot be validated, or a local write remains pending, the application shows
an incomplete-trail warning and refuses report export. Resolve the Diagnostics errors before relying
on inventory totals or reports.

The application reloads shared events before inventory mutations and rejects a removal above the
latest visible on-hand balance. OneDrive does not provide distributed transactions: if separate
workstations make truly simultaneous offline removals, replay can still discover a negative balance.
That condition is treated as an incomplete trail, blocks later writes and reports, and must be resolved
under the facility's audit-remediation procedure after reviewing both source events.

## Deployment, backup, and retention

Install the workstation kit under a user-writable local directory and point it at a locally synced,
always-available OneDrive/SharePoint folder. Enable “Always keep on this device” for the shared event
root so online-only placeholders cannot make replay incomplete. Do not run two active operators under
one shared Windows account.

Back up the complete shared root, including `events`, `configuration`, `reports`, and `diagnostics`.
Do not delete or rewrite individual event files. Retention periods are controlled by the facility’s
records policy; restoring a backup must restore the entire event set, not selected months. Per-user
pending writes live under `%LOCALAPPDATA%\MedicalSupply\pending` and should be included in workstation
recovery procedures until their count returns to zero.
