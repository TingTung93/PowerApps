# Commercial Tracking UI/UX Specification

Status: Draft for workflow review  
Parent PRD: `java-commercial-tracking-spec.md`  
Target: Java 8 localhost application with bundled React/MUI browser interface

## 1. Experience objective

Commercial Tracking is a receiving appliance first and an accountability system second. A routine operator should be able to confirm the receiving location, scan repeatedly, and immediately understand each result without learning the underlying event or synchronization model.

The interface must provide deep manifest, reporting, correction, and configuration capability without putting that complexity in the routine scan path.

The governing UX test is:

> Can a new operator safely receive a normal package after being told only, “Confirm the location, then scan the barcode”?

## 2. User roles and needs

Roles describe workflows, not verified security identities. The portable client cannot prove Microsoft 365 identity.

### 2.1 Receiving operator

Needs to:

- Receive packages rapidly.
- See an unmistakable result after every scan.
- Correct a simple recipient omission.
- Review the packages handled in the current session.
- Prepare an inbound manifest at the end of a batch.
- Recover from a scanner or synchronization problem without losing work.

### 2.2 Release operator

Needs to:

- Find or scan an existing package.
- Verify recipient, location, and status before release.
- Record custody transfer with minimal ambiguity.
- Prepare recipient custody documentation when required.

### 2.3 Supervisor

Needs to:

- Search complete package and event history.
- Reconcile recipients.
- Review and resolve conflicts.
- Void or correct records with reasons.
- Find, inspect, and reprint audited manifests.
- Produce daily reporting extracts.
- Review diagnostics and effective settings.

### 2.4 Workstation custodian

Needs to:

- Select and validate the synchronized folder.
- Configure scanner timing and termination behavior.
- Set default location, time display, printer preferences, and feedback.
- Run scanner and storage health tests.

## 3. Information architecture

Use a persistent left navigation rail on standard workstation displays. Collapse it to labeled icons only when horizontal space is constrained; never use icon-only navigation without tooltips and accessible names.

```text
OPERATIONS
  Receive Packages          default
  Release Packages
  Current Session

ACCOUNTABILITY
  Package History
  Recipients
  Manifests
  Reports
  Attention                 conflicts, pending work, malformed input

ADMINISTRATION
  Settings
  Diagnostics
```

Receive and Release are separate destinations rather than a small mode selector. This prevents an accidental mode change from silently changing the meaning of the next scan. Switching into Release requires a clear page-title and color-neutral mode label; color alone must not distinguish modes.

Navigation badges are reserved for items requiring action:

- **Current Session:** number of package records, not event count.
- **Attention:** unresolved conflict plus overdue pending count.
- **Recipients:** active unassigned count when nonzero.

Do not display the total historical event count as a primary operational metric.

## 4. Application shell

### 4.1 Header

The header contains:

- Product name.
- Current workspace title.
- Compact storage state: `Saved locally`, `Submitted to shared folder`, or `Attention needed`.
- Overflow/help menu containing Help, About, Diagnostics, and Exit.

Device ID, full synchronized path, event counts, and refresh controls belong in Diagnostics or the overflow menu. They must not compete with scanning.

### 4.2 Page header

Each workspace has:

- Plain-language title.
- One-sentence task instruction.
- At most one visually primary page action.
- Optional compact filters below the title.

### 4.3 Status language

Use these result categories consistently:

| State | Heading | Meaning |
|---|---|---|
| Ready | `Ready to scan` | Input is focused and no action is pending |
| Capturing | `Reading scanner…` | Suffixless idle timer is active |
| Saving | `Saving package…` | Durable local commit is in progress |
| Success | `Package received` or `Package released` | Operation is durably saved locally |
| Needs review | `Check this package` | Operator confirmation or correction is required |
| Not saved | `Package was not saved` | The operation failed before durable local commit |

Avoid technical headings such as `INFO`, `WARNING`, `ERROR`, or `SUCCESS` in operator-facing results.

## 5. Receive Packages workspace

### 5.1 Layout

```text
┌─────────────────────────────────────────────────────────────────┐
│ Receive Packages                         Saved locally / shared │
│ Confirm the location, then scan each package.                   │
├─────────────────────────────────────────────────────────────────┤
│ Receiving location: [ Main Receiving ▾ ]   [Change]             │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ Scan package barcode                                      │ │
│ │ [ focused scanner input                                  ] │ │
│ │ Ready to scan                                               │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌ Last result ────────────────────────────────────────────────┐ │
│ │ ✓ Package received                                          │ │
│ │ UPS  •  1Z…6784                                             │ │
│ │ Main Receiving  •  Jul 30, 2026, 10:42 AM                  │ │
│ │ Recipient: Unassigned                 [Assign recipient]     │ │
│ └─────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│ Current session · 12 packages                        [View all] │
│ 10:42 AM  UPS    1Z…6784    Unassigned                         │
│ 10:41 AM  FedEx  78…1234    J. Smith                           │
└─────────────────────────────────────────────────────────────────┘
```

