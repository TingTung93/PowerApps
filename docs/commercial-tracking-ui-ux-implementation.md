# Commercial Tracking UI/UX Implementation

Status: Repository implementation complete; environment qualification pending
Source specification: `java-commercial-tracking-ui-ux-spec.md`

## Delivered interaction model

- A persistent navigation rail separates Operations, Accountability, and Administration.
- Receive Packages is the default workspace.
- Receive and Release are separate destinations; changing a small mode selector can no longer change the meaning of the next scan.
- Routine receiving contains only the required location, focused scanner input, durable-save result, and recent session packages.
- Technical identifiers, paths, event totals, refresh behavior, setup, and malformed-record details are confined to Settings, Diagnostics, and Attention.
- Operator result headings use `Ready to scan`, `Saving package…`, `Package received`, `Check this package`, and `Package was not saved`.
- Current Session shows one projected row per package and a separate event-activity tab.
- Package selection opens a contextual side panel. Recipient assignment is contextual and package voiding is isolated in a danger area.
- Local time is primary; exact UTC remains available as table-cell hover text or diagnostic data.

## Workspace behavior

| Workspace | Primary job | Current implementation |
|---|---|---|
| Receive Packages | One-scan inbound receiving | Functional |
| Release Packages | Verified outbound lookup/release | Verification card and explicit confirmation |
| Current Session | Reconcile the current batch | Package and activity tabs; manifest finish action |
| Package History | Find projected package state | Multi-field local search and detail panel |
| Recipients | Resolve active unassigned packages | Reviewed single/bulk assignment |
| Manifests | Prepare audited list | Inbound/custody preparation, immutable register, PDF/HTML, reprint |
| Reports | Explain non-mutating extracts | Live preview, filters/layout, PDF/HTML/CSV and shared save |
| Attention | Prioritize conflicts/malformed records | Pending retry, conflicts, malformed records, audit details |
| Settings | Workstation configuration | Scanner calibration, local preferences, shared revisions/rollback |
| Diagnostics | Technical support data | Runtime/config/cache health, rebuild, redacted export |

## Responsive and accessibility rules

- The rail becomes a labeled drawer below the desktop breakpoint.
- Tables scroll inside their workspace.
- Scan completion is announced through a live region; failures use an assertive announcement.
- Table rows support Enter-key selection.
- Icon-only controls have accessible names and tooltips.
- Focus returns to the scan input after actions and dialogs.
- Reduced-motion preference minimizes transitions and animations.
- Status always uses text and iconography in addition to color.

## Qualification boundary

The code-controlled UI/UX requirements are implemented. Physical scanner timing,
two-workstation synchronization, real display scaling, and assistive-technology
behavior must be signed off using the worksheets in
`commercial-tracking-java/qualification/`; these cannot be established by unit
tests or source inspection alone.
