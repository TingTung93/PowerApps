# Commercial Tracking 0.2.0-rc1

Build date: 2026-07-30  
Runtime: Java 8 or newer  
Deployment: Portable folder; no installer or elevation

## Purpose

Monday release-candidate testing of the OneDrive/Teams synchronized immutable-event architecture and scanner workflow.

## Included

- Modern React/Material UI dashboard compiled into the JAR.
- Offline Roboto fonts and SVG Material icons.
- Loopback-only Java HTTP service with an ephemeral port.
- Random per-launch API token and request-origin enforcement.
- System-browser launch with Swing `--classic-ui` fallback.
- Inbound receiving at four locations.
- Outbound release using locally observed shared state.
- UPS, USPS, FedEx, DHL, Amazon, application-reference, ANSI MH10 `31Z`, and bounded GS1 parsing.
- Parser confidence, provenance, and confirmation for ambiguous identifiers.
- Immutable JSON event files partitioned by UTC year/month.
- Local pending file followed by `.partial` and finalized event commit.
- Cross-workstation event refresh every 15 seconds and on demand.
- Current-session ledger and derived package history.
- Duplicate receive and competing release conflict reporting.
- Recipient assignment and audited package voiding.
- Printable inbound-session HTML manifest.
- Malformed-event diagnostics.
- Packaged `--self-test`.

## Important limitations

- This RC does not update the production SharePoint `Tracking` list.
- Test workstations need Java 8 and a supported existing Edge/Chrome browser; they do not need Node, npm, React, MUI, or internet access.
- Use only an empty non-production synchronized test folder.
- OneDrive upload completion cannot be proven by ordinary filesystem access; the UI reports locally observed state.
- Cross-workstation updates are eventually consistent.
- Windows username is audit context, not verified Microsoft identity.
- Existing `PKGID-<SharePoint ID>` barcodes cannot resolve because the filesystem event store has no SharePoint numeric list IDs. `PKG|...|<tracking>` references are supported.
- Carrier APIs and secrets are not included.
- Carrier-specific 2D metadata needs validation against approved redacted scanner captures.
- Printable manifests use the system browser rather than a bundled PDF engine.
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