The scanner input and last result are the visual center. Summary cards, shared path, historical totals, manifest buttons, diagnostics, and destructive actions do not appear in this workspace.

### 5.2 Location behavior

- The location is required and appears above the scanner.
- Remember the last valid location on this workstation.
- On a normal return visit, show the remembered location and keep the scanner focused.
- A location change requires one explicit selection but no modal.
- If packages already exist in the session, show: `New scans will use Loading Dock. Existing records will not change.`
- Never infer a new location from another workstation's activity.

### 5.3 Optional recipient

Recipient entry is not permanently displayed in the scan form. After a successful receive, the result card offers **Assign recipient**. A configurable keyboard shortcut may open recipient entry before a scan for workflows that receive pre-sorted packages.

This choice keeps the happy path one-scan while still supporting known-recipient batches.

### 5.4 Result persistence

- Keep the last result visible until the next scan begins.
- When a new scan begins, reduce the prior result to a compact recent-session row.
- Never clear a failure automatically.
- Success may use a short sound; failure must use a distinct sound if sound is enabled.
- Include text and icon in addition to color.

### 5.5 Duplicate receive

For a scan that matches an active package:

```text
Check this package
This tracking number was already received at Main Receiving
on Jul 30, 2026, 9:18 AM.

[Keep existing record]    [Record location change…]
```

`Keep existing record` is the safe default and returns focus to scanning. Do not label a duplicate as a generic error.

### 5.6 Ambiguous barcode

Show only fields needed for confirmation:

- Proposed tracking number.
- Proposed carrier.
- Why confirmation is needed in plain language.
- Editable tracking number if policy permits.

Primary action: **Confirm and receive**.  
Secondary action: **Cancel—do not save**.

Never show parser class names, confidence enums, or raw metadata on the routine confirmation surface.

## 6. Release Packages workspace

Release is visually and navigationally distinct from Receive.

Workflow:

1. Scan or search tracking number.
2. Show a package verification card containing tracking, carrier, recipient, received location/time, and current status.
3. Require the operator to activate **Confirm release**.
4. Save the release event.
5. Show `Package released` and return focus.

Unlike inbound receiving, release is not zero-confirmation because the operator must verify the physical custody transfer. If the package is unassigned, conflicted, voided, or already released, replace the confirmation action with a specific resolution path.

The recipient name is visually prominent. The release control must never be enabled while the package lookup is ambiguous.

## 7. Current Session workspace

### 7.1 Default view

Show one row per tracking number representing current projected state:

- Received time in local compact form.
- Tracking number.
- Carrier.
- Location.
- Recipient or `Unassigned`.
- Current status.
- Shared-folder submission state only when attention is needed.

Default sort is most recently handled first. Selecting a row opens a side panel; it does not navigate away.

### 7.2 Package detail side panel

The panel contains:

- Package summary.
- Session actions appropriate to current state.
- A short activity timeline.
- **View complete history** link.

Actions are contextual. **Assign recipient** may be available. **Void package** sits in a clearly separated danger area and is not a table toolbar button.

### 7.3 Session Activity

Event-level detail is a secondary tab within Current Session:

- Plain event labels such as `Received`, `Recipient assigned`, `Location changed`, and `Released`.
- Local timestamp first; exact UTC and event ID in expandable audit details.
- No package action buttons on event rows.

### 7.4 Finish-session workflow

Primary action: **Finish receiving session**.

If unmanifested inbound packages exist:

1. Show the count and locations involved.
2. Offer **Prepare inbound manifest** as primary.
3. Offer **Keep session open**.
4. Put **Close without manifest** behind an additional explanation and confirmation.

Closing a session never deletes events.

## 8. Package History workspace

### 8.1 Search

One prominent search field searches tracking, manifest ID, carrier, recipient, location, status, and notes. Begin full search at three characters, but allow exact barcode scans immediately.

Advanced filters are collapsed under **Filters**:

- Date range.
- Status.
- Location.
- Carrier.
- Recipient assignment.
- Manifest state.

### 8.2 Results

- Default to a readable table on workstation displays.
- Keep tracking and status visible when horizontally constrained.
- Selecting a row opens a complete package timeline in a side panel.
- Provide copy actions for tracking and manifest ID.
- Do not expose immutable filenames in normal history.

## 9. Recipient Reconciliation workspace

