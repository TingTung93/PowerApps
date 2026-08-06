# Commercial Tracking 0.2.0-rc1

Build date: 2026-07-30
Runtime: Java 8 or newer
Deployment: Portable folder; no installer or elevation

## Purpose

Specification-complete repository candidate for qualification of the
OneDrive/Teams synchronized immutable-event architecture and scanner workflow.

## Included

- Modern React/Material UI task workspaces compiled into the JAR.
- Offline Roboto fonts and SVG Material icons.
- Loopback-only Java HTTP service with an ephemeral port.
- Random per-launch API token and request-origin enforcement.
- System-browser launch with Swing `--classic-ui` fallback.
- First-run shared-root validation, harmless write probe, device identity, and
  initial cache rebuild.
- Inbound receiving and verified, explicitly confirmed outbound release.
- Scanner automatic/terminator/manual completion, suffixless burst capture,
  paste capture, edit cancellation, and calibration.
- UPS, USPS, FedEx, DHL, Amazon, application-reference, ANSI MH10 `31Z`, and bounded GS1 parsing.
- Parser confidence, provenance, and confirmation for ambiguous identifiers.
- Immutable JSON event files partitioned by UTC year/month.
- Local pending file followed by `.partial` and finalized event commit.
- WatchService notification plus 15-second reconciliation and safe pending retry.
- Current-session ledger and derived package history.
- Duplicate receive and competing release conflict reporting.
- Recipient assignment and audited package voiding.
- Audited inbound and custody manifest PDF/HTML output, checksums, register,
  exact membership, and reprint.
- Day/week/month/custom receiving and custody report PDF/HTML/CSV output.
- Shared operational settings validation, immutable audit, last-valid fallback,
  and rollback.
- Rebuildable checksummed local event index and redacted diagnostics export.
- Malformed-event diagnostics.
- Packaged `--self-test`.
- Packaged-browser receive, recipient-assignment, and verified-release smoke
  workflow.
- Automated 100,000-event projection performance smoke gate.

## Important limitations

- This RC does not update the production SharePoint `Tracking` list.
- Test workstations need Java 8 and a supported existing Edge/Chrome browser; they do not need Node, npm, React, MUI, or internet access.
- Use only an empty non-production synchronized test folder.
- OneDrive upload completion cannot be proven by ordinary filesystem access; the UI reports locally observed state.
- Cross-workstation updates are eventually consistent.
- Windows username is audit context, not verified Microsoft identity.
- `PKGID-<tracking>` and `PKG|...|<tracking>` application references are supported.
- Carrier APIs and secrets are not included.
- Carrier-specific 2D metadata needs validation against approved redacted scanner captures.
- PDF files are generated locally; HTML remains an accessible print fallback.
- No automated retention or archive maintenance is included.

## Stop-test conditions

Stop testing and retain the affected files if:

- A finalized event disappears or changes content.
- Two clients with the same shared event set derive different package status.
- A scan reports success without creating a finalized JSON event.
- OneDrive creates conflict copies for uniquely named event files.
- The application exposes raw sensitive label data unexpectedly.

## Verification

From the extracted release folder:

```text
java -jar CommercialTracking-RC.jar --self-test
```

Expected result:

```text
CommercialTracking RC self-test: PASS
```