Default view shows active unassigned packages, newest first.

Workflow:

1. Search or select one or more packages.
2. Enter/select a normalized recipient.
3. Review the package count and names.
4. Select **Assign recipient**.
5. Show a success summary and preserve an event for every affected package.

Bulk assignment requires review because a mistaken bulk recipient creates material audit cleanup. Recipient suggestions may come from recent local values, but a suggestion must never be committed without operator selection.

## 10. Manifests workspace

Use two tabs: **Prepare manifest** and **Manifest register**.

### 10.1 Prepare manifest

Step 1 — Choose purpose:

- Inbound receiving.
- Recipient custody.

Step 2 — Choose eligible packages:

- Inbound defaults to unmanifested packages from the current session.
- Custody requires one recipient and defaults to eligible active packages for that recipient.
- Filters remain compact and task-specific.

Step 3 — Review:

- Human-readable manifest ID.
- Type, location or recipient, prepared date/time, item count.
- Included package table with checkboxes.
- Explicit excluded-item count.
- Readiness banner: detailed, high-density, empty, or over-limit.

Step 4 — Finalize:

- Primary action: **Finalize and open print view**.
- Explain that finalization writes the audit record even if printing is later canceled.
- After finalization, show **Print**, **Save copy**, and **Return to register**.

Do not use a generic **Continue** label for finalization.

### 10.2 Manifest register

Search and filters:

- Manifest ID.
- Type.
- Local date range.
- Location.
- Recipient.
- Tracking number.

Selecting a manifest opens an immutable detail view containing membership, original preparation/finalization metadata, checksum status, and reprint. **Reprint** never creates a new manifest ID.

## 11. Reports workspace

Reports are generated on demand and are not audited manifests.

The page includes:

- Report type: Receiving Activity or Outbound/Custody Activity.
- Range preset: Day, Week, Month, or Custom.
- Local reporting range and operational time zone.
- Optional location, carrier, status, or recipient filters.
- Group-by and sort controls under **Layout options**.
- Live count and preview.

A persistent banner states:

`Reporting extract — creating or printing this list does not change package records.`

Primary action: **Create PDF / Print**.
Secondary actions: **Export CSV** and **Save copy to shared reports**.

Receiving Activity defaults to the current local day, grouped by location and sorted by received time. It includes received time, tracking, carrier, recipient, location, status, manifest ID, Windows account, and workstation, plus counts by location, carrier, assignment state, and status.

If all rows share one audited manifest ID, offer **Open audited manifest**.

## 12. Attention and recovery workspace

Combine problems requiring intervention into one prioritized queue:

1. Not durably saved.
2. Pending local events exceeding threshold.
3. Conflicting package operations.
4. Malformed synchronized records.
5. Informational diagnostics.

Each item answers:

- What happened?
- Is the package operation safely stored locally?
- What should the operator do?
- Does this require a supervisor?

Technical details are collapsed under **Audit details**. Retry is available only when safe and idempotent.

## 13. Settings and Diagnostics

### 13.1 Settings organization

Use a simple category list:

- Workstation.
- Scanner.
- Receiving defaults.
- Date and time.
- Printing and exports.
- Feedback and accessibility.
- Shared operational settings.

Place the synchronized-folder selector under Workstation, not on the Receive page. Show its health state and a **Test folder** action.

Scanner settings include a guided test:

1. Ask the user to scan the same test barcode three times.
2. Show whether a terminator was detected.
3. Measure timing without retaining the package value.
4. Recommend Automatic, Terminator, or Manual completion and a safe idle delay.
5. Let the user test before saving.

### 13.2 Shared settings

Shared settings show a strong scope label: `Applies to all workstations after synchronization`.

Saving requires:

- Summary of changes.
- Validation result.
- Confirmation.
- Immutable configuration audit event.

### 13.3 Diagnostics

Diagnostics contains:

- Application/runtime version.
- Device ID.
- Effective shared root.
- Effective settings and their source.
- Local cache/event health.
- Pending submission counts.
- Last shared-folder rescan.
- Redacted diagnostic export.

Diagnostics is not part of ordinary navigation emphasis.

## 14. Dialog and action rules

- Use a dialog only when the operator must decide before continuing.
- Use a side panel for inspection and contextual actions.
- Use inline expansion for optional filters and technical details.
- Use a toast only for low-risk confirmation that does not need to remain visible.
- Never put a destructive action beside a routine primary action with equal visual weight.
- Buttons describe outcomes: **Confirm release**, **Assign recipient**, **Finalize manifest**, **Void package**.
- `Cancel` always means no state change.
- Closing a dirty form requires confirmation.

## 15. Keyboard and scanner behavior

- Scanner focus is restored after completed receive/release actions and canceled scan dialogs.
- `Escape` closes a non-destructive dialog and returns scanner focus.
- `Enter` activates the primary action in ordinary forms but never bypasses a required destructive confirmation.
- `Tab` follows visual order and is not globally intercepted unless configured as the scanner terminator while the scan field is focused.
- Suffixless detection runs only in the focused scan field.
- Keyboard shortcuts must not use printable characters by themselves.
- A visible focus indicator is required on every interactive element.

## 16. Visual design choices

### 16.1 Character

Modern, calm, operational, and trustworthy. Avoid consumer-style decoration, dense dashboard chrome, and excessive gradients.

### 16.2 Color

- Primary navy/blue communicates structure and actions.
- Green is reserved for confirmed success and healthy states.
- Amber means review is required.
- Red means not saved, conflict, or destructive action.
- Neutral gray supports secondary metadata.
- Never encode state through color alone.

The application shell should use a solid or very subtle primary surface. Large multicolor gradients are avoided because they compete with operational status colors.

### 16.3 Typography and density

- Use bundled Roboto or a metrically stable bundled sans-serif.
- Base text: 14–16 px.
- Scanner input: at least 20 px.
- Last-result heading: 20–24 px.
- Tracking values use tabular or monospaced numerals where helpful.
- Tables use comfortable density by default; compact density is a user preference.

### 16.4 Spacing and surfaces

- Use an 8 px spacing system.
- Prefer one primary content surface per workspace.
- Avoid nesting cards inside cards.
- Use borders and spacing before shadows.
- Keep touch targets at least 40 px high even though keyboard/mouse is primary.

## 17. Responsive and workstation behavior

Primary target is 1366×768 through 1920×1080 at Windows scaling from 100% to 150%.

- At 1366×768, the scan input, last result, and at least three recent rows remain visible without page scrolling.
- Tables may scroll internally but page headers and filters remain stable.
- Below 900 px, the navigation rail collapses and detail panels become full-width overlays.
- Do not require hover for essential information.
- The browser zoom range from 90% to 125% must remain usable.

## 18. Accessibility

- Meet WCAG 2.1 AA contrast for text and controls.
- Every icon button has a visible tooltip and accessible name.
- Live scan results use an appropriately polite or assertive live region.
- Do not announce every scanner character to assistive technology; announce completion and result.
- Tables have real headers and keyboard-accessible row selection.
- Dialog focus is trapped and restored to the originating control.
- Motion is minimal and respects reduced-motion preference.
- Sound is optional and always duplicated visually.

## 19. UX validation plan

Test with representative operators and actual workstation/scanner combinations.

Core tasks:

1. First-run folder and scanner setup.
2. Receive ten normal packages without touching the mouse.
3. Receive with Enter, Tab, and no scanner suffix.
4. Handle a duplicate, ambiguous scan, and locally pending save.
5. Find a package received earlier that day.
6. Assign a recipient.
7. Finish a session and finalize an inbound manifest.
8. Reprint an existing manifest.
9. Produce a daily receiving extract without changing audit state.
10. Release a package after verifying its recipient.

Target outcomes:

- Zero unexplained scan losses.
- Zero duplicate commits from one physical scan.
- At least 95% first-attempt success on routine receiving.
- Median normal receive interaction is one scan and no mouse/keyboard action.
- Operators correctly distinguish package count from activity-event count.
- Operators correctly distinguish an audited manifest from a reporting extract.
- Operators can state whether a failed/pending scan was saved locally.

## 20. Explicit design decisions

The following choices are firm unless usability testing disproves them:

- Separate Receive and Release workspaces.
- Receive is the default startup destination.
- One current-state row per package; event history is separate.
- Recipient is not a required inbound field.
- Manifest creation is a reviewed, multi-step accountability workflow.
- Reports never mutate package or manifest audit state.
- Reports are always generated on demand; the application does not schedule or distribute them.
- Settings and diagnostics are removed from the routine scanner surface.
- Local time is primary for people; UTC remains available for audit.
- Browser/MUI is the default interface; Swing is fallback only.
- Scanner suffixes are optional.

## 21. Decisions requiring operational confirmation

1. Whether inbound manifests may combine more than one location.
2. Whether recipient custody manifests include released packages, active packages, or an explicit selection of both.
3. Whether release always requires confirmation or may use a supervisor-enabled rapid-release mode.
4. Whether recipient suggestions may be retained locally.
5. Required signature and certification wording.
6. Approved paper size, printer defaults, and export locations.
7. Whether routine operators may close a session without a manifest.
8. Whether shared-settings editing is exposed in the pilot or remains file-managed.
